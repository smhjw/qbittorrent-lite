package com.hjw.qbremote.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hjw.qbremote.data.AppLanguage
import com.hjw.qbremote.data.AppTheme
import com.hjw.qbremote.data.ChartSortMode
import com.hjw.qbremote.data.ConnectionSettings
import com.hjw.qbremote.data.ConnectionStore
import com.hjw.qbremote.data.QbRepository
import com.hjw.qbremote.data.ServerProfile
import com.hjw.qbremote.data.model.AddTorrentFile
import com.hjw.qbremote.data.model.AddTorrentRequest
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

enum class RefreshScene {
    DASHBOARD,
    TORRENT_DETAIL,
    SETTINGS,
}

data class MainUiState(
    val settings: ConnectionSettings = ConnectionSettings(),
    val isConnecting: Boolean = false,
    val isRefreshing: Boolean = false,
    val connected: Boolean = false,
    val serverVersion: String = "-",
    val transferInfo: TransferInfo = TransferInfo(),
    val torrents: List<TorrentInfo> = emptyList(),
    val detailHash: String = "",
    val detailLoading: Boolean = false,
    val detailProperties: TorrentProperties? = null,
    val detailFiles: List<TorrentFileInfo> = emptyList(),
    val detailTrackers: List<TorrentTracker> = emptyList(),
    val serverProfiles: List<ServerProfile> = emptyList(),
    val activeServerProfileId: String? = null,
    val categoryOptions: List<String> = emptyList(),
    val tagOptions: List<String> = emptyList(),
    val refreshScene: RefreshScene = RefreshScene.DASHBOARD,
    val pendingHashes: Set<String> = emptySet(),
    val errorMessage: String? = null,
)

class MainViewModel(
    private val connectionStore: ConnectionStore,
    private val repository: QbRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var autoRefreshJob: Job? = null
    private var autoConnectAttempted = false

    init {
        viewModelScope.launch {
            connectionStore.migrateLegacyPasswordIfNeeded()
            connectionStore.settingsFlow.collect { settings ->
                _uiState.update { current -> current.copy(settings = settings) }
                autoConnectIfNeeded(settings)
            }
        }
        viewModelScope.launch {
            connectionStore.serverProfilesFlow.collect { profiles ->
                _uiState.update { current -> current.copy(serverProfiles = profiles) }
            }
        }
        viewModelScope.launch {
            connectionStore.activeServerProfileIdFlow.collect { profileId ->
                _uiState.update { current -> current.copy(activeServerProfileId = profileId) }
            }
        }
    }

    fun updateHost(value: String) = updateSettings { it.copy(host = value) }
    fun updatePort(value: String) = updateSettings { it.copy(port = value.toIntOrNull() ?: 0) }
    fun updateUseHttps(value: Boolean) = updateSettings { it.copy(useHttps = value) }
    fun updateUsername(value: String) = updateSettings { it.copy(username = value) }
    fun updatePassword(value: String) = updateSettings { it.copy(password = value) }
    fun updateRefreshSeconds(value: String) {
        val sec = value.toIntOrNull()?.coerceIn(10, 120) ?: 10
        updateSettings { it.copy(refreshSeconds = sec) }
    }

    fun updateShowSpeedTotals(value: Boolean) = updateAndPersistSettings {
        it.copy(showSpeedTotals = value)
    }

    fun updateAppLanguage(value: AppLanguage) = updateAndPersistSettings {
        it.copy(appLanguage = value)
    }

    fun updateAppTheme(value: AppTheme) = updateAndPersistSettings {
        it.copy(appTheme = value)
    }

    fun updateEnableServerGrouping(value: Boolean) = updateAndPersistSettings {
        it.copy(enableServerGrouping = value)
    }

    fun updateShowChartPanel(value: Boolean) = updateAndPersistSettings {
        it.copy(showChartPanel = value)
    }

    fun updateChartShowSiteName(value: Boolean) = updateAndPersistSettings {
        it.copy(chartShowSiteName = value)
    }

    fun updateChartSortMode(value: ChartSortMode) = updateAndPersistSettings {
        it.copy(chartSortMode = value)
    }

    fun updateDeleteFilesDefault(value: Boolean) = updateAndPersistSettings {
        it.copy(deleteFilesDefault = value)
    }

    fun updateDeleteFilesWhenNoSeeders(value: Boolean) = updateAndPersistSettings {
        it.copy(deleteFilesWhenNoSeeders = value)
    }

    fun updateRefreshScene(scene: RefreshScene) {
        _uiState.update { current ->
            if (current.refreshScene == scene) current else current.copy(refreshScene = scene)
        }
    }

    fun addServerProfile(
        name: String,
        host: String,
        portText: String,
        useHttps: Boolean,
        username: String,
        password: String,
        refreshSecondsText: String,
        connectNow: Boolean,
    ) {
        viewModelScope.launch {
            runCatching {
                val normalizedHost = host.trim()
                require(normalizedHost.isNotBlank()) { "服务器地址不能为空。" }

                val normalizedUsername = username.trim()
                require(normalizedUsername.isNotBlank()) { "用户名不能为空。" }

                val normalizedPort = portText.trim().toIntOrNull()?.coerceIn(1, 65535)
                    ?: throw IllegalArgumentException("端口格式不正确。")
                val normalizedRefreshSeconds = refreshSecondsText.trim().toIntOrNull()?.coerceIn(10, 120)
                    ?: throw IllegalArgumentException("刷新间隔应为 10-120 秒。")

                val profile = ServerProfile(
                    id = UUID.randomUUID().toString(),
                    name = name.trim().ifBlank { normalizedHost },
                    host = normalizedHost,
                    port = normalizedPort,
                    useHttps = useHttps,
                    username = normalizedUsername,
                    refreshSeconds = normalizedRefreshSeconds,
                )
                connectionStore.addOrUpdateServerProfile(profile, password)

                if (connectNow) {
                    val updatedSettings = profile.toConnectionSettings(
                        password = password,
                        template = _uiState.value.settings,
                    )
                    connectionStore.setActiveServerProfile(profile.id)
                    connectionStore.save(updatedSettings)
                    _uiState.update { it.copy(settings = updatedSettings) }
                    connectInternal(persistSettings = false, showErrorOnFailure = true)
                }
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "添加服务器失败。") }
            }
        }
    }

    fun connectServerProfile(profileId: String) {
        viewModelScope.launch {
            runCatching {
                val profile = connectionStore.getServerProfile(profileId)
                    ?: throw IllegalArgumentException("未找到服务器配置。")
                val password = connectionStore.getPasswordForProfile(profile.id)
                val updatedSettings = profile.toConnectionSettings(
                    password = password,
                    template = _uiState.value.settings,
                )
                connectionStore.setActiveServerProfile(profile.id)
                connectionStore.save(updatedSettings)
                _uiState.update { it.copy(settings = updatedSettings) }
                connectInternal(persistSettings = false, showErrorOnFailure = true)
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "切换服务器失败。") }
            }
        }
    }

    fun deleteServerProfile(profileId: String) {
        viewModelScope.launch {
            runCatching {
                connectionStore.removeServerProfile(profileId)
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "删除服务器失败。") }
            }
        }
    }

    fun connect() {
        connectInternal(persistSettings = true, showErrorOnFailure = true)
    }

    private fun autoConnectIfNeeded(settings: ConnectionSettings) {
        if (autoConnectAttempted) return
        if (settings.host.isBlank() || settings.username.isBlank()) return
        autoConnectAttempted = true
        connectInternal(persistSettings = false, showErrorOnFailure = false)
    }

    private fun connectInternal(
        persistSettings: Boolean,
        showErrorOnFailure: Boolean,
    ) {
        if (_uiState.value.isConnecting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true, errorMessage = null) }
            val settings = _uiState.value.settings
            if (persistSettings) {
                connectionStore.save(settings)
            }

            repository.connect(settings)
                .onSuccess {
                    _uiState.update { it.copy(isConnecting = false, connected = true) }
                    refreshServerVersion()
                    refresh()
                    loadGlobalSelectionOptions()
                    startAutoRefresh()
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
                }
        }
    }

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            repository.fetchDashboard()
                .onSuccess { data ->
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            transferInfo = data.transferInfo,
                            torrents = data.torrents,
                        )
                    }
                    refreshServerVersion()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            errorMessage = error.message ?: "Refresh failed."
                        )
                    }
                }
        }
    }

    fun pauseTorrent(hash: String) = runTorrentAction(hash) {
        repository.pauseTorrent(hash).getOrThrow()
    }

    fun resumeTorrent(hash: String) = runTorrentAction(hash) {
        repository.resumeTorrent(hash).getOrThrow()
    }

    fun deleteTorrent(hash: String, deleteFiles: Boolean) = runTorrentAction(hash) {
        repository.deleteTorrent(hash, deleteFiles).getOrThrow()
    }

    fun loadTorrentDetail(hash: String) {
        if (hash.isBlank()) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    detailHash = hash,
                    detailLoading = true,
                    errorMessage = null,
                )
            }
            repository.fetchTorrentDetail(hash)
                .onSuccess { detail ->
                    val trackers = repository.fetchTorrentTrackers(hash).getOrElse { emptyList() }
                    val categoryOptions = repository.fetchCategoryOptions().getOrElse { emptyList() }
                    val tagOptions = repository.fetchTagOptions().getOrElse { emptyList() }
                    _uiState.update {
                        it.copy(
                            detailLoading = false,
                            detailProperties = detail.properties,
                            detailFiles = detail.files,
                            detailTrackers = trackers,
                            categoryOptions = categoryOptions,
                            tagOptions = tagOptions,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
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
    fun renameTorrent(hash: String, newName: String) = runDetailAction(hash) {
        repository.renameTorrent(hash, newName).getOrThrow()
    }

    fun setTorrentLocation(hash: String, location: String) = runDetailAction(hash) {
        repository.setTorrentLocation(hash, location).getOrThrow()
    }

    fun setTorrentCategory(hash: String, category: String) = runDetailAction(hash) {
        repository.setTorrentCategory(hash, category).getOrThrow()
    }

    fun setTorrentTags(hash: String, oldTags: String, newTags: String) = runDetailAction(hash) {
        repository.setTorrentTags(hash, oldTags, newTags).getOrThrow()
    }

    fun setTorrentSpeedLimit(hash: String, downloadLimitKb: String, uploadLimitKb: String) = runDetailAction(hash) {
        val dl = parseLimitKbToBytes(downloadLimitKb)
        val up = parseLimitKbToBytes(uploadLimitKb)
        repository.setTorrentSpeedLimit(hash, dl, up).getOrThrow()
    }

    fun setTorrentShareRatio(hash: String, ratio: String) = runDetailAction(hash) {
        val value = ratio.trim().toDoubleOrNull() ?: throw IllegalArgumentException("分享比率格式无效")
        repository.setTorrentShareRatio(hash, value).getOrThrow()
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun loadGlobalSelectionOptions() {
        if (!_uiState.value.connected) return
        viewModelScope.launch {
            val categoryOptions = repository.fetchCategoryOptions().getOrElse { emptyList() }
            val tagOptions = repository.fetchTagOptions().getOrElse { emptyList() }
            _uiState.update {
                it.copy(
                    categoryOptions = categoryOptions,
                    tagOptions = tagOptions,
                )
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
            _uiState.update { it.copy(errorMessage = "请先连接 qBittorrent 服务器。") }
            return
        }
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
                repository.addTorrent(request).getOrThrow()
            }.onSuccess {
                loadGlobalSelectionOptions()
                refresh()
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "添加种子失败。") }
            }
        }
    }

    private fun runTorrentAction(hash: String, action: suspend () -> Unit) {
        if (hash.isBlank()) return
        if (_uiState.value.pendingHashes.contains(hash)) return

        viewModelScope.launch {
            _uiState.update { it.copy(pendingHashes = it.pendingHashes + hash, errorMessage = null) }
            runCatching { action() }
                .onSuccess { refresh() }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.message ?: "Action failed.")
                    }
                }
            _uiState.update { it.copy(pendingHashes = it.pendingHashes - hash) }
        }
    }

    private fun runDetailAction(hash: String, action: suspend () -> Unit) {
        runTorrentAction(hash) {
            action()
            val detail = repository.fetchTorrentDetail(hash).getOrThrow()
            val trackers = repository.fetchTorrentTrackers(hash).getOrElse { emptyList() }
            val categoryOptions = repository.fetchCategoryOptions().getOrElse { emptyList() }
            val tagOptions = repository.fetchTagOptions().getOrElse { emptyList() }
            _uiState.update {
                it.copy(
                    detailHash = hash,
                    detailProperties = detail.properties,
                    detailFiles = detail.files,
                    detailTrackers = trackers,
                    categoryOptions = categoryOptions,
                    tagOptions = tagOptions,
                )
            }
        }
    }
    private fun refreshServerVersion() {
        viewModelScope.launch {
            repository.fetchServerVersion()
                .onSuccess { version ->
                    _uiState.update { it.copy(serverVersion = version.ifBlank { "-" }) }
                }
        }
    }

    private fun parseLimitKbToBytes(value: String): Long {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return -1L
        val kb = trimmed.toLongOrNull() ?: throw IllegalArgumentException("限速值必须是数字")
        if (kb < 0L) return -1L
        return kb * 1024L
    }

    private fun updateSettings(update: (ConnectionSettings) -> ConnectionSettings) {
        _uiState.update { current -> current.copy(settings = update(current.settings)) }
    }

    private fun updateAndPersistSettings(update: (ConnectionSettings) -> ConnectionSettings) {
        updateSettings(update)
        viewModelScope.launch {
            connectionStore.save(_uiState.value.settings)
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

    private fun resolveAutoRefreshIntervalMs(state: MainUiState): Long {
        val base = state.settings.refreshSeconds.coerceIn(10, 120)
        val adaptiveSeconds = when (state.refreshScene) {
            RefreshScene.TORRENT_DETAIL -> (base - 2).coerceIn(8, 20)
            RefreshScene.SETTINGS -> (base * 2).coerceIn(15, 120)
            RefreshScene.DASHBOARD -> base
        }
        return adaptiveSeconds * 1000L
    }

    companion object {
        fun factory(
            connectionStore: ConnectionStore,
            repository: QbRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(connectionStore, repository) as T
            }
        }
    }
}




