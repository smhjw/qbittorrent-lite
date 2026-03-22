package com.hjw.qbremote.data

import com.hjw.qbremote.data.model.AddTorrentRequest
import com.hjw.qbremote.data.model.CountryPeerSnapshot
import com.hjw.qbremote.data.model.DashboardData
import com.hjw.qbremote.data.model.TorrentDetailData
import com.hjw.qbremote.data.model.TorrentTracker
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TorrentRepository {
    private data class SessionEntry(
        var settings: ConnectionSettings,
        var backend: TorrentBackend,
        var connected: Boolean = false,
    )

    private val mutex = Mutex()
    private val sessions = linkedMapOf<String, SessionEntry>()
    private var selectedProfileId: String? = null

    fun capabilitiesFor(backendType: ServerBackendType): ServerCapabilities {
        return defaultCapabilitiesFor(backendType)
    }

    fun capabilitiesFor(settings: ConnectionSettings): ServerCapabilities {
        return capabilitiesFor(settings.serverBackendType)
    }

    fun selectProfile(profileId: String?) {
        selectedProfileId = profileId?.trim()?.takeIf(String::isNotBlank)
    }

    fun activeCapabilities(): ServerCapabilities {
        val selectedId = selectedProfileId
        val selectedBackend = selectedId?.let { sessions[it]?.backend }
        return selectedBackend?.capabilities ?: defaultCapabilitiesFor(ServerBackendType.QBITTORRENT)
    }

    fun capabilitiesForProfile(profileId: String): ServerCapabilities {
        return sessions[profileId]?.backend?.capabilities
            ?: sessions[profileId]?.settings?.let(::capabilitiesFor)
            ?: defaultCapabilitiesFor(ServerBackendType.QBITTORRENT)
    }

    fun isConnected(profileId: String): Boolean {
        return sessions[profileId]?.connected == true
    }

    suspend fun connect(
        profileId: String,
        settings: ConnectionSettings,
    ): Result<Unit> {
        require(profileId.isNotBlank()) { "Profile id cannot be blank." }
        val entry = mutex.withLock {
            val existing = sessions[profileId]
            when {
                existing == null -> {
                    SessionEntry(
                        settings = settings,
                        backend = createBackend(settings.serverBackendType),
                    ).also { sessions[profileId] = it }
                }

                existing.settings.serverBackendType != settings.serverBackendType -> {
                    existing.backend.clearSession()
                    SessionEntry(
                        settings = settings,
                        backend = createBackend(settings.serverBackendType),
                    ).also { sessions[profileId] = it }
                }

                else -> {
                    existing.settings = settings
                    existing
                }
            }
        }

        return entry.backend.connect(settings)
            .onSuccess { entry.connected = true }
            .onFailure { entry.connected = false }
    }

    suspend fun connect(settings: ConnectionSettings): Result<Unit> {
        val profileId = selectedProfileId
            ?: throw IllegalStateException("No selected server profile.")
        return connect(profileId, settings)
    }

    fun clearSession(profileId: String) {
        val removed = sessions.remove(profileId)
        removed?.backend?.clearSession()
        if (selectedProfileId == profileId) {
            selectedProfileId = null
        }
    }

    fun clearSession() {
        val profileId = selectedProfileId ?: return
        clearSession(profileId)
    }

    fun clearAllSessions() {
        sessions.values.forEach { it.backend.clearSession() }
        sessions.clear()
        selectedProfileId = null
    }

    fun removeProfile(profileId: String) {
        clearSession(profileId)
    }

    suspend fun fetchDashboard(): Result<DashboardData> = requireSelectedEntry().backend.fetchDashboard()

    suspend fun fetchDashboard(profileId: String): Result<DashboardData> =
        requireEntry(profileId).backend.fetchDashboard()

    suspend fun fetchDashboardSnapshot(settings: ConnectionSettings): Result<DashboardSnapshotFetchResult> {
        return createBackend(settings.serverBackendType).fetchDashboardSnapshot(settings)
    }

    suspend fun pauseTorrent(hash: String): Result<Unit> = requireSelectedEntry().backend.pauseTorrent(hash)

    suspend fun pauseTorrent(
        profileId: String,
        hash: String,
    ): Result<Unit> = requireEntry(profileId).backend.pauseTorrent(hash)

    suspend fun resumeTorrent(hash: String): Result<Unit> = requireSelectedEntry().backend.resumeTorrent(hash)

    suspend fun resumeTorrent(
        profileId: String,
        hash: String,
    ): Result<Unit> = requireEntry(profileId).backend.resumeTorrent(hash)

    suspend fun deleteTorrent(hash: String, deleteFiles: Boolean): Result<Unit> =
        requireSelectedEntry().backend.deleteTorrent(hash, deleteFiles)

    suspend fun deleteTorrent(
        profileId: String,
        hash: String,
        deleteFiles: Boolean,
    ): Result<Unit> = requireEntry(profileId).backend.deleteTorrent(hash, deleteFiles)

    suspend fun reannounceTorrent(hash: String): Result<Unit> =
        requireSelectedEntry().backend.reannounceTorrent(hash)

    suspend fun reannounceTorrent(
        profileId: String,
        hash: String,
    ): Result<Unit> = requireEntry(profileId).backend.reannounceTorrent(hash)

    suspend fun recheckTorrent(hash: String): Result<Unit> =
        requireSelectedEntry().backend.recheckTorrent(hash)

    suspend fun recheckTorrent(
        profileId: String,
        hash: String,
    ): Result<Unit> = requireEntry(profileId).backend.recheckTorrent(hash)

    suspend fun fetchServerVersion(): Result<String> = requireSelectedEntry().backend.fetchServerVersion()

    suspend fun fetchServerVersion(profileId: String): Result<String> =
        requireEntry(profileId).backend.fetchServerVersion()

    suspend fun fetchTorrentDetail(hash: String): Result<TorrentDetailData> =
        requireSelectedEntry().backend.fetchTorrentDetail(hash)

    suspend fun fetchTorrentDetail(
        profileId: String,
        hash: String,
    ): Result<TorrentDetailData> = requireEntry(profileId).backend.fetchTorrentDetail(hash)

    suspend fun fetchTorrentTrackers(hash: String): Result<List<TorrentTracker>> =
        requireSelectedEntry().backend.fetchTorrentTrackers(hash)

    suspend fun fetchTorrentTrackers(
        profileId: String,
        hash: String,
    ): Result<List<TorrentTracker>> = requireEntry(profileId).backend.fetchTorrentTrackers(hash)

    suspend fun addTracker(hash: String, trackerUrl: String): Result<Unit> =
        requireSelectedEntry().backend.addTracker(hash, trackerUrl)

    suspend fun addTracker(
        profileId: String,
        hash: String,
        trackerUrl: String,
    ): Result<Unit> = requireEntry(profileId).backend.addTracker(hash, trackerUrl)

    suspend fun editTracker(
        hash: String,
        tracker: TorrentTracker,
        newUrl: String,
    ): Result<Unit> = requireSelectedEntry().backend.editTracker(hash, tracker, newUrl)

    suspend fun editTracker(
        profileId: String,
        hash: String,
        tracker: TorrentTracker,
        newUrl: String,
    ): Result<Unit> = requireEntry(profileId).backend.editTracker(hash, tracker, newUrl)

    suspend fun removeTracker(hash: String, tracker: TorrentTracker): Result<Unit> =
        requireSelectedEntry().backend.removeTracker(hash, tracker)

    suspend fun removeTracker(
        profileId: String,
        hash: String,
        tracker: TorrentTracker,
    ): Result<Unit> = requireEntry(profileId).backend.removeTracker(hash, tracker)

    suspend fun exportTorrentFile(hash: String): Result<ByteArray> =
        requireSelectedEntry().backend.exportTorrentFile(hash)

    suspend fun exportTorrentFile(
        profileId: String,
        hash: String,
    ): Result<ByteArray> = requireEntry(profileId).backend.exportTorrentFile(hash)

    suspend fun fetchCategoryOptions(): Result<List<String>> =
        requireSelectedEntry().backend.fetchCategoryOptions()

    suspend fun fetchTagOptions(): Result<List<String>> =
        requireSelectedEntry().backend.fetchTagOptions()

    suspend fun fetchCategoryOptions(profileId: String): Result<List<String>> =
        requireEntry(profileId).backend.fetchCategoryOptions()

    suspend fun fetchTagOptions(profileId: String): Result<List<String>> =
        requireEntry(profileId).backend.fetchTagOptions()

    suspend fun fetchCountryPeerSnapshots(hashes: List<String>): Result<List<CountryPeerSnapshot>> =
        requireSelectedEntry().backend.fetchCountryPeerSnapshots(hashes)

    suspend fun fetchCountryPeerSnapshots(
        profileId: String,
        hashes: List<String>,
    ): Result<List<CountryPeerSnapshot>> {
        return requireEntry(profileId).backend.fetchCountryPeerSnapshots(hashes)
    }

    suspend fun fetchCountryPeerSnapshots(
        settings: ConnectionSettings,
        hashes: List<String>,
    ): Result<List<CountryPeerSnapshot>> {
        if (!capabilitiesFor(settings).supportsCountryDistribution) {
            return Result.success(emptyList())
        }
        return when (settings.serverBackendType) {
            ServerBackendType.QBITTORRENT -> runCatching {
                val temp = QbRepository()
                temp.connect(settings).getOrThrow()
                temp.fetchCountryPeerSnapshots(hashes).getOrThrow()
            }

            ServerBackendType.TRANSMISSION -> Result.success(emptyList())
        }
    }

    suspend fun renameTorrent(hash: String, name: String): Result<Unit> =
        requireSelectedEntry().backend.renameTorrent(hash, name)

    suspend fun renameTorrent(
        profileId: String,
        hash: String,
        name: String,
    ): Result<Unit> = requireEntry(profileId).backend.renameTorrent(hash, name)

    suspend fun setTorrentLocation(hash: String, location: String): Result<Unit> =
        requireSelectedEntry().backend.setTorrentLocation(hash, location)

    suspend fun setTorrentLocation(
        profileId: String,
        hash: String,
        location: String,
    ): Result<Unit> = requireEntry(profileId).backend.setTorrentLocation(hash, location)

    suspend fun setTorrentCategory(hash: String, category: String): Result<Unit> =
        requireSelectedEntry().backend.setTorrentCategory(hash, category)

    suspend fun setTorrentCategory(
        profileId: String,
        hash: String,
        category: String,
    ): Result<Unit> = requireEntry(profileId).backend.setTorrentCategory(hash, category)

    suspend fun setTorrentTags(hash: String, oldTags: String, newTags: String): Result<Unit> =
        requireSelectedEntry().backend.setTorrentTags(hash, oldTags, newTags)

    suspend fun setTorrentTags(
        profileId: String,
        hash: String,
        oldTags: String,
        newTags: String,
    ): Result<Unit> = requireEntry(profileId).backend.setTorrentTags(hash, oldTags, newTags)

    suspend fun setTorrentSpeedLimit(
        hash: String,
        downloadLimitBytes: Long,
        uploadLimitBytes: Long,
    ): Result<Unit> = requireSelectedEntry().backend.setTorrentSpeedLimit(
        hash = hash,
        downloadLimitBytes = downloadLimitBytes,
        uploadLimitBytes = uploadLimitBytes,
    )

    suspend fun setTorrentSpeedLimit(
        profileId: String,
        hash: String,
        downloadLimitBytes: Long,
        uploadLimitBytes: Long,
    ): Result<Unit> = requireEntry(profileId).backend.setTorrentSpeedLimit(
        hash = hash,
        downloadLimitBytes = downloadLimitBytes,
        uploadLimitBytes = uploadLimitBytes,
    )

    suspend fun setTorrentShareRatio(hash: String, ratioLimit: Double): Result<Unit> =
        requireSelectedEntry().backend.setTorrentShareRatio(hash, ratioLimit)

    suspend fun setTorrentShareRatio(
        profileId: String,
        hash: String,
        ratioLimit: Double,
    ): Result<Unit> = requireEntry(profileId).backend.setTorrentShareRatio(hash, ratioLimit)

    suspend fun addTorrent(request: AddTorrentRequest): Result<Unit> =
        requireSelectedEntry().backend.addTorrent(request)

    suspend fun addTorrent(
        profileId: String,
        request: AddTorrentRequest,
    ): Result<Unit> = requireEntry(profileId).backend.addTorrent(request)

    private fun requireSelectedEntry(): SessionEntry {
        val profileId = selectedProfileId
            ?: throw IllegalStateException("No selected server profile.")
        return requireEntry(profileId)
    }

    private fun requireEntry(profileId: String): SessionEntry {
        return sessions[profileId]
            ?: throw IllegalStateException("Server session is not connected.")
    }

    private fun createBackend(type: ServerBackendType): TorrentBackend {
        return when (type) {
            ServerBackendType.QBITTORRENT -> QbRepository()
            ServerBackendType.TRANSMISSION -> TransmissionBackend()
        }
    }
}
