package com.doit.app.sync

/**
 * 双向合并的核心，纯 Kotlin、无 Android 依赖。
 *
 * 三个输入决定一条记录该取谁：
 * - local / remote：两边的当前全量快照（含墓碑）
 * - base：上一次**成功同步**时的共同快照，用来做字段级三方合并
 * - lastSyncAt：上一次成功同步时间；base 缺失时用它退化为「谁在上次同步之后改过」
 *
 * 冲突规则（全部确定性，重复跑结果一致）：
 * 1. 只在单边出现的记录一律保留 —— 合并是并集，绝不拿本地全量替换远程。
 * 2. **本地在同步后改过、远程与 base 完全一致 → 整条以本地为准**。这条是硬保证：
 *    只要远程没动过，本机刚做的任何改动（含时间戳缺失的老记录）都不会被远程覆盖。
 * 3. 同一字段只有一边相对 base 变了 → 取改过的那边；两边改了不同字段 → 两边都保留。
 * 4. 同一字段两边都改且值不同 → 取 effectiveTime 较新的一边；
 *    时间相同或都缺失 → 远程优先（远程始终是最不该被覆盖的一方）。
 * 5. 删除 vs 编辑：删除发生在上次成功同步之后、且活记录没有同步后的编辑 → 删除生效；
 *    双方都动过则比较时间，删除严格更晚才生效；时间相同/缺失一律保留活记录，
 *    宁可多留也不误删。旧版 v1 记录没有时间戳，不能再靠 `hasTimestamp` 一票否决删除。
 * 6. 远程文件版本落后（缓存 / 未覆盖成功）→ 忽略它的旧值并强制重新发布本机数据。
 */
object SyncMerge {

    /** 允许设备间时钟偏差，超过这个差值才认定「远程文件是旧副本」 */
    private const val CLOCK_SKEW_MS = 10L * 60L * 1000L

    fun merge(
        local: SyncSnapshot,
        remote: SyncSnapshot?,
        ctx: SyncContext,
        now: Long,
        deviceId: String,
    ): SyncOutcome {
        val strategy = decide(local, remote, ctx)

        val (tasks, taskConflicts) = when (strategy) {
            SyncStrategy.FIRST_SYNC_REMOTE_WINS -> remote!!.tasks.sortedBy { it.uid } to 0
            SyncStrategy.PUBLISH_LOCAL -> local.tasks.sortedBy { it.uid } to 0
            else -> mergeTasks(
                local.tasks,
                remote?.tasks.orEmpty(),
                ctx.base?.tasks,
                ctx.lastSyncAt,
            )
        }
        val (categories, catConflicts) = when (strategy) {
            SyncStrategy.FIRST_SYNC_REMOTE_WINS -> remote!!.categories.sortedBy { it.name } to 0
            SyncStrategy.PUBLISH_LOCAL -> local.categories.sortedBy { it.name } to 0
            else -> mergeCategories(
                local.categories,
                remote?.categories.orEmpty(),
                ctx.base?.categories,
                ctx.lastSyncAt,
            )
        }

        val conflicts = taskConflicts + catConflicts
        // fingerprint 只看记录内容，不看 revision / exportedAt / deviceId，
        // 所以「内容没变就重复同步」不会触发新的 PUT —— 幂等靠这一条。
        val body = SyncSnapshot(
            schemaVersion = SyncSnapshot.SCHEMA_VERSION,
            revision = 0,
            exportedAt = 0,
            deviceId = "",
            lastSyncAt = 0,
            tasks = tasks,
            categories = categories,
        )
        val needUpload = ctx.staleRemote || fingerprint(body) != (remote?.let { fingerprint(it) } ?: "")
        val nextRevision = if (needUpload) {
            maxOf(ctx.localRevision, remote?.revision ?: 0L) + 1
        } else {
            maxOf(ctx.localRevision, remote?.revision ?: 0L)
        }

        val merged = body.copy(
            revision = nextRevision,
            exportedAt = now,
            deviceId = deviceId,
            lastSyncAt = now,
        )
        return SyncOutcome(
            merged = merged,
            strategy = strategy,
            changedForLocal = countChanged(local, body),
            needUpload = needUpload,
            conflicts = conflicts,
            staleRemote = ctx.staleRemote,
        )
    }

    private fun decide(local: SyncSnapshot, remote: SyncSnapshot?, ctx: SyncContext): SyncStrategy = when {
        remote == null -> SyncStrategy.PUBLISH_LOCAL
        // 新装 APP：没有同步时间、远程已有数据、本地没有自己的历史 → 远程为准
        ctx.lastSyncAt == 0L && remote.hasData && !local.hasData -> SyncStrategy.FIRST_SYNC_REMOTE_WINS
        // 新装 APP 但用户已经先建了几条：并集，两边都不丢
        ctx.lastSyncAt == 0L && remote.hasData -> SyncStrategy.FIRST_SYNC_UNION
        else -> SyncStrategy.MERGE
    }

    /**
     * 远程文件是不是「上一次成功同步之前」的旧副本。
     * 命中时不能拿它参与三方比较：否则旧值会被当成远程新改动，把本机改动回滚掉。
     */
    fun isStaleRemote(remote: SyncSnapshot, lastSyncAt: Long, localRevision: Long): Boolean {
        if (lastSyncAt <= 0L) return false
        // 远程版本比本机还新，说明别的设备确实写过，绝不能当旧副本忽略
        if (remote.revision > localRevision) return false
        // 上一轮我们已经写过 v2 文件；还读回 v1 说明服务器返回的是旧内容
        if (remote.schemaVersion < SyncSnapshot.SCHEMA_VERSION) return true
        // 我们自己写的 revision 单调递增，读到更小的必然是旧副本
        if (remote.revision > 0L && remote.revision < localRevision) return true
        // 兜底：其它设备时钟可能略慢，留出 10 分钟容差
        return remote.exportedAt > 0L && remote.exportedAt + CLOCK_SKEW_MS < lastSyncAt
    }

    // ---------------------------------------------------------------- 任务

    private fun mergeTasks(
        local: List<SyncTask>,
        remote: List<SyncTask>,
        baseList: List<SyncTask>?,
        lastSyncAt: Long,
    ): Pair<List<SyncTask>, Int> {
        val l = local.associateBy { it.uid }
        val r = remote.associateBy { it.uid }
        val b = baseList?.associateBy { it.uid }
        val uids = (l.keys + r.keys + b?.keys.orEmpty()).toSortedSet()
        var conflicts = 0
        val out = mutableListOf<SyncTask>()
        for (uid in uids) {
            val lv = l[uid]
            val rv = r[uid]
            when {
                lv == null && rv == null -> Unit          // 只在 base 里出现过，双方都没有了
                lv == null -> out += rv!!                 // 远程独有 → 拉回本地
                rv == null -> out += lv                  // 本地独有 → 推到远程
                else -> {
                    val (one, c) = mergeTask(lv, rv, b?.get(uid), lastSyncAt)
                    out += one
                    conflicts += c
                }
            }
        }
        return out.sortedBy { it.uid } to conflicts
    }

    private fun mergeTask(l: SyncTask, r: SyncTask, b: SyncTask?, lastSyncAt: Long): Pair<SyncTask, Int> {
        val lDel = l.deletedAt
        val rDel = r.deletedAt
        if (lDel == null && rDel == null) return mergeTaskFields(l, r, b, lastSyncAt)

        // 两边都删了：取较晚的墓碑
        if (lDel != null && rDel != null) return (if (lDel >= rDel) l else r) to 0

        val dead = if (lDel != null) l else r
        val live = if (lDel != null) r else l
        val deadAt = dead.deletedAt!!

        // 活记录在「上次成功同步之后」有没有实质编辑。
        // 有 base 时直接比内容；没有 base 时退化为时间戳判断，时间戳缺失视为没编辑过。
        val liveEditedSinceSync = when {
            b != null -> b.isDeleted || live.content() != b.content()
            else -> live.effectiveTime > lastSyncAt
        }
        // 从未成功同步过时没有任何基线，无法判断删除是不是「最近」发生的，保守保留活记录
        val deletedSinceSync = lastSyncAt > 0L && deadAt > lastSyncAt

        val deleteWins = when {
            // 删除发生在同步之后、对方没编辑过 → 删除生效（旧版无时间戳记录也走这里）
            !liveEditedSinceSync && deletedSinceSync -> true
            // 双方都动过：只有删除时间严格更晚、且对方时间可信时才删
            live.hasTimestamp && deadAt > live.effectiveTime -> true
            else -> false
        }
        if (deleteWins) {
            return dead.copy(
                deletedAt = deadAt,
                updatedAt = maxOf(dead.updatedAt, deadAt),
            ) to (if (liveEditedSinceSync) 1 else 0)
        }

        // 删除没能胜出：保留活着那一侧。若活记录正是本机，本机改动原样留下；
        // 若本机是墓碑，则本机删除被撤销，拿远程活记录回填，并记一次冲突。
        val survivor = if (l.isDeleted) r else l
        return survivor.copy(deletedAt = null) to 1
    }

    private fun mergeTaskFields(l: SyncTask, r: SyncTask, b: SyncTask?, lastSyncAt: Long): Pair<SyncTask, Int> {
        // 硬保证：本机同步后改过、远程还停在上次同步的样子 → 整条以本机为准。
        // 不能逐字段走 tie()：老记录时间戳为 0 时，tie 会把本机改动判给远程。
        if (b != null && l.content() != b.content() && r.content() == b.content()) {
            return l to 0
        }

        var conflicts = 0
        val lt = l.effectiveTime
        val rt = r.effectiveTime
        val hasBase = b != null

        fun <T> pick(lv: T, rv: T, bv: T?): T {
            if (lv == rv) return lv
            val lChanged = if (hasBase) lv != bv else lt > lastSyncAt
            val rChanged = if (hasBase) rv != bv else rt > lastSyncAt
            return when {
                lChanged && !rChanged -> lv
                !lChanged && rChanged -> rv
                !lChanged && !rChanged -> tie(lv, rv, lt, rt)
                else -> {
                    conflicts++
                    tie(lv, rv, lt, rt)
                }
            }
        }

        val created = listOf(l.createdAt, r.createdAt).filter { it > 0 }.minOrNull() ?: 0L
        return l.copy(
            parentUid = pick(l.parentUid, r.parentUid, b?.parentUid),
            title = pick(l.title, r.title, b?.title),
            category = pick(l.category, r.category, b?.category),
            priority = pick(l.priority, r.priority, b?.priority),
            timeLabel = pick(l.timeLabel, r.timeLabel, b?.timeLabel),
            done = pick(l.done, r.done, b?.done),
            completedAt = pick(l.completedAt, r.completedAt, b?.completedAt),
            createdAt = created,
            updatedAt = maxOf(l.updatedAt, r.updatedAt),
            deletedAt = null,
            sortOrder = pick(l.sortOrder, r.sortOrder, b?.sortOrder),
            depth = pick(l.depth, r.depth, b?.depth),
        ) to conflicts
    }

    // ---------------------------------------------------------------- 分类

    private fun mergeCategories(
        local: List<SyncCategory>,
        remote: List<SyncCategory>,
        baseList: List<SyncCategory>?,
        lastSyncAt: Long,
    ): Pair<List<SyncCategory>, Int> {
        val l = local.associateBy { it.name }
        val r = remote.associateBy { it.name }
        val b = baseList?.associateBy { it.name }
        val names = (l.keys + r.keys + b?.keys.orEmpty()).toSortedSet()
        var conflicts = 0
        val out = mutableListOf<SyncCategory>()
        for (name in names) {
            val lv = l[name]
            val rv = r[name]
            when {
                lv == null && rv == null -> Unit
                lv == null -> out += rv!!
                rv == null -> out += lv
                else -> {
                    val (one, c) = mergeCategory(lv, rv, b?.get(name), lastSyncAt)
                    out += one
                    conflicts += c
                }
            }
        }
        return out.sortedBy { it.name } to conflicts
    }

    private fun mergeCategory(
        l: SyncCategory,
        r: SyncCategory,
        b: SyncCategory?,
        lastSyncAt: Long,
    ): Pair<SyncCategory, Int> {
        val lDel = l.deletedAt
        val rDel = r.deletedAt
        if (lDel == null && rDel == null) return mergeCategoryFields(l, r, b, lastSyncAt)
        if (lDel != null && rDel != null) {
            // 两边都是墓碑：保留删除，取较新的墓碑时间戳（其它字段取较新侧，保证颜色/排序不回退）
            val newer = if (lDel >= rDel) l else r
            return newer.copy(updatedAt = maxOf(l.updatedAt, r.updatedAt)) to 0
        }

        val dead = if (lDel != null) l else r
        val live = if (lDel != null) r else l
        val deadAt = dead.deletedAt!!
        // 「本地是否真的编辑过该分类」—— 只看用户可见内容（名称/颜色/排序）相对 base 有没有变化。
        // 刻意排除 updatedAt / deletedAt：服务端回写或上次同步落库会刷 updatedAt，
        // 但它不代表用户在手机端有编辑，不能因为时间戳"看着新"就把已删分类复活。
        // 有 base 就严格比内容；没有 base（首次/基线丢失）无法证伪本地编辑，保守让删除传播。
        val liveEditedSinceSync = b?.let { live.visibleContent() != it.visibleContent() } ?: false
        val deleteWins = when {
            // 本地从未编辑过 → 删除意图必须传播（不复活），无论时间戳怎么倒挂
            !liveEditedSinceSync -> true
            // 本地编辑晚于删除 → 编辑胜（合理冲突）；编辑早于删除 → 删除晚，仍应删
            live.effectiveTime > deadAt -> false
            else -> true
        }
        if (deleteWins) {
            return dead.copy(
                deletedAt = deadAt,
                updatedAt = maxOf(dead.updatedAt, deadAt),
            ) to (if (liveEditedSinceSync) 1 else 0)
        }
        val survivor = if (l.isDeleted) r else l
        return survivor.copy(deletedAt = null, updatedAt = maxOf(l.updatedAt, r.updatedAt)) to 1
    }

    /** 用户可见的内容指纹：名称 / 颜色 / 排序，不含时间戳与删除标记。 */
    private fun SyncCategory.visibleContent(): String = buildString {
        field(name); field(colorHex); field(sortOrder)
    }

    private fun mergeCategoryFields(
        l: SyncCategory,
        r: SyncCategory,
        b: SyncCategory?,
        lastSyncAt: Long,
    ): Pair<SyncCategory, Int> {
        // 同任务：本机改过、远程没动 → 整条本机为准
        if (b != null && l.content() != b.content() && r.content() == b.content()) {
            return l to 0
        }
        var conflicts = 0
        val lt = l.effectiveTime
        val rt = r.effectiveTime
        val hasBase = b != null
        fun <T> pick(lv: T, rv: T, bv: T?): T {
            if (lv == rv) return lv
            val lChanged = if (hasBase) lv != bv else lt > lastSyncAt
            val rChanged = if (hasBase) rv != bv else rt > lastSyncAt
            return when {
                lChanged && !rChanged -> lv
                !lChanged && rChanged -> rv
                !lChanged && !rChanged -> tie(lv, rv, lt, rt)
                else -> {
                    conflicts++
                    tie(lv, rv, lt, rt)
                }
            }
        }
        return l.copy(
            colorHex = pick(l.colorHex, r.colorHex, b?.colorHex),
            sortOrder = pick(l.sortOrder, r.sortOrder, b?.sortOrder),
            updatedAt = maxOf(l.updatedAt, r.updatedAt),
            deletedAt = null,
        ) to conflicts
    }

    /** 时间较新者胜；相同或都缺失 → 远程优先，保证裁决可重复且远程不被覆盖 */
    private fun <T> tie(l: T, r: T, lt: Long, rt: Long): T = when {
        lt > rt -> l
        rt > lt -> r
        else -> r
    }

    // ---------------------------------------------------------------- 内容指纹

    /**
     * 记录「用户看得见的内容」指纹，忽略 createdAt / updatedAt 这类元信息，
     * 专门用来判断「这条记录上次同步之后有没有被真正编辑过」。
     */
    private fun SyncTask.content(): String = buildString {
        field(uid); field(parentUid); field(title); field(category)
        field(priority); field(timeLabel); field(done); field(completedAt)
        field(sortOrder); field(depth); field(deletedAt)
    }

    private fun SyncCategory.content(): String = buildString {
        field(name); field(colorHex); field(sortOrder); field(deletedAt)
    }

    // ---------------------------------------------------------------- 幂等

    /**
     * 内容指纹：只编码记录本身，按 uid / name 排序，忽略 revision / exportedAt /
     * deviceId / lastSyncAt。两次同步内容相同 → 指纹相同 → 不再重复上传。
     */
    fun fingerprint(s: SyncSnapshot): String = buildString {
        append("schema=").append(s.schemaVersion).append('|')
        s.tasks.sortedBy { it.uid }.forEach { t ->
            append("T|")
            field(t.uid); field(t.parentUid); field(t.title); field(t.category)
            field(t.priority); field(t.timeLabel); field(t.done); field(t.completedAt)
            field(t.createdAt); field(t.updatedAt); field(t.deletedAt)
            field(t.sortOrder); field(t.depth)
        }
        s.categories.sortedBy { it.name }.forEach { c ->
            append("C|")
            field(c.name); field(c.colorHex); field(c.sortOrder)
            field(c.updatedAt); field(c.deletedAt)
        }
    }

    /** 长度前缀编码，标题里出现任何分隔符都不会造成歧义 */
    private fun StringBuilder.field(v: Any?) {
        val text = v?.toString() ?: "~"
        append(text.length).append(':').append(text).append(';')
    }

    /** 存成下一次合并的 base：去掉易变元信息并排序 */
    fun canonical(s: SyncSnapshot): SyncSnapshot = s.copy(
        revision = 0,
        exportedAt = 0,
        deviceId = "",
        lastSyncAt = 0,
        tasks = s.tasks.sortedBy { it.uid },
        categories = s.categories.sortedBy { it.name },
    )

    private fun countChanged(local: SyncSnapshot, merged: SyncSnapshot): Int {
        val lt = local.taskByUid()
        val mt = merged.taskByUid()
        val lc = local.categoryByName()
        val mc = merged.categoryByName()
        return (lt.keys + mt.keys).count { lt[it] != mt[it] } +
            (lc.keys + mc.keys).count { lc[it] != mc[it] }
    }
}
