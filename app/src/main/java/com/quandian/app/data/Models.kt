package com.quandian.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 没有归到任何分类的待办，category 存空串。
 * 首页只有「全部」一个默认胶囊，分类全部由用户自己建，所以空分类是常态，
 * 界面上遇到空串就只显示时间、不显示"分类 · "前缀。
 */
const val NO_CATEGORY = ""

/**
 * 一条待办 / 子任务。父子关系靠 parentId 自引用，层级不设上限，
 * 与视觉稿「子任务还能一层层往下拆」一致。
 */
@Entity(tableName = "tasks", indices = [Index("parentId")])
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val parentId: Long? = null,
    val title: String,
    val category: String = NO_CATEGORY,
    val priority: Int = 0,
    val timeLabel: String = "刚刚",
    val done: Boolean = false,
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val sortOrder: Long = 0,
    val depth: Int = 0,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val name: String,
    val colorHex: String,
    val sortOrder: Int = 0,
)

/** 完成日志：统计页的热力图 / 环形图 / 折线图都由它聚合 */
@Entity(tableName = "completion_log", indices = [Index("completedAt")])
data class CompletionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val category: String,
    val completedAt: Long,
)

/** 渲染用的树节点，连同它这一层的完成进度 */
data class TaskNode(
    val task: TaskEntity,
    val children: List<TaskNode> = emptyList(),
) {
    val hasChildren: Boolean get() = children.isNotEmpty()
    val doneChildren: Int get() = children.count { it.task.done }
    val allChildrenDone: Boolean get() = children.isNotEmpty() && children.all { it.task.done }

    /** 有子任务且没全做完：圈是虚线，既滑不动也点不了 */
    val locked: Boolean get() = hasChildren && !allChildrenDone

    /** 子任务全做完但自己还没勾：呼吸提示「可以收了」 */
    val ready: Boolean get() = hasChildren && allChildrenDone && !task.done
}

fun buildTree(tasks: List<TaskEntity>): List<TaskNode> {
    val byParent = tasks.groupBy { it.parentId }
    fun build(entity: TaskEntity): TaskNode = TaskNode(
        task = entity,
        children = byParent[entity.id].orEmpty()
            .sortedWith(compareBy({ it.sortOrder }, { it.id }))
            .map(::build),
    )
    return byParent[null].orEmpty()
        .sortedWith(compareBy({ it.sortOrder }, { it.id }))
        .map(::build)
}

/** 拍平成一维，方便统计与批量操作 */
fun flatten(nodes: List<TaskNode>): List<TaskNode> = nodes.flatMap { listOf(it) + flatten(it.children) }
