package com.doit.app.data

import android.content.Context
import androidx.room.withTransaction

/** 首次启动的示例清单：与视觉稿里的六条待办一一对应（仅调试包写入） */
object SeedData {

    val categories = listOf(
        CategoryEntity("工作", "#5B9BD5", 0),
        CategoryEntity("采购", "#C9A24D", 1),
        CategoryEntity("阅读", "#9186C9", 2),
        CategoryEntity("运动", "#4E9BB8", 3),
    )

    data class Seed(
        val title: String,
        val category: String,
        val time: String,
        val priority: Int = 0,
        val done: Boolean = false,
        val children: List<Seed> = emptyList(),
    )

    private fun seed(title: String, category: String, time: String, priority: Int = 0, done: Boolean = false, children: List<Seed> = emptyList()) =
        Seed(title, category, time, priority, done, children)

    val tasks = listOf(
        seed("给妈妈回个电话", "工作", "09:30"),
        seed("整理这个月的读书笔记", "阅读", "10:00"),
        seed("买咖啡豆和燕麦奶", "采购", "12:00", priority = 3),
        seed(
            "读完《设计中的设计》第三章", "阅读", "20:00",
            children = listOf(
                seed("读完「设计的日常」", "阅读", "20:10", done = true),
                seed("折角标三处好句", "阅读", "20:20", priority = 1, done = true),
                seed(
                    "写一段读后记", "阅读", "20:30", priority = 3,
                    children = listOf(
                        seed("摘三句原文", "阅读", "20:40", done = true),
                        seed("写 100 字感受", "阅读", "20:50"),
                    ),
                ),
            ),
        ),
        seed("预约周六的羽毛球场", "运动", "21:00"),
        seed("把季度复盘发给大家", "工作", "明天", priority = 4),
    )
}

/**
 * 同步落地用的一条任务：Room 只认 [TaskEntity.id] 做父子关系，
 * 而快照里父子关系用稳定 uid 表示，所以额外带一个 [parentUid] 供落地时二次映射。
 */
data class TaskSyncRow(val entity: TaskEntity, val parentUid: String?)

class TaskRepository(private val db: DoitDatabase) {

    private val taskDao = db.taskDao()
    private val categoryDao = db.categoryDao()
    private val logDao = db.logDao()

    val tasks = taskDao.observeAll()
    val categories = categoryDao.observeAll()
    val logs = logDao.observeAll()
    /** 已完成的待办（带完成时间），月视图按完成日期聚合成每日圆点 */
    val doneTasks = taskDao.observeDone()

    /**
     * 正式包首次启动什么都不预置：不建分类、不带示例待办。
     * 首页只剩「全部」这一个内置胶囊，分类全由用户自己在「管理分类」里建；
     * 没归类的待办 category 存空串（[NO_CATEGORY]），卡片上只显示时间。
     *
     * 仅调试包调用 [ensureSeeded]：分类 + 示例待办一次性写入，
     * 方便打开就能看到各种卡片形态。
     */
    suspend fun ensureSeeded() {
        if (taskDao.count() > 0) return
        db.withTransaction {
            if (categoryDao.count() == 0) {
                categoryDao.upsertAll(SeedData.categories.map { it.copy(updatedAt = nowMs()) })
            }
            val now = nowMs()
            SeedData.tasks.forEachIndexed { index, item ->
                insertSeed(item, parentId = null, sortOrder = index.toLong(), doneAt = now)
            }
        }
    }

    /** 老数据 / 意外漏写元信息的行补齐，幂等，启动时跑一次即可 */
    suspend fun ensureSyncMeta() {
        val missing = taskDao.allIncludingDeleted().filter { it.uid.isBlank() || it.updatedAt == 0L }
        missing.forEach { t ->
            taskDao.update(
                t.copy(
                    uid = if (t.uid.isBlank()) newUid() else t.uid,
                    updatedAt = if (t.updatedAt == 0L) t.createdAt else t.updatedAt,
                ),
            )
        }
    }

    private suspend fun insertSeed(seed: SeedData.Seed, parentId: Long?, sortOrder: Long, doneAt: Long): Long {
        val id = taskDao.insert(
            TaskEntity(
                uid = newUid(),
                parentId = parentId,
                title = seed.title,
                category = seed.category,
                priority = seed.priority,
                timeLabel = seed.time,
                done = seed.done,
                completedAt = if (seed.done) doneAt else null,
                createdAt = doneAt,
                updatedAt = doneAt,
                sortOrder = sortOrder,
            ),
        )
        if (seed.done) {
            logDao.insert(CompletionLogEntity(taskId = id, category = seed.category, completedAt = doneAt))
        }
        seed.children.forEachIndexed { i, child ->
            insertSeed(child, id, i.toLong(), doneAt)
        }
        return id
    }

    /**
     * 仅调试包使用：造一年的完成记录，好在真机上核对热力图 / 环形图 / 折线图的样式。
     * 正式包不会调用，统计页永远只反映真实完成情况。
     */
    suspend fun seedDemoHistory() {
        // 已经有示例记录就不再重复造，避免每次启动都叠加
        if (logDao.since(0L).any { it.taskId >= DEMO_TASK_ID_BASE }) return
        val random = java.util.Random(20261111L)
        val cats = listOf("工作", "阅读", "采购", "运动")
        val now = nowMs()
        val day = 24L * 60 * 60 * 1000
        val logs = mutableListOf<CompletionLogEntity>()
        for (offset in 364 downTo 0) {
            val roll = random.nextDouble()
            val count = when {
                roll < 0.16 -> 0
                roll < 0.42 -> random.nextInt(2) + 1
                roll < 0.70 -> random.nextInt(3) + 3
                roll < 0.90 -> random.nextInt(3) + 6
                else -> random.nextInt(3) + 9
            }
            repeat(count) {
                val category = cats[random.nextInt(cats.size)]
                logs += CompletionLogEntity(
                    taskId = DEMO_TASK_ID_BASE + random.nextInt(9999),
                    category = category,
                    completedAt = now - offset * day - random.nextInt(12) * 3600_000L,
                )
            }
        }
        logDao.insertAll(logs)
    }

    /** 轻点圆圈：没有子任务才能直接勾；有子任务由子任务完成情况自动决定 */
    suspend fun toggleDone(id: Long) {
        db.withTransaction {
            val task = taskDao.byId(id) ?: return@withTransaction
            if (taskDao.childrenOf(id).isNotEmpty()) return@withTransaction
            applyDone(task, !task.done)
            recalcUp(task.parentId)
        }
    }

    suspend fun setDone(id: Long, done: Boolean) {
        db.withTransaction {
            val task = taskDao.byId(id) ?: return@withTransaction
            if (taskDao.childrenOf(id).isNotEmpty()) return@withTransaction
            applyDone(task, done)
            recalcUp(task.parentId)
        }
    }

    private suspend fun applyDone(task: TaskEntity, done: Boolean) {
        val now = nowMs()
        taskDao.update(
            task.copy(
                done = done,
                completedAt = if (done) now else null,
                updatedAt = now,
            ),
        )
        if (done) {
            logDao.insert(
                CompletionLogEntity(taskId = task.id, category = task.category, completedAt = now),
            )
        } else {
            logDao.deleteForTask(task.id)
        }
    }

    /**
     * 子任务全做完 → 自己自动勾上；有子任务被取消 → 自己退回未完成。
     * 一层层往上冒，和视觉稿 recalc() 的行为一致。
     */
    private suspend fun recalcUp(startParentId: Long?) {
        var parentId = startParentId
        while (parentId != null) {
            val parent = taskDao.byId(parentId) ?: break
            val kids = taskDao.childrenOf(parentId)
            if (kids.isEmpty()) break
            val allDone = kids.all { it.done }
            when {
                allDone && !parent.done -> {
                    applyDone(parent, true)
                    parentId = parent.parentId
                }
                !allDone && parent.done -> {
                    applyDone(parent, false)
                    parentId = parent.parentId
                }
                else -> break
            }
        }
    }

    suspend fun addTask(title: String, category: String, priority: Int = 0) {
        db.withTransaction {
            val min = taskDao.minSortOf(null) ?: 0L
            val now = nowMs()
            taskDao.insert(
                TaskEntity(
                    uid = newUid(),
                    title = title.trim(),
                    category = category,
                    priority = priority,
                    timeLabel = "刚刚",
                    createdAt = now,
                    updatedAt = now,
                    sortOrder = min - 1,
                ),
            )
        }
    }

    suspend fun addSubtask(parentId: Long, title: String, priority: Int) {
        db.withTransaction {
            val parent = taskDao.byId(parentId) ?: return@withTransaction
            val siblings = taskDao.childrenOf(parentId)
            val now = nowMs()
            val id = taskDao.insert(
                TaskEntity(
                    uid = newUid(),
                    parentId = parentId,
                    title = title.trim(),
                    category = parent.category,
                    priority = priority,
                    timeLabel = "刚刚",
                    createdAt = now,
                    updatedAt = now,
                    sortOrder = (siblings.maxOfOrNull { it.sortOrder } ?: -1L) + 1,
                    depth = parent.depth + 1,
                ),
            )
            if (id > 0) recalcUp(parentId) // 新加了没做完的步骤，父任务要退回未完成
        }
    }

    suspend fun setPriority(id: Long, level: Int) {
        val task = taskDao.byId(id) ?: return
        taskDao.update(task.copy(priority = level, updatedAt = nowMs()))
    }

    suspend fun moveToCategory(id: Long, category: String) {
        db.withTransaction {
            val ids = descendantIds(id)
            taskDao.setCategory(ids, category, nowMs())
        }
    }

    /**
     * 编辑任务：标题只改自己；分类若变化则整体级联到该任务及其全部子孙（与「移动分类」一致），
     * 保持父子层级分类一致。每次编辑刷新 updatedAt，保证能同步到另一台设备。
     */
    suspend fun updateTask(id: Long, title: String, category: String) {
        db.withTransaction {
            val task = taskDao.byId(id) ?: return@withTransaction
            val text = title.trim()
            taskDao.update(task.copy(title = text, category = category, updatedAt = nowMs()))
            val children = descendantIds(id).filter { it != id }
            if (children.isNotEmpty()) taskDao.setCategory(children, category, nowMs())
        }
    }

    /** 软删除：留下墓碑，删除动作才能同步到另一台设备，而不是被当成「本地没有这条」 */
    suspend fun delete(id: Long) {
        db.withTransaction {
            val ids = descendantIds(id)
            val now = nowMs()
            ids.forEach { tid ->
                val t = taskDao.byId(tid) ?: return@forEach
                taskDao.update(t.copy(deletedAt = now, updatedAt = now))
            }
            logDao.deleteForTasks(ids)
        }
    }

    /** 自己 + 全部下级（只算没删的） */
    private suspend fun descendantIds(id: Long): List<Long> {
        val result = mutableListOf<Long>()
        suspend fun walk(current: Long) {
            result += current
            taskDao.childrenOf(current).forEach { walk(it.id) }
        }
        walk(id)
        return result
    }

    suspend fun addCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val existing = categoryDao.allIncludingDeleted().firstOrNull { it.name == trimmed }
        if (existing != null) {
            // 之前删过的同名分类重新启用：把墓碑清掉，算一次新编辑
            if (existing.deletedAt != null) {
                categoryDao.upsert(existing.copy(deletedAt = null, updatedAt = nowMs()))
            }
            return
        }
        val alive = categoryDao.all()
        val color = com.doit.app.ui.theme.PaletteColors[alive.size % com.doit.app.ui.theme.PaletteColors.size]
        categoryDao.upsert(
            CategoryEntity(trimmed, color.removePrefix("#"), alive.size, updatedAt = nowMs()),
        )
    }

    suspend fun setCategoryColor(name: String, hex: String) {
        val target = categoryDao.all().firstOrNull { it.name == name } ?: return
        categoryDao.upsert(target.copy(colorHex = hex.removePrefix("#"), updatedAt = nowMs()))
    }

    /**
     * 删除分类不会删掉待办，只是让它们变成「未分类」（category 清空）。
     * 没有任何分类能删不掉——首页的「全部」不是分类，一直在。
     */
    suspend fun deleteCategory(name: String) {
        db.withTransaction {
            val now = nowMs()
            val cat = categoryDao.allIncludingDeleted().firstOrNull { it.name == name && it.deletedAt == null }
                ?: return@withTransaction
            categoryDao.upsert(cat.copy(deletedAt = now, updatedAt = now))
            val all = taskDao.all().filter { it.category == name }
            if (all.isNotEmpty()) taskDao.setCategory(all.map { it.id }, NO_CATEGORY, now)
        }
    }

    suspend fun categoryCounts(): Map<String, Int> = taskDao.all().filter { it.parentId == null }.groupingBy { it.category }.eachCount()

    /** 供同步读取：含墓碑的完整本地状态 */
    suspend fun snapshotEntities(): Pair<List<TaskEntity>, List<CategoryEntity>> =
        taskDao.allIncludingDeleted() to categoryDao.allIncludingDeleted()

    /**
     * 把合并结果落回本地。按 uid 复用已有的 Room 行号，保证完成日志等按 id 关联的数据不丢；
     * 新记录先插入再统一回填 parentId（快照里的父子关系用 uid 表示）。
     */
    suspend fun applyMerged(rows: List<TaskSyncRow>, categoryRows: List<CategoryEntity>) {
        db.withTransaction {
            val idByUid = taskDao.allIncludingDeleted().associate { it.uid to it.id }.toMutableMap()
            val insertRows = mutableListOf<TaskEntity>()
            val updateRows = mutableListOf<TaskEntity>()
            for (row in rows) {
                val e = row.entity.copy(parentId = null)
                val existing = idByUid[e.uid]
                if (existing == null) insertRows += e.copy(id = 0)
                else updateRows += e.copy(id = existing)
            }
            taskDao.insertAll(insertRows)
            taskDao.updateAll(updateRows)

            // 重新取一次，把新插入的行号也纳进映射
            val finalIds = taskDao.allIncludingDeleted().associate { it.uid to it.id }
            val aliveIds = finalIds.values.toHashSet()
            for (row in rows) {
                val id = finalIds[row.entity.uid] ?: continue
                // 父记录被清理掉时把这条提成顶层，避免整条记录从界面上消失
                val parentId = row.parentUid?.let { finalIds[it] }?.takeIf { aliveIds.contains(it) }
                taskDao.setParentId(id, parentId)
            }

            categoryDao.upsertAll(categoryRows)
            // 墓碑行对应的完成日志不再出现在统计里
            val deletedIds = taskDao.allIncludingDeleted().filter { it.deletedAt != null }.map { it.id }
            if (deletedIds.isNotEmpty()) logDao.deleteForTasks(deletedIds)
        }
    }

    companion object {
        /** 示例完成记录用的虚拟任务 id 起点，仅调试包写入 */
        const val DEMO_TASK_ID_BASE = 900000L

        @Volatile
        private var instance: TaskRepository? = null

        fun get(context: Context): TaskRepository = instance ?: synchronized(this) {
            instance ?: TaskRepository(
                androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    DoitDatabase::class.java,
                    DoitDatabase.NAME,
                ).addMigrations(DoitDatabase.MIGRATION_1_2).build(),
            ).also { instance = it }
        }
    }
}
