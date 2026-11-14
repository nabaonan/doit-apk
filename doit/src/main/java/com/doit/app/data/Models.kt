package com.doit.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * 没有归到任何分类的待办，category 存空串。
 * 首页只有「全部」一个默认胶囊，分类全部由用户自己建，所以空分类是常态，
 * 界面上遇到空串就只显示时间、不显示"分类 · "前缀。
 */
const val NO_CATEGORY = ""

/** 同步用的稳定 ID。本地自增 id 在两台设备上会从 1 开始撞号，不能拿来跨设备认记录。 */
fun newUid(): String = UUID.randomUUID().toString()

/** 同步元信息的「本条记录本次编辑时间」。0 表示时间戳缺失（老数据 / v1 快照）。 */
fun nowMs(): Long = System.currentTimeMillis()

/**
 * 一条待办 / 子任务。父子关系靠 parentId 自引用，层级不设上限，
 * 与视觉稿「子任务还能一层层往下拆」一致。
 *
 * 同步相关字段：
 * - [uid] 跨设备唯一、永不变化；本地 [id] 只是 Room 的行号，换设备会重新分配。
 * - [updatedAt] 每次编辑（含完成 / 取消完成 / 改分类 / 删除）都刷新。
 * - [deletedAt] 墓碑：删除不物理删行，留一条带删除时间的记录，才能把「删掉了」
 *   这件事同步给另一台设备；所有业务查询都过滤掉它。
 */
@Entity(
    tableName = "tasks",
    indices = [Index("parentId"), Index(value = ["uid"], unique = true)],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "''") val uid: String = "",
    val parentId: Long? = null,
    val title: String,
    val category: String = NO_CATEGORY,
    val priority: Int = 0,
    val timeLabel: String = "刚刚",
    val done: Boolean = false,
    val completedAt: Long? = null,
    val createdAt: Long = nowMs(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
    val sortOrder: Long = 0,
    val depth: Int = 0,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val name: String,
    val colorHex: String,
    val sortOrder: Int = 0,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
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
