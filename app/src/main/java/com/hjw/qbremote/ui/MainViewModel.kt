package com.hjw.qbremote.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hjw.qbremote.data.ConnectionSettings
import com.hjw.qbremote.data.ConnectionStore
import com.hjw.qbremote.data.QbRepository
import com.hjw.qbremote.data.model.TorrentInfo
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

enum class TorrentFilter(val label: String) {
    ALL("All"),
    DOWNLOADING("Downloading"),
    SEEDING("Seeding"),
    PAUSED("Paused"),
    COMPLETED("Completed"),
    ERROR("Error");

    fun matches(torrent: TorrentInfo): Boolean {
        val state = torrent.state.lowercase()
        return when (this) {
            ALL -> true
            DOWNLOADING -> state in setOf("downloading", "forceddl", "stalleddl", "metadl")
            SEEDING -> state in setOf("uploading", "forcedup", "stalledup")
            PAUSED -> state in setOf("pauseddl", "pausedup")
            COMPLETED -> torrent.progress >= 1f && state !in setOf("error", "missingfiles")
            ERROR -> state in setOf("error", "missingfiles")
        }
    }
}

data class MainUiState(
    val settings: ConnectionSettings = ConnectionSettings(),
    val isConnecting: Boolean = false,
    val isRefreshing: Boolean = false,
    val connected: Boolean = false,
    val transferInfo: TransferInfo = TransferInfo(),
    val torrents: List<TorrentInfo> = emptyList(),
    val selectedFilter: TorrentFilter = TorrentFilter.ALL,
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

    init {
        viewModelScope.launch {
            connectionStore.migrateLegacyPasswordIfNeeded()
            connectionStore.settingsFlow.collect { settings ->
                _uiState.update { current -> current.copy(settings = settings) }
            }
        }
    }

    fun updateHost(value: String) = updateSettings { it.copy(host = value) }
    fun updatePort(value: String) = updateSettings { it.copy(port = value.toIntOrNull() ?: 0) }
    fun updateUseHttps(value: Boolean) = updateSettings { it.copy(useHttps = value) }
    fun updateUsername(value: String) = updateSettings { it.copy(username = value) }
    fun updatePassword(value: String) = updateSettings { it.copy(password = value) }
    fun updateRefreshSeconds(value: String) {
        val sec = value.toIntOrNull()?.coerceIn(2, 60) ?: 3
        updateSettings { it.copy(refreshSeconds = sec) }
    }

    fun setFilter(filter: TorrentFilter) {
        _uiState.update { it.copy(selectedFilter = filter) }
    }

    fun connect() {
        if (_uiState.value.isConnecting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true, errorMessage = null) }
            val settings = _uiState.value.settings
            connectionStore.save(settings)

            repository.connect(settings)
                .onSuccess {
                    _uiState.update { it.copy(isConnecting = false, connected = true) }
                    refresh()
                    startAutoRefresh()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isConnecting = false,
                            connected = false,
                            errorMessage = error.message ?: "Connection failed."
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

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
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

    private fun updateSettings(update: (ConnectionSettings) -> ConnectionSettings) {
        _uiState.update { current -> current.copy(settings = update(current.settings)) }
    }

    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                delay((_uiState.value.settings.refreshSeconds.coerceIn(2, 60) * 1000L))
                if (_uiState.value.connected) refresh()
            }
        }
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
