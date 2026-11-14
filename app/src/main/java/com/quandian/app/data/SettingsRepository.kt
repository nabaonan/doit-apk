package com.quandian.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.settingsStore by preferencesDataStore(name = "quandian_settings")

data class AppSettings(
    val skin: String = "green",
    /** 主题模式：system / light / dark */
    val themeMode: String = "system",
    val davEnabled: Boolean = true,
    val davUrl: String = "",
    val davUser: String = "",
    val davPass: String = "",
    /** 给人看的上次同步摘要 */
    val lastSync: String = "还没有同步过",
    /** 机器可读：上一次**成功**同步的时间戳，0 = 从未成功同步过 */
    val lastSyncAt: Long = 0L,
    /** 上一次成功同步后的共同快照（base），JSON 字符串 */
    val syncBase: String = "",
    val syncRevision: Long = 0L,
    val deviceId: String = "",
    /** 可选：GitHub 只读 Token，私有仓库「检查更新」用，留空则匿名请求 */
    val githubToken: String = "",
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val skin = stringPreferencesKey("skin")
        val themeMode = stringPreferencesKey("theme_mode")
        val davEnabled = booleanPreferencesKey("dav_enabled")
        val davUrl = stringPreferencesKey("dav_url")
        val davUser = stringPreferencesKey("dav_user")
        val davPass = stringPreferencesKey("dav_pass")
        val lastSync = stringPreferencesKey("last_sync")
        val lastSyncAt = longPreferencesKey("last_sync_at")
        val syncBase = stringPreferencesKey("sync_base")
        val syncRevision = longPreferencesKey("sync_revision")
        val deviceId = stringPreferencesKey("device_id")
        val githubToken = stringPreferencesKey("github_token")
    }

    val settings: Flow<AppSettings> = context.settingsStore.data.map { p ->
        AppSettings(
            skin = p[Keys.skin] ?: "green",
            themeMode = p[Keys.themeMode] ?: "system",
            davEnabled = p[Keys.davEnabled] ?: true,
            davUrl = p[Keys.davUrl] ?: "",
            davUser = p[Keys.davUser] ?: "",
            davPass = p[Keys.davPass] ?: "",
            lastSync = p[Keys.lastSync] ?: "还没有同步过",
            lastSyncAt = p[Keys.lastSyncAt] ?: 0L,
            syncBase = p[Keys.syncBase] ?: "",
            syncRevision = p[Keys.syncRevision] ?: 0L,
            deviceId = p[Keys.deviceId] ?: "",
            githubToken = p[Keys.githubToken] ?: "",
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setSkin(key: String) = context.settingsStore.edit { it[Keys.skin] = key }

    suspend fun setThemeMode(mode: String) = context.settingsStore.edit { it[Keys.themeMode] = mode }

    suspend fun setDavEnabled(enabled: Boolean) = context.settingsStore.edit { it[Keys.davEnabled] = enabled }

    suspend fun setDavConfig(url: String, user: String, pass: String) = context.settingsStore.edit {
        it[Keys.davUrl] = url
        it[Keys.davUser] = user
        it[Keys.davPass] = pass
    }

    suspend fun setGithubToken(token: String) = context.settingsStore.edit { it[Keys.githubToken] = token }

    suspend fun setLastSync(text: String) = context.settingsStore.edit { it[Keys.lastSync] = text }

    /** 只在一轮同步**完整成功**后调用：时间、base、版本状态一起落盘，失败时不留半截状态 */
    suspend fun setSyncState(at: Long, base: String, revision: Long, summary: String) =
        context.settingsStore.edit {
            it[Keys.lastSyncAt] = at
            it[Keys.syncBase] = base
            it[Keys.syncRevision] = revision
            it[Keys.lastSync] = summary
        }

    /** 设备标识只生成一次，写进快照便于排查是哪台设备写的 */
    suspend fun deviceId(): String {
        val existing = context.settingsStore.data.first()[Keys.deviceId]
        if (!existing.isNullOrBlank()) return existing
        val id = UUID.randomUUID().toString()
        context.settingsStore.edit { it[Keys.deviceId] = id }
        return id
    }
}
