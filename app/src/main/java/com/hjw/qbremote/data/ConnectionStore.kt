package com.hjw.qbremote.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "qb_connection")

data class ConnectionSettings(
    val host: String = "",
    val port: Int = 8080,
    val useHttps: Boolean = false,
    val username: String = "admin",
    val password: String = "",
    val refreshSeconds: Int = 3,
    val showSpeedTotals: Boolean = true,
    val enableServerGrouping: Boolean = true,
    val showChartPanel: Boolean = true,
    val chartShowSiteName: Boolean = true,
    val chartSortMode: ChartSortMode = ChartSortMode.TOTAL_SPEED,
    val deleteFilesDefault: Boolean = true,
    val deleteFilesWhenNoSeeders: Boolean = false,
) {
    fun baseUrl(): String {
        val cleanHost = host.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .trimEnd('/')
        val scheme = if (useHttps) "https" else "http"
        return "$scheme://$cleanHost:$port/"
    }
}

enum class ChartSortMode {
    TOTAL_SPEED,
    DOWNLOAD_SPEED,
    UPLOAD_SPEED,
    TORRENT_COUNT,
}

class ConnectionStore(private val context: Context) {
    private val secureCredentials = SecureCredentialStore(context)

    private object Keys {
        val Host = stringPreferencesKey("host")
        val Port = intPreferencesKey("port")
        val UseHttps = booleanPreferencesKey("use_https")
        val Username = stringPreferencesKey("username")
        val PasswordLegacy = stringPreferencesKey("password")
        val RefreshSeconds = intPreferencesKey("refresh_seconds")
        val ShowSpeedTotals = booleanPreferencesKey("show_speed_totals")
        val EnableServerGrouping = booleanPreferencesKey("enable_server_grouping")
        val ShowChartPanel = booleanPreferencesKey("show_chart_panel")
        val ChartShowSiteName = booleanPreferencesKey("chart_show_site_name")
        val ChartSortMode = stringPreferencesKey("chart_sort_mode")
        val DeleteFilesDefault = booleanPreferencesKey("delete_files_default")
        val DeleteFilesWhenNoSeeders = booleanPreferencesKey("delete_files_when_no_seeders")
    }

    val settingsFlow: Flow<ConnectionSettings> = context.dataStore.data.map { pref ->
        pref.toSettings(secureCredentials.getPassword())
    }

    suspend fun save(settings: ConnectionSettings) {
        secureCredentials.savePassword(settings.password)
        context.dataStore.edit { pref ->
            pref[Keys.Host] = settings.host
            pref[Keys.Port] = settings.port
            pref[Keys.UseHttps] = settings.useHttps
            pref[Keys.Username] = settings.username
            pref[Keys.RefreshSeconds] = settings.refreshSeconds
            pref[Keys.ShowSpeedTotals] = settings.showSpeedTotals
            pref[Keys.EnableServerGrouping] = settings.enableServerGrouping
            pref[Keys.ShowChartPanel] = settings.showChartPanel
            pref[Keys.ChartShowSiteName] = settings.chartShowSiteName
            pref[Keys.ChartSortMode] = settings.chartSortMode.name
            pref[Keys.DeleteFilesDefault] = settings.deleteFilesDefault
            pref[Keys.DeleteFilesWhenNoSeeders] = settings.deleteFilesWhenNoSeeders
            pref.remove(Keys.PasswordLegacy)
        }
    }

    suspend fun migrateLegacyPasswordIfNeeded() {
        val pref = context.dataStore.data.first()
        val legacy = pref[Keys.PasswordLegacy].orEmpty()
        if (legacy.isBlank()) return

        secureCredentials.savePassword(legacy)
        context.dataStore.edit { it.remove(Keys.PasswordLegacy) }
    }

    private fun Preferences.toSettings(securePassword: String): ConnectionSettings {
        return ConnectionSettings(
            host = this[Keys.Host] ?: "",
            port = this[Keys.Port] ?: 8080,
            useHttps = this[Keys.UseHttps] ?: false,
            username = this[Keys.Username] ?: "admin",
            password = securePassword,
            refreshSeconds = this[Keys.RefreshSeconds] ?: 3,
            showSpeedTotals = this[Keys.ShowSpeedTotals] ?: true,
            enableServerGrouping = this[Keys.EnableServerGrouping] ?: true,
            showChartPanel = this[Keys.ShowChartPanel] ?: true,
            chartShowSiteName = this[Keys.ChartShowSiteName] ?: true,
            chartSortMode = runCatching {
                enumValueOf<ChartSortMode>(this[Keys.ChartSortMode].orEmpty())
            }.getOrDefault(ChartSortMode.TOTAL_SPEED),
            deleteFilesDefault = this[Keys.DeleteFilesDefault] ?: true,
            deleteFilesWhenNoSeeders = this[Keys.DeleteFilesWhenNoSeeders] ?: false,
        )
    }
}
