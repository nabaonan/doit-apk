package com.quandian.app.sync

/**
 * 同步层的数据模型：刻意与 Room 实体解耦，纯 Kotlin，方便在 JVM 单测里覆盖所有合并分支。
 *
 * 约定：`updatedAt == 0` 表示时间戳缺失（老数据 / v1 快照），
 * 不能当成「1970 年」，合并规则对它有专门处理。
 */

data class SyncTask(
    val uid: String,
    val parentUid: String? = null,
    val title: String = "",
    val category: String = "",
    val priority: Int = 0,
    val timeLabel: String = "刚刚",
    val done: Boolean = false,
    val completedAt: Long? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
    val sortOrder: Long = 0L,
    val depth: Int = 0,
) {
    val isDeleted: Boolean get() = deletedAt != null

    /** 这条记录「最后一次动作」的时间：删除 > 编辑 > 完成 > 创建；全都没有就是 0 */
    val effectiveTime: Long
        get() = deletedAt ?: updatedAt.takeIf { it > 0 }
            ?: completedAt?.takeIf { it > 0 }
            ?: createdAt.takeIf { it > 0 }
            ?: 0L

    val hasTimestamp: Boolean get() = effectiveTime > 0L
}

data class SyncCategory(
    val name: String,
    val colorHex: String = "",
    val sortOrder: Int = 0,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
) {
    val isDeleted: Boolean get() = deletedAt != null
    val effectiveTime: Long get() = deletedAt ?: updatedAt
    val hasTimestamp: Boolean get() = effectiveTime > 0L
}

/** 一次完整快照。schemaVersion=2 起带 uid / updatedAt / 墓碑，v1 只在读取时兼容。 */
data class SyncSnapshot(
    val schemaVersion: Int = SCHEMA_VERSION,
    val revision: Long = 0L,
    val exportedAt: Long = 0L,
    val deviceId: String = "",
    val lastSyncAt: Long = 0L,
    val tasks: List<SyncTask> = emptyList(),
    val categories: List<SyncCategory> = emptyList(),
) {
    /** 是否含有「用户看得见」的数据，用来判断首次同步该不该以远程为准 */
    val hasData: Boolean
        get() = tasks.any { !it.isDeleted } || categories.any { !it.isDeleted }

    fun taskByUid(): Map<String, SyncTask> = tasks.associateBy { it.uid }
    fun categoryByName(): Map<String, SyncCategory> = categories.associateBy { it.name }

    companion object {
        const val SCHEMA_VERSION = 2
        val EMPTY = SyncSnapshot()
    }
}

/**
 * 本机同步状态。
 * - [lastSyncAt] 为 0 表示从未成功同步过。
 * - [base] 是上一次成功同步后双方共同的快照，判断「谁改过」的唯一可靠依据。
 * - [staleRemote] 表示这次读到的远程文件比上一次成功同步还旧（服务器缓存 / 没覆盖成功），
 *   此时绝不能把它的旧值当成「远程新改动」回滚本机。
 */
data class SyncContext(
    val lastSyncAt: Long = 0L,
    val base: SyncSnapshot? = null,
    val localRevision: Long = 0L,
    val staleRemote: Boolean = false,
)

enum class SyncStrategy {
    /** 远程还没有数据，直接发布本地 */
    PUBLISH_LOCAL,
    /** 首次同步：无同步时间 + 远程有数据 + 本地无历史数据 → 远程为准 */
    FIRST_SYNC_REMOTE_WINS,
    /** 首次同步但本地已经有自己建的数据 → 并集，谁都不覆盖谁 */
    FIRST_SYNC_UNION,
    /** 常规双向三方合并 */
    MERGE,
}

data class SyncOutcome(
    val merged: SyncSnapshot,
    val strategy: SyncStrategy,
    /** 本次有多少条记录从远程合并进本地（或本地无变更被跳过） */
    val changedForLocal: Int,
    /** 是否需要把合并结果写回远程 */
    val needUpload: Boolean,
    val conflicts: Int,
    /** 远程文件是旧副本，本轮按本机数据重新发布 */
    val staleRemote: Boolean = false,
) {
    val summary: String
        get() = when {
            staleRemote && strategy == SyncStrategy.PUBLISH_LOCAL -> "远程是旧副本 · 已按本机数据重新发布"
            staleRemote -> "远程是旧副本 · 已保留本机改动并重新发布"
            strategy == SyncStrategy.FIRST_SYNC_REMOTE_WINS -> "首次同步 · 以远程数据为准"
            strategy == SyncStrategy.FIRST_SYNC_UNION -> "首次同步 · 本地与远程合并，未覆盖远程"
            strategy == SyncStrategy.PUBLISH_LOCAL -> "远程为空 · 已发布本地数据"
            conflicts > 0 -> "双向同步 · 已处理 $conflicts 处冲突"
            else -> "双向同步 · 数据已一致"
        }
}
