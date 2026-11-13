package com.quandian.app.sync

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
 * 2. 同一字段只有一边相对 base 变了 → 取改过的那边；两边改了不同字段 → 两边都保留。
 * 3. 同一字段两边都改且值不同 → 取 effectiveTime 较新的一边；
 *    时间相同或都缺失 → 远程优先（远程始终是最不该被覆盖的一方）。
 * 4. 删除 vs 编辑：只有删除时间严格晚于对方最后编辑时间、且对方有有效时间戳时才允许删除；
 *    时间相同或时间戳缺失一律保留记录，宁可多留也不误删。
 */
object SyncMerge {

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
        val needUpload = fingerprint(body) != (remote?.let { fingerprint(it) } ?: "")
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
        if (lDel != null || rDel != null) {
            val deleteWins = when {
                lDel != null && rDel != null -> true
                lDel != null -> r.hasTimestamp && lDel > r.effectiveTime
                rDel != null -> l.hasTimestamp && rDel > l.effectiveTime
                else -> false
            }
            if (deleteWins) {
                val tomb = when {
                    lDel != null && rDel != null -> if (lDel >= rDel) l else r
                    lDel != null -> l
                    else -> r
                }
                return tomb to 0
            }
            // 删除没能胜出：直接保留活着那一侧。墓碑侧除了「删除」之外没有有效字段
            // （标题等都是空的），拿它参与字段合并反而会把活记录的内容洗掉。
            val survivor = if (l.isDeleted) r else l
            return survivor.copy(deletedAt = null) to 1
        }
        return mergeTaskFields(l, r, b, lastSyncAt)
    }

    private fun mergeTaskFields(l: SyncTask, r: SyncTask, b: SyncTask?, lastSyncAt: Long): Pair<SyncTask, Int> {
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
        if (lDel != null || rDel != null) {
            val deleteWins = when {
                lDel != null && rDel != null -> true
                lDel != null -> r.hasTimestamp && lDel > r.effectiveTime
                rDel != null -> l.hasTimestamp && rDel > l.effectiveTime
                else -> false
            }
            if (deleteWins) {
                return (if (lDel != null && rDel != null) (if (lDel >= rDel) l else r) else if (lDel != null) l else r) to 0
            }
            // 同理：删除被拒时保留活着那侧，别让墓碑的空字段洗掉分类颜色
            val survivor = if (l.isDeleted) r else l
            return survivor.copy(
                deletedAt = null,
                updatedAt = maxOf(l.updatedAt, r.updatedAt),
            ) to 1
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
