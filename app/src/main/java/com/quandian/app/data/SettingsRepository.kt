package com.quandian.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "quandian_settings")

data class AppSettings(
    val skin: String = "green",
    /** 主题模式：system / light / dark */
    val themeMode: String = "system",
    val davEnabled: Boolean = true,
    val davUrl: String = "",
    val davUser: String = "",
    val davPass: String = "",
    val lastSync: String = "还没有同步过",
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
        )
    }

    suspend fun setSkin(key: String) = context.settingsStore.edit { it[Keys.skin] = key }

    suspend fun setThemeMode(mode: String) = context.settingsStore.edit { it[Keys.themeMode] = mode }

    suspend fun setDavEnabled(enabled: Boolean) = context.settingsStore.edit { it[Keys.davEnabled] = enabled }

    suspend fun setDavConfig(url: String, user: String, pass: String) = context.settingsStore.edit {
        it[Keys.davUrl] = url
        it[Keys.davUser] = user
        it[Keys.davPass] = pass
    }

    suspend fun setLastSync(text: String) = context.settingsStore.edit { it[Keys.lastSync] = text }
}
