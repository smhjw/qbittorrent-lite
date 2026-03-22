package com.hjw.qbremote.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.hjw.qbremote.data.AppLanguage
import com.hjw.qbremote.data.AppTheme
import com.hjw.qbremote.data.BackendConnectionError
import com.hjw.qbremote.data.CachedDashboardServerSnapshot
import com.hjw.qbremote.data.ConnectionSettings
import com.hjw.qbremote.data.ConnectionStore
import com.hjw.qbremote.data.CachedDailyTagUploadStat
import com.hjw.qbremote.data.DailyCountryUploadTrackingSnapshot
import com.hjw.qbremote.data.DailyUploadTrackingSnapshot
import com.hjw.qbremote.data.DashboardCacheSnapshot
import com.hjw.qbremote.data.ServerBackendType
import com.hjw.qbremote.data.ServerCapabilities
import com.hjw.qbremote.data.ServerDashboardPreferences
import com.hjw.qbremote.data.ServerProfile
import com.hjw.qbremote.data.TorrentRepository
import com.hjw.qbremote.data.defaultCapabilitiesFor
import com.hjw.qbremote.data.model.AddTorrentFile
import com.hjw.qbremote.data.model.AddTorrentRequest
import com.hjw.qbremote.data.model.CountryPeerSnapshot
import com.hjw.qbremote.data.model.CountryUploadRecord
import com.hjw.qbremote.data.model.DailyCountryUploadStats
import com.hjw.qbremote.data.model.TorrentFileInfo
import com.hjw.qbremote.data.model.TorrentInfo
import com.hjw.qbremote.data.model.TorrentProperties
import com.hjw.qbremote.data.model.TorrentTracker
import com.hjw.qbremote.data.model.TransferInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.Locale

enum class RefreshScene {
    DASHBOARD,
    SERVER,
    TORRENT_DETAIL,
    SETTINGS,
}

data class DailyTagUploadStat(
    val tag: String,
    val uploadedBytes: Long,
    val torrentCount: Int,
    val isNoTag: Boolean = false,
)

data class RealtimeSpeedPoint(
    val timestamp: Long = 0L,
    val uploadSpeed: Long = 0L,
    val downloadSpeed: Long = 0L,
    val onlineServerCount: Int = 0,
)

data class DashboardAggregateState(
    val transferInfo: TransferInfo = TransferInfo(),
    val torrents: List<TorrentInfo> = emptyList(),
    val dailyTagUploadDate: String = "",
    val dailyTagUploadStats: List<DailyTagUploadStat> = emptyList(),
    val dailyCountryUploadDate: String = "",
    val dailyCountryUploadStats: List<CountryUploadRecord> = emptyList(),
    val realtimeSpeedSeries: List<RealtimeSpeedPoint> = emptyList(),
    val totalServerCount: Int = 0,
    val categoryCoverageServerCount: Int = 0,
    val countryCoverageServerCount: Int = 0,
)

data class PendingBackendRepair(
    val profileId: String,
    val profileName: String,
    val expectedBackend: ServerBackendType,
    val detectedBackend: ServerBackendType,
    val detail: String = "",
)

data class MainUiState(
    val settings: ConnectionSettings = ConnectionSettings(),
    val serverProfiles: List<ServerProfile> = emptyList(),
    val activeServerProfileId: String? = null,
    val activeCapabilities: ServerCapabilities = defaultCapabilitiesFor(ServerBackendType.QBITTORRENT),
    val aggregateOnlineServerCount: Int = 0,
    val isConnecting: Boolean = false,
    val isManualRefreshing: Boolean = false,
    val connected: Boolean = false,
    val serverVersion: String = "-",
    val transferInfo: TransferInfo = TransferInfo(),
    val torrents: List<TorrentInfo> = emptyList(),
    val detailHash: String = "",
    val detailLoading: Boolean = false,
    val detailProperties: TorrentProperties? = null,
    val detailFiles: List<TorrentFileInfo> = emptyList(),
    val detailTrackers: List<TorrentTracker> = emptyList(),
    val categoryOptions: List<String> = emptyList(),
    val tagOptions: List<String> = emptyList(),
    val dailyTagUploadDate: String = "",
    val dailyTagUploadStats: List<DailyTagUploadStat> = emptyList(),
    val dailyCountryUploadDate: String = "",
    val dailyCountryUploadStats: List<CountryUploadRecord> = emptyList(),
    val dashboardServerSnapshots: List<CachedDashboardServerSnapshot> = emptyList(),
    val serverDashboardPreferences: Map<String, ServerDashboardPreferences> = emptyMap(),
    val selectedDashboardProfileId: String? = null,
    val dashboardSessionToken: Long = 0L,
    val dashboardAggregate: DashboardAggregateState = DashboardAggregateState(),
    val dashboardCacheHydrated: Boolean = false,
    val hasDashboardSnapshot: Boolean = false,
    val startupRestoreComplete: Boolean = false,
    val refreshScene: RefreshScene = RefreshScene.DASHBOARD,
    val pendingActionKeys: Set<String> = emptySet(),
    val pendingBackendRepair: PendingBackendRepair? = null,
    val errorMessage: String? = null,
)

internal fun buildPendingActionKey(
    profileId: String,
    hash: String,
): String {
    return "${profileId.trim()}|${hash.trim()}"
}

class MainViewModel(
    private val connectionStore: ConnectionStore,
    private val repository: TorrentRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var autoRefreshJob: Job? = null
    private var hourlyBoundaryRefreshJob: Job? = null
    private var countryPeerTrackerJob: Job? = null
    private var dashboardCacheHydrationJob: Job? = null
    private var dashboardAggregationJob: Job? = null
    private var serverSchedulerJob: Job? = null
    private var autoConnectAttempted = false
    private var isRefreshInProgress = false
    private var hydratedDashboardScopeKey: String? = null
    private var initialSettingsLoaded = false
    private var initialServerProfilesLoaded = false
    private var initialDashboardCacheHydrated = false
    private var initialDashboardSnapshotsHydrated = false
    private var activeProfileRequestVersion = 0L
    private val countryTrackingMutex = Mutex()
    private val serverRefreshMutex = Mutex()
    private val nextServerRefreshAt = mutableMapOf<String, Long>()
    private val homeRealtimeSpeedSeries = mutableListOf<RealtimeSpeedPoint>()
    private var dailyUploadTrackingScopeKey: String? = null
    private var dailyUploadBaselineDate: LocalDate? = null
    private val dailyUploadBaselineByTorrent = mutableMapOf<String, Long>()
    private val dailyUploadLastSeenByTorrent = mutableMapOf<String, Long>()
    private var dailyCountryTrackingScopeKey: String? = null
    private var dailyCountryTrackingDate: LocalDate? = null
    private val dailyCountryTotalsByCode = mutableMapOf<String, Long>()
    private val dailyCountryPeerSnapshots = mutableMapOf<String, CountryPeerSnapshot>()
    private val dailyCountryLastSeenByTorrent = mutableMapOf<String, Long>()
    private val activeCountryTrackedHashes = mutableMapOf<String, Long>()

    init {
        viewModelScope.launch {
            connectionStore.migrateLegacyPasswordIfNeeded()
            connectionStore.cleanupLegacyGlobalChartSettingsIfNeeded()
            launch {
                connectionStore.settingsFlow.collect { settings ->
                    _uiState.update { current ->
                        current.copy(
                            settings = settings,
                            activeCapabilities = repository.capabilitiesFor(settings),
                        )
                    }
                    hydrateDashboardCacheForCurrentScope()
                    markInitialSettingsLoaded()
                }
            }
            launch {
                connectionStore.serverProfilesFlow.collect { profilesState ->
                    val previousActiveProfileId = _uiState.value.activeServerProfileId
                    if (profilesState.activeProfileId != previousActiveProfileId) {
                        bumpActiveProfileRequestVersion()
                    }
                    repository.selectProfile(profilesState.activeProfileId)
                    val dashboardPreferences = connectionStore.loadServerDashboardPreferences()
                    _uiState.update { current ->
                        current.copy(
                            serverProfiles = profilesState.profiles,
                            serverDashboardPreferences = dashboardPreferences
                                .filterKeys { profileId -> profilesState.profiles.any { it.id == profileId } },
                            activeServerProfileId = profilesState.activeProfileId,
                            selectedDashboardProfileId = current.selectedDashboardProfileId
                                ?.takeIf { selected -> profilesState.profiles.any { it.id == selected } }
                                ?: profilesState.activeProfileId
                                ?: profilesState.profiles.firstOrNull()?.id,
                            pendingBackendRepair = current.pendingBackendRepair
                                ?.takeIf { pending -> profilesState.profiles.any { it.id == pending.profileId } },
                        )
                    }
                    hydrateDashboardCacheForCurrentScope()
                    hydrateDashboardServerSnapshots()
                    synchronizeServerScheduler()
                    autoConnectIfNeeded(_uiState.value.settings)
                    markInitialServerProfilesLoaded()
                }
            }
        }
    }

    private fun markInitialSettingsLoaded() {
        if (!initialSettingsLoaded) {
            initialSettingsLoaded = true
            maybeMarkStartupRestoreComplete()
        }
    }

    private fun markInitialServerProfilesLoaded() {
        if (!initialServerProfilesLoaded) {
            initialServerProfilesLoaded = true
            maybeMarkStartupRestoreComplete()
        }
    }

    private fun markInitialDashboardCacheHydrated() {
        if (!initialDashboardCacheHydrated) {
            initialDashboardCacheHydrated = true
            maybeMarkStartupRestoreComplete()
        }
    }

    private fun markInitialDashboardSnapshotsHydrated() {
        if (!initialDashboardSnapshotsHydrated) {
            initialDashboardSnapshotsHydrated = true
            maybeMarkStartupRestoreComplete()
        }
    }

    private fun maybeMarkStartupRestoreComplete() {
        if (
            _uiState.value.startupRestoreComplete ||
            !initialSettingsLoaded ||
            !initialServerProfilesLoaded ||
            !initialDashboardCacheHydrated ||
            !initialDashboardSnapshotsHydrated
        ) {
            return
        }
        _uiState.update { current ->
            if (current.startupRestoreComplete) current else current.copy(startupRestoreComplete = true)
        }
    }

    fun updateHost(value: String) = updateSettings { current ->
        val parsed = parseHostInputHints(value)
        current.copy(
            host = value,
            port = parsed?.port ?: current.port,
            useHttps = parsed?.useHttps ?: current.useHttps,
        )
    }
    fun updatePort(value: String) = updateSettings { it.copy(port = value.toIntOrNull() ?: 0) }
    fun updateUseHttps(value: Boolean) = updateSettings { it.copy(useHttps = value) }
    fun updateUsername(value: String) = updateSettings { it.copy(username = value) }
    fun updatePassword(value: String) = updateSettings { it.copy(password = value) }
    fun updateServerBackendType(value: ServerBackendType) = updateSettings { it.copy(serverBackendType = value) }
    fun updateRefreshSeconds(value: String) {
        val sec = value.toIntOrNull()?.coerceIn(5, 120) ?: 5
        updateSettings { it.copy(refreshSeconds = sec) }
    }

    fun updateAppLanguage(value: AppLanguage) = updateAndPersistSettings {
        it.copy(appLanguage = value)
    }

    fun updateAppTheme(value: AppTheme) = updateAndPersistSettings {
        it.copy(appTheme = value)
    }

    fun applyCustomThemeBackground(
        imagePath: String,
        toneIsLight: Boolean,
    ) = updateAndPersistSettings {
        it.copy(
            appTheme = AppTheme.CUSTOM,
            customBackgroundImagePath = imagePath,
            customBackgroundToneIsLight = toneIsLight,
        )
    }

    fun updateDeleteFilesDefault(value: Boolean) = updateAndPersistSettings {
        it.copy(deleteFilesDefault = value)
    }

    fun updateDeleteFilesWhenNoSeeders(value: Boolean) = updateAndPersistSettings {
        it.copy(deleteFilesWhenNoSeeders = value)
    }

    fun dismissHomeTorrentEntryHint() = updateAndPersistSettings {
        if (it.homeTorrentEntryHintDismissed) {
            it
        } else {
            it.copy(homeTorrentEntryHintDismissed = true)
        }
    }

    fun markDashboardHideHintSeen() = updateAndPersistSettings {
        if (it.hasSeenDashboardHideHint) it else it.copy(hasSeenDashboardHideHint = true)
    }

    fun markDashboardHiddenSnackSeen() = updateAndPersistSettings {
        if (it.hasSeenDashboardHiddenSnack) it else it.copy(hasSeenDashboardHiddenSnack = true)
    }

    fun updateRefreshScene(scene: RefreshScene) {
        _uiState.update { current ->
            if (current.refreshScene == scene) current else current.copy(refreshScene = scene)
        }
    }

    fun prepareServerDashboardTransition(profileId: String) {
        val normalizedProfileId = profileId.trim()
        if (normalizedProfileId.isBlank()) return
        _uiState.update { current ->
            current.copy(
                selectedDashboardProfileId = normalizedProfileId,
                dashboardSessionToken = current.dashboardSessionToken + 1L,
                isConnecting = true,
                connected = false,
                errorMessage = null,
                pendingBackendRepair = current.pendingBackendRepair
                    ?.takeUnless { it.profileId != normalizedProfileId },
                serverVersion = "-",
                transferInfo = TransferInfo(),
                torrents = emptyList(),
                dailyTagUploadDate = "",
                dailyTagUploadStats = emptyList(),
                dailyCountryUploadDate = "",
                dailyCountryUploadStats = emptyList(),
                categoryOptions = emptyList(),
                tagOptions = emptyList(),
                dashboardCacheHydrated = false,
                hasDashboardSnapshot = false,
                detailHash = "",
                detailLoading = false,
                detailProperties = null,
                detailFiles = emptyList(),
                detailTrackers = emptyList(),
                pendingActionKeys = emptySet(),
            )
        }
    }

    fun connect() {
        viewModelScope.launch {
            runCatching {
                val currentState = _uiState.value
                val targetProfileId = when {
                    !currentState.activeServerProfileId.isNullOrBlank() -> currentState.activeServerProfileId
                    currentState.settings.host.trim().isNotBlank() && currentState.settings.username.trim().isNotBlank() -> {
                        connectionStore.save(currentState.settings)
                        connectionStore.serverProfilesFlow.first().activeProfileId
                    }

                    else -> null
                } ?: error("请先添加服务器。")

                val targetSettings = connectionStore.switchToServerProfile(targetProfileId)
                repository.selectProfile(targetProfileId)
                bumpActiveProfileRequestVersion()
                _uiState.update { current ->
                    current.copy(
                        settings = targetSettings,
                        activeServerProfileId = targetProfileId,
                        selectedDashboardProfileId = targetProfileId,
                        dashboardSessionToken = current.dashboardSessionToken + 1L,
                        activeCapabilities = repository.capabilitiesFor(targetSettings),
                        isConnecting = true,
                        connected = false,
                        pendingBackendRepair = null,
                        errorMessage = null,
                        serverVersion = "-",
                        transferInfo = TransferInfo(),
                        torrents = emptyList(),
                        dailyTagUploadDate = "",
                        dailyTagUploadStats = emptyList(),
                        dailyCountryUploadDate = "",
                        dailyCountryUploadStats = emptyList(),
                        categoryOptions = emptyList(),
                        tagOptions = emptyList(),
                        detailHash = "",
                        detailLoading = false,
                        detailProperties = null,
                        detailFiles = emptyList(),
                        detailTrackers = emptyList(),
                        pendingActionKeys = emptySet(),
                    )
                }
                hydrateDashboardCacheForCurrentScope(force = true)
                synchronizeServerScheduler()
                nextServerRefreshAt[targetProfileId] = 0L
                refreshServerSnapshotNow(
                    profileId = targetProfileId,
                    showSelectedError = true,
                    forceSettings = targetSettings,
                )
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        connected = false,
                        errorMessage = error.message ?: "连接服务器失败",
                    )
                }
            }
        }
    }

    fun addServerProfile(
        name: String,
        backendType: ServerBackendType,
        host: String,
        port: String,
        useHttps: Boolean,
        username: String,
        password: String,
        refreshSeconds: String,
    ) {
        viewModelScope.launch {
            val result = runCatching {
                val nextSettings = buildProfileSettingsDraft(
                    backendType = backendType,
                    host = host,
                    port = port,
                    useHttps = useHttps,
                    username = username,
                    password = password,
                    refreshSeconds = refreshSeconds,
                )

                val profile = connectionStore.addServerProfile(name = name, settings = nextSettings)
                val switched = connectionStore.switchToServerProfile(profile.id)
                repository.selectProfile(profile.id)
                bumpActiveProfileRequestVersion()
                _uiState.update { current ->
                    current.copy(
                        settings = switched,
                        activeServerProfileId = profile.id,
                        selectedDashboardProfileId = profile.id,
                        dashboardSessionToken = current.dashboardSessionToken + 1L,
                        activeCapabilities = repository.capabilitiesFor(switched),
                        isConnecting = true,
                        connected = false,
                        pendingBackendRepair = null,
                        errorMessage = null,
                        serverVersion = "-",
                        transferInfo = TransferInfo(),
                        torrents = emptyList(),
                        dailyTagUploadDate = "",
                        dailyTagUploadStats = emptyList(),
                        dailyCountryUploadDate = "",
                        dailyCountryUploadStats = emptyList(),
                        categoryOptions = emptyList(),
                        tagOptions = emptyList(),
                        detailHash = "",
                        detailLoading = false,
                        detailProperties = null,
                        detailFiles = emptyList(),
                        detailTrackers = emptyList(),
                        pendingActionKeys = emptySet(),
                    )
                }
            }
            result.onFailure { error ->
                _uiState.update {
                    it.copy(errorMessage = error.message ?: "添加服务器失败")
                }
            }
            if (result.isSuccess) {
                hydrateDashboardCacheForCurrentScope(force = true)
                synchronizeServerScheduler()
                val profileId = _uiState.value.activeServerProfileId ?: return@launch
                nextServerRefreshAt[profileId] = 0L
                refreshServerSnapshotNow(profileId = profileId, showSelectedError = true)
            }
        }
    }

    fun updateServerProfile(
        profileId: String,
        name: String,
        backendType: ServerBackendType,
        host: String,
        port: String,
        useHttps: Boolean,
        username: String,
        password: String,
        refreshSeconds: String,
    ) {
        if (profileId.isBlank()) return
        viewModelScope.launch {
            val wasActive = _uiState.value.activeServerProfileId == profileId
            val result = runCatching {
                val existingSettings = connectionStore.loadSettingsForProfile(profileId)
                    ?: error("服务器配置不存在")
                val nextSettings = buildProfileSettingsDraft(
                    baseSettings = existingSettings,
                    backendType = backendType,
                    host = host,
                    port = port,
                    useHttps = useHttps,
                    username = username,
                    password = password.ifBlank { existingSettings.password },
                    refreshSeconds = refreshSeconds,
                )
                connectionStore.updateServerProfile(
                    profileId = profileId,
                    name = name,
                    settings = nextSettings,
                    passwordOverride = password.takeIf { it.isNotBlank() },
                )
                repository.removeProfile(profileId)
                nextServerRefreshAt[profileId] = 0L
                if (wasActive) {
                    val switched = connectionStore.switchToServerProfile(profileId)
                    repository.selectProfile(profileId)
                    bumpActiveProfileRequestVersion()
                    _uiState.update { current ->
                        current.copy(
                            settings = switched,
                            activeServerProfileId = profileId,
                            selectedDashboardProfileId = profileId,
                            dashboardSessionToken = current.dashboardSessionToken + 1L,
                            activeCapabilities = repository.capabilitiesFor(switched),
                            isConnecting = true,
                            connected = false,
                            pendingBackendRepair = null,
                            errorMessage = null,
                            serverVersion = "-",
                            transferInfo = TransferInfo(),
                            torrents = emptyList(),
                            dailyTagUploadDate = "",
                            dailyTagUploadStats = emptyList(),
                            dailyCountryUploadDate = "",
                            dailyCountryUploadStats = emptyList(),
                            categoryOptions = emptyList(),
                            tagOptions = emptyList(),
                            detailHash = "",
                            detailLoading = false,
                            detailProperties = null,
                            detailFiles = emptyList(),
                            detailTrackers = emptyList(),
                            pendingActionKeys = emptySet(),
                        )
                    }
                }
            }
            result.onFailure { error ->
                _uiState.update {
                    it.copy(errorMessage = error.message ?: "更新服务器失败")
                }
            }
            if (result.isSuccess) {
                hydrateDashboardServerSnapshots()
                synchronizeServerScheduler()
                refreshServerSnapshotNow(profileId = profileId, showSelectedError = wasActive)
            }
        }
    }

    fun deleteServerProfile(profileId: String) {
        if (profileId.isBlank()) return
        viewModelScope.launch {
            val result = runCatching {
                connectionStore.deleteServerProfile(profileId)
            }
            result.onFailure { error ->
                _uiState.update {
                    it.copy(errorMessage = error.message ?: "删除服务器失败")
                }
            }
            result.getOrNull()?.let { resultValue ->
                repository.removeProfile(profileId)
                nextServerRefreshAt.remove(profileId)
                hydrateDashboardServerSnapshots()

                val nextProfileId = resultValue.activeProfileId
                if (nextProfileId.isNullOrBlank()) {
                    serverSchedulerJob?.cancel()
                    serverSchedulerJob = null
                    repository.clearAllSessions()
                    bumpActiveProfileRequestVersion()
                    _uiState.update { current ->
                        current.copy(
                            activeServerProfileId = null,
                            selectedDashboardProfileId = null,
                            dashboardSessionToken = current.dashboardSessionToken + 1L,
                            connected = false,
                            isConnecting = false,
                            serverVersion = "-",
                            transferInfo = TransferInfo(),
                            torrents = emptyList(),
                            dailyTagUploadDate = "",
                            dailyTagUploadStats = emptyList(),
                            dailyCountryUploadDate = "",
                            dailyCountryUploadStats = emptyList(),
                            dashboardServerSnapshots = emptyList(),
                            dashboardAggregate = DashboardAggregateState(),
                            categoryOptions = emptyList(),
                            tagOptions = emptyList(),
                            pendingBackendRepair = null,
                            detailHash = "",
                            detailLoading = false,
                            detailProperties = null,
                            detailFiles = emptyList(),
                            detailTrackers = emptyList(),
                            pendingActionKeys = emptySet(),
                        )
                    }
                } else {
                    repository.selectProfile(nextProfileId)
                    val nextSettings = resultValue.settings
                        ?: connectionStore.loadSettingsForProfile(nextProfileId)
                        ?: _uiState.value.settings
                    bumpActiveProfileRequestVersion()
                    _uiState.update { current ->
                        current.copy(
                            settings = nextSettings,
                            activeServerProfileId = nextProfileId,
                            selectedDashboardProfileId = nextProfileId,
                            dashboardSessionToken = current.dashboardSessionToken + 1L,
                            activeCapabilities = repository.capabilitiesFor(nextSettings),
                            isConnecting = true,
                            connected = false,
                            pendingBackendRepair = null,
                            errorMessage = null,
                            serverVersion = "-",
                            transferInfo = TransferInfo(),
                            torrents = emptyList(),
                            dailyTagUploadDate = "",
                            dailyTagUploadStats = emptyList(),
                            dailyCountryUploadDate = "",
                            dailyCountryUploadStats = emptyList(),
                            categoryOptions = emptyList(),
                            tagOptions = emptyList(),
                            detailHash = "",
                            detailLoading = false,
                            detailProperties = null,
                            detailFiles = emptyList(),
                            detailTrackers = emptyList(),
                            pendingActionKeys = emptySet(),
                        )
                    }
                    hydrateDashboardCacheForCurrentScope(force = true)
                    synchronizeServerScheduler()
                    nextServerRefreshAt[nextProfileId] = 0L
                    refreshServerSnapshotNow(profileId = nextProfileId, showSelectedError = false)
                }
            }
        }
    }

    fun switchServerProfile(profileId: String) {
        val normalizedProfileId = profileId.trim()
        if (normalizedProfileId.isBlank()) return
        prepareServerDashboardTransition(normalizedProfileId)
        viewModelScope.launch {
            val result = runCatching {
                val switched = connectionStore.switchToServerProfile(normalizedProfileId)
                repository.selectProfile(normalizedProfileId)
                bumpActiveProfileRequestVersion()
                _uiState.update { current ->
                    current.copy(
                        settings = switched,
                        activeServerProfileId = normalizedProfileId,
                        selectedDashboardProfileId = normalizedProfileId,
                        activeCapabilities = repository.capabilitiesFor(switched),
                        isConnecting = true,
                        pendingBackendRepair = null,
                    )
                }
            }
            result.onFailure { error ->
                _uiState.update {
                    it.copy(errorMessage = error.message ?: "切换服务器失败")
                }
            }
            if (result.isSuccess) {
                hydrateDashboardCacheForCurrentScope(force = true)
                synchronizeServerScheduler()
                nextServerRefreshAt[normalizedProfileId] = 0L
                refreshServerSnapshotNow(profileId = normalizedProfileId, showSelectedError = true)
            }
        }
    }

    fun selectDashboardProfile(profileId: String) {
        if (profileId.isBlank()) return
        switchServerProfile(profileId)
    }

    fun reorderServerProfiles(profileIds: List<String>) {
        val normalizedIds = profileIds.map { it.trim() }.filter { it.isNotBlank() }
        if (normalizedIds.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                connectionStore.reorderServerProfiles(normalizedIds)
            }.onFailure { error ->
                _uiState.update { current ->
                    current.copy(errorMessage = error.message ?: "调整服务器顺序失败")
                }
            }
        }
    }

    fun updateServerDashboardCardVisibility(
        profileId: String,
        card: DashboardChartCard,
        visible: Boolean,
        onComplete: (Boolean) -> Unit = {},
    ) {
        if (profileId.isBlank()) return
        viewModelScope.launch {
            val fallbackSettings = connectionStore.loadSettingsForProfile(profileId) ?: _uiState.value.settings
            runCatching {
                connectionStore.updateServerDashboardPreferences(profileId, fallbackSettings) { current ->
                    val visibleCards = current.visibleCards.toMutableList()
                    if (visible) {
                        if (!visibleCards.contains(card.storageKey)) visibleCards += card.storageKey
                    } else {
                        visibleCards.remove(card.storageKey)
                    }
                    current.copy(visibleCards = visibleCards)
                }
            }.onSuccess { preferences ->
                _uiState.update { current ->
                    current.copy(
                        serverDashboardPreferences = current.serverDashboardPreferences + (profileId to preferences),
                    )
                }
                onComplete(true)
            }.onFailure { error ->
                _uiState.update { current ->
                    current.copy(errorMessage = error.message ?: "更新图表显示失败")
                }
                onComplete(false)
            }
        }
    }

    fun updateServerDashboardCardOrder(
        profileId: String,
        order: List<DashboardChartCard>,
        onComplete: (Boolean) -> Unit = {},
    ) {
        if (profileId.isBlank()) return
        viewModelScope.launch {
            val fallbackSettings = connectionStore.loadSettingsForProfile(profileId) ?: _uiState.value.settings
            runCatching {
                connectionStore.updateServerDashboardPreferences(profileId, fallbackSettings) { current ->
                    current.copy(cardOrder = order.joinToString(",") { it.storageKey })
                }
            }.onSuccess { preferences ->
                _uiState.update { current ->
                    current.copy(
                        serverDashboardPreferences = current.serverDashboardPreferences + (profileId to preferences),
                    )
                }
                onComplete(true)
            }.onFailure { error ->
                _uiState.update { current ->
                    current.copy(errorMessage = error.message ?: "更新图表排序失败")
                }
                onComplete(false)
            }
        }
    }

    fun resetServerDashboardPreferences(
        profileId: String,
        onComplete: (Boolean) -> Unit = {},
    ) {
        if (profileId.isBlank()) return
        viewModelScope.launch {
            val fallbackSettings = connectionStore.loadSettingsForProfile(profileId) ?: _uiState.value.settings
            val defaults = defaultServerDashboardPreferences(fallbackSettings)
            runCatching {
                connectionStore.saveServerDashboardPreferences(profileId, defaults)
                defaults
            }.onSuccess { preferences ->
                _uiState.update { current ->
                    current.copy(
                        serverDashboardPreferences = current.serverDashboardPreferences + (profileId to preferences),
                    )
                }
                onComplete(true)
            }.onFailure { error ->
                _uiState.update { current ->
                    current.copy(errorMessage = error.message ?: "恢复图表设置失败")
                }
                onComplete(false)
            }
        }
    }

    fun markServerStackReorderHintSeen() = updateAndPersistSettings { current ->
        current.copy(hasSeenServerStackReorderHint = true)
    }

    fun markServerDashboardSwipeHintSeen() = updateAndPersistSettings { current ->
        current.copy(hasSeenServerDashboardSwipeHint = true)
    }

    fun markServerDashboardCardHintSeen() = updateAndPersistSettings { current ->
        current.copy(hasSeenServerDashboardCardHint = true)
    }

    fun exportTorrentFile(
        hash: String,
        onSuccess: (ByteArray) -> Unit,
    ) {
        val profileId = _uiState.value.activeServerProfileId?.trim().orEmpty()
        val normalizedHash = hash.trim()
        if (profileId.isBlank() || normalizedHash.isBlank()) return
        val requestVersion = currentActiveProfileRequestVersion()
        viewModelScope.launch {
            repository.exportTorrentFile(profileId, normalizedHash)
                .onSuccess { bytes -> onSuccess(bytes) }
                .onFailure { error ->
                    if (isActiveProfileRequestValid(profileId, requestVersion)) {
                        _uiState.update {
                            it.copy(errorMessage = error.message ?: "导出种子失败")
                        }
                    }
                }
        }
    }

    private fun autoConnectIfNeeded(settings: ConnectionSettings) {
        if (autoConnectAttempted) return
        if (settings.host.isBlank() || settings.username.isBlank()) return
        val state = _uiState.value
        if (state.serverProfiles.isNotEmpty() && state.activeServerProfileId.isNullOrBlank()) return
        autoConnectAttempted = true
        connectInternal(persistSettings = false, showErrorOnFailure = false)
    }

    private fun defaultServerDashboardPreferences(settings: ConnectionSettings): ServerDashboardPreferences {
        val isTransmission = settings.serverBackendType == ServerBackendType.TRANSMISSION
        val defaultKeys = if (isTransmission) {
            listOf(
                DashboardChartCard.CATEGORY_SHARE.storageKey,
                DashboardChartCard.TAG_UPLOAD.storageKey,
                DashboardChartCard.TORRENT_STATE.storageKey,
                DashboardChartCard.TRACKER_SITE.storageKey,
            )
        } else {
            listOf(
                DashboardChartCard.COUNTRY_FLOW.storageKey,
                DashboardChartCard.CATEGORY_SHARE.storageKey,
                DashboardChartCard.DAILY_UPLOAD.storageKey,
            )
        }
        return ServerDashboardPreferences(
            visibleCards = defaultKeys,
            cardOrder = defaultKeys.joinToString(","),
        )
    }

    private fun bumpActiveProfileRequestVersion() {
        activeProfileRequestVersion += 1
    }

    private fun currentActiveProfileRequestVersion(): Long = activeProfileRequestVersion

    private fun isActiveProfileRequestValid(
        profileId: String,
        requestVersion: Long,
    ): Boolean {
        val normalizedProfileId = profileId.trim()
        return normalizedProfileId.isNotBlank() &&
            _uiState.value.activeServerProfileId == normalizedProfileId &&
            activeProfileRequestVersion == requestVersion
    }

    private fun isDetailRequestValid(
        profileId: String,
        hash: String,
        requestVersion: Long,
    ): Boolean {
        val normalizedHash = hash.trim()
        return normalizedHash.isNotBlank() &&
            isActiveProfileRequestValid(profileId, requestVersion) &&
            _uiState.value.detailHash == normalizedHash
    }

    private fun connectInternal(
        persistSettings: Boolean,
        showErrorOnFailure: Boolean,
    ) {
        if (_uiState.value.isConnecting) return
        viewModelScope.launch {
            resetDailyUploadTrackingState()
            resetDailyCountryUploadTrackingState()
            _uiState.update { it.copy(isConnecting = true, errorMessage = null) }
            val settings = _uiState.value.settings
            if (persistSettings) {
                connectionStore.save(settings)
            }
            hydrateDashboardCacheForCurrentScope()

            repository.connect(settings)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isConnecting = false,
                            connected = true,
                            activeCapabilities = repository.activeCapabilities(),
                        )
                    }
                    refreshServerVersion()
                    refresh()
                    loadGlobalSelectionOptions()
                    startAutoRefresh()
                    startHourlyBoundaryRefresh()
                    if (repository.activeCapabilities().supportsCountryDistribution) {
                        startCountryPeerTracker()
                    } else {
                        countryPeerTrackerJob?.cancel()
                        _uiState.update {
                            if (it.dailyCountryUploadStats.isEmpty()) it
                            else it.copy(
                                dailyCountryUploadDate = "",
                                dailyCountryUploadStats = emptyList(),
                            )
                        }
                    }
                    refreshDashboardServerSnapshotsAsync()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isConnecting = false,
                            connected = false,
                            errorMessage = if (showErrorOnFailure) {
                                error.message ?: "Connection failed."
                            } else {
                                null
                            }
                        )
                    }
                    autoRefreshJob?.cancel()
                    hourlyBoundaryRefreshJob?.cancel()
                    countryPeerTrackerJob?.cancel()
                }
        }
    }

    private fun stopBackgroundJobs() {
        autoRefreshJob?.cancel()
        hourlyBoundaryRefreshJob?.cancel()
        countryPeerTrackerJob?.cancel()
        dashboardAggregationJob?.cancel()
        serverSchedulerJob?.cancel()
        autoRefreshJob = null
        hourlyBoundaryRefreshJob = null
        countryPeerTrackerJob = null
        dashboardAggregationJob = null
        serverSchedulerJob = null
    }

    private fun buildProfileSettingsDraft(
        baseSettings: ConnectionSettings = _uiState.value.settings,
        backendType: ServerBackendType,
        host: String,
        port: String,
        useHttps: Boolean,
        username: String,
        password: String,
        refreshSeconds: String,
    ): ConnectionSettings {
        val normalizedHost = host.trim()
        val parsed = parseHostInputHints(normalizedHost)
        val defaultPort = when (backendType) {
            ServerBackendType.QBITTORRENT -> 8080
            ServerBackendType.TRANSMISSION -> 9091
        }
        val resolvedPort = parsed?.port ?: (port.toIntOrNull() ?: defaultPort)
        val resolvedUseHttps = parsed?.useHttps ?: useHttps
        val nextSettings = baseSettings.copy(
            host = normalizedHost,
            port = resolvedPort.coerceIn(1, 65535),
            useHttps = resolvedUseHttps,
            username = username.trim(),
            password = password,
            serverBackendType = backendType,
            refreshSeconds = (refreshSeconds.toIntOrNull() ?: 5).coerceIn(5, 120),
        )
        require(nextSettings.host.isNotBlank()) { "主机不能为空" }
        require(nextSettings.username.isNotBlank()) { "用户名不能为空" }
        return nextSettings
    }

    private fun resetUiForServerSwitch(
        settings: ConnectionSettings,
        activeProfileId: String?,
    ) {
        _uiState.update {
            it.copy(
                settings = settings,
                activeServerProfileId = activeProfileId,
                activeCapabilities = repository.capabilitiesFor(settings),
                connected = false,
                serverVersion = "-",
                transferInfo = TransferInfo(),
                torrents = emptyList(),
                dailyTagUploadDate = "",
                dailyTagUploadStats = emptyList(),
                dailyCountryUploadDate = "",
                dailyCountryUploadStats = emptyList(),
                selectedDashboardProfileId = activeProfileId ?: it.selectedDashboardProfileId,
                dashboardCacheHydrated = false,
                hasDashboardSnapshot = false,
                detailHash = "",
                detailLoading = false,
                detailProperties = null,
                detailFiles = emptyList(),
                detailTrackers = emptyList(),
                pendingActionKeys = emptySet(),
            )
        }
    }

    fun refresh(manual: Boolean = false) {
        if (isRefreshInProgress) return
        isRefreshInProgress = true
        viewModelScope.launch {
            try {
                if (manual) {
                    _uiState.update {
                        it.copy(
                            isManualRefreshing = true,
                            errorMessage = null,
                        )
                    }
                }

                val state = _uiState.value
                val refreshAllServers = state.refreshScene == RefreshScene.DASHBOARD &&
                    state.serverProfiles.size > 1

                if (refreshAllServers) {
                    state.serverProfiles.forEach { profile ->
                        refreshServerSnapshotNow(
                            profileId = profile.id,
                            showSelectedError = manual && profile.id == state.activeServerProfileId,
                        )
                    }
                } else {
                    val activeProfileId = state.activeServerProfileId
                    if (!activeProfileId.isNullOrBlank()) {
                        refreshServerSnapshotNow(
                            profileId = activeProfileId,
                            showSelectedError = manual,
                        )
                    }
                }
            } finally {
                isRefreshInProgress = false
                if (manual) {
                    _uiState.update {
                        if (it.isManualRefreshing) {
                            it.copy(isManualRefreshing = false)
                        } else {
                            it
                        }
                    }
                }
            }
        }
    }

    fun pauseTorrent(hash: String) = runTorrentAction(hash) { profileId ->
        repository.pauseTorrent(profileId, hash).getOrThrow()
    }

    fun resumeTorrent(hash: String) = runTorrentAction(hash) { profileId ->
        repository.resumeTorrent(profileId, hash).getOrThrow()
    }

    fun deleteTorrent(hash: String, deleteFiles: Boolean) = runTorrentAction(hash) { profileId ->
        repository.deleteTorrent(profileId, hash, deleteFiles).getOrThrow()
    }

    fun reannounceTorrent(hash: String) = runDetailAction(hash) { profileId ->
        repository.reannounceTorrent(profileId, hash).getOrThrow()
    }

    fun recheckTorrent(hash: String) = runDetailAction(hash) { profileId ->
        repository.recheckTorrent(profileId, hash).getOrThrow()
    }

    fun loadTorrentDetail(hash: String) {
        val profileId = _uiState.value.activeServerProfileId?.trim().orEmpty()
        val normalizedHash = hash.trim()
        if (profileId.isBlank() || normalizedHash.isBlank()) return
        val requestVersion = currentActiveProfileRequestVersion()
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    detailHash = normalizedHash,
                    detailLoading = true,
                    errorMessage = null,
                )
            }
            repository.fetchTorrentDetail(profileId, normalizedHash)
                .onSuccess { detail ->
                    val trackers = repository.fetchTorrentTrackers(profileId, normalizedHash)
                        .getOrElse { emptyList() }
                    val categoryOptions = repository.fetchCategoryOptions(profileId)
                        .getOrElse { emptyList() }
                    val tagOptions = repository.fetchTagOptions(profileId)
                        .getOrElse { emptyList() }
                    _uiState.update { current ->
                        if (!isDetailRequestValid(profileId, normalizedHash, requestVersion)) {
                            current
                        } else {
                            current.copy(
                                detailLoading = false,
                                detailProperties = detail.properties,
                                detailFiles = detail.files,
                                detailTrackers = trackers,
                                categoryOptions = categoryOptions,
                                tagOptions = tagOptions,
                            )
                        }
                    }
                }
                .onFailure { error ->
                    _uiState.update { current ->
                        if (!isDetailRequestValid(profileId, normalizedHash, requestVersion)) {
                            current
                        } else {
                            current.copy(
                                detailLoading = false,
                                detailProperties = null,
                                detailFiles = emptyList(),
                                detailTrackers = emptyList(),
                                errorMessage = error.message ?: "加载种子详情失败",
                            )
                        }
                    }
                }
        }
    }

    fun renameTorrent(hash: String, newName: String) = runDetailAction(hash) { profileId ->
        repository.renameTorrent(profileId, hash, newName).getOrThrow()
    }

    fun setTorrentLocation(hash: String, location: String) = runDetailAction(hash) { profileId ->
        repository.setTorrentLocation(profileId, hash, location).getOrThrow()
    }

    fun setTorrentCategory(hash: String, category: String) = runDetailAction(hash) { profileId ->
        repository.setTorrentCategory(profileId, hash, category).getOrThrow()
    }

    fun setTorrentTags(hash: String, oldTags: String, newTags: String) = runDetailAction(hash) { profileId ->
        repository.setTorrentTags(profileId, hash, oldTags, newTags).getOrThrow()
    }

    fun setTorrentSpeedLimit(hash: String, downloadLimitKb: String, uploadLimitKb: String) = runDetailAction(hash) { profileId ->
        val dl = parseLimitKbToBytes(downloadLimitKb)
        val up = parseLimitKbToBytes(uploadLimitKb)
        repository.setTorrentSpeedLimit(profileId, hash, dl, up).getOrThrow()
    }

    fun setTorrentShareRatio(hash: String, ratio: String) = runDetailAction(hash) { profileId ->
        val value = ratio.trim().toDoubleOrNull() ?: throw IllegalArgumentException("分享比率格式无效")
        repository.setTorrentShareRatio(profileId, hash, value).getOrThrow()
    }

    fun addTracker(hash: String, trackerUrl: String) = runDetailAction(hash) { profileId ->
        repository.addTracker(profileId, hash, trackerUrl.trim()).getOrThrow()
    }

    fun editTracker(
        hash: String,
        tracker: TorrentTracker,
        newUrl: String,
    ) = runDetailAction(hash) { profileId ->
        repository.editTracker(
            profileId = profileId,
            hash = hash,
            tracker = tracker,
            newUrl = newUrl.trim(),
        ).getOrThrow()
    }

    fun removeTracker(
        hash: String,
        tracker: TorrentTracker,
    ) = runDetailAction(hash) { profileId ->
        repository.removeTracker(
            profileId = profileId,
            hash = hash,
            tracker = tracker,
        ).getOrThrow()
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun dismissPendingBackendRepair() {
        _uiState.update { current ->
            current.copy(pendingBackendRepair = null)
        }
    }

    fun confirmPendingBackendRepair() {
        val pending = _uiState.value.pendingBackendRepair ?: return
        viewModelScope.launch {
            runCatching {
                val profile = _uiState.value.serverProfiles.firstOrNull { it.id == pending.profileId }
                    ?: error("服务器配置不存在")
                val existingSettings = connectionStore.loadSettingsForProfile(pending.profileId)
                    ?: error("服务器配置不存在")
                val updatedSettings = existingSettings.copy(serverBackendType = pending.detectedBackend)
                connectionStore.updateServerProfile(
                    profileId = pending.profileId,
                    name = profile.name,
                    settings = updatedSettings,
                    passwordOverride = null,
                )
                repository.removeProfile(pending.profileId)
                nextServerRefreshAt[pending.profileId] = 0L
                val isActive = _uiState.value.activeServerProfileId == pending.profileId
                if (isActive) {
                    val switched = connectionStore.switchToServerProfile(pending.profileId)
                    repository.selectProfile(pending.profileId)
                    bumpActiveProfileRequestVersion()
                    _uiState.update { current ->
                        current.copy(
                            settings = switched,
                            activeServerProfileId = pending.profileId,
                            selectedDashboardProfileId = pending.profileId,
                            dashboardSessionToken = current.dashboardSessionToken + 1L,
                            activeCapabilities = repository.capabilitiesFor(switched),
                            isConnecting = true,
                            connected = false,
                            pendingBackendRepair = null,
                            errorMessage = null,
                            serverVersion = "-",
                            transferInfo = TransferInfo(),
                            torrents = emptyList(),
                            dailyTagUploadDate = "",
                            dailyTagUploadStats = emptyList(),
                            dailyCountryUploadDate = "",
                            dailyCountryUploadStats = emptyList(),
                            categoryOptions = emptyList(),
                            tagOptions = emptyList(),
                            detailHash = "",
                            detailLoading = false,
                            detailProperties = null,
                            detailFiles = emptyList(),
                            detailTrackers = emptyList(),
                            pendingActionKeys = emptySet(),
                        )
                    }
                    hydrateDashboardCacheForCurrentScope(force = true)
                } else {
                    _uiState.update { current ->
                        current.copy(
                            pendingBackendRepair = null,
                            errorMessage = null,
                        )
                    }
                }
                hydrateDashboardServerSnapshots()
                synchronizeServerScheduler()
                refreshServerSnapshotNow(
                    profileId = pending.profileId,
                    showSelectedError = true,
                )
            }.onFailure { error ->
                _uiState.update { current ->
                    current.copy(
                        pendingBackendRepair = null,
                        errorMessage = userFacingConnectionMessage(error),
                    )
                }
            }
        }
    }

    fun loadGlobalSelectionOptions() {
        val profileId = _uiState.value.activeServerProfileId?.trim().orEmpty()
        if (!_uiState.value.connected || profileId.isBlank()) return
        val requestVersion = currentActiveProfileRequestVersion()
        viewModelScope.launch {
            val categoryOptions = repository.fetchCategoryOptions(profileId).getOrElse { emptyList() }
            val tagOptions = repository.fetchTagOptions(profileId).getOrElse { emptyList() }
            _uiState.update { current ->
                if (!isActiveProfileRequestValid(profileId, requestVersion)) {
                    current
                } else {
                    current.copy(
                        categoryOptions = categoryOptions,
                        tagOptions = tagOptions,
                    )
                }
            }
        }
    }

    fun addTorrent(
        urls: String,
        files: List<AddTorrentFile>,
        autoTmm: Boolean,
        category: String,
        tags: String,
        savePath: String,
        paused: Boolean,
        skipChecking: Boolean,
        sequentialDownload: Boolean,
        firstLastPiecePrio: Boolean,
        uploadLimitKb: String,
        downloadLimitKb: String,
    ) {
        if (!_uiState.value.connected) {
            _uiState.update { it.copy(errorMessage = "请先连接服务器。") }
            return
        }
        val profileId = _uiState.value.activeServerProfileId?.trim().orEmpty()
        if (profileId.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请先选择服务器。") }
            return
        }
        val requestVersion = currentActiveProfileRequestVersion()
        viewModelScope.launch {
            _uiState.update { it.copy(errorMessage = null) }
            runCatching {
                val request = AddTorrentRequest(
                    urls = urls.trim(),
                    files = files,
                    autoTmm = autoTmm,
                    category = category.trim(),
                    tags = tags.trim(),
                    savePath = savePath.trim(),
                    paused = paused,
                    skipChecking = skipChecking,
                    sequentialDownload = sequentialDownload,
                    firstLastPiecePrio = firstLastPiecePrio,
                    uploadLimitBytes = parseLimitKbToBytes(uploadLimitKb),
                    downloadLimitBytes = parseLimitKbToBytes(downloadLimitKb),
                )
                repository.addTorrent(profileId, request).getOrThrow()
            }.onSuccess {
                if (isActiveProfileRequestValid(profileId, requestVersion)) {
                    loadGlobalSelectionOptions()
                    refresh()
                } else {
                    nextServerRefreshAt[profileId] = 0L
                }
            }.onFailure { error ->
                if (isActiveProfileRequestValid(profileId, requestVersion)) {
                    _uiState.update { it.copy(errorMessage = error.message ?: "添加种子失败。") }
                }
            }
        }
    }

    private fun runTorrentAction(
        hash: String,
        action: suspend (String) -> Unit,
    ) {
        val profileId = _uiState.value.activeServerProfileId?.trim().orEmpty()
        val normalizedHash = hash.trim()
        if (profileId.isBlank() || normalizedHash.isBlank()) return
        val pendingActionKey = buildPendingActionKey(profileId, normalizedHash)
        if (_uiState.value.pendingActionKeys.contains(pendingActionKey)) return
        val requestVersion = currentActiveProfileRequestVersion()

        viewModelScope.launch {
            _uiState.update {
                it.copy(pendingActionKeys = it.pendingActionKeys + pendingActionKey, errorMessage = null)
            }
            runCatching { action(profileId) }
                .onSuccess {
                    if (isActiveProfileRequestValid(profileId, requestVersion)) {
                        refresh()
                    } else {
                        nextServerRefreshAt[profileId] = 0L
                    }
                }
                .onFailure { error ->
                    if (isActiveProfileRequestValid(profileId, requestVersion)) {
                        _uiState.update {
                            it.copy(errorMessage = error.message ?: "Action failed.")
                        }
                    }
                }
            _uiState.update {
                it.copy(pendingActionKeys = it.pendingActionKeys - pendingActionKey)
            }
        }
    }

    private fun runDetailAction(
        hash: String,
        action: suspend (String) -> Unit,
    ) {
        val normalizedHash = hash.trim()
        if (normalizedHash.isBlank()) return
        val requestVersion = currentActiveProfileRequestVersion()
        runTorrentAction(normalizedHash) { profileId ->
            action(profileId)
            val detail = repository.fetchTorrentDetail(profileId, normalizedHash).getOrThrow()
            val trackers = repository.fetchTorrentTrackers(profileId, normalizedHash).getOrElse { emptyList() }
            val categoryOptions = repository.fetchCategoryOptions(profileId).getOrElse { emptyList() }
            val tagOptions = repository.fetchTagOptions(profileId).getOrElse { emptyList() }
            _uiState.update { current ->
                if (!isDetailRequestValid(profileId, normalizedHash, requestVersion)) {
                    current
                } else {
                    current.copy(
                        detailHash = normalizedHash,
                        detailLoading = false,
                        detailProperties = detail.properties,
                        detailFiles = detail.files,
                        detailTrackers = trackers,
                        categoryOptions = categoryOptions,
                        tagOptions = tagOptions,
                    )
                }
            }
        }
    }

    private suspend fun refreshDetailSnapshot(
        profileId: String,
        hash: String,
        requestVersion: Long,
    ) {
        val detail = repository.fetchTorrentDetail(profileId, hash).getOrNull() ?: return
        val trackers = repository.fetchTorrentTrackers(profileId, hash).getOrElse { emptyList() }
        _uiState.update { current ->
            if (!isDetailRequestValid(profileId, hash, requestVersion)) {
                current
            } else {
                current.copy(
                    detailProperties = detail.properties,
                    detailFiles = detail.files,
                    detailTrackers = trackers,
                )
            }
        }
    }

    private fun refreshServerVersion() {
        viewModelScope.launch {
            repository.fetchServerVersion()
                .onSuccess { version ->
                    _uiState.update { it.copy(serverVersion = version.ifBlank { "-" }) }
                    saveActiveDashboardServerSnapshot()
                }
        }
    }

    private fun refreshCountryUploadStatsAsync(torrents: List<TorrentInfo>) {
        viewModelScope.launch {
            val countryStats = countryTrackingMutex.withLock {
                buildDailyCountryUploadStats(torrents)
            }
            _uiState.update {
                it.copy(
                    dailyCountryUploadDate = countryStats.dateLabel,
                    dailyCountryUploadStats = countryStats.countries,
                    dashboardCacheHydrated = true,
                    hasDashboardSnapshot = true,
                )
            }
            saveDashboardCache()
            saveActiveDashboardServerSnapshot()
            refreshDashboardServerSnapshotsAsync(skipActive = true)
        }
    }

    private fun saveDashboardCache() {
        viewModelScope.launch {
            val state = _uiState.value
            connectionStore.saveDashboardCacheSnapshot(
                scopeKey = currentDailyUploadTrackingScopeKey(),
                snapshot = DashboardCacheSnapshot(
                    transferInfo = state.transferInfo,
                    torrents = state.torrents,
                    dailyTagUploadDate = state.dailyTagUploadDate,
                    dailyTagUploadStats = state.dailyTagUploadStats.map { stat ->
                        CachedDailyTagUploadStat(
                            tag = stat.tag,
                            uploadedBytes = stat.uploadedBytes,
                            torrentCount = stat.torrentCount,
                            isNoTag = stat.isNoTag,
                        )
                    },
                    dailyCountryUploadDate = state.dailyCountryUploadDate,
                    dailyCountryUploadStats = state.dailyCountryUploadStats,
                ),
            )
        }
    }

    private fun hydrateDashboardServerSnapshots() {
        dashboardAggregationJob?.cancel()
        dashboardAggregationJob = viewModelScope.launch {
            val ordered = orderedDashboardServerSnapshots(
                profiles = _uiState.value.serverProfiles,
                snapshotsById = connectionStore.loadDashboardServerSnapshots(),
            )
            val aggregate = buildDashboardAggregateWithHistory(
                snapshots = ordered,
                sampleFreshData = false,
            )
            _uiState.update { current ->
                val selectedProfileId = current.activeServerProfileId
                    ?.takeIf { active -> ordered.any { it.profileId == active } }
                    ?: current.selectedDashboardProfileId
                        ?.takeIf { selected -> ordered.any { it.profileId == selected } }
                    ?: ordered.firstOrNull()?.profileId
                current.copy(
                    dashboardServerSnapshots = ordered,
                    selectedDashboardProfileId = selectedProfileId,
                    dashboardAggregate = aggregate,
                    aggregateOnlineServerCount = ordered.count { !it.isStale },
                )
            }
            syncSelectedUiFromStoredSnapshot()
            markInitialDashboardSnapshotsHydrated()
        }
    }

    private fun synchronizeServerScheduler() {
        val profiles = _uiState.value.serverProfiles
        if (profiles.isEmpty()) {
            serverSchedulerJob?.cancel()
            serverSchedulerJob = null
            nextServerRefreshAt.clear()
            repository.clearAllSessions()
            return
        }

        val activeIds = profiles.map { it.id }.toSet()
        nextServerRefreshAt.keys.retainAll(activeIds)
        profiles.forEach { profile ->
            nextServerRefreshAt.putIfAbsent(profile.id, 0L)
        }
        repository.selectProfile(_uiState.value.activeServerProfileId)

        if (serverSchedulerJob?.isActive == true) return
        serverSchedulerJob = viewModelScope.launch {
            while (isActive) {
                val currentProfiles = _uiState.value.serverProfiles
                if (currentProfiles.isEmpty()) break
                val now = System.currentTimeMillis()
                currentProfiles.forEach { profile ->
                    val dueAt = nextServerRefreshAt[profile.id] ?: 0L
                    if (now >= dueAt) {
                        refreshServerSnapshotNow(
                            profileId = profile.id,
                            showSelectedError = false,
                        )
                    }
                }
                delay(1_000L)
            }
        }
    }

    private suspend fun refreshServerSnapshotNow(
        profileId: String,
        showSelectedError: Boolean,
        forceSettings: ConnectionSettings? = null,
    ) {
        if (profileId.isBlank()) return
        serverRefreshMutex.withLock {
            val state = _uiState.value
            val profile = state.serverProfiles.firstOrNull { it.id == profileId }
            val settings = forceSettings ?: connectionStore.loadSettingsForProfile(profileId) ?: return
            val isSelectedProfile = state.activeServerProfileId == profileId
            val selectedRequestVersion = currentActiveProfileRequestVersion()
            if (isSelectedProfile) {
                _uiState.update { current ->
                    current.copy(
                        isConnecting = true,
                        errorMessage = if (showSelectedError) null else current.errorMessage,
                    )
                }
            }

            val result = runCatching {
                repository.connect(profileId, settings).getOrThrow()
                val serverVersion = repository.fetchServerVersion(profileId).getOrElse { "-" }
                val dashboardData = repository.fetchDashboard(profileId).getOrThrow()
                val (tagDate, tagStats) = buildDashboardTagUploadStatsForScope(
                    scopeKey = "profile:$profileId",
                    torrents = dashboardData.torrents,
                )
                val countryStats = if (repository.capabilitiesFor(settings).supportsCountryDistribution) {
                    buildDashboardCountryUploadStatsForScope(
                        scopeKey = "profile:$profileId",
                        torrents = dashboardData.torrents,
                        fetchPeerSnapshots = { hashes ->
                            repository.fetchCountryPeerSnapshots(profileId, hashes)
                                .getOrElse { emptyList() }
                        },
                    )
                } else {
                    DailyCountryUploadStats(
                        dateLabel = tagDate,
                        countries = emptyList(),
                    )
                }
                CachedDashboardServerSnapshot(
                    profileId = profileId,
                    profileName = profile?.name ?: settings.host,
                    backendType = profile?.backendType ?: settings.serverBackendType,
                    host = profile?.host ?: settings.host,
                    port = profile?.port ?: settings.port,
                    useHttps = profile?.useHttps ?: settings.useHttps,
                    serverVersion = serverVersion.ifBlank { "-" },
                    transferInfo = dashboardData.transferInfo,
                    torrents = dashboardData.torrents,
                    dailyTagUploadDate = tagDate,
                    dailyTagUploadStats = tagStats.map { stat ->
                        CachedDailyTagUploadStat(
                            tag = stat.tag,
                            uploadedBytes = stat.uploadedBytes,
                            torrentCount = stat.torrentCount,
                            isNoTag = stat.isNoTag,
                        )
                    },
                    dailyCountryUploadDate = countryStats.dateLabel,
                    dailyCountryUploadStats = countryStats.countries,
                    lastUpdatedAt = System.currentTimeMillis(),
                    errorMessage = "",
                    isStale = false,
                )
            }

            result.onSuccess { snapshot ->
                connectionStore.saveDashboardServerSnapshot(snapshot)
                mergeDashboardSnapshot(snapshot, sampleFreshData = true)
                nextServerRefreshAt[profileId] = System.currentTimeMillis() + nextRefreshIntervalMs(settings)

                if (isSelectedProfile) {
                    repository.selectProfile(profileId)
                    if (isActiveProfileRequestValid(profileId, selectedRequestVersion)) {
                        syncSelectedUiFromSnapshot(
                            profileId = profileId,
                            settings = settings,
                            snapshot = snapshot,
                            connected = true,
                            selectedErrorMessage = null,
                            requestVersion = selectedRequestVersion,
                        )
                    }
                }
            }.onFailure { error ->
                Log.w("QBRemote", "refreshServerSnapshotNow failed for profile=$profileId", error)
                val summaryMessage = userFacingConnectionMessage(error)
                val currentSnapshot = _uiState.value.dashboardServerSnapshots
                    .firstOrNull { it.profileId == profileId }
                    ?: connectionStore.loadDashboardServerSnapshots()[profileId]
                val staleSnapshot = (currentSnapshot ?: CachedDashboardServerSnapshot(
                    profileId = profileId,
                    profileName = profile?.name ?: settings.host,
                    backendType = profile?.backendType ?: settings.serverBackendType,
                    host = profile?.host ?: settings.host,
                    port = profile?.port ?: settings.port,
                    useHttps = profile?.useHttps ?: settings.useHttps,
                )).copy(
                    profileName = profile?.name ?: currentSnapshot?.profileName ?: settings.host,
                    backendType = profile?.backendType ?: currentSnapshot?.backendType ?: settings.serverBackendType,
                    host = profile?.host ?: currentSnapshot?.host ?: settings.host,
                    port = profile?.port ?: currentSnapshot?.port ?: settings.port,
                    useHttps = profile?.useHttps ?: currentSnapshot?.useHttps ?: settings.useHttps,
                    errorMessage = summaryMessage,
                    isStale = true,
                )
                connectionStore.saveDashboardServerSnapshot(staleSnapshot)
                mergeDashboardSnapshot(staleSnapshot, sampleFreshData = false)
                nextServerRefreshAt[profileId] = System.currentTimeMillis() + nextRefreshIntervalMs(settings)

                if (isSelectedProfile && error is BackendConnectionError.WrongBackend) {
                    maybeQueueBackendRepair(
                        profileId = profileId,
                        profileName = profile?.name ?: staleSnapshot.profileName,
                        error = error,
                    )
                }

                if (isSelectedProfile) {
                    repository.selectProfile(profileId)
                    if (isActiveProfileRequestValid(profileId, selectedRequestVersion)) {
                        syncSelectedUiFromSnapshot(
                            profileId = profileId,
                            settings = settings,
                            snapshot = staleSnapshot,
                            connected = false,
                            selectedErrorMessage = if (error is BackendConnectionError.WrongBackend) {
                                null
                            } else if (showSelectedError && !shouldSuppressRefreshError(summaryMessage)) {
                                summaryMessage
                            } else {
                                null
                            },
                            requestVersion = selectedRequestVersion,
                        )
                    }
                }
            }
        }
    }

    private suspend fun syncSelectedUiFromStoredSnapshot() {
        val profileId = _uiState.value.activeServerProfileId ?: return
        val settings = connectionStore.loadSettingsForProfile(profileId) ?: return
        val snapshot = _uiState.value.dashboardServerSnapshots.firstOrNull { it.profileId == profileId }
            ?: connectionStore.loadDashboardServerSnapshots()[profileId]
        repository.selectProfile(profileId)
        val requestVersion = currentActiveProfileRequestVersion()
        syncSelectedUiFromSnapshot(
            profileId = profileId,
            settings = settings,
            snapshot = snapshot,
            connected = repository.isConnected(profileId) && snapshot?.isStale == false,
            selectedErrorMessage = null,
            requestVersion = requestVersion,
        )
    }

    private suspend fun syncSelectedUiFromSnapshot(
        profileId: String,
        settings: ConnectionSettings,
        snapshot: CachedDashboardServerSnapshot?,
        connected: Boolean,
        selectedErrorMessage: String?,
        requestVersion: Long,
    ) {
        if (!isActiveProfileRequestValid(profileId, requestVersion)) return

        val categoryOptions = if (connected) {
            repository.fetchCategoryOptions(profileId).getOrElse { emptyList() }
        } else {
            emptyList()
        }
        val tagOptions = if (connected) {
            repository.fetchTagOptions(profileId).getOrElse { emptyList() }
        } else {
            emptyList()
        }

        _uiState.update { current ->
            if (!isActiveProfileRequestValid(profileId, requestVersion)) {
                current
            } else {
                current.copy(
                    settings = settings,
                    activeCapabilities = repository.capabilitiesFor(settings),
                    isConnecting = false,
                    connected = connected,
                    serverVersion = snapshot?.serverVersion?.ifBlank { "-" } ?: "-",
                    transferInfo = snapshot?.transferInfo ?: TransferInfo(),
                    torrents = snapshot?.torrents ?: emptyList(),
                    dailyTagUploadDate = snapshot?.dailyTagUploadDate.orEmpty(),
                    dailyTagUploadStats = snapshot?.dailyTagUploadStats?.map { stat ->
                        DailyTagUploadStat(
                            tag = stat.tag,
                            uploadedBytes = stat.uploadedBytes,
                            torrentCount = stat.torrentCount,
                            isNoTag = stat.isNoTag,
                        )
                    }.orEmpty(),
                    dailyCountryUploadDate = snapshot?.dailyCountryUploadDate.orEmpty(),
                    dailyCountryUploadStats = snapshot?.dailyCountryUploadStats.orEmpty(),
                    categoryOptions = categoryOptions,
                    tagOptions = tagOptions,
                    dashboardCacheHydrated = true,
                    hasDashboardSnapshot = snapshot != null,
                    pendingBackendRepair = current.pendingBackendRepair
                        ?.takeUnless { connected && it.profileId == profileId },
                    errorMessage = selectedErrorMessage,
                )
            }
        }

        if (snapshot != null && isActiveProfileRequestValid(profileId, requestVersion)) {
            saveDashboardCache()
        }

        val detailHash = _uiState.value.detailHash
        if (connected && _uiState.value.refreshScene == RefreshScene.TORRENT_DETAIL && detailHash.isNotBlank()) {
            refreshDetailSnapshot(profileId, detailHash, requestVersion)
        }
    }

    private suspend fun mergeDashboardSnapshot(
        snapshot: CachedDashboardServerSnapshot,
        sampleFreshData: Boolean,
    ) {
        val current = _uiState.value
        val snapshotsById = current.dashboardServerSnapshots
            .associateBy { it.profileId }
            .toMutableMap()
        snapshotsById[snapshot.profileId] = snapshot
        val ordered = orderedDashboardServerSnapshots(current.serverProfiles, snapshotsById)
        val aggregate = buildDashboardAggregateWithHistory(
            snapshots = ordered,
            sampleFreshData = sampleFreshData,
        )
        _uiState.update { latest ->
            val selectedProfileId = latest.activeServerProfileId
                ?.takeIf { active -> ordered.any { it.profileId == active } }
                ?: latest.selectedDashboardProfileId
                    ?.takeIf { selected -> ordered.any { it.profileId == selected } }
                ?: ordered.firstOrNull()?.profileId
            latest.copy(
                dashboardServerSnapshots = ordered,
                selectedDashboardProfileId = selectedProfileId,
                dashboardAggregate = aggregate,
                aggregateOnlineServerCount = ordered.count { !it.isStale },
            )
        }
    }

    private fun nextRefreshIntervalMs(settings: ConnectionSettings): Long {
        return settings.refreshSeconds.coerceIn(5, 120) * 1_000L
    }

    private fun refreshDashboardServerSnapshotsAsync(skipActive: Boolean = false) {
        dashboardAggregationJob?.cancel()
        dashboardAggregationJob = viewModelScope.launch {
            val profiles = _uiState.value.serverProfiles
            if (profiles.isEmpty()) {
                clearHomeRealtimeSpeedSeries()
                _uiState.update { current ->
                    current.copy(
                        dashboardServerSnapshots = emptyList(),
                        selectedDashboardProfileId = null,
                        dashboardAggregate = DashboardAggregateState(),
                        aggregateOnlineServerCount = 0,
                    )
                }
                return@launch
            }

            val snapshots = connectionStore.loadDashboardServerSnapshots().toMutableMap()
            val activeProfileId = _uiState.value.activeServerProfileId
            val activeProfile = profiles.firstOrNull { it.id == activeProfileId }

            if (!skipActive && _uiState.value.connected && activeProfile != null) {
                val activeSnapshot = buildActiveDashboardServerSnapshot(activeProfile, _uiState.value)
                snapshots[activeProfile.id] = activeSnapshot
                connectionStore.saveDashboardServerSnapshot(activeSnapshot)
            }

            for (profile in profiles) {
                if (profile.id == activeProfileId && _uiState.value.connected) {
                    continue
                }
                val settings = connectionStore.loadSettingsForProfile(profile.id) ?: continue
                repository.fetchDashboardSnapshot(settings)
                    .onSuccess { fetched ->
                        val tagStats = buildDashboardTagUploadStatsForScope(
                            scopeKey = "profile:${profile.id}",
                            torrents = fetched.dashboardData.torrents,
                        )
                        val countryStats = if (repository.capabilitiesFor(settings).supportsCountryDistribution) {
                            buildDashboardCountryUploadStatsForScope(
                                scopeKey = "profile:${profile.id}",
                                torrents = fetched.dashboardData.torrents,
                                fetchPeerSnapshots = { hashes ->
                                    repository.fetchCountryPeerSnapshots(settings, hashes)
                                        .getOrElse { emptyList() }
                                },
                            )
                        } else {
                            com.hjw.qbremote.data.model.DailyCountryUploadStats(
                                dateLabel = tagStats.first,
                                countries = emptyList(),
                            )
                        }
                        val snapshot = CachedDashboardServerSnapshot(
                            profileId = profile.id,
                            profileName = profile.name,
                            backendType = profile.backendType,
                            host = profile.host,
                            port = profile.port,
                            useHttps = profile.useHttps,
                            serverVersion = fetched.serverVersion,
                            transferInfo = fetched.dashboardData.transferInfo,
                            torrents = fetched.dashboardData.torrents,
                            dailyTagUploadDate = tagStats.first,
                            dailyTagUploadStats = tagStats.second.map { stat ->
                                CachedDailyTagUploadStat(
                                    tag = stat.tag,
                                    uploadedBytes = stat.uploadedBytes,
                                    torrentCount = stat.torrentCount,
                                    isNoTag = stat.isNoTag,
                                )
                            },
                            dailyCountryUploadDate = countryStats.dateLabel,
                            dailyCountryUploadStats = countryStats.countries,
                            lastUpdatedAt = System.currentTimeMillis(),
                            errorMessage = "",
                            isStale = false,
                        )
                        snapshots[profile.id] = snapshot
                        connectionStore.saveDashboardServerSnapshot(snapshot)
                    }
                    .onFailure { error ->
                        val staleSnapshot = (snapshots[profile.id] ?: CachedDashboardServerSnapshot(
                            profileId = profile.id,
                            profileName = profile.name,
                            backendType = profile.backendType,
                            host = profile.host,
                            port = profile.port,
                            useHttps = profile.useHttps,
                        )).copy(
                            profileName = profile.name,
                            backendType = profile.backendType,
                            host = profile.host,
                            port = profile.port,
                            useHttps = profile.useHttps,
                            errorMessage = error.message ?: "Refresh failed.",
                            isStale = true,
                        )
                        snapshots[profile.id] = staleSnapshot
                        connectionStore.saveDashboardServerSnapshot(staleSnapshot)
                    }
            }

            val ordered = orderedDashboardServerSnapshots(profiles, snapshots)
            val aggregate = buildDashboardAggregateWithHistory(
                snapshots = ordered,
                sampleFreshData = true,
            )
            _uiState.update { current ->
                current.copy(
                    dashboardServerSnapshots = ordered,
                    selectedDashboardProfileId = current.selectedDashboardProfileId
                        ?.takeIf { selected -> ordered.any { it.profileId == selected } }
                        ?: current.activeServerProfileId
                        ?: ordered.firstOrNull()?.profileId,
                    dashboardAggregate = aggregate,
                    aggregateOnlineServerCount = ordered.count { !it.isStale },
                )
            }
        }
    }

    private suspend fun saveActiveDashboardServerSnapshot() {
        val state = _uiState.value
        val activeProfile = state.serverProfiles.firstOrNull { it.id == state.activeServerProfileId } ?: return
        val snapshot = buildActiveDashboardServerSnapshot(activeProfile, state)
        connectionStore.saveDashboardServerSnapshot(snapshot)
    }

    private fun buildActiveDashboardServerSnapshot(
        profile: ServerProfile,
        state: MainUiState,
    ): CachedDashboardServerSnapshot {
        return CachedDashboardServerSnapshot(
            profileId = profile.id,
            profileName = profile.name,
            backendType = profile.backendType,
            host = profile.host,
            port = profile.port,
            useHttps = profile.useHttps,
            serverVersion = state.serverVersion,
            transferInfo = state.transferInfo,
            torrents = state.torrents,
            dailyTagUploadDate = state.dailyTagUploadDate,
            dailyTagUploadStats = state.dailyTagUploadStats.map { stat ->
                CachedDailyTagUploadStat(
                    tag = stat.tag,
                    uploadedBytes = stat.uploadedBytes,
                    torrentCount = stat.torrentCount,
                    isNoTag = stat.isNoTag,
                )
            },
            dailyCountryUploadDate = state.dailyCountryUploadDate,
            dailyCountryUploadStats = state.dailyCountryUploadStats,
            lastUpdatedAt = System.currentTimeMillis(),
            errorMessage = "",
            isStale = false,
        )
    }

    private fun orderedDashboardServerSnapshots(
        profiles: List<ServerProfile>,
        snapshotsById: Map<String, CachedDashboardServerSnapshot>,
    ): List<CachedDashboardServerSnapshot> {
        return profiles.map { profile ->
            snapshotsById[profile.id]?.copy(
                profileName = profile.name,
                backendType = profile.backendType,
                host = profile.host,
                port = profile.port,
                useHttps = profile.useHttps,
            ) ?: CachedDashboardServerSnapshot(
                profileId = profile.id,
                profileName = profile.name,
                backendType = profile.backendType,
                host = profile.host,
                port = profile.port,
                useHttps = profile.useHttps,
                isStale = true,
            )
        }
    }

    private fun buildDashboardAggregate(
        snapshots: List<CachedDashboardServerSnapshot>,
    ): DashboardAggregateState {
        if (snapshots.isEmpty()) return DashboardAggregateState()

        val liveSnapshots = snapshots.filter { !it.isStale }
        val aggregateSource = liveSnapshots.ifEmpty { snapshots }
        val totalTransfer = aggregateSource.fold(TransferInfo()) { acc, snapshot ->
            TransferInfo(
                downloadSpeed = acc.downloadSpeed + snapshot.transferInfo.downloadSpeed,
                uploadSpeed = acc.uploadSpeed + snapshot.transferInfo.uploadSpeed,
                downloadedTotal = acc.downloadedTotal + snapshot.transferInfo.downloadedTotal,
                uploadedTotal = acc.uploadedTotal + snapshot.transferInfo.uploadedTotal,
                downloadRateLimit = acc.downloadRateLimit + snapshot.transferInfo.downloadRateLimit,
                uploadRateLimit = acc.uploadRateLimit + snapshot.transferInfo.uploadRateLimit,
                freeSpaceOnDisk = acc.freeSpaceOnDisk + snapshot.transferInfo.freeSpaceOnDisk,
                dhtNodes = acc.dhtNodes + snapshot.transferInfo.dhtNodes,
            )
        }
        val mergedTorrents = aggregateSource.flatMap { it.torrents }
        val mergedTagStats = aggregateDashboardTagStats(aggregateSource)
        val mergedCountryStats = aggregateDashboardCountryStats(aggregateSource)
        val totalServerCount = snapshots.size

        return DashboardAggregateState(
            transferInfo = totalTransfer,
            torrents = mergedTorrents,
            dailyTagUploadDate = mergedTagStats.first,
            dailyTagUploadStats = mergedTagStats.second,
            dailyCountryUploadDate = mergedCountryStats.first,
            dailyCountryUploadStats = mergedCountryStats.second,
            totalServerCount = totalServerCount,
            categoryCoverageServerCount = snapshots.count {
                defaultCapabilitiesFor(it.backendType).supportsCategories
            },
            countryCoverageServerCount = snapshots.count {
                defaultCapabilitiesFor(it.backendType).supportsCountryDistribution
            },
        )
    }

    private suspend fun buildDashboardAggregateWithHistory(
        snapshots: List<CachedDashboardServerSnapshot>,
        sampleFreshData: Boolean,
    ): DashboardAggregateState {
        if (snapshots.isEmpty()) return DashboardAggregateState()
        val aggregate = buildDashboardAggregate(snapshots)
        val liveServerCount = snapshots.count { !it.isStale }
        val realtimeSpeedSeries = when {
            liveServerCount <= 0 -> {
                clearHomeRealtimeSpeedSeries()
                emptyList()
            }
            sampleFreshData -> sampleHomeRealtimeSpeedPoint(
                transferInfo = aggregate.transferInfo,
                onlineServerCount = liveServerCount,
            )
            else -> homeRealtimeSpeedSeries.toList()
        }
        return aggregate.copy(realtimeSpeedSeries = realtimeSpeedSeries)
    }

    private fun aggregateDashboardTagStats(
        snapshots: List<CachedDashboardServerSnapshot>,
    ): Pair<String, List<DailyTagUploadStat>> {
        val totals = linkedMapOf<String, DailyTagUploadStat>()
        snapshots.forEach { snapshot ->
            snapshot.dailyTagUploadStats.forEach { stat ->
                val key = if (stat.isNoTag) NO_TAG_KEY else stat.tag.trim().lowercase(Locale.US)
                val existing = totals[key]
                totals[key] = DailyTagUploadStat(
                    tag = if (stat.isNoTag) NO_TAG_KEY else stat.tag,
                    uploadedBytes = (existing?.uploadedBytes ?: 0L) + stat.uploadedBytes,
                    torrentCount = (existing?.torrentCount ?: 0) + stat.torrentCount,
                    isNoTag = stat.isNoTag,
                )
            }
        }
        val date = snapshots.map { it.dailyTagUploadDate.trim() }.firstOrNull { it.isNotBlank() }.orEmpty()
        return date to totals.values
            .filter { it.uploadedBytes > 0L }
            .sortedByDescending { it.uploadedBytes }
    }

    private fun aggregateDashboardCountryStats(
        snapshots: List<CachedDashboardServerSnapshot>,
    ): Pair<String, List<CountryUploadRecord>> {
        val totals = linkedMapOf<String, CountryUploadRecord>()
        snapshots
            .filter { defaultCapabilitiesFor(it.backendType).supportsCountryDistribution }
            .forEach { snapshot ->
                snapshot.dailyCountryUploadStats.forEach recordLoop@{ record ->
                    val key = record.countryCode.trim().uppercase(Locale.US)
                    if (key.isBlank()) return@recordLoop
                    val existing = totals[key]
                    totals[key] = CountryUploadRecord(
                        countryCode = key,
                        countryName = record.countryName.ifBlank { existing?.countryName.orEmpty() },
                        uploadedBytes = (existing?.uploadedBytes ?: 0L) + record.uploadedBytes,
                    )
                }
            }
        val date = snapshots.map { it.dailyCountryUploadDate.trim() }.firstOrNull { it.isNotBlank() }.orEmpty()
        return date to totals.values
            .filter { it.uploadedBytes > 0L }
            .sortedByDescending { it.uploadedBytes }
    }

    private suspend fun buildDashboardTagUploadStatsForScope(
        scopeKey: String,
        torrents: List<TorrentInfo>,
    ): Pair<String, List<DailyTagUploadStat>> {
        val snapshot = connectionStore.loadDailyUploadTrackingSnapshot(scopeKey)
        val today = LocalDate.now()
        val baselineByTorrent = snapshot?.baselineByTorrent?.toMutableMap() ?: mutableMapOf()
        val lastSeenByTorrent = snapshot?.lastSeenByTorrent?.toMutableMap() ?: mutableMapOf()
        val snapshotDate = runCatching {
            snapshot?.date?.takeIf { it.isNotBlank() }?.let(LocalDate::parse)
        }.getOrNull()

        if (snapshotDate != today) {
            val carryOver = lastSeenByTorrent.toMap()
            baselineByTorrent.clear()
            baselineByTorrent.putAll(carryOver)
        }

        val activeKeys = torrents.map(::torrentTrackingKey).toSet()
        baselineByTorrent.keys.retainAll(activeKeys)
        lastSeenByTorrent.keys.retainAll(activeKeys)

        val uploadByTag = mutableMapOf<String, Long>()
        val torrentCountByTag = mutableMapOf<String, Int>()

        torrents.forEach { torrent ->
            val trackingKey = torrentTrackingKey(torrent)
            val currentUploaded = torrent.uploaded.coerceAtLeast(0L)
            val baseline = baselineByTorrent[trackingKey] ?: lastSeenByTorrent[trackingKey]

            if (baseline == null) {
                baselineByTorrent[trackingKey] = currentUploaded
                lastSeenByTorrent[trackingKey] = currentUploaded
                return@forEach
            }

            if (currentUploaded < baseline) {
                baselineByTorrent[trackingKey] = currentUploaded
                lastSeenByTorrent[trackingKey] = currentUploaded
                return@forEach
            }

            val delta = currentUploaded - baseline
            lastSeenByTorrent[trackingKey] = currentUploaded
            if (delta <= 0L) return@forEach

            val tags = parseTorrentTags(torrent.tags).ifEmpty { listOf(NO_TAG_KEY) }
            val baseShare = delta / tags.size
            var remainder = delta % tags.size
            tags.forEach tagLoop@{ tag ->
                val share = baseShare + if (remainder > 0L) {
                    remainder -= 1L
                    1L
                } else {
                    0L
                }
                if (share <= 0L) return@tagLoop
                uploadByTag[tag] = (uploadByTag[tag] ?: 0L) + share
                torrentCountByTag[tag] = (torrentCountByTag[tag] ?: 0) + 1
            }
        }

        connectionStore.saveDailyUploadTrackingSnapshot(
            scopeKey = scopeKey,
            snapshot = DailyUploadTrackingSnapshot(
                date = today.toString(),
                baselineByTorrent = baselineByTorrent,
                lastSeenByTorrent = lastSeenByTorrent,
            ),
        )

        return today.toString() to uploadByTag.entries
            .filter { it.value > 0L }
            .sortedByDescending { it.value }
            .map { (tag, uploaded) ->
                DailyTagUploadStat(
                    tag = tag,
                    uploadedBytes = uploaded,
                    torrentCount = torrentCountByTag[tag] ?: 0,
                    isNoTag = tag == NO_TAG_KEY,
                )
            }
    }

    private suspend fun buildDashboardCountryUploadStatsForScope(
        scopeKey: String,
        torrents: List<TorrentInfo>,
        fetchPeerSnapshots: suspend (List<String>) -> List<CountryPeerSnapshot>,
    ): com.hjw.qbremote.data.model.DailyCountryUploadStats {
        val snapshot = connectionStore.loadDailyCountryUploadTrackingSnapshot(scopeKey)
        val today = LocalDate.now()
        val totalsByCountry = snapshot?.totalsByCountry?.toMutableMap() ?: mutableMapOf()
        val peerSnapshots = snapshot?.peerSnapshots?.toMutableMap() ?: mutableMapOf()
        val lastSeenByTorrent = snapshot?.lastSeenByTorrent?.toMutableMap() ?: mutableMapOf()
        val snapshotDate = runCatching {
            snapshot?.date?.takeIf { it.isNotBlank() }?.let(LocalDate::parse)
        }.getOrNull()

        if (snapshotDate != today) {
            totalsByCountry.clear()
            peerSnapshots.clear()
            lastSeenByTorrent.clear()
        }

        val activeKeys = torrents.map(::torrentTrackingKey).toSet()
        lastSeenByTorrent.keys.retainAll(activeKeys)

        val activeHashes = mutableListOf<String>()
        torrents.forEach { torrent ->
            val trackingKey = torrentTrackingKey(torrent)
            val hash = torrent.hash.trim()
            if (hash.isBlank()) return@forEach
            val currentUploaded = torrent.uploaded.coerceAtLeast(0L)
            val previousUploaded = lastSeenByTorrent[trackingKey]
            lastSeenByTorrent[trackingKey] = currentUploaded
            if (previousUploaded == null) {
                if (torrent.uploadSpeed > 0L) {
                    activeHashes += hash
                }
                return@forEach
            }
            if (currentUploaded > previousUploaded || torrent.uploadSpeed > 0L) {
                activeHashes += hash
            }
        }

        val samples = fetchPeerSnapshots(activeHashes.distinct())
        val currentPeerSnapshots = samples.associateBy { it.key }
        val fallbackNames = samples
            .groupBy { it.countryCode.trim().uppercase(Locale.US) }
            .mapValues { (_, entries) ->
                entries.firstNotNullOfOrNull { it.countryName.trim().takeIf(String::isNotBlank) }.orEmpty()
            }

        samples.forEach { entry ->
            val countryCode = entry.countryCode.trim().uppercase(Locale.US)
            if (countryCode.isBlank()) return@forEach
            val previous = peerSnapshots[entry.key]
            val previousUploaded = previous?.uploadedBytes?.coerceAtLeast(0L)
            val currentUploaded = entry.uploadedBytes.coerceAtLeast(0L)
            val delta = when {
                previousUploaded == null -> 0L
                currentUploaded < previousUploaded -> currentUploaded
                else -> currentUploaded - previousUploaded
            }
            if (delta <= 0L) return@forEach
            totalsByCountry[countryCode] = (totalsByCountry[countryCode] ?: 0L) + delta
        }

        peerSnapshots.keys.retainAll(currentPeerSnapshots.keys)
        peerSnapshots.putAll(currentPeerSnapshots)

        connectionStore.saveDailyCountryUploadTrackingSnapshot(
            scopeKey = scopeKey,
            snapshot = DailyCountryUploadTrackingSnapshot(
                date = today.toString(),
                totalsByCountry = totalsByCountry,
                peerSnapshots = peerSnapshots,
                lastSeenByTorrent = lastSeenByTorrent,
            ),
        )

        return com.hjw.qbremote.data.model.DailyCountryUploadStats(
            dateLabel = today.toString(),
            countries = totalsByCountry.entries
                .filter { it.value > 0L }
                .sortedByDescending { it.value }
                .map { (countryCode, uploadedBytes) ->
                    CountryUploadRecord(
                        countryCode = countryCode,
                        countryName = fallbackNames[countryCode].orEmpty(),
                        uploadedBytes = uploadedBytes,
                    )
                },
        )
    }

    private fun sampleHomeRealtimeSpeedPoint(
        transferInfo: TransferInfo,
        onlineServerCount: Int,
    ): List<RealtimeSpeedPoint> {
        val nextPoint = RealtimeSpeedPoint(
            timestamp = System.currentTimeMillis(),
            uploadSpeed = transferInfo.uploadSpeed.coerceAtLeast(0L),
            downloadSpeed = transferInfo.downloadSpeed.coerceAtLeast(0L),
            onlineServerCount = onlineServerCount.coerceAtLeast(0),
        )
        val lastPoint = homeRealtimeSpeedSeries.lastOrNull()
        if (
            lastPoint != null &&
            nextPoint.timestamp - lastPoint.timestamp < HOME_REALTIME_SPEED_MIN_SAMPLE_INTERVAL_MS
        ) {
            homeRealtimeSpeedSeries[homeRealtimeSpeedSeries.lastIndex] = nextPoint
        } else {
            homeRealtimeSpeedSeries += nextPoint
        }
        while (homeRealtimeSpeedSeries.size > HOME_REALTIME_SPEED_MAX_POINTS) {
            homeRealtimeSpeedSeries.removeAt(0)
        }
        return homeRealtimeSpeedSeries.toList()
    }

    private fun clearHomeRealtimeSpeedSeries() {
        homeRealtimeSpeedSeries.clear()
    }

    private fun parseLimitKbToBytes(value: String): Long {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return -1L
        val kb = trimmed.toLongOrNull() ?: throw IllegalArgumentException("限速值必须是数字")
        if (kb < 0L) return -1L
        return kb * 1024L
    }

    private fun shouldSuppressRefreshError(message: String?): Boolean {
        val normalized = message?.trim()?.lowercase().orEmpty()
        if (normalized.isBlank()) return false
        return normalized.contains("unable to resolve host") ||
            normalized.contains("no address associated with hostname")
    }

    private fun maybeQueueBackendRepair(
        profileId: String,
        profileName: String,
        error: BackendConnectionError.WrongBackend,
    ) {
        _uiState.update { current ->
            current.copy(
                pendingBackendRepair = PendingBackendRepair(
                    profileId = profileId,
                    profileName = profileName.ifBlank { profileId },
                    expectedBackend = error.expected,
                    detectedBackend = error.detected,
                    detail = error.detail,
                ),
            )
        }
    }

    private fun userFacingConnectionMessage(error: Throwable): String {
        return when (error) {
            is BackendConnectionError.WrongBackend -> {
                "服务器类型不匹配，目标看起来是 ${backendDisplayName(error.detected)}。"
            }

            is BackendConnectionError.RpcPathNotFound -> {
                if (error.failureSummary.isBlank()) {
                    "Transmission RPC 路径未找到。"
                } else {
                    "Transmission RPC 路径未找到。${error.failureSummary}"
                }
            }

            is BackendConnectionError.AuthFailed -> "${backendDisplayName(error.backendType)} 认证失败。"
            else -> error.message?.takeIf { it.isNotBlank() } ?: "刷新失败"
        }
    }

    private fun backendDisplayName(type: ServerBackendType): String {
        return when (type) {
            ServerBackendType.QBITTORRENT -> "qBittorrent"
            ServerBackendType.TRANSMISSION -> "Transmission"
        }
    }

    private fun hydrateDashboardCacheForCurrentScope(force: Boolean = false) {
        val scopeKey = currentDailyUploadTrackingScopeKey()
        if (!force && scopeKey == hydratedDashboardScopeKey && _uiState.value.dashboardCacheHydrated) {
            return
        }

        hydratedDashboardScopeKey = scopeKey
        dashboardCacheHydrationJob?.cancel()
        _uiState.update { current ->
            current.copy(
                dashboardCacheHydrated = false,
            )
        }

        dashboardCacheHydrationJob = viewModelScope.launch {
            val cache = connectionStore.loadDashboardCacheSnapshot(scopeKey)
            if (hydratedDashboardScopeKey != scopeKey) return@launch

            _uiState.update { current ->
                if (hydratedDashboardScopeKey != scopeKey) {
                    current
                } else if (cache == null) {
                    current.copy(
                        dashboardCacheHydrated = true,
                        hasDashboardSnapshot = false,
                    )
                } else {
                    current.copy(
                        transferInfo = cache.transferInfo,
                        torrents = cache.torrents,
                        dailyTagUploadDate = cache.dailyTagUploadDate,
                        dailyTagUploadStats = cache.dailyTagUploadStats.map { stat ->
                            DailyTagUploadStat(
                                tag = stat.tag,
                                uploadedBytes = stat.uploadedBytes,
                                torrentCount = stat.torrentCount,
                                isNoTag = stat.isNoTag,
                            )
                        },
                        dailyCountryUploadDate = cache.dailyCountryUploadDate,
                        dailyCountryUploadStats = cache.dailyCountryUploadStats,
                        dashboardCacheHydrated = true,
                        hasDashboardSnapshot = true,
                    )
                }
            }
            markInitialDashboardCacheHydrated()
        }
    }

    private fun updateSettings(update: (ConnectionSettings) -> ConnectionSettings) {
        _uiState.update { current ->
            val nextSettings = update(current.settings)
            if (nextSettings == current.settings) {
                current
            } else {
                current.copy(settings = nextSettings)
            }
        }
    }

    private fun updateAndPersistSettings(update: (ConnectionSettings) -> ConnectionSettings) {
        var changed = false
        _uiState.update { current ->
            val nextSettings = update(current.settings)
            if (nextSettings == current.settings) {
                current
            } else {
                changed = true
                current.copy(settings = nextSettings)
            }
        }
        if (!changed) return
        val settingsToPersist = _uiState.value.settings
        viewModelScope.launch {
            connectionStore.save(settingsToPersist)
        }
    }

    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                val intervalMs = resolveAutoRefreshIntervalMs(_uiState.value)
                delay(intervalMs)
                if (_uiState.value.connected) refresh()
            }
        }
    }

    private fun startHourlyBoundaryRefresh() {
        hourlyBoundaryRefreshJob?.cancel()
        hourlyBoundaryRefreshJob = viewModelScope.launch {
            while (isActive) {
                delay(millisUntilNextHourBoundary())
                if (_uiState.value.connected) {
                    refresh()
                }
            }
        }
    }

    private fun startCountryPeerTracker() {
        countryPeerTrackerJob?.cancel()
        countryPeerTrackerJob = viewModelScope.launch {
            while (isActive) {
                delay(COUNTRY_TRACKER_SAMPLE_INTERVAL_MS)
                val state = _uiState.value
                if (!state.connected) continue
                if (!state.activeCapabilities.supportsCountryDistribution) continue

                val candidateHashes = collectTrackedCountryHashes(state.torrents, refreshActivity = false)
                if (candidateHashes.isEmpty()) continue

                val countryStats = countryTrackingMutex.withLock {
                    sampleDailyCountryUploadStats(activeHashes = candidateHashes)
                }
                _uiState.update {
                    it.copy(
                        dailyCountryUploadDate = countryStats.dateLabel,
                        dailyCountryUploadStats = countryStats.countries,
                    )
                }
                saveDashboardCache()
            }
        }
    }

    private fun resolveAutoRefreshIntervalMs(state: MainUiState): Long {
        val base = state.settings.refreshSeconds.coerceIn(5, 120)
        val adaptiveSeconds = when (state.refreshScene) {
            RefreshScene.TORRENT_DETAIL -> base
            RefreshScene.SETTINGS -> (base * 2).coerceIn(10, 120)
            RefreshScene.DASHBOARD -> base
            RefreshScene.SERVER -> base
        }
        return adaptiveSeconds * 1000L
    }

    private fun millisUntilNextHourBoundary(): Long {
        val now = ZonedDateTime.now()
        val nextHour = now.plusHours(1).withMinute(0).withSecond(0).withNano(0)
        return Duration.between(now, nextHour)
            .toMillis()
            .coerceAtLeast(1_000L)
    }

    private fun resetDailyUploadTrackingState() {
        dailyUploadTrackingScopeKey = null
        dailyUploadBaselineDate = null
        dailyUploadBaselineByTorrent.clear()
        dailyUploadLastSeenByTorrent.clear()
        _uiState.update {
            it.copy(
                dailyTagUploadDate = "",
                dailyTagUploadStats = emptyList(),
            )
        }
    }

    private fun resetDailyCountryUploadTrackingState() {
        dailyCountryTrackingScopeKey = null
        dailyCountryTrackingDate = null
        dailyCountryTotalsByCode.clear()
        dailyCountryPeerSnapshots.clear()
        dailyCountryLastSeenByTorrent.clear()
        activeCountryTrackedHashes.clear()
        _uiState.update {
            it.copy(
                dailyCountryUploadDate = "",
                dailyCountryUploadStats = emptyList(),
            )
        }
    }

    private suspend fun buildDailyTagUploadStats(torrents: List<TorrentInfo>): Pair<String, List<DailyTagUploadStat>> {
        val scopeKey = currentDailyUploadTrackingScopeKey()
        ensureDailyUploadTrackingLoaded(scopeKey)
        val today = LocalDate.now()
        if (dailyUploadBaselineDate != today) {
            val carryOver = dailyUploadLastSeenByTorrent.toMap()
            dailyUploadBaselineDate = today
            dailyUploadBaselineByTorrent.clear()
            if (carryOver.isNotEmpty()) {
                dailyUploadBaselineByTorrent.putAll(carryOver)
            }
        }

        val activeKeys = torrents.map(::torrentTrackingKey).toSet()
        dailyUploadBaselineByTorrent.keys.retainAll(activeKeys)
        dailyUploadLastSeenByTorrent.keys.retainAll(activeKeys)

        val uploadByTag = mutableMapOf<String, Long>()
        val torrentCountByTag = mutableMapOf<String, Int>()

        torrents.forEach { torrent ->
            val trackingKey = torrentTrackingKey(torrent)
            val currentUploaded = torrent.uploaded.coerceAtLeast(0L)
            val baseline = dailyUploadBaselineByTorrent[trackingKey]
                ?: dailyUploadLastSeenByTorrent[trackingKey]

            if (baseline == null) {
                dailyUploadBaselineByTorrent[trackingKey] = currentUploaded
                dailyUploadLastSeenByTorrent[trackingKey] = currentUploaded
                return@forEach
            }
            if (currentUploaded < baseline) {
                dailyUploadBaselineByTorrent[trackingKey] = currentUploaded
                dailyUploadLastSeenByTorrent[trackingKey] = currentUploaded
                return@forEach
            }

            val delta = currentUploaded - baseline
            dailyUploadLastSeenByTorrent[trackingKey] = currentUploaded
            if (delta <= 0L) return@forEach

            val tags = parseTorrentTags(torrent.tags).ifEmpty { listOf(NO_TAG_KEY) }
            val baseShare = delta / tags.size
            var remainder = delta % tags.size

            for (tag in tags) {
                val share = baseShare + if (remainder > 0L) {
                    remainder -= 1L
                    1L
                } else {
                    0L
                }
                if (share <= 0L) continue
                uploadByTag[tag] = (uploadByTag[tag] ?: 0L) + share
                torrentCountByTag[tag] = (torrentCountByTag[tag] ?: 0) + 1
            }
        }

        val stats = uploadByTag.entries
            .filter { it.value > 0L }
            .sortedByDescending { it.value }
            .map { (tag, uploaded) ->
                DailyTagUploadStat(
                    tag = tag,
                    uploadedBytes = uploaded,
                    torrentCount = torrentCountByTag[tag] ?: 0,
                    isNoTag = tag == NO_TAG_KEY,
                )
            }

        connectionStore.saveDailyUploadTrackingSnapshot(
            scopeKey = scopeKey,
            snapshot = DailyUploadTrackingSnapshot(
                date = today.toString(),
                baselineByTorrent = dailyUploadBaselineByTorrent.toMap(),
                lastSeenByTorrent = dailyUploadLastSeenByTorrent.toMap(),
            ),
        )

        return today.toString() to stats
    }

    private suspend fun ensureDailyUploadTrackingLoaded(scopeKey: String) {
        if (dailyUploadTrackingScopeKey == scopeKey) return

        dailyUploadTrackingScopeKey = scopeKey
        dailyUploadBaselineDate = null
        dailyUploadBaselineByTorrent.clear()
        dailyUploadLastSeenByTorrent.clear()

        val snapshot = connectionStore.loadDailyUploadTrackingSnapshot(scopeKey) ?: return
        dailyUploadBaselineDate = runCatching {
            snapshot.date
                .takeIf { it.isNotBlank() }
                ?.let(LocalDate::parse)
        }.getOrNull()
        dailyUploadBaselineByTorrent.putAll(snapshot.baselineByTorrent)
        dailyUploadLastSeenByTorrent.putAll(snapshot.lastSeenByTorrent)
    }

    private suspend fun buildDailyCountryUploadStats(torrents: List<TorrentInfo>): DailyCountryUploadStats {
        val scopeKey = currentDailyUploadTrackingScopeKey()
        ensureDailyCountryUploadTrackingLoaded(scopeKey)
        val activeHashes = collectTrackedCountryHashes(torrents, refreshActivity = true)
        return sampleDailyCountryUploadStats(
            activeHashes = activeHashes,
        )
    }

    private suspend fun sampleDailyCountryUploadStats(
        activeHashes: List<String>,
    ): DailyCountryUploadStats {
        val scopeKey = currentDailyUploadTrackingScopeKey()
        ensureDailyCountryUploadTrackingLoaded(scopeKey)
        val today = LocalDate.now()
        if (dailyCountryTrackingDate != today) {
            dailyCountryTrackingDate = today
            dailyCountryTotalsByCode.clear()
            dailyCountryPeerSnapshots.clear()
            dailyCountryLastSeenByTorrent.clear()
            activeCountryTrackedHashes.clear()
        }

        val samples = if (activeHashes.isNotEmpty()) {
            repository.fetchCountryPeerSnapshots(activeHashes).getOrElse { emptyList() }
        } else {
            emptyList()
        }

        val currentPeerSnapshots = samples.associateBy { it.key }
        val fallbackNames = samples
            .groupBy { it.countryCode.trim().uppercase(Locale.US) }
            .mapValues { (_, snapshots) ->
                snapshots.firstNotNullOfOrNull { it.countryName.trim().takeIf { name -> name.isNotBlank() } }.orEmpty()
            }

        samples.forEach peerLoop@{ snapshot ->
            val countryCode = snapshot.countryCode.trim().uppercase(Locale.US)
            if (countryCode.isBlank()) return@peerLoop
            val previousSnapshot = dailyCountryPeerSnapshots[snapshot.key]
            val previousUploaded = previousSnapshot?.uploadedBytes?.coerceAtLeast(0L)
            val currentUploaded = snapshot.uploadedBytes.coerceAtLeast(0L)
            val delta = when {
                previousUploaded == null -> 0L
                currentUploaded < previousUploaded -> currentUploaded
                else -> currentUploaded - previousUploaded
            }
            if (delta <= 0L) return@peerLoop
            dailyCountryTotalsByCode[countryCode] = (dailyCountryTotalsByCode[countryCode] ?: 0L) + delta
        }

        dailyCountryPeerSnapshots.keys.retainAll(currentPeerSnapshots.keys)
        dailyCountryPeerSnapshots.putAll(currentPeerSnapshots)

        val confirmedCountryTotals = dailyCountryTotalsByCode
            .filterValues { it > 0L }

        connectionStore.saveDailyCountryUploadTrackingSnapshot(
            scopeKey = scopeKey,
            snapshot = DailyCountryUploadTrackingSnapshot(
                date = today.toString(),
                totalsByCountry = dailyCountryTotalsByCode.toMap(),
                peerSnapshots = dailyCountryPeerSnapshots.toMap(),
                lastSeenByTorrent = dailyCountryLastSeenByTorrent.toMap(),
            ),
        )

        return DailyCountryUploadStats(
            dateLabel = today.toString(),
            countries = confirmedCountryTotals.entries
                .sortedByDescending { it.value }
                .map { (countryCode, uploadedBytes) ->
                    CountryUploadRecord(
                        countryCode = countryCode,
                        countryName = fallbackNames[countryCode].orEmpty(),
                        uploadedBytes = uploadedBytes,
                    )
                },
        )
    }

    private fun collectTrackedCountryHashes(
        torrents: List<TorrentInfo>,
        refreshActivity: Boolean,
    ): List<String> {
        val now = System.currentTimeMillis()
        val hashesByTrackingKey = torrents.associateBy(::torrentTrackingKey)

        if (refreshActivity) {
            torrents.forEach { torrent ->
                val trackingKey = torrentTrackingKey(torrent)
                val hash = torrent.hash.trim()
                if (hash.isBlank()) return@forEach
                val currentUploaded = torrent.uploaded.coerceAtLeast(0L)
                val previousUploaded = dailyCountryLastSeenByTorrent[trackingKey]
                dailyCountryLastSeenByTorrent[trackingKey] = currentUploaded

                if (previousUploaded == null) {
                    if (torrent.uploadSpeed > 0L) {
                        activeCountryTrackedHashes[hash] = now + COUNTRY_TRACKER_ACTIVE_TTL_MS
                    }
                    return@forEach
                }

                if (currentUploaded > previousUploaded || torrent.uploadSpeed > 0L) {
                    activeCountryTrackedHashes[hash] = now + COUNTRY_TRACKER_ACTIVE_TTL_MS
                }
            }

            dailyCountryLastSeenByTorrent.keys.retainAll(hashesByTrackingKey.keys)
        }

        activeCountryTrackedHashes.entries.removeAll { (hash, expiresAt) ->
            expiresAt < now || torrents.none { it.hash.trim() == hash }
        }

        return activeCountryTrackedHashes.keys
            .filter { hash -> torrents.any { it.hash.trim() == hash } }
            .sorted()
    }

    private suspend fun ensureDailyCountryUploadTrackingLoaded(scopeKey: String) {
        if (dailyCountryTrackingScopeKey == scopeKey) return

        dailyCountryTrackingScopeKey = scopeKey
        dailyCountryTrackingDate = null
        dailyCountryTotalsByCode.clear()
        dailyCountryPeerSnapshots.clear()
        dailyCountryLastSeenByTorrent.clear()
        activeCountryTrackedHashes.clear()

        val snapshot = connectionStore.loadDailyCountryUploadTrackingSnapshot(scopeKey) ?: return
        dailyCountryTrackingDate = runCatching {
            snapshot.date
                .takeIf { it.isNotBlank() }
                ?.let(LocalDate::parse)
        }.getOrNull()
        dailyCountryTotalsByCode.putAll(snapshot.totalsByCountry)
        dailyCountryPeerSnapshots.putAll(snapshot.peerSnapshots)
        dailyCountryLastSeenByTorrent.putAll(snapshot.lastSeenByTorrent)
    }

    private fun currentDailyUploadTrackingScopeKey(): String {
        val activeProfileId = _uiState.value.activeServerProfileId.orEmpty().trim()
        if (activeProfileId.isNotBlank()) {
            return "profile:$activeProfileId"
        }

        val settings = _uiState.value.settings
        val host = settings.host.trim().lowercase()
        return if (host.isNotBlank()) {
            "server:${settings.useHttps}|$host|${settings.port}"
        } else {
            "default"
        }
    }

    private fun parseTorrentTags(rawTags: String): List<String> {
        val normalizedByKey = linkedMapOf<String, String>()
        rawTags
            .split(',', ';', '|')
            .map { it.trim() }
            .filter { it.isNotBlank() && it != "-" && !it.equals("null", ignoreCase = true) }
            .forEach { tag ->
                val key = tag.lowercase()
                if (!normalizedByKey.containsKey(key)) {
                    normalizedByKey[key] = tag
                }
            }
        return normalizedByKey.values.toList()
    }

    private fun torrentTrackingKey(torrent: TorrentInfo): String {
        return torrent.hash.ifBlank {
            "${torrent.name}|${torrent.addedOn}|${torrent.savePath}|${torrent.size}"
        }
    }

    override fun onCleared() {
        autoRefreshJob?.cancel()
        hourlyBoundaryRefreshJob?.cancel()
        countryPeerTrackerJob?.cancel()
        dashboardAggregationJob?.cancel()
        serverSchedulerJob?.cancel()
        repository.clearAllSessions()
        super.onCleared()
    }

    companion object {
        private const val NO_TAG_KEY = "__NO_TAG__"
        private const val COUNTRY_TRACKER_SAMPLE_INTERVAL_MS = 1_500L
        private const val COUNTRY_TRACKER_ACTIVE_TTL_MS = 20_000L
        private const val HOME_REALTIME_SPEED_MIN_SAMPLE_INTERVAL_MS = 1_000L
        private const val HOME_REALTIME_SPEED_MAX_POINTS = 60

        fun factory(
            connectionStore: ConnectionStore,
            repository: TorrentRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(connectionStore, repository) as T
            }
        }
    }
}




