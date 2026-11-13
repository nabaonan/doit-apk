package com.quandian.app.sync

import com.quandian.app.data.SettingsRepository
import com.quandian.app.data.TaskRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

data class SyncReport(
    val strategy: SyncStrategy,
    val summary: String,
    /** 从远程合并进本地的记录变化数 */
    val pulled: Int,
    /** 本次是否真的写了远程 */
    val pushed: Boolean,
    val conflicts: Int,
    val revision: Long,
    val bytes: Int,
    val dir: String,
) {
    val detail: String
        get() = buildString {
            append(summary)
            if (pulled > 0) append(" · 本地更新 $pulled 条")
            if (pushed) append(" · 已写回远程 $bytes 字节")
            if (!pushed) append(" · 内容一致，跳过上传")
            append(" · v$revision")
        }
}

/**
 * 一次完整同步的编排：
 * 找可用目录 → 下载远程 → 读本地（含墓碑）→ 逐记录合并 → 落地本地 → 必要时回写远程 → 保存状态。
 *
 * 只有整轮成功才会更新 lastSyncAt / base / revision；任何一步失败都保持原状态，下次可安全重试。
 * Mutex 串行化，避免「保存并同步」被连点两次时并发写同一个远程文件。
 */
class SyncEngine(
    private val repo: TaskRepository,
    private val settings: SettingsRepository,
    private val dav: WebDavClient = WebDavClient,
) {
    private val mutex = Mutex()

    suspend fun synchronize(url: String, user: String, pass: String): Result<SyncReport> = mutex.withLock {
        runCatching {
            // 老数据补齐 uid / updatedAt，保证本地快照里没有「无法识别」的记录
            repo.ensureSyncMeta()
            val st = settings.current()
            val deviceId = settings.deviceId()
            val base = st.syncBase.takeIf { it.isNotBlank() }?.let { SnapshotCodec.decode(it) }
            val ctx = SyncContext(
                lastSyncAt = st.lastSyncAt,
                base = base,
                localRevision = st.syncRevision,
            )

            // 复用 405 自动纠错：探测出一个确实可写的目录，下载和上传都落在它下面
            val probe = dav.test(url, user, pass).getOrThrow()
            val dir = probe.workingUrl ?: WebDavClient.directoryOf(url)

            val remoteText = dav.fetch(dir, user, pass, SnapshotCodec.BACKUP_FILE).getOrThrow()
            val remote = when {
                remoteText.isNullOrBlank() -> null
                else -> SnapshotCodec.decode(remoteText)
                    ?: throw IOException("远程备份不是可识别的 doit 数据，已中止同步以免覆盖")
            }

            val (taskEntities, categoryEntities) = repo.snapshotEntities()
            val local = SnapshotCodec.fromEntities(
                taskEntities,
                categoryEntities,
                revision = ctx.localRevision,
                deviceId = deviceId,
                lastSyncAt = ctx.lastSyncAt,
            )
            val now = System.currentTimeMillis()
            val outcome = SyncMerge.merge(local, remote, ctx, now, deviceId)

            // 先让本机看到合并结果；即使后面上传失败，base 不变，下次重试仍能收敛
            if (outcome.changedForLocal > 0) {
                val (rows, mergedCats) = SnapshotCodec.toRows(outcome.merged)
                repo.applyMerged(rows, mergedCats)
            }

            var pushed = false
            var bytes = 0
            if (outcome.needUpload) {
                val json = SnapshotCodec.encode(outcome.merged)
                dav.put(dir, user, pass, SnapshotCodec.BACKUP_FILE, json).getOrThrow()
                pushed = true
                bytes = json.toByteArray().size
            }

            // 整轮成功才写状态：base 用规范化后的合并结果，revision 单调递增
            val baseJson = SnapshotCodec.encode(SyncMerge.canonical(outcome.merged))
            settings.setSyncState(
                at = now,
                base = baseJson,
                revision = outcome.merged.revision,
                summary = "上次同步 · 刚刚 · ${outcome.summary} · v${outcome.merged.revision}",
            )
            if (probe.workingUrl != null) {
                settings.setDavConfig(probe.workingUrl!!, user, pass)
            }

            SyncReport(
                strategy = outcome.strategy,
                summary = outcome.summary,
                pulled = outcome.changedForLocal,
                pushed = pushed,
                conflicts = outcome.conflicts,
                revision = outcome.merged.revision,
                bytes = bytes,
                dir = dir,
            )
        }
    }
}
