package com.hjw.qbremote.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.net.URI

private val Context.dataStore by preferencesDataStore(name = "qb_connection")

data class ConnectionSettings(
    val host: String = "",
    val port: Int = 8080,
    val useHttps: Boolean = false,
    val username: String = "admin",
    val password: String = "",
    val refreshSeconds: Int = 10,
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    val appTheme: AppTheme = AppTheme.DARK,
    val showSpeedTotals: Boolean = true,
    val enableServerGrouping: Boolean = true,
    val showChartPanel: Boolean = true,
    val chartShowSiteName: Boolean = true,
    val chartSortMode: ChartSortMode = ChartSortMode.TOTAL_SPEED,
    val deleteFilesDefault: Boolean = true,
    val deleteFilesWhenNoSeeders: Boolean = false,
) {
    fun baseUrl(): String {
        return baseUrlCandidates().first()
    }

    fun baseUrlCandidates(): List<String> {
        val rawInput = host.trim()
        require(rawInput.isNotBlank()) { "Host cannot be empty." }

        val hasExplicitScheme = rawInput.contains("://")
        val normalizedInput = if (hasExplicitScheme) rawInput else "http://$rawInput"

        val parsedUri = runCatching { URI(normalizedInput) }.getOrElse {
            throw IllegalArgumentException(
                "Host format is invalid. Use host, host:port, or http(s)://host[:port]."
            )
        }

        val parsedHost = parsedUri.host?.takeIf { it.isNotBlank() }
            ?: parsedUri.rawAuthority
                ?.substringAfterLast('@')
                ?.substringBefore(':')
                ?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException(
                "Host format is invalid. Use host, host:port, or http(s)://host[:port]."
            )

        val scheme = if (hasExplicitScheme) {
            val parsedScheme = parsedUri.scheme?.lowercase()
            val validatedScheme = parsedScheme
                ?: throw IllegalArgumentException("Only http/https is supported.")
            if (validatedScheme != "http" && validatedScheme != "https") {
                throw IllegalArgumentException("Only http/https is supported.")
            }
            validatedScheme
        } else {
            if (useHttps) "https" else "http"
        }

        val hostForUrl = if (parsedHost.contains(':') && !parsedHost.startsWith("[")) {
            "[$parsedHost]"
        } else {
            parsedHost
        }

        val rawPath = parsedUri.rawPath.orEmpty().trim()
        val normalizedPath = if (rawPath.isBlank() || rawPath == "/") {
            ""
        } else {
            rawPath.trimEnd('/')
        }
        val pathForUrl = if (normalizedPath.isNotEmpty() && !normalizedPath.startsWith('/')) {
            "/$normalizedPath"
        } else {
            normalizedPath
        }

        val explicitPort = parsedUri.port.takeIf { it in 1..65535 }
        val schemeDefaultPort = if (scheme == "https") 443 else 80
        val configuredPort = port.takeIf { it in 1..65535 } ?: schemeDefaultPort
        val primaryPort = explicitPort ?: configuredPort
        val primaryUrl = "$scheme://$hostForUrl:$primaryPort$pathForUrl/"

        if (!hasExplicitScheme || explicitPort != null || configuredPort == schemeDefaultPort) {
            return listOf(primaryUrl)
        }

        val fallbackUrl = "$scheme://$hostForUrl:$schemeDefaultPort$pathForUrl/"
        return listOf(primaryUrl, fallbackUrl).distinct()
    }
}

data class ServerProfile(
    val id: String = "",
    val name: String = "",
    val host: String = "",
    val port: Int = 8080,
    val useHttps: Boolean = false,
    val username: String = "admin",
    val refreshSeconds: Int = 10,
) {
    fun resolvedName(): String {
        return name.trim().ifBlank { host.trim().ifBlank { "Server" } }
    }

    fun toConnectionSettings(
        password: String,
        template: ConnectionSettings,
    ): ConnectionSettings {
        return template.copy(
            host = host,
            port = port,
            useHttps = useHttps,
            username = username,
            password = password,
            refreshSeconds = refreshSeconds,
        )
    }
}

enum class ChartSortMode {
    TOTAL_SPEED,
    DOWNLOAD_SPEED,
    UPLOAD_SPEED,
    TORRENT_COUNT,
}

enum class AppLanguage {
    SYSTEM,
    ZH_CN,
    EN,
}

enum class AppTheme {
    DARK,
    LIGHT,
}

class ConnectionStore(private val context: Context) {
    private val secureCredentials = SecureCredentialStore(context)
    private val gson = Gson()
    private val serverProfilesType = object : TypeToken<List<ServerProfile>>() {}.type

    private object Keys {
        val Host = stringPreferencesKey("host")
        val Port = intPreferencesKey("port")
        val UseHttps = booleanPreferencesKey("use_https")
        val Username = stringPreferencesKey("username")
        val PasswordLegacy = stringPreferencesKey("password")
        val RefreshSeconds = intPreferencesKey("refresh_seconds")
        val AppLanguage = stringPreferencesKey("app_language")
        val AppTheme = stringPreferencesKey("app_theme")
        val ShowSpeedTotals = booleanPreferencesKey("show_speed_totals")
        val EnableServerGrouping = booleanPreferencesKey("enable_server_grouping")
        val ShowChartPanel = booleanPreferencesKey("show_chart_panel")
        val ChartShowSiteName = booleanPreferencesKey("chart_show_site_name")
        val ChartSortMode = stringPreferencesKey("chart_sort_mode")
        val DeleteFilesDefault = booleanPreferencesKey("delete_files_default")
        val DeleteFilesWhenNoSeeders = booleanPreferencesKey("delete_files_when_no_seeders")
        val ServerProfilesJson = stringPreferencesKey("server_profiles_json")
        val ActiveServerProfileId = stringPreferencesKey("active_server_profile_id")
    }

    val settingsFlow: Flow<ConnectionSettings> = context.dataStore.data.map { pref ->
        val activeProfileId = pref[Keys.ActiveServerProfileId].orEmpty()
        val activeProfilePassword = if (activeProfileId.isNotBlank()) {
            secureCredentials.getPasswordForProfile(activeProfileId)
        } else {
            ""
        }
        val effectivePassword = activeProfilePassword.ifBlank { secureCredentials.getPassword() }
        pref.toSettings(effectivePassword)
    }

    val serverProfilesFlow: Flow<List<ServerProfile>> = context.dataStore.data.map { pref ->
        decodeServerProfiles(pref[Keys.ServerProfilesJson].orEmpty())
    }

    val activeServerProfileIdFlow: Flow<String?> = context.dataStore.data.map { pref ->
        pref[Keys.ActiveServerProfileId]?.takeIf { it.isNotBlank() }
    }

    suspend fun save(settings: ConnectionSettings) {
        secureCredentials.savePassword(settings.password)
        context.dataStore.edit { pref ->
            pref[Keys.Host] = settings.host
            pref[Keys.Port] = settings.port
            pref[Keys.UseHttps] = settings.useHttps
            pref[Keys.Username] = settings.username
            pref[Keys.RefreshSeconds] = settings.refreshSeconds
            pref[Keys.AppLanguage] = settings.appLanguage.name
            pref[Keys.AppTheme] = settings.appTheme.name
            pref[Keys.ShowSpeedTotals] = settings.showSpeedTotals
            pref[Keys.EnableServerGrouping] = settings.enableServerGrouping
            pref[Keys.ShowChartPanel] = settings.showChartPanel
            pref[Keys.ChartShowSiteName] = settings.chartShowSiteName
            pref[Keys.ChartSortMode] = settings.chartSortMode.name
            pref[Keys.DeleteFilesDefault] = settings.deleteFilesDefault
            pref[Keys.DeleteFilesWhenNoSeeders] = settings.deleteFilesWhenNoSeeders
            pref.remove(Keys.PasswordLegacy)
            syncActiveProfileFromSettings(pref, settings)
        }
    }

    suspend fun addOrUpdateServerProfile(profile: ServerProfile, password: String) {
        val normalizedHost = profile.host.trim()
        require(normalizedHost.isNotBlank()) { "Host cannot be empty." }

        val normalizedProfile = profile.copy(
            id = profile.id.trim().ifBlank {
                throw IllegalArgumentException("Profile id cannot be empty.")
            },
            name = profile.name.trim().ifBlank { normalizedHost },
            host = normalizedHost,
            port = profile.port.coerceIn(1, 65535),
            username = profile.username.trim().ifBlank { "admin" },
            refreshSeconds = profile.refreshSeconds.coerceIn(10, 120),
        )

        context.dataStore.edit { pref ->
            val existing = decodeServerProfiles(pref[Keys.ServerProfilesJson].orEmpty()).toMutableList()
            val index = existing.indexOfFirst { it.id == normalizedProfile.id }
            if (index >= 0) {
                existing[index] = normalizedProfile
            } else {
                existing.add(normalizedProfile)
            }
            pref[Keys.ServerProfilesJson] = encodeServerProfiles(existing)
        }
        secureCredentials.savePasswordForProfile(normalizedProfile.id, password)
    }

    suspend fun removeServerProfile(profileId: String) {
        val normalizedId = profileId.trim()
        if (normalizedId.isBlank()) return

        context.dataStore.edit { pref ->
            val existing = decodeServerProfiles(pref[Keys.ServerProfilesJson].orEmpty())
            val updated = existing.filterNot { it.id == normalizedId }
            pref[Keys.ServerProfilesJson] = encodeServerProfiles(updated)
            if (pref[Keys.ActiveServerProfileId] == normalizedId) {
                pref.remove(Keys.ActiveServerProfileId)
            }
        }

        secureCredentials.removePasswordForProfile(normalizedId)
    }

    suspend fun getServerProfile(profileId: String): ServerProfile? {
        val normalizedId = profileId.trim()
        if (normalizedId.isBlank()) return null
        val pref = context.dataStore.data.first()
        return decodeServerProfiles(pref[Keys.ServerProfilesJson].orEmpty())
            .firstOrNull { it.id == normalizedId }
    }

    suspend fun setActiveServerProfile(profileId: String?) {
        context.dataStore.edit { pref ->
            val normalized = profileId?.trim().orEmpty()
            if (normalized.isBlank()) {
                pref.remove(Keys.ActiveServerProfileId)
            } else {
                pref[Keys.ActiveServerProfileId] = normalized
            }
        }
    }

    fun getPasswordForProfile(profileId: String): String {
        return secureCredentials.getPasswordForProfile(profileId)
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
            refreshSeconds = (this[Keys.RefreshSeconds] ?: 10).coerceIn(10, 120),
            appLanguage = runCatching {
                enumValueOf<AppLanguage>(this[Keys.AppLanguage].orEmpty())
            }.getOrDefault(AppLanguage.SYSTEM),
            appTheme = runCatching {
                enumValueOf<AppTheme>(this[Keys.AppTheme].orEmpty())
            }.getOrDefault(AppTheme.DARK),
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

    private fun syncActiveProfileFromSettings(
        pref: MutablePreferences,
        settings: ConnectionSettings,
    ) {
        val activeId = pref[Keys.ActiveServerProfileId].orEmpty()
        if (activeId.isBlank()) return

        val profiles = decodeServerProfiles(pref[Keys.ServerProfilesJson].orEmpty()).toMutableList()
        val index = profiles.indexOfFirst { it.id == activeId }
        if (index < 0) return

        val existing = profiles[index]
        profiles[index] = existing.copy(
            name = existing.name.trim().ifBlank { settings.host.trim().ifBlank { "Server" } },
            host = settings.host.trim(),
            port = settings.port.coerceIn(1, 65535),
            useHttps = settings.useHttps,
            username = settings.username.trim().ifBlank { "admin" },
            refreshSeconds = settings.refreshSeconds.coerceIn(10, 120),
        )
        pref[Keys.ServerProfilesJson] = encodeServerProfiles(profiles)
        secureCredentials.savePasswordForProfile(activeId, settings.password)
    }

    private fun decodeServerProfiles(rawJson: String): List<ServerProfile> {
        if (rawJson.isBlank()) return emptyList()

        val parsed: List<ServerProfile> = runCatching {
            gson.fromJson<List<ServerProfile>>(rawJson, serverProfilesType).orEmpty()
        }.getOrDefault(emptyList())

        return parsed.map { profile ->
            profile.copy(
                id = profile.id.trim(),
                name = profile.name.trim(),
                host = profile.host.trim(),
                port = profile.port.coerceIn(1, 65535),
                username = profile.username.trim().ifBlank { "admin" },
                refreshSeconds = profile.refreshSeconds.coerceIn(10, 120),
            )
        }.filter { it.id.isNotBlank() && it.host.isNotBlank() }
    }

    private fun encodeServerProfiles(profiles: List<ServerProfile>): String {
        return gson.toJson(profiles.distinctBy { it.id })
    }
}
