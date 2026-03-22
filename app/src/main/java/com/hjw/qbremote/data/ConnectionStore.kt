package com.hjw.qbremote.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hjw.qbremote.data.model.CountryPeerSnapshot
import com.hjw.qbremote.data.model.CountryUploadRecord
import com.hjw.qbremote.data.model.TorrentInfo
import com.hjw.qbremote.data.model.TransferInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.net.URI
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "qb_connection")

private const val DASHBOARD_CARD_COUNTRY_FLOW = "country_flow"
private const val DASHBOARD_CARD_CATEGORY_SHARE = "category_share"
private const val DASHBOARD_CARD_DAILY_UPLOAD = "daily_upload"
private const val DASHBOARD_CARD_TAG_UPLOAD = "tag_upload"
private const val DASHBOARD_CARD_TORRENT_STATE = "torrent_state"
private const val DASHBOARD_CARD_TRACKER_SITE = "tracker_site"
private val VALID_DASHBOARD_CARD_KEYS = setOf(
    DASHBOARD_CARD_COUNTRY_FLOW,
    DASHBOARD_CARD_CATEGORY_SHARE,
    DASHBOARD_CARD_DAILY_UPLOAD,
    DASHBOARD_CARD_TAG_UPLOAD,
    DASHBOARD_CARD_TORRENT_STATE,
    DASHBOARD_CARD_TRACKER_SITE,
)

data class ConnectionSettings(
    val host: String = "",
    val port: Int = 8080,
    val useHttps: Boolean = false,
    val username: String = "admin",
    val password: String = "",
    val serverBackendType: ServerBackendType = ServerBackendType.QBITTORRENT,
    val refreshSeconds: Int = 5,
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    val appTheme: AppTheme = AppTheme.DARK,
    val customBackgroundImagePath: String = "",
    val customBackgroundToneIsLight: Boolean = false,
    val deleteFilesDefault: Boolean = true,
    val deleteFilesWhenNoSeeders: Boolean = false,
    val homeTorrentEntryHintDismissed: Boolean = false,
    val hasSeenDashboardHideHint: Boolean = false,
    val hasSeenDashboardHiddenSnack: Boolean = false,
    val hasSeenServerStackReorderHint: Boolean = false,
    val hasSeenServerDashboardSwipeHint: Boolean = false,
    val hasSeenServerDashboardCardHint: Boolean = false,
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
    val id: String,
    val name: String,
    val backendType: ServerBackendType,
    val host: String,
    val port: Int,
    val useHttps: Boolean,
    val username: String,
    val refreshSeconds: Int,
)

data class ServerProfilesState(
    val profiles: List<ServerProfile> = emptyList(),
    val activeProfileId: String? = null,
)

data class DeleteServerProfileResult(
    val deletedProfileId: String,
    val activeProfileId: String? = null,
    val settings: ConnectionSettings? = null,
)

data class DailyUploadTrackingSnapshot(
    val date: String = "",
    val baselineByTorrent: Map<String, Long> = emptyMap(),
    val lastSeenByTorrent: Map<String, Long> = emptyMap(),
)

data class DailyCountryUploadTrackingSnapshot(
    val date: String = "",
    val totalsByCountry: Map<String, Long> = emptyMap(),
    val peerSnapshots: Map<String, CountryPeerSnapshot> = emptyMap(),
    val lastSeenByTorrent: Map<String, Long> = emptyMap(),
)

data class HomeSpeedHistoryPoint(
    val timestamp: Long = 0L,
    val uploadSpeed: Long = 0L,
    val downloadSpeed: Long = 0L,
    val onlineServerCount: Int = 0,
)

data class HomeAggregateSpeedHistorySnapshot(
    val points: List<HomeSpeedHistoryPoint> = emptyList(),
)

data class DashboardCacheSnapshot(
    val transferInfo: TransferInfo = TransferInfo(),
    val torrents: List<TorrentInfo> = emptyList(),
    val dailyTagUploadDate: String = "",
    val dailyTagUploadStats: List<CachedDailyTagUploadStat> = emptyList(),
    val dailyCountryUploadDate: String = "",
    val dailyCountryUploadStats: List<CountryUploadRecord> = emptyList(),
)

data class DashboardServerSnapshotStore(
    val snapshots: Map<String, CachedDashboardServerSnapshot> = emptyMap(),
)

data class CachedDailyTagUploadStat(
    val tag: String = "",
    val uploadedBytes: Long = 0L,
    val torrentCount: Int = 0,
    val isNoTag: Boolean = false,
)

data class ServerDashboardPreferences(
    val visibleCards: List<String> = listOf("country_flow", "category_share", "daily_upload"),
    val cardOrder: String = "country_flow,category_share,daily_upload",
    val hasSeenStackReorderHint: Boolean = false,
    val hasSeenDashboardSwipeHint: Boolean = false,
    val hasSeenDashboardCardHint: Boolean = false,
)

enum class AppLanguage {
    SYSTEM,
    ZH_CN,
    EN,
}

enum class AppTheme {
    DARK,
    LIGHT,
    CUSTOM,
}

class ConnectionStore(private val context: Context) {
    private val secureCredentials = SecureCredentialStore(context)
    private val gson = Gson()
    private val serverProfileListType = object : TypeToken<List<ServerProfile>>() {}.type
    private val dailyUploadTrackingMapType =
        object : TypeToken<Map<String, DailyUploadTrackingSnapshot>>() {}.type
    private val dailyCountryUploadTrackingMapType =
        object : TypeToken<Map<String, DailyCountryUploadTrackingSnapshot>>() {}.type
    private val dashboardCacheMapType =
        object : TypeToken<Map<String, DashboardCacheSnapshot>>() {}.type
    private val dashboardServerSnapshotMapType =
        object : TypeToken<Map<String, CachedDashboardServerSnapshot>>() {}.type
    private val serverDashboardPreferencesMapType =
        object : TypeToken<Map<String, ServerDashboardPreferences>>() {}.type

    private object Keys {
        val Host = stringPreferencesKey("host")
        val Port = intPreferencesKey("port")
        val UseHttps = booleanPreferencesKey("use_https")
        val Username = stringPreferencesKey("username")
        val ServerBackendType = stringPreferencesKey("server_backend_type")
        val PasswordLegacy = stringPreferencesKey("password")
        val RefreshSeconds = intPreferencesKey("refresh_seconds")
        val AppLanguage = stringPreferencesKey("app_language")
        val AppTheme = stringPreferencesKey("app_theme")
        val CustomBackgroundImagePath = stringPreferencesKey("custom_background_image_path")
        val CustomBackgroundToneIsLight = booleanPreferencesKey("custom_background_tone_is_light")
        val DeleteFilesDefault = booleanPreferencesKey("delete_files_default")
        val DeleteFilesWhenNoSeeders = booleanPreferencesKey("delete_files_when_no_seeders")
        val HomeTorrentEntryHintDismissed = booleanPreferencesKey("home_torrent_entry_hint_dismissed")
        val HasSeenDashboardHideHint = booleanPreferencesKey("has_seen_dashboard_hide_hint")
        val HasSeenDashboardHiddenSnack = booleanPreferencesKey("has_seen_dashboard_hidden_snack")
        val HasSeenServerStackReorderHint = booleanPreferencesKey("has_seen_server_stack_reorder_hint")
        val HasSeenServerDashboardSwipeHint = booleanPreferencesKey("has_seen_server_dashboard_swipe_hint")
        val HasSeenServerDashboardCardHint = booleanPreferencesKey("has_seen_server_dashboard_card_hint")
        val ServerProfilesJson = stringPreferencesKey("server_profiles_json")
        val ActiveServerProfileId = stringPreferencesKey("active_server_profile_id")
        val DailyUploadTrackingJson = stringPreferencesKey("daily_upload_tracking_json")
        val DailyCountryUploadTrackingJson = stringPreferencesKey("daily_country_upload_tracking_json")
        val DashboardCacheJson = stringPreferencesKey("dashboard_cache_json")
        val DashboardServerSnapshotsJson = stringPreferencesKey("dashboard_server_snapshots_json")
        val DeprecatedDashboardTrendHistoryJson = stringPreferencesKey("dashboard_trend_history_json")
        val HomeAggregateSpeedHistoryJson = stringPreferencesKey("home_aggregate_speed_history_json")
        val ServerDashboardPreferencesJson = stringPreferencesKey("server_dashboard_preferences_json")
    }

    val settingsFlow: Flow<ConnectionSettings> = context.dataStore.data.map { pref ->
        val activeProfileId = pref[Keys.ActiveServerProfileId].orEmpty()
        pref.toSettings(resolvePassword(activeProfileId))
    }

    val serverProfilesFlow: Flow<ServerProfilesState> = context.dataStore.data.map { pref ->
        val profiles = parseProfiles(pref[Keys.ServerProfilesJson])
        val storedActive = pref[Keys.ActiveServerProfileId].orEmpty()
        val active = when {
            storedActive.isNotBlank() && profiles.any { it.id == storedActive } -> storedActive
            profiles.isNotEmpty() -> profiles.first().id
            else -> null
        }
        ServerProfilesState(
            profiles = profiles,
            activeProfileId = active,
        )
    }

    suspend fun save(settings: ConnectionSettings) {
        val pref = context.dataStore.data.first()
        val activeProfileId = pref[Keys.ActiveServerProfileId].orEmpty()
        val profiles = parseProfiles(pref[Keys.ServerProfilesJson]).toMutableList()
        var resolvedActiveProfileId = activeProfileId

        if (resolvedActiveProfileId.isBlank() && settings.host.trim().isNotBlank()) {
            val newProfileId = generateProfileId()
            val newProfile = settings.toServerProfile(
                id = newProfileId,
                name = buildProfileName(
                    requestedName = "",
                    host = settings.host,
                    index = profiles.size + 1,
                )
            )
            profiles += newProfile
            resolvedActiveProfileId = newProfileId
        }

        if (resolvedActiveProfileId.isNotBlank()) {
            val index = profiles.indexOfFirst { it.id == resolvedActiveProfileId }
            if (index >= 0) {
                val current = profiles[index]
                profiles[index] = settings.toServerProfile(
                    id = current.id,
                    name = current.name,
                )
            } else if (settings.host.trim().isNotBlank()) {
                profiles += settings.toServerProfile(
                    id = resolvedActiveProfileId,
                    name = buildProfileName(
                        requestedName = "",
                        host = settings.host,
                        index = profiles.size + 1,
                    )
                )
            }
        }

        secureCredentials.savePassword(settings.password)
        if (resolvedActiveProfileId.isNotBlank()) {
            secureCredentials.savePasswordForProfile(resolvedActiveProfileId, settings.password)
        }

        context.dataStore.edit { target ->
            target[Keys.Host] = settings.host
            target[Keys.Port] = settings.port
            target[Keys.UseHttps] = settings.useHttps
            target[Keys.Username] = settings.username
            target[Keys.ServerBackendType] = settings.serverBackendType.name
            target[Keys.RefreshSeconds] = settings.refreshSeconds
            target[Keys.AppLanguage] = settings.appLanguage.name
            target[Keys.AppTheme] = settings.appTheme.name
            target[Keys.CustomBackgroundImagePath] = settings.customBackgroundImagePath
            target[Keys.CustomBackgroundToneIsLight] = settings.customBackgroundToneIsLight
            target[Keys.DeleteFilesDefault] = settings.deleteFilesDefault
            target[Keys.DeleteFilesWhenNoSeeders] = settings.deleteFilesWhenNoSeeders
            target[Keys.HomeTorrentEntryHintDismissed] = settings.homeTorrentEntryHintDismissed
            target[Keys.HasSeenDashboardHideHint] = settings.hasSeenDashboardHideHint
            target[Keys.HasSeenDashboardHiddenSnack] = settings.hasSeenDashboardHiddenSnack
            target[Keys.HasSeenServerStackReorderHint] = settings.hasSeenServerStackReorderHint
            target[Keys.HasSeenServerDashboardSwipeHint] = settings.hasSeenServerDashboardSwipeHint
            target[Keys.HasSeenServerDashboardCardHint] = settings.hasSeenServerDashboardCardHint
            target.remove(Keys.PasswordLegacy)
            if (resolvedActiveProfileId.isNotBlank()) {
                target[Keys.ActiveServerProfileId] = resolvedActiveProfileId
            }
            target[Keys.ServerProfilesJson] = gson.toJson(profiles)
        }
    }

    suspend fun addServerProfile(name: String, settings: ConnectionSettings): ServerProfile {
        val pref = context.dataStore.data.first()
        val profiles = parseProfiles(pref[Keys.ServerProfilesJson]).toMutableList()
        val id = generateProfileId()
        val profile = settings.toServerProfile(
            id = id,
            name = buildProfileName(
                requestedName = name,
                host = settings.host,
                index = profiles.size + 1,
            ),
        )
        profiles += profile

        secureCredentials.savePasswordForProfile(id, settings.password)
        secureCredentials.savePassword(settings.password)

        context.dataStore.edit { target ->
            target[Keys.ServerProfilesJson] = gson.toJson(profiles)
            target[Keys.ActiveServerProfileId] = id
            target[Keys.Host] = profile.host
            target[Keys.Port] = profile.port
            target[Keys.UseHttps] = profile.useHttps
            target[Keys.Username] = profile.username
            target[Keys.ServerBackendType] = profile.backendType.name
            target[Keys.RefreshSeconds] = profile.refreshSeconds
        }

        saveServerDashboardPreferences(
            profileId = id,
            preferences = defaultServerDashboardPreferences(settings),
        )

        return profile
    }

    suspend fun cleanupLegacyGlobalChartSettingsIfNeeded() {
        val pref = context.dataStore.data.first()
        val legacyKeysPresent = listOf(
            stringPreferencesKey("dashboard_card_order"),
            stringPreferencesKey("chart_sort_mode"),
            booleanPreferencesKey("show_speed_totals"),
            booleanPreferencesKey("enable_server_grouping"),
            booleanPreferencesKey("show_chart_panel"),
            booleanPreferencesKey("show_country_flow_card"),
            booleanPreferencesKey("show_upload_distribution_card"),
            booleanPreferencesKey("show_category_distribution_card"),
            booleanPreferencesKey("chart_show_site_name"),
        ).any { key -> pref.contains(key) }
        if (!legacyKeysPresent) return

        context.dataStore.edit { target ->
            target.remove(stringPreferencesKey("dashboard_card_order"))
            target.remove(stringPreferencesKey("chart_sort_mode"))
            target.remove(booleanPreferencesKey("show_speed_totals"))
            target.remove(booleanPreferencesKey("enable_server_grouping"))
            target.remove(booleanPreferencesKey("show_chart_panel"))
            target.remove(booleanPreferencesKey("show_country_flow_card"))
            target.remove(booleanPreferencesKey("show_upload_distribution_card"))
            target.remove(booleanPreferencesKey("show_category_distribution_card"))
            target.remove(booleanPreferencesKey("chart_show_site_name"))
        }
    }

    suspend fun switchToServerProfile(profileId: String): ConnectionSettings {
        require(profileId.isNotBlank()) { "Invalid server profile id." }
        val pref = context.dataStore.data.first()
        val profiles = parseProfiles(pref[Keys.ServerProfilesJson])
        val profile = profiles.firstOrNull { it.id == profileId }
            ?: throw IllegalArgumentException("Server profile not found.")

        val password = resolvePassword(profileId)
        val currentSettings = pref.toSettings(password)
        val switched = currentSettings.copy(
            host = profile.host,
            port = profile.port,
            useHttps = profile.useHttps,
            username = profile.username,
            password = password,
            serverBackendType = profile.backendType,
            refreshSeconds = profile.refreshSeconds,
        )

        secureCredentials.savePassword(password)

        context.dataStore.edit { target ->
            target[Keys.ActiveServerProfileId] = profile.id
            target[Keys.Host] = profile.host
            target[Keys.Port] = profile.port
            target[Keys.UseHttps] = profile.useHttps
            target[Keys.Username] = profile.username
            target[Keys.ServerBackendType] = profile.backendType.name
            target[Keys.RefreshSeconds] = profile.refreshSeconds
        }

        return switched
    }

    suspend fun updateServerProfile(
        profileId: String,
        name: String,
        settings: ConnectionSettings,
        passwordOverride: String? = null,
    ): ServerProfile {
        require(profileId.isNotBlank()) { "Invalid server profile id." }
        val pref = context.dataStore.data.first()
        val profiles = parseProfiles(pref[Keys.ServerProfilesJson]).toMutableList()
        val index = profiles.indexOfFirst { it.id == profileId }
        require(index >= 0) { "Server profile not found." }

        val updatedProfile = settings.toServerProfile(
            id = profileId,
            name = buildProfileName(
                requestedName = name,
                host = settings.host,
                index = index + 1,
            ),
        )
        profiles[index] = updatedProfile

        val activeProfileId = pref[Keys.ActiveServerProfileId].orEmpty()
        if (passwordOverride != null) {
            secureCredentials.savePasswordForProfile(profileId, passwordOverride)
            if (activeProfileId == profileId) {
                secureCredentials.savePassword(passwordOverride)
            }
        }

        context.dataStore.edit { target ->
            target[Keys.ServerProfilesJson] = gson.toJson(profiles)
            if (activeProfileId == profileId) {
                target[Keys.Host] = updatedProfile.host
                target[Keys.Port] = updatedProfile.port
                target[Keys.UseHttps] = updatedProfile.useHttps
                target[Keys.Username] = updatedProfile.username
                target[Keys.ServerBackendType] = updatedProfile.backendType.name
                target[Keys.RefreshSeconds] = updatedProfile.refreshSeconds
            }
        }

        return updatedProfile
    }

    suspend fun reorderServerProfiles(profileIds: List<String>): List<ServerProfile> {
        val normalizedIds = profileIds.map { it.trim() }.filter { it.isNotBlank() }
        if (normalizedIds.isEmpty()) return serverProfilesFlow.first().profiles
        val pref = context.dataStore.data.first()
        val currentProfiles = parseProfiles(pref[Keys.ServerProfilesJson])
        if (currentProfiles.isEmpty()) return emptyList()
        val profilesById = currentProfiles.associateBy { it.id }
        val reordered = buildList<ServerProfile> {
            normalizedIds.forEach { id ->
                profilesById[id]?.let(::add)
            }
            currentProfiles.forEach { profile ->
                if (none { existing -> existing.id == profile.id }) add(profile)
            }
        }
        context.dataStore.edit { target ->
            target[Keys.ServerProfilesJson] = gson.toJson(reordered)
        }
        return reordered
    }

    suspend fun deleteServerProfile(profileId: String): DeleteServerProfileResult {
        require(profileId.isNotBlank()) { "Invalid server profile id." }
        val pref = context.dataStore.data.first()
        val profiles = parseProfiles(pref[Keys.ServerProfilesJson]).toMutableList()
        val index = profiles.indexOfFirst { it.id == profileId }
        require(index >= 0) { "Server profile not found." }

        val removed = profiles.removeAt(index)
        val currentActiveProfileId = pref[Keys.ActiveServerProfileId].orEmpty()
        val deletingActiveProfile = currentActiveProfileId == profileId

        secureCredentials.removePasswordForProfile(profileId)
        removeServerDashboardPreferences(profileId)

        var nextActiveProfileId: String? = currentActiveProfileId.takeIf { it.isNotBlank() && it != profileId }
        var nextSettings: ConnectionSettings? = null

        if (deletingActiveProfile) {
            val nextProfile = profiles.firstOrNull()
            if (nextProfile != null) {
                nextActiveProfileId = nextProfile.id
                val password = resolvePassword(nextProfile.id)
                secureCredentials.savePassword(password)
                nextSettings = pref.toSettings(password).copy(
                    host = nextProfile.host,
                    port = nextProfile.port,
                    useHttps = nextProfile.useHttps,
                    username = nextProfile.username,
                    password = password,
                    serverBackendType = nextProfile.backendType,
                    refreshSeconds = nextProfile.refreshSeconds,
                )
            } else {
                secureCredentials.clearPassword()
                nextActiveProfileId = null
                nextSettings = pref.toSettings("").copy(
                    host = "",
                    port = 8080,
                    useHttps = false,
                    username = "admin",
                    password = "",
                    serverBackendType = ServerBackendType.QBITTORRENT,
                    refreshSeconds = 5,
                )
            }
        }

        context.dataStore.edit { target ->
            target[Keys.ServerProfilesJson] = gson.toJson(profiles)
            val dailyUploadSnapshots = parseDailyUploadTrackingSnapshots(target[Keys.DailyUploadTrackingJson]).toMutableMap()
            dailyUploadSnapshots.remove("profile:$profileId")
            target[Keys.DailyUploadTrackingJson] = gson.toJson(dailyUploadSnapshots)

            val dailyCountrySnapshots = parseDailyCountryUploadTrackingSnapshots(
                target[Keys.DailyCountryUploadTrackingJson]
            ).toMutableMap()
            dailyCountrySnapshots.remove("profile:$profileId")
            target[Keys.DailyCountryUploadTrackingJson] = gson.toJson(dailyCountrySnapshots)

            val dashboardCaches = parseDashboardCacheSnapshots(target[Keys.DashboardCacheJson]).toMutableMap()
            dashboardCaches.remove("profile:$profileId")
            target[Keys.DashboardCacheJson] = gson.toJson(dashboardCaches)

            val dashboardServerSnapshots = parseDashboardServerSnapshots(
                target[Keys.DashboardServerSnapshotsJson]
            ).toMutableMap()
            dashboardServerSnapshots.remove(profileId)
            target[Keys.DashboardServerSnapshotsJson] = gson.toJson(dashboardServerSnapshots)

            if (deletingActiveProfile) {
                if (nextActiveProfileId.isNullOrBlank()) {
                    target.remove(Keys.ActiveServerProfileId)
                    target[Keys.Host] = ""
                    target[Keys.Port] = 8080
                    target[Keys.UseHttps] = false
                    target[Keys.Username] = "admin"
                    target[Keys.ServerBackendType] = ServerBackendType.QBITTORRENT.name
                    target[Keys.RefreshSeconds] = 5
                } else {
                    val nextProfile = profiles.first { it.id == nextActiveProfileId }
                    target[Keys.ActiveServerProfileId] = nextActiveProfileId
                    target[Keys.Host] = nextProfile.host
                    target[Keys.Port] = nextProfile.port
                    target[Keys.UseHttps] = nextProfile.useHttps
                    target[Keys.Username] = nextProfile.username
                    target[Keys.ServerBackendType] = nextProfile.backendType.name
                    target[Keys.RefreshSeconds] = nextProfile.refreshSeconds
                }
            }
            if (profiles.isEmpty()) {
                target.remove(Keys.HomeAggregateSpeedHistoryJson)
            }
        }

        return DeleteServerProfileResult(
            deletedProfileId = removed.id,
            activeProfileId = nextActiveProfileId,
            settings = nextSettings,
        )
    }

    suspend fun loadDailyUploadTrackingSnapshot(scopeKey: String): DailyUploadTrackingSnapshot? {
        if (scopeKey.isBlank()) return null
        val pref = context.dataStore.data.first()
        val snapshots = parseDailyUploadTrackingSnapshots(pref[Keys.DailyUploadTrackingJson])
        return snapshots[scopeKey]
    }

    suspend fun saveDailyUploadTrackingSnapshot(
        scopeKey: String,
        snapshot: DailyUploadTrackingSnapshot,
    ) {
        if (scopeKey.isBlank()) return
        context.dataStore.edit { target ->
            val snapshots = parseDailyUploadTrackingSnapshots(target[Keys.DailyUploadTrackingJson]).toMutableMap()
            snapshots[scopeKey] = snapshot.normalized()
            target[Keys.DailyUploadTrackingJson] = gson.toJson(snapshots)
        }
    }

    suspend fun loadDailyCountryUploadTrackingSnapshot(scopeKey: String): DailyCountryUploadTrackingSnapshot? {
        if (scopeKey.isBlank()) return null
        val pref = context.dataStore.data.first()
        val snapshots = parseDailyCountryUploadTrackingSnapshots(pref[Keys.DailyCountryUploadTrackingJson])
        return snapshots[scopeKey]
    }

    suspend fun saveDailyCountryUploadTrackingSnapshot(
        scopeKey: String,
        snapshot: DailyCountryUploadTrackingSnapshot,
    ) {
        if (scopeKey.isBlank()) return
        context.dataStore.edit { target ->
            val snapshots = parseDailyCountryUploadTrackingSnapshots(
                target[Keys.DailyCountryUploadTrackingJson]
            ).toMutableMap()
            snapshots[scopeKey] = snapshot.normalized()
            target[Keys.DailyCountryUploadTrackingJson] = gson.toJson(snapshots)
        }
    }

    suspend fun loadDashboardCacheSnapshot(scopeKey: String): DashboardCacheSnapshot? {
        if (scopeKey.isBlank()) return null
        val pref = context.dataStore.data.first()
        val snapshots = parseDashboardCacheSnapshots(pref[Keys.DashboardCacheJson])
        return snapshots[scopeKey]
    }

    suspend fun saveDashboardCacheSnapshot(
        scopeKey: String,
        snapshot: DashboardCacheSnapshot,
    ) {
        if (scopeKey.isBlank()) return
        context.dataStore.edit { target ->
            val snapshots = parseDashboardCacheSnapshots(target[Keys.DashboardCacheJson]).toMutableMap()
            snapshots[scopeKey] = snapshot.normalized()
            target[Keys.DashboardCacheJson] = gson.toJson(snapshots)
        }
    }

    suspend fun loadDashboardServerSnapshots(): Map<String, CachedDashboardServerSnapshot> {
        val pref = context.dataStore.data.first()
        return parseDashboardServerSnapshots(pref[Keys.DashboardServerSnapshotsJson])
    }

    suspend fun loadHomeAggregateSpeedHistorySnapshot(): HomeAggregateSpeedHistorySnapshot {
        val pref = context.dataStore.data.first()
        return parseHomeAggregateSpeedHistorySnapshot(pref[Keys.HomeAggregateSpeedHistoryJson])
    }

    suspend fun saveHomeAggregateSpeedHistorySnapshot(snapshot: HomeAggregateSpeedHistorySnapshot) {
        context.dataStore.edit { target ->
            val normalized = snapshot.normalized()
            if (normalized.points.isEmpty()) {
                target.remove(Keys.HomeAggregateSpeedHistoryJson)
            } else {
                target[Keys.HomeAggregateSpeedHistoryJson] = gson.toJson(normalized)
            }
        }
    }

    suspend fun loadServerDashboardPreferences(): Map<String, ServerDashboardPreferences> {
        val pref = context.dataStore.data.first()
        return parseServerDashboardPreferences(pref[Keys.ServerDashboardPreferencesJson])
    }

    suspend fun loadServerDashboardPreferences(profileId: String): ServerDashboardPreferences? {
        if (profileId.isBlank()) return null
        return loadServerDashboardPreferences()[profileId.trim()]
    }

    suspend fun saveServerDashboardPreferences(
        profileId: String,
        preferences: ServerDashboardPreferences,
    ) {
        val normalizedProfileId = profileId.trim()
        if (normalizedProfileId.isBlank()) return
        context.dataStore.edit { target ->
            val preferencesById = parseServerDashboardPreferences(target[Keys.ServerDashboardPreferencesJson]).toMutableMap()
            preferencesById[normalizedProfileId] = preferences.normalized()
            target[Keys.ServerDashboardPreferencesJson] = gson.toJson(preferencesById)
        }
    }

    suspend fun updateServerDashboardPreferences(
        profileId: String,
        fallbackSettings: ConnectionSettings,
        update: (ServerDashboardPreferences) -> ServerDashboardPreferences,
    ): ServerDashboardPreferences {
        val current = loadServerDashboardPreferences(profileId)
            ?: defaultServerDashboardPreferences(fallbackSettings)
        val next = update(current).normalized()
        saveServerDashboardPreferences(profileId, next)
        return next
    }

    suspend fun removeServerDashboardPreferences(profileId: String) {
        if (profileId.isBlank()) return
        context.dataStore.edit { target ->
            val preferencesById = parseServerDashboardPreferences(target[Keys.ServerDashboardPreferencesJson]).toMutableMap()
            preferencesById.remove(profileId.trim())
            target[Keys.ServerDashboardPreferencesJson] = gson.toJson(preferencesById)
        }
    }

    suspend fun saveDashboardServerSnapshot(snapshot: CachedDashboardServerSnapshot) {
        val profileId = snapshot.profileId.trim()
        if (profileId.isBlank()) return
        context.dataStore.edit { target ->
            val snapshots = parseDashboardServerSnapshots(target[Keys.DashboardServerSnapshotsJson]).toMutableMap()
            snapshots[profileId] = snapshot.normalized()
            target[Keys.DashboardServerSnapshotsJson] = gson.toJson(snapshots)
        }
    }

    suspend fun removeDashboardServerSnapshot(profileId: String) {
        if (profileId.isBlank()) return
        context.dataStore.edit { target ->
            val snapshots = parseDashboardServerSnapshots(target[Keys.DashboardServerSnapshotsJson]).toMutableMap()
            snapshots.remove(profileId)
            target[Keys.DashboardServerSnapshotsJson] = gson.toJson(snapshots)
        }
    }

    suspend fun loadSettingsForProfile(profileId: String): ConnectionSettings? {
        if (profileId.isBlank()) return null
        val pref = context.dataStore.data.first()
        val profile = parseProfiles(pref[Keys.ServerProfilesJson]).firstOrNull { it.id == profileId } ?: return null
        return pref.toSettings(resolvePassword(profileId)).copy(
            host = profile.host,
            port = profile.port,
            useHttps = profile.useHttps,
            username = profile.username,
            serverBackendType = profile.backendType,
            refreshSeconds = profile.refreshSeconds,
        )
    }

    suspend fun migrateLegacyPasswordIfNeeded() {
        val prefBefore = context.dataStore.data.first()
        val legacy = prefBefore[Keys.PasswordLegacy].orEmpty()
        if (legacy.isNotBlank()) {
            secureCredentials.savePassword(legacy)
            context.dataStore.edit { it.remove(Keys.PasswordLegacy) }
        }

        ensureDefaultServerProfileIfMissing()
        migrateLegacyDashboardHintsIfNeeded()
        cleanupDeprecatedDashboardTrendHistoryIfNeeded()
    }

    private suspend fun ensureDefaultServerProfileIfMissing() {
        val pref = context.dataStore.data.first()
        val profiles = parseProfiles(pref[Keys.ServerProfilesJson]).toMutableList()
        if (profiles.isNotEmpty()) return

        val host = pref[Keys.Host].orEmpty().trim()
        if (host.isBlank()) return

        val profile = ServerProfile(
            id = generateProfileId(),
            name = buildProfileName(
                requestedName = "",
                host = host,
                index = 1,
            ),
            backendType = runCatching {
                enumValueOf<ServerBackendType>(pref[Keys.ServerBackendType].orEmpty())
            }.getOrDefault(ServerBackendType.QBITTORRENT),
            host = host,
            port = (pref[Keys.Port] ?: 8080).coerceIn(1, 65535),
            useHttps = pref[Keys.UseHttps] ?: false,
            username = pref[Keys.Username] ?: "admin",
            refreshSeconds = (pref[Keys.RefreshSeconds] ?: 5).coerceIn(5, 120),
        )
        profiles += profile

        val password = secureCredentials.getPassword()
        if (password.isNotBlank()) {
            secureCredentials.savePasswordForProfile(profile.id, password)
        }

        context.dataStore.edit { target ->
            target[Keys.ServerProfilesJson] = gson.toJson(profiles)
            target[Keys.ActiveServerProfileId] = profile.id
        }
    }

    private fun parseProfiles(raw: String?): List<ServerProfile> {
        val text = raw.orEmpty().trim()
        if (text.isBlank()) return emptyList()
        return runCatching {
            gson.fromJson(text, com.google.gson.JsonArray::class.java)
                ?.mapNotNull { element ->
                    val obj = element?.asJsonObject ?: return@mapNotNull null
                    val id = obj.get("id")?.asString.orEmpty().trim()
                    val host = obj.get("host")?.asString.orEmpty().trim()
                    if (id.isBlank() || host.isBlank()) return@mapNotNull null
                    val backendType = runCatching {
                        enumValueOf<ServerBackendType>(
                            obj.get("backendType")?.asString.orEmpty().ifBlank {
                                ServerBackendType.QBITTORRENT.name
                            }
                        )
                    }.getOrDefault(ServerBackendType.QBITTORRENT)
                    ServerProfile(
                        id = id,
                        name = obj.get("name")?.asString.orEmpty().trim().ifBlank {
                            buildProfileName("", host, 0)
                        },
                        backendType = backendType,
                        host = host,
                        port = (obj.get("port")?.asInt ?: 8080).coerceIn(1, 65535),
                        useHttps = obj.get("useHttps")?.asBoolean ?: false,
                        username = obj.get("username")?.asString.orEmpty().ifBlank { "admin" },
                        refreshSeconds = (obj.get("refreshSeconds")?.asInt ?: 5).coerceIn(5, 120),
                    )
                }
                .orEmpty()
                .distinctBy { it.id }
        }.getOrDefault(emptyList())
    }

    private fun parseDailyUploadTrackingSnapshots(raw: String?): Map<String, DailyUploadTrackingSnapshot> {
        val text = raw.orEmpty().trim()
        if (text.isBlank()) return emptyMap()
        return runCatching {
            gson.fromJson<Map<String, DailyUploadTrackingSnapshot>>(text, dailyUploadTrackingMapType)
                .orEmpty()
                .mapKeys { it.key.trim() }
                .filterKeys { it.isNotBlank() }
                .mapValues { (_, snapshot) -> snapshot.normalized() }
        }.getOrDefault(emptyMap())
    }

    private fun parseDailyCountryUploadTrackingSnapshots(raw: String?): Map<String, DailyCountryUploadTrackingSnapshot> {
        val text = raw.orEmpty().trim()
        if (text.isBlank()) return emptyMap()
        return runCatching {
            gson.fromJson<Map<String, DailyCountryUploadTrackingSnapshot>>(text, dailyCountryUploadTrackingMapType)
                .orEmpty()
                .mapKeys { it.key.trim() }
                .filterKeys { it.isNotBlank() }
                .mapValues { (_, snapshot) -> snapshot.normalized() }
        }.getOrDefault(emptyMap())
    }

    private fun parseDashboardCacheSnapshots(raw: String?): Map<String, DashboardCacheSnapshot> {
        val text = raw.orEmpty().trim()
        if (text.isBlank()) return emptyMap()
        return runCatching {
            gson.fromJson<Map<String, DashboardCacheSnapshot>>(text, dashboardCacheMapType)
                .orEmpty()
                .mapKeys { it.key.trim() }
                .filterKeys { it.isNotBlank() }
                .mapValues { (_, snapshot) -> snapshot.normalized() }
        }.getOrDefault(emptyMap())
    }

    private fun parseHomeAggregateSpeedHistorySnapshot(raw: String?): HomeAggregateSpeedHistorySnapshot {
        val text = raw.orEmpty().trim()
        if (text.isBlank()) return HomeAggregateSpeedHistorySnapshot()
        return runCatching {
            gson.fromJson(text, HomeAggregateSpeedHistorySnapshot::class.java)
                ?.normalized()
        }.getOrNull() ?: HomeAggregateSpeedHistorySnapshot()
    }

    private fun parseServerDashboardPreferences(raw: String?): Map<String, ServerDashboardPreferences> {
        val text = raw.orEmpty().trim()
        if (text.isBlank()) return emptyMap()
        return runCatching {
            gson.fromJson<Map<String, ServerDashboardPreferences>>(text, serverDashboardPreferencesMapType)
                .orEmpty()
                .mapKeys { it.key.trim() }
                .filterKeys { it.isNotBlank() }
                .mapValues { (_, preferences) -> preferences.normalized() }
        }.getOrDefault(emptyMap())
    }

    private fun parseDashboardServerSnapshots(raw: String?): Map<String, CachedDashboardServerSnapshot> {
        val text = raw.orEmpty().trim()
        if (text.isBlank()) return emptyMap()
        return runCatching {
            gson.fromJson<Map<String, CachedDashboardServerSnapshot>>(text, dashboardServerSnapshotMapType)
                .orEmpty()
                .mapKeys { it.key.trim() }
                .filterKeys { it.isNotBlank() }
                .mapValues { (_, snapshot) -> snapshot.normalized() }
        }.getOrDefault(emptyMap())
    }

    private fun resolvePassword(profileId: String): String {
        return if (profileId.isBlank()) {
            secureCredentials.getPassword()
        } else {
            secureCredentials.getPasswordForProfile(profileId).ifBlank {
                secureCredentials.getPassword()
            }
        }
    }

    private fun buildProfileName(
        requestedName: String,
        host: String,
        index: Int,
    ): String {
        val trimmedName = requestedName.trim()
        if (trimmedName.isNotBlank()) return trimmedName
        val fallbackHost = host.trim().ifBlank { "Server $index" }
        return fallbackHost
    }

    private fun generateProfileId(): String = UUID.randomUUID().toString()

    private fun DailyUploadTrackingSnapshot.normalized(): DailyUploadTrackingSnapshot {
        return copy(
            date = date.trim(),
            baselineByTorrent = baselineByTorrent
                .filterKeys { it.isNotBlank() }
                .mapValues { (_, value) -> value.coerceAtLeast(0L) },
            lastSeenByTorrent = lastSeenByTorrent
                .filterKeys { it.isNotBlank() }
                .mapValues { (_, value) -> value.coerceAtLeast(0L) },
        )
    }

    private fun DailyCountryUploadTrackingSnapshot.normalized(): DailyCountryUploadTrackingSnapshot {
        return copy(
            date = date.trim(),
            totalsByCountry = totalsByCountry
                .mapKeys { it.key.trim().uppercase() }
                .filterKeys { it.isNotBlank() }
                .mapValues { (_, value) -> value.coerceAtLeast(0L) },
            peerSnapshots = peerSnapshots
                .filterKeys { it.isNotBlank() }
                .mapValues { (_, snapshot) ->
                    snapshot.copy(
                        key = snapshot.key.trim(),
                        peerAddress = snapshot.peerAddress.trim(),
                        countryCode = snapshot.countryCode.trim().uppercase(),
                        countryName = snapshot.countryName.trim(),
                        uploadedBytes = snapshot.uploadedBytes.coerceAtLeast(0L),
                    )
                },
            lastSeenByTorrent = lastSeenByTorrent
                .filterKeys { it.isNotBlank() }
                .mapValues { (_, value) -> value.coerceAtLeast(0L) },
        )
    }

    private fun DashboardCacheSnapshot.normalized(): DashboardCacheSnapshot {
        return copy(
            dailyTagUploadDate = dailyTagUploadDate.trim(),
            dailyTagUploadStats = dailyTagUploadStats.map { it.copy(tag = it.tag.trim()) },
            dailyCountryUploadDate = dailyCountryUploadDate.trim(),
            dailyCountryUploadStats = dailyCountryUploadStats.map { record ->
                record.copy(
                    countryCode = record.countryCode.trim().uppercase(),
                    countryName = record.countryName.trim(),
                    uploadedBytes = record.uploadedBytes.coerceAtLeast(0L),
                )
            },
        )
    }

    private fun HomeSpeedHistoryPoint.normalized(): HomeSpeedHistoryPoint {
        return copy(
            timestamp = timestamp.coerceAtLeast(0L),
            uploadSpeed = uploadSpeed.coerceAtLeast(0L),
            downloadSpeed = downloadSpeed.coerceAtLeast(0L),
            onlineServerCount = onlineServerCount.coerceAtLeast(0),
        )
    }

    private fun HomeAggregateSpeedHistorySnapshot.normalized(): HomeAggregateSpeedHistorySnapshot {
        return copy(
            points = points
                .map { it.normalized() }
                .sortedBy { it.timestamp },
        )
    }

    private fun CachedDashboardServerSnapshot.normalized(): CachedDashboardServerSnapshot {
        return copy(
            profileId = profileId.trim(),
            profileName = profileName.trim(),
            host = host.trim(),
            port = port.coerceIn(1, 65535),
            serverVersion = serverVersion.trim().ifBlank { "-" },
            dailyTagUploadDate = dailyTagUploadDate.trim(),
            dailyTagUploadStats = dailyTagUploadStats.map { it.copy(tag = it.tag.trim()) },
            dailyCountryUploadDate = dailyCountryUploadDate.trim(),
            dailyCountryUploadStats = dailyCountryUploadStats.map { record ->
                record.copy(
                    countryCode = record.countryCode.trim().uppercase(),
                    countryName = record.countryName.trim(),
                    uploadedBytes = record.uploadedBytes.coerceAtLeast(0L),
                )
            },
            lastUpdatedAt = lastUpdatedAt.coerceAtLeast(0L),
            errorMessage = errorMessage.trim(),
        )
    }

    private fun ServerDashboardPreferences.normalized(): ServerDashboardPreferences {
        val normalizedVisible = visibleCards
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filter { it in VALID_DASHBOARD_CARD_KEYS }
            .distinct()
        val normalizedOrder = cardOrder
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filter { it in VALID_DASHBOARD_CARD_KEYS }
            .let { tokens ->
                val ordered = buildList {
                    tokens.distinct().forEach(::add)
                    normalizedVisible.forEach { token ->
                        if (!contains(token)) add(token)
                    }
                }
                ordered.joinToString(",")
            }
        return copy(
            visibleCards = normalizedVisible,
            cardOrder = normalizedOrder,
        )
    }

    private fun defaultServerDashboardPreferences(settings: ConnectionSettings): ServerDashboardPreferences {
        val visibleCards = when (settings.serverBackendType) {
            ServerBackendType.QBITTORRENT -> listOf(
                DASHBOARD_CARD_COUNTRY_FLOW,
                DASHBOARD_CARD_CATEGORY_SHARE,
                DASHBOARD_CARD_DAILY_UPLOAD,
            )
            ServerBackendType.TRANSMISSION -> listOf(
                DASHBOARD_CARD_CATEGORY_SHARE,
                DASHBOARD_CARD_TAG_UPLOAD,
                DASHBOARD_CARD_TORRENT_STATE,
                DASHBOARD_CARD_TRACKER_SITE,
            )
        }
        return ServerDashboardPreferences(
            visibleCards = visibleCards,
            cardOrder = visibleCards.joinToString(","),
        ).normalized()
    }

    private fun ConnectionSettings.toServerProfile(
        id: String,
        name: String,
    ): ServerProfile {
        return ServerProfile(
            id = id,
            name = name,
            backendType = serverBackendType,
            host = host.trim(),
            port = port.coerceIn(1, 65535),
            useHttps = useHttps,
            username = username.trim().ifBlank { "admin" },
            refreshSeconds = refreshSeconds.coerceIn(5, 120),
        )
    }

    private fun Preferences.toSettings(securePassword: String): ConnectionSettings {
        return ConnectionSettings(
            host = this[Keys.Host] ?: "",
            port = this[Keys.Port] ?: 8080,
            useHttps = this[Keys.UseHttps] ?: false,
            username = this[Keys.Username] ?: "admin",
            password = securePassword,
            serverBackendType = runCatching {
                enumValueOf<ServerBackendType>(this[Keys.ServerBackendType].orEmpty())
            }.getOrDefault(ServerBackendType.QBITTORRENT),
            refreshSeconds = (this[Keys.RefreshSeconds] ?: 5).coerceIn(5, 120),
            appLanguage = runCatching {
                enumValueOf<AppLanguage>(this[Keys.AppLanguage].orEmpty())
            }.getOrDefault(AppLanguage.SYSTEM),
            appTheme = runCatching {
                enumValueOf<AppTheme>(this[Keys.AppTheme].orEmpty())
            }.getOrDefault(AppTheme.DARK),
            customBackgroundImagePath = this[Keys.CustomBackgroundImagePath].orEmpty(),
            customBackgroundToneIsLight = this[Keys.CustomBackgroundToneIsLight] ?: false,
            deleteFilesDefault = this[Keys.DeleteFilesDefault] ?: true,
            deleteFilesWhenNoSeeders = this[Keys.DeleteFilesWhenNoSeeders] ?: false,
            homeTorrentEntryHintDismissed = this[Keys.HomeTorrentEntryHintDismissed] ?: false,
            hasSeenDashboardHideHint = this[Keys.HasSeenDashboardHideHint] ?: false,
            hasSeenDashboardHiddenSnack = this[Keys.HasSeenDashboardHiddenSnack] ?: false,
            hasSeenServerStackReorderHint = this[Keys.HasSeenServerStackReorderHint] ?: false,
            hasSeenServerDashboardSwipeHint = this[Keys.HasSeenServerDashboardSwipeHint] ?: false,
            hasSeenServerDashboardCardHint = this[Keys.HasSeenServerDashboardCardHint] ?: false,
        )
    }

    private suspend fun migrateLegacyDashboardHintsIfNeeded() {
        val pref = context.dataStore.data.first()
        val existingStackHint = pref[Keys.HasSeenServerStackReorderHint] ?: false
        val existingSwipeHint = pref[Keys.HasSeenServerDashboardSwipeHint] ?: false
        val existingCardHint = pref[Keys.HasSeenServerDashboardCardHint] ?: false
        if (existingStackHint && existingSwipeHint && existingCardHint) return

        val preferences = parseServerDashboardPreferences(pref[Keys.ServerDashboardPreferencesJson]).values
        val migratedStackHint = existingStackHint || preferences.any { it.hasSeenStackReorderHint }
        val migratedSwipeHint = existingSwipeHint || preferences.any { it.hasSeenDashboardSwipeHint }
        val migratedCardHint = existingCardHint || preferences.any { it.hasSeenDashboardCardHint }
        if (
            migratedStackHint == existingStackHint &&
            migratedSwipeHint == existingSwipeHint &&
            migratedCardHint == existingCardHint
        ) {
            return
        }

        context.dataStore.edit { target ->
            target[Keys.HasSeenServerStackReorderHint] = migratedStackHint
            target[Keys.HasSeenServerDashboardSwipeHint] = migratedSwipeHint
            target[Keys.HasSeenServerDashboardCardHint] = migratedCardHint
        }
    }

    private suspend fun cleanupDeprecatedDashboardTrendHistoryIfNeeded() {
        val pref = context.dataStore.data.first()
        if (!pref.contains(Keys.DeprecatedDashboardTrendHistoryJson)) return
        context.dataStore.edit { target ->
            target.remove(Keys.DeprecatedDashboardTrendHistoryJson)
        }
    }
}
