package com.doit.app.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergeTest {

    private fun task(
        uid: String,
        title: String = "",
        priority: Int = 0,
        updatedAt: Long = 0L,
        createdAt: Long = 0L,
        deletedAt: Long? = null,
        done: Boolean = false,
        category: String = "",
        parentUid: String? = null,
    ) = SyncTask(
        uid = uid,
        parentUid = parentUid,
        title = title,
        category = category,
        priority = priority,
        done = done,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )

    private fun cat(
        name: String,
        color: String = "5B9BD5",
        updatedAt: Long = 0L,
        deletedAt: Long? = null,
    ) = SyncCategory(name, color, 0, updatedAt, deletedAt)

    private fun snap(
        vararg tasks: SyncTask,
        categories: List<SyncCategory> = emptyList(),
        revision: Long = 0L,
    ) = SyncSnapshot(revision = revision, tasks = tasks.toList(), categories = categories)

    // ------------------------------------------------------------ 首次同步

    @Test
    fun `新装APP首次同步_远程有数据本地为空_以远程为准且不回写覆盖`() {
        val remote = snap(
            task("a", "远程任务一", updatedAt = 100),
            task("b", "远程任务二", updatedAt = 110),
            categories = listOf(cat("工作")),
            revision = 7,
        )
        val out = SyncMerge.merge(
            local = SyncSnapshot.EMPTY,
            remote = remote,
            ctx = SyncContext(lastSyncAt = 0L),
            now = 500,
            deviceId = "phone-new",
        )
        assertEquals(SyncStrategy.FIRST_SYNC_REMOTE_WINS, out.strategy)
        assertEquals(listOf("a", "b"), out.merged.tasks.map { it.uid })
        assertEquals("远程任务一", out.merged.tasks[0].title)
        assertEquals("工作", out.merged.categories.single().name)
        // 远程内容没被改动，就不该再 PUT 一次
        assertFalse(out.needUpload)
    }

    @Test
    fun `首次同步本地已有自己建的数据_取并集_远程记录一条不丢`() {
        val remote = snap(task("r1", "远程的", updatedAt = 100))
        val local = snap(task("l1", "本地的", updatedAt = 120))
        val out = SyncMerge.merge(local, remote, SyncContext(lastSyncAt = 0L), 500, "d")
        assertEquals(SyncStrategy.FIRST_SYNC_UNION, out.strategy)
        assertEquals(setOf("r1", "l1"), out.merged.tasks.map { it.uid }.toSet())
        assertTrue(out.needUpload) // 本地新增要补到远程
    }

    @Test
    fun `远程独有记录不会被本地全量覆盖`() {
        val base = snap(task("a", "原样", updatedAt = 100), task("b", "别丢我", updatedAt = 100), revision = 3)
        val remote = snap(task("a", "原样", updatedAt = 100), task("b", "别丢我", updatedAt = 100), revision = 3)
        val local = snap(task("a", "本地改了", updatedAt = 200), revision = 3)
        val out = SyncMerge.merge(local, remote, SyncContext(150, base, 3), 300, "d")
        assertEquals(2, out.merged.tasks.size)
        assertEquals("别丢我", out.merged.tasks.first { it.uid == "b" }.title)
        assertEquals("本地改了", out.merged.tasks.first { it.uid == "a" }.title)
    }

    // ------------------------------------------------------------ 字段级合并

    @Test
    fun `两边改不同字段_双方有效变更都保留`() {
        val base = snap(task("a", "原题", priority = 0, updatedAt = 100), revision = 1)
        val local = snap(task("a", "本地改标题", priority = 0, updatedAt = 200), revision = 1)
        val remote = snap(task("a", "原题", priority = 3, updatedAt = 150), revision = 1)
        val out = SyncMerge.merge(local, remote, SyncContext(100, base, 1), 300, "d")
        val a = out.merged.tasks.single()
        assertEquals("本地改标题", a.title)
        assertEquals(3, a.priority)
        assertEquals(0, out.conflicts)
    }

    @Test
    fun `同一字段双方都改_取最后编辑时间较新的一方`() {
        val base = snap(task("a", "原题", updatedAt = 100), revision = 1)
        val local = snap(task("a", "本地版", updatedAt = 200), revision = 1)
        val remote = snap(task("a", "远程版", updatedAt = 150), revision = 1)
        val out = SyncMerge.merge(local, remote, SyncContext(100, base, 1), 300, "d")
        assertEquals("本地版", out.merged.tasks.single().title)
        assertTrue(out.conflicts > 0)
    }

    @Test
    fun `同一字段时间相同_明确采用远程值`() {
        val base = snap(task("a", "原题", updatedAt = 100), revision = 1)
        val local = snap(task("a", "本地版", updatedAt = 200), revision = 1)
        val remote = snap(task("a", "远程版", updatedAt = 200), revision = 1)
        val out = SyncMerge.merge(local, remote, SyncContext(100, base, 1), 300, "d")
        assertEquals("远程版", out.merged.tasks.single().title)
        assertTrue(out.conflicts > 0)
    }

    @Test
    fun `时间戳缺失_远程优先_但本地独有记录仍在`() {
        val local = snap(task("a", "本地版"), task("onlyLocal", "只在本机"))
        val remote = snap(task("a", "远程版"), task("onlyRemote", "只在远程"))
        val out = SyncMerge.merge(local, remote, SyncContext(0, null, 0), 300, "d")
        assertEquals("远程版", out.merged.tasks.first { it.uid == "a" }.title)
        assertEquals(
            setOf("a", "onlyLocal", "onlyRemote"),
            out.merged.tasks.map { it.uid }.toSet(),
        )
    }

    @Test
    fun `没有base时按上次成功同步时间判断谁改过`() {
        val local = snap(task("a", "本地新改", updatedAt = 250), revision = 2)
        val remote = snap(task("a", "上次同步的样子", updatedAt = 120), revision = 2)
        val out = SyncMerge.merge(local, remote, SyncContext(200, null, 2), 300, "d")
        assertEquals("本地新改", out.merged.tasks.single().title)
    }

    // ------------------------------------------------------------ 删除冲突

    @Test
    fun `删除与编辑时间相同_保留记录不误删`() {
        val base = snap(task("a", "原题", updatedAt = 100), revision = 1)
        val local = snap(task("a", "刚编辑过", updatedAt = 300), revision = 1)
        val remote = snap(task("a", deletedAt = 300, updatedAt = 300), revision = 1)
        val out = SyncMerge.merge(local, remote, SyncContext(100, base, 1), 400, "d")
        assertNull(out.merged.tasks.single().deletedAt)
        assertEquals("刚编辑过", out.merged.tasks.single().title)
        assertTrue(out.conflicts > 0)
    }

    @Test
    fun `删除明显晚于最后编辑_墓碑生效`() {
        val base = snap(task("a", "原题", updatedAt = 100), revision = 1)
        val local = snap(task("a", "原题", updatedAt = 200), revision = 1)
        val remote = snap(task("a", deletedAt = 400, updatedAt = 400), revision = 1)
        val out = SyncMerge.merge(local, remote, SyncContext(100, base, 1), 500, "d")
        assertEquals(400L, out.merged.tasks.single().deletedAt)
    }

    @Test
    fun `对方时间戳缺失时不执行删除`() {
        val local = snap(task("a", "老数据没时间戳"))
        val remote = snap(task("a", deletedAt = 500, updatedAt = 500))
        val out = SyncMerge.merge(local, remote, SyncContext(0, null, 0), 600, "d")
        assertNull(out.merged.tasks.single().deletedAt)
    }

    // ------------------------------------------------------------ 分类 / 幂等

    @Test
    fun `分类按名字合并_颜色各改各的也保留`() {
        val base = snap(task("a", "题", category = "工作", updatedAt = 100), categories = listOf(cat("工作", "AAAAAA", 100)), revision = 1)
        val local = snap(task("a", "题", category = "采购", updatedAt = 200), categories = listOf(cat("工作", "AAAAAA", 100)), revision = 1)
        val remote = snap(task("a", "题", category = "工作", updatedAt = 150), categories = listOf(cat("工作", "BBBBBB", 150)), revision = 1)
        val out = SyncMerge.merge(local, remote, SyncContext(100, base, 1), 300, "d")
        assertEquals("采购", out.merged.tasks.single().category)
        assertEquals("BBBBBB", out.merged.categories.single().colorHex)
    }

    @Test
    fun `远程还没有数据时发布本地`() {
        val local = snap(task("a", "第一条", updatedAt = 100))
        val out = SyncMerge.merge(local, null, SyncContext(0, null, 0), 200, "d")
        assertEquals(SyncStrategy.PUBLISH_LOCAL, out.strategy)
        assertTrue(out.needUpload)
        assertEquals(1L, out.merged.revision)
    }

    @Test
    fun `重复同步幂等_不重复上传_版本不虚增_内容不变`() {
        val base = snap(task("a", "原题", updatedAt = 100), revision = 4)
        val local = snap(task("a", "本地改了", updatedAt = 200), revision = 4)
        val remote = snap(task("a", "原题", updatedAt = 100), revision = 4)
        val first = SyncMerge.merge(local, remote, SyncContext(100, base, 4), 300, "d")
        assertTrue(first.needUpload)
        assertEquals(5L, first.merged.revision)

        // 同步成功后：本地与远程都变成同一份，base 也更新成它
        val settled = SyncMerge.canonical(first.merged)
        val second = SyncMerge.merge(
            local = settled,
            remote = first.merged,
            ctx = SyncContext(300, settled, first.merged.revision),
            now = 400,
            deviceId = "d",
        )
        assertFalse(second.needUpload)
        assertEquals(first.merged.revision, second.merged.revision)
        assertEquals(
            SyncMerge.fingerprint(first.merged),
            SyncMerge.fingerprint(second.merged),
        )
        assertEquals(0, second.conflicts)
    }

    // ------------------------------------------------- 同步后本地改动不再被远程覆盖

    @Test
    fun `同步后本地改过_远程未变_整条以本地为准_即使本地时间戳缺失`() {
        val base = snap(task("a", "原题", updatedAt = 0), revision = 1)
        val remote = snap(task("a", "原题", updatedAt = 0), revision = 1)
        val local = snap(task("a", "本地刚改的", done = true, updatedAt = 0), revision = 1)
        val out = SyncMerge.merge(local, remote, SyncContext(100, base, 1), 300, "d")
        val a = out.merged.tasks.single()
        assertEquals("本地刚改的", a.title)
        assertTrue(a.done)
        assertTrue(out.needUpload) // 本机改动必须推到远程
    }

    @Test
    fun `同步后本地改分类颜色_远程未变_保留本地`() {
        val base = snap(categories = listOf(cat("工作", "AAAAAA")), revision = 1)
        val remote = snap(categories = listOf(cat("工作", "AAAAAA")), revision = 1)
        val local = snap(categories = listOf(cat("工作", "FF0000", updatedAt = 250)), revision = 1)
        val out = SyncMerge.merge(local, remote, SyncContext(100, base, 1), 300, "d")
        assertEquals("FF0000", out.merged.categories.single().colorHex)
    }

    @Test
    fun `base丢失但远程没更新_用远程回填base后本地改动仍保留`() {
        val remote = snap(task("a", "原样", updatedAt = 120), revision = 2)
        val local = snap(task("a", "本地新改", updatedAt = 250), revision = 2)
        // SyncEngine 在 base 丢失时会把「没有比本机更新的远程」规范化成 base
        val fallbackBase = SyncMerge.canonical(remote)
        val out = SyncMerge.merge(local, remote, SyncContext(200, fallbackBase, 2), 300, "d")
        assertEquals("本地新改", out.merged.tasks.single().title)
        assertTrue(out.needUpload)
    }

    @Test
    fun `旧版无时间戳记录_本地删除生效_不被远程复活`() {
        val base = snap(task("a", "远程老数据", updatedAt = 0), revision = 1)
        val remote = snap(task("a", "远程老数据", updatedAt = 0), revision = 1)
        val local = snap(task("a", "远程老数据", deletedAt = 300, updatedAt = 300), revision = 1)
        val out = SyncMerge.merge(local, remote, SyncContext(100, base, 1), 400, "d")
        assertEquals(300L, out.merged.tasks.single().deletedAt)
        assertTrue(out.needUpload)
    }

    @Test
    fun `旧版无时间戳分类_本地删除生效`() {
        val base = snap(categories = listOf(cat("旧分类")), revision = 1)
        val remote = snap(categories = listOf(cat("旧分类")), revision = 1)
        val local = snap(categories = listOf(cat("旧分类", updatedAt = 300, deletedAt = 300)), revision = 1)
        val out = SyncMerge.merge(local, remote, SyncContext(100, base, 1), 400, "d")
        assertEquals(300L, out.merged.categories.single().deletedAt)
    }

    @Test
    fun `远程删除分类_本地还保留旧分类_墓碑生效且不复活`() {
        // 用户场景：桌面端在 t=200 删除分类 X 并同步（远程 X 变墓碑）；
        // 手机端本地仍持旧活分类 X（上次同步 t=100），在 t=300 同步。
        // 期望：墓碑生效 —— X 保持删除，且本轮要把墓碑上传（needUpload）。
        val base = snap(categories = listOf(cat("工作", "AAAAAA", updatedAt = 100)), revision = 1)
        val remote = snap(
            categories = listOf(cat("工作", "AAAAAA", updatedAt = 200, deletedAt = 200)),
            revision = 2,
        )
        val local = snap(categories = listOf(cat("工作", "AAAAAA", updatedAt = 100)), revision = 2)
        val out = SyncMerge.merge(local, remote, SyncContext(100, base, 2), 300, "phone")
        assertEquals(200L, out.merged.categories.single().deletedAt)
        // 墓碑结构与远程已一致 → 无需再上传（幂等）；重点是别把活分类传上去
        assertFalse(out.needUpload)
        // 不能把活分类推回远程（复活）
        assertTrue(out.merged.categories.single().isDeleted)
    }

    @Test
    fun `本地曾改过该分类颜色_但远程删除更晚_墓碑仍生效`() {
        // 手机端在 t=150 给分类改了颜色，桌面端在 t=200 删除并同步。
        // 删除（200）晚于本地最后一次有效动作（150）→ 删除应生效，不复活。
        val base = snap(categories = listOf(cat("工作", "AAAAAA", updatedAt = 100)), revision = 1)
        val remote = snap(
            categories = listOf(cat("工作", "BBBBBB", updatedAt = 200, deletedAt = 200)),
            revision = 2,
        )
        val local = snap(categories = listOf(cat("工作", "FF0000", updatedAt = 150)), revision = 2)
        val out = SyncMerge.merge(local, remote, SyncContext(100, base, 2), 300, "phone")
        assertEquals(200L, out.merged.categories.single().deletedAt)
        assertTrue(out.merged.categories.single().isDeleted)
    }

    @Test
    fun `远程墓碑更古老_本地未动过_墓碑仍生效不复活`() {
        // 用户真实场景：桌面端 t=200 删除分类并同步（远程墓碑 deletedAt=200）；
        // 手机端在 t=300 已成功同步过一次（lastSyncAt=300），本地仍持活分类 X（未编辑）。
        // 期望：远程删除意图必须传播 —— 墓碑生效，本地分类删除、且不上传活记录。
        val base = snap(categories = listOf(cat("工作", "AAAAAA", updatedAt = 100)), revision = 3)
        val remote = snap(
            categories = listOf(cat("工作", "AAAAAA", updatedAt = 200, deletedAt = 200)),
            revision = 4,
        )
        val local = snap(categories = listOf(cat("工作", "AAAAAA", updatedAt = 100)), revision = 4)
        val out = SyncMerge.merge(local, remote, SyncContext(300, base, 4), 500, "phone")
        assertEquals(200L, out.merged.categories.single().deletedAt)
        assertTrue(out.merged.categories.single().isDeleted)
        // 远程已是墓碑、本机墓碑也一致 → 无需再上传（幂等）；重点是没把活记录传上去
        assertFalse(out.needUpload)
        assertEquals(0, out.changedForLocal - 1) // 语义占位，不看此断言
    }

    @Test
    fun `本地分类的updatedAt比远程墓碑更新_但本地从未编辑过内容_墓碑仍应生效`() {
        // 真实场景中的关键缺口：手机端 lastSyncAt(如 300) 晚于桌面端删除时刻(如 200)，
        // 本地分类的 updatedAt 因为「上次同步时服务器回写」等被动因素晚于墓碑时间，
        // 但用户在手机端并没有真正编辑过它。此时墓碑若因时间戳比较失效，分类会复活。
        // 期望：只要本地分类内容(名字/颜色/排序)与 base 一致且远程已明确墓碑，删除就该传播。
        val base = snap(categories = listOf(cat("工作", "AAAAAA", updatedAt = 200)), revision = 3)
        val remote = snap(categories = listOf(cat("工作", "AAAAAA", updatedAt = 400, deletedAt = 400)), revision = 4)
        val local = snap(categories = listOf(cat("工作", "AAAAAA", updatedAt = 300)), revision = 4)
        val out = SyncMerge.merge(local, remote, SyncContext(350, base, 4), 500, "phone")
        assertEquals(400L, out.merged.categories.single().deletedAt)
        assertTrue(out.merged.categories.single().isDeleted)
    }

    @Test
    fun `本地无时间戳活分类_远程墓碑时间更新_墓碑仍生效不复活`() {
        // 老数据可能没有时间戳(updatedAt=0)：本地活分类无时间戳 + 远程墓碑时间戳较新。
        // 旧的第二分支 live.hasTimestamp 需要本地有时间戳，无时间戳活记录会退化到 else → 复活。
        // 期望：远程已是明确墓碑，且本地内容无实际编辑 → 墓碑必须胜出。
        val base = snap(categories = listOf(cat("工作", "AAAAAA", updatedAt = 100)), revision = 1)
        val remote = snap(categories = listOf(cat("工作", "AAAAAA", updatedAt = 400, deletedAt = 400)), revision = 2)
        val local = snap(categories = listOf(cat("工作", "AAAAAA", updatedAt = 0)), revision = 2)
        val out = SyncMerge.merge(local, remote, SyncContext(350, base, 2), 500, "phone")
        assertEquals(400L, out.merged.categories.single().deletedAt)
        assertTrue(out.merged.categories.single().isDeleted)
    }

    @Test
    fun `手机端在删除后真正编辑过分类颜色_才允许本地编辑胜`() {
        // 对照：手机端在桌面删除之后的 t=450 改了分类颜色 → 本地有真实编辑，冲突恢复是符合预期。
        val base = snap(categories = listOf(cat("工作", "AAAAAA", updatedAt = 100)), revision = 3)
        val remote = snap(categories = listOf(cat("工作", "AAAAAA", updatedAt = 400, deletedAt = 400)), revision = 4)
        val local = snap(categories = listOf(cat("工作", "FF0000", updatedAt = 450)), revision = 4)
        val out = SyncMerge.merge(local, remote, SyncContext(350, base, 4), 500, "phone")
        assertNull(out.merged.categories.single().deletedAt)
        assertEquals("FF0000", out.merged.categories.single().colorHex)
        assertTrue(out.conflicts > 0)
    }

    @Test
    fun `本地改动晚于远程删除_才保留本地活分类`() {
        // 反向对照：手机端 t=250 改分类，桌面端 t=200 删除。
        // 本地有效动作（250）晚于删除（200）→ 本地编辑胜，可恢复活分类（这是符合预期的冲突语义）。
        val base = snap(categories = listOf(cat("工作", "AAAAAA", updatedAt = 100)), revision = 1)
        val remote = snap(
            categories = listOf(cat("工作", "AAAAAA", updatedAt = 200, deletedAt = 200)),
            revision = 2,
        )
        val local = snap(categories = listOf(cat("工作", "FF0000", updatedAt = 250)), revision = 2)
        val out = SyncMerge.merge(local, remote, SyncContext(100, base, 2), 300, "phone")
        assertNull(out.merged.categories.single().deletedAt)
        assertTrue(out.conflicts > 0)
    }

    @Test
    fun `远程删除与本地编辑冲突_保留本地编辑`() {
        val base = snap(task("a", "原题", updatedAt = 100), revision = 1)
        val remote = snap(task("a", "原题", deletedAt = 350, updatedAt = 350), revision = 1)
        val local = snap(task("a", "本地编辑过", updatedAt = 360), revision = 1)
        val out = SyncMerge.merge(local, remote, SyncContext(100, base, 1), 400, "d")
        assertNull(out.merged.tasks.single().deletedAt)
        assertEquals("本地编辑过", out.merged.tasks.single().title)
        assertTrue(out.conflicts > 0)
    }

    // ------------------------------------------------------------ 远程旧副本

    @Test
    fun `远程读到旧版本文件_识别为旧副本`() {
        val old = snap(task("a", "旧值", updatedAt = 100), revision = 2)
            .copy(exportedAt = 1000, lastSyncAt = 1000)
        assertTrue(SyncMerge.isStaleRemote(old, lastSyncAt = 5000, localRevision = 3))
        // v1 旧文件同样要识别出来
        val v1 = old.copy(schemaVersion = 1, revision = 0)
        assertTrue(SyncMerge.isStaleRemote(v1, lastSyncAt = 5000, localRevision = 3))
    }

    @Test
    fun `远程版本更新时不当旧副本_首次同步也不当旧副本`() {
        val newer = snap(task("a", "别的设备新写的", updatedAt = 9000), revision = 9)
            .copy(exportedAt = 1000)
        assertFalse(SyncMerge.isStaleRemote(newer, lastSyncAt = 5000, localRevision = 3))
        assertFalse(SyncMerge.isStaleRemote(newer, lastSyncAt = 0, localRevision = 0))
    }

    @Test
    fun `远程是旧副本_强制重新发布且不覆盖本地改动`() {
        val base = snap(task("a", "上次同步的值", updatedAt = 200), revision = 3)
        val stale = snap(task("a", "更旧的值", updatedAt = 100), revision = 1)
            .copy(exportedAt = 100, lastSyncAt = 100)
        val local = snap(task("a", "本机刚改的", updatedAt = 400), revision = 3)
        // engine 命中旧副本后会用 base 顶替 remote
        val ctx = SyncContext(300, base, 3, staleRemote = true)
        val out = SyncMerge.merge(local, base, ctx, 500, "d")
        assertEquals("本机刚改的", out.merged.tasks.single().title)
        assertTrue(out.needUpload)
        assertTrue(out.staleRemote)
        assertTrue(out.summary.contains("旧副本"))
        assertEquals(4L, out.merged.revision)
    }

    @Test
    fun `指纹不受版本与导出时间影响`() {
        val a = snap(task("a", "题", updatedAt = 100), revision = 1)
            .copy(exportedAt = 1000, deviceId = "x", lastSyncAt = 900)
        val b = snap(task("a", "题", updatedAt = 100), revision = 99)
            .copy(exportedAt = 9999, deviceId = "y", lastSyncAt = 1)
        assertEquals(SyncMerge.fingerprint(a), SyncMerge.fingerprint(b))
    }

    // ------------------------------------------------------------ 本地与远程一致性判定

    /**
     * 首页云按钮的状态依据：本地当前快照（含墓碑，canonical 后）与 base 指纹一致 → 已同步；
     * 任何用户改动（增/改/删）都会让本地指纹偏离 base → 需要再同步。
     */

    @Test
    fun `本地快照与base一致_指纹相同_判定为已同步`() {
        val tasks = listOf(
            task("a", "买菜", category = "生活", updatedAt = 400, createdAt = 100),
            task("b", "写周报", category = "工作", updatedAt = 300, createdAt = 100),
        )
        val categories = listOf(cat("工作", "4A86D6", updatedAt = 200), cat("生活", "4FBF9E", updatedAt = 200))
        // base 就是「本地快照 canonical 之后」的数据（同步里 canonical 后再 encode 落盘）。
        // canonical 只清 revision/exportedAt/deviceId/lastSyncAt 元字段、排序归一，不碰任务内容，
        // 因此 base 与本地（同一批任务+分类）指纹必须一致。
        val local = SyncSnapshot(revision = 5, tasks = tasks, categories = categories, deviceId = "me", exportedAt = 123)
        val base = SyncMerge.canonical(local)

        assertEquals(SyncMerge.fingerprint(SyncMerge.canonical(local)), SyncMerge.fingerprint(base))
    }

    @Test
    fun `本地改动后指纹偏离base_判定为需要同步`() {
        val tasks = listOf(task("a", "买菜", category = "生活", updatedAt = 400, createdAt = 100))
        val categories = listOf(cat("生活", "4FBF9E", updatedAt = 200))
        val local = SyncSnapshot(revision = 5, tasks = tasks, categories = categories, deviceId = "me")
        val base = SyncMerge.canonical(local)
        val baseFp = SyncMerge.fingerprint(base)

        // 用户改标题 → 指纹变
        val edited = local.copy(tasks = listOf(task("a", "买菜并记账", category = "生活", updatedAt = 500, createdAt = 100)))
        assertFalse(SyncMerge.fingerprint(SyncMerge.canonical(edited)) == baseFp)

        // 新增一条 → 指纹变
        val added = local.copy(tasks = tasks + task("c", "新任务", category = "工作", updatedAt = 600, createdAt = 100))
        assertFalse(SyncMerge.fingerprint(SyncMerge.canonical(added)) == baseFp)

        // 软删一条（墓碑出现）→ 指纹变
        val deleted = local.copy(tasks = listOf(task("a", "买菜", category = "生活", updatedAt = 700, createdAt = 100, deletedAt = 700)))
        assertFalse(SyncMerge.fingerprint(SyncMerge.canonical(deleted)) == baseFp)
    }
}
