package com.doit.app.sync

import com.doit.app.data.CategoryEntity
import com.doit.app.data.TaskEntity
import com.doit.app.data.TaskSyncRow
import org.json.JSONArray
import org.json.JSONObject

/**
 * 快照编解码。
 *
 * v1 是旧版「本地全量覆盖」时代写出来的文件（只有本地自增 id、没有时间戳），
 * 必须能读：读进来时把 id 映射成 `v1-<id>` 形式的 uid、时间戳记 0（缺失），
 * 让合并算法走「时间戳缺失」分支，而不是直接当成空文件覆盖掉。
 */
object SnapshotCodec {

    const val BACKUP_FILE = "doit-backup.json"

    fun decode(text: String): SyncSnapshot? {
        if (text.isBlank()) return null
        return runCatching {
            val o = JSONObject(text)
            val version = o.optInt("version", 1)
            if (version >= 2) decodeV2(o) else decodeV1(o)
        }.getOrNull()
    }

    fun encode(s: SyncSnapshot): String = JSONObject().apply {
        put("version", s.schemaVersion)
        put("revision", s.revision)
        put("exportedAt", s.exportedAt)
        put("deviceId", s.deviceId)
        put("lastSyncAt", s.lastSyncAt)
        put("categories", JSONArray().apply {
            s.categories.sortedBy { it.name }.forEach { c ->
                put(JSONObject().apply {
                    put("name", c.name)
                    put("color", c.colorHex)
                    put("sortOrder", c.sortOrder)
                    put("updatedAt", c.updatedAt)
                    if (c.deletedAt != null) put("deletedAt", c.deletedAt) else put("deletedAt", JSONObject.NULL)
                })
            }
        })
        put("tasks", JSONArray().apply {
            s.tasks.sortedBy { it.uid }.forEach { t ->
                put(JSONObject().apply {
                    put("uid", t.uid)
                    if (t.parentUid != null) put("parentUid", t.parentUid) else put("parentUid", JSONObject.NULL)
                    put("title", t.title)
                    put("category", t.category)
                    put("priority", t.priority)
                    put("timeLabel", t.timeLabel)
                    put("done", t.done)
                    nullableLong("completedAt", t.completedAt)
                    put("createdAt", t.createdAt)
                    put("updatedAt", t.updatedAt)
                    nullableLong("deletedAt", t.deletedAt)
                    put("sortOrder", t.sortOrder)
                    put("depth", t.depth)
                })
            }
        })
    }.toString()

    private fun JSONObject.nullableLong(key: String, value: Long?) {
        if (value != null) put(key, value) else put(key, JSONObject.NULL)
    }

    private fun JSONObject.longOrNull(key: String): Long? =
        if (!has(key) || isNull(key)) null else optLong(key)

    private fun JSONObject.longOrZero(key: String): Long = longOrNull(key) ?: 0L

    private fun JSONObject.stringOrEmpty(key: String): String =
        if (!has(key) || isNull(key)) "" else optString(key, "")

    private fun decodeV2(o: JSONObject): SyncSnapshot {
        val tasks = o.optJSONArray("tasks")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val t = arr.optJSONObject(i) ?: return@mapNotNull null
                val uid = t.stringOrEmpty("uid")
                if (uid.isBlank()) return@mapNotNull null
                SyncTask(
                    uid = uid,
                    parentUid = t.stringOrEmpty("parentUid").takeIf { it.isNotBlank() },
                    title = t.stringOrEmpty("title"),
                    category = t.stringOrEmpty("category"),
                    priority = t.optInt("priority", 0),
                    timeLabel = t.stringOrEmpty("timeLabel").ifBlank { "刚刚" },
                    done = t.optBoolean("done", false),
                    completedAt = t.longOrNull("completedAt"),
                    createdAt = t.longOrZero("createdAt"),
                    updatedAt = t.longOrZero("updatedAt"),
                    deletedAt = t.longOrNull("deletedAt"),
                    sortOrder = t.longOrZero("sortOrder"),
                    depth = t.optInt("depth", 0),
                )
            }
        }.orEmpty()
        val cats = o.optJSONArray("categories")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val c = arr.optJSONObject(i) ?: return@mapNotNull null
                val name = c.stringOrEmpty("name")
                if (name.isBlank()) return@mapNotNull null
                SyncCategory(
                    name = name,
                    colorHex = c.stringOrEmpty("color"),
                    sortOrder = c.optInt("sortOrder", 0),
                    updatedAt = c.longOrZero("updatedAt"),
                    deletedAt = c.longOrNull("deletedAt"),
                )
            }
        }.orEmpty()
        return SyncSnapshot(
            schemaVersion = o.optInt("version", 2),
            revision = o.longOrZero("revision"),
            exportedAt = o.longOrZero("exportedAt"),
            deviceId = o.stringOrEmpty("deviceId"),
            lastSyncAt = o.longOrZero("lastSyncAt"),
            tasks = tasks,
            categories = cats,
        )
    }

    /** v1：老备份没有稳定 uid 与时间戳，按「时间戳缺失」导入，绝不把它当成新数据覆盖远程 */
    private fun decodeV1(o: JSONObject): SyncSnapshot {
        val tasks = o.optJSONArray("tasks")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val t = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = t.optLong("id", -1L)
                if (id < 0) return@mapNotNull null
                val parent = t.optLong("parentId", 0L)
                SyncTask(
                    uid = "v1-$id",
                    parentUid = if (parent > 0) "v1-$parent" else null,
                    title = t.stringOrEmpty("title"),
                    category = t.stringOrEmpty("category"),
                    priority = t.optInt("priority", 0),
                    timeLabel = t.stringOrEmpty("time").ifBlank { "刚刚" },
                    done = t.optBoolean("done", false),
                    createdAt = 0L,
                    updatedAt = 0L,
                    sortOrder = t.longOrZero("sortOrder"),
                )
            }
        }.orEmpty()
        val cats = o.optJSONArray("categories")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val c = arr.optJSONObject(i) ?: return@mapNotNull null
                val name = c.stringOrEmpty("name")
                if (name.isBlank()) return@mapNotNull null
                SyncCategory(name = name, colorHex = c.stringOrEmpty("color"))
            }
        }.orEmpty()
        return SyncSnapshot(
            schemaVersion = 1,
            exportedAt = o.longOrZero("exportedAt"),
            tasks = tasks,
            categories = cats,
        )
    }

    // ------------------------------------------------------------ Room ↔ 快照

    fun fromEntities(
        tasks: List<TaskEntity>,
        categories: List<CategoryEntity>,
        revision: Long = 0L,
        deviceId: String = "",
        lastSyncAt: Long = 0L,
    ): SyncSnapshot {
        val uidById = tasks.associate { it.id to it.uid }
        return SyncSnapshot(
            revision = revision,
            deviceId = deviceId,
            lastSyncAt = lastSyncAt,
            tasks = tasks.map { t ->
                SyncTask(
                    uid = t.uid,
                    parentUid = t.parentId?.let { uidById[it] },
                    title = t.title,
                    category = t.category,
                    priority = t.priority,
                    timeLabel = t.timeLabel,
                    done = t.done,
                    completedAt = t.completedAt,
                    createdAt = t.createdAt,
                    updatedAt = t.updatedAt,
                    deletedAt = t.deletedAt,
                    sortOrder = t.sortOrder,
                    depth = t.depth,
                )
            },
            categories = categories.map { c ->
                SyncCategory(
                    name = c.name,
                    colorHex = c.colorHex,
                    sortOrder = c.sortOrder,
                    updatedAt = c.updatedAt,
                    deletedAt = c.deletedAt,
                )
            },
        )
    }

    fun toRows(s: SyncSnapshot): Pair<List<TaskSyncRow>, List<CategoryEntity>> {
        val rows = s.tasks.map { t ->
            TaskSyncRow(
                entity = TaskEntity(
                    uid = t.uid,
                    title = t.title,
                    category = t.category,
                    priority = t.priority,
                    timeLabel = t.timeLabel,
                    done = t.done,
                    completedAt = t.completedAt,
                    createdAt = t.createdAt,
                    updatedAt = t.updatedAt,
                    deletedAt = t.deletedAt,
                    sortOrder = t.sortOrder,
                    depth = t.depth,
                ),
                parentUid = t.parentUid,
            )
        }
        val cats = s.categories.map { c ->
            CategoryEntity(
                name = c.name,
                colorHex = c.colorHex,
                sortOrder = c.sortOrder,
                updatedAt = c.updatedAt,
                deletedAt = c.deletedAt,
            )
        }
        return rows to cats
    }
}
