package com.hjw.qbremote.data

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.hjw.qbremote.data.model.AddTorrentRequest
import com.hjw.qbremote.data.model.CountryPeerSnapshot
import com.hjw.qbremote.data.model.DashboardData
import com.hjw.qbremote.data.model.TorrentDetailData
import com.hjw.qbremote.data.model.TorrentFileInfo
import com.hjw.qbremote.data.model.TorrentInfo
import com.hjw.qbremote.data.model.TorrentProperties
import com.hjw.qbremote.data.model.TorrentTracker
import com.hjw.qbremote.data.model.TransferInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URI
import java.util.Base64
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class TransmissionBackend : TorrentBackend {
    override val backendType: ServerBackendType = ServerBackendType.TRANSMISSION
    override val capabilities: ServerCapabilities = defaultCapabilitiesFor(backendType)

    private val gson = Gson()
    private var activeClient: TransmissionRpcClient? = null

    override suspend fun connect(settings: ConnectionSettings): Result<Unit> = runCatching {
        val client = TransmissionRpcClient(settings)
        client.sessionGet()
        activeClient = client
    }

    override fun clearSession() {
        activeClient = null
    }

    override suspend fun fetchDashboard(): Result<DashboardData> = runCatching {
        val client = requireClient()
        fetchDashboardData(client)
    }

    override suspend fun fetchDashboardSnapshot(settings: ConnectionSettings): Result<DashboardSnapshotFetchResult> =
        runCatching {
            val client = TransmissionRpcClient(settings)
            val session = client.sessionGet()
            DashboardSnapshotFetchResult(
                serverVersion = sanitizeTransmissionVersion(session.version),
                dashboardData = fetchDashboardData(client),
            )
        }

    override suspend fun fetchServerVersion(): Result<String> = runCatching {
        sanitizeTransmissionVersion(requireClient().sessionGet().version)
    }

    override suspend fun fetchTorrentDetail(hash: String): Result<TorrentDetailData> = runCatching {
        require(hash.isNotBlank()) { "Invalid torrent hash." }
        val client = requireClient()
        val torrents = client.torrentGet(
            ids = listOf(hash),
            fields = DETAIL_FIELDS,
        )
        val torrent = torrents.firstOrNull() ?: throw IllegalStateException("Torrent not found.")
        TorrentDetailData(
            properties = torrent.toTorrentProperties(),
            files = torrent.toTorrentFiles(),
        )
    }

    override suspend fun fetchTorrentTrackers(hash: String): Result<List<TorrentTracker>> = runCatching {
        require(hash.isNotBlank()) { "Invalid torrent hash." }
        val client = requireClient()
        val torrents = client.torrentGet(
            ids = listOf(hash),
            fields = listOf("trackers", "trackerStats"),
        )
        torrents.firstOrNull()?.toTorrentTrackers().orEmpty()
    }

    override suspend fun exportTorrentFile(hash: String): Result<ByteArray> = Result.failure(
        UnsupportedOperationException("Transmission does not support exporting torrent files via RPC.")
    )

    override suspend fun fetchCategoryOptions(): Result<List<String>> = Result.success(emptyList())

    override suspend fun fetchTagOptions(): Result<List<String>> = runCatching {
        val torrents = requireClient().torrentGet(
            ids = emptyList(),
            fields = listOf("labels"),
        )
        torrents
            .flatMap { it.labels }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.US) }
            .sorted()
    }

    override suspend fun fetchCountryPeerSnapshots(hashes: List<String>): Result<List<CountryPeerSnapshot>> =
        Result.success(emptyList())

    override suspend fun renameTorrent(hash: String, name: String): Result<Unit> = Result.failure(
        UnsupportedOperationException("Transmission does not support renaming torrents via RPC.")
    )

    override suspend fun reannounceTorrent(hash: String): Result<Unit> = runCatching {
        require(hash.isNotBlank()) { "Invalid torrent hash." }
        requireClient().rpc(
            method = "torrent-reannounce",
            arguments = buildJsonObject("ids" to listOf(hash)),
        )
    }

    override suspend fun recheckTorrent(hash: String): Result<Unit> = runCatching {
        require(hash.isNotBlank()) { "Invalid torrent hash." }
        requireClient().rpc(
            method = "torrent-verify",
            arguments = buildJsonObject("ids" to listOf(hash)),
        )
    }

    override suspend fun setTorrentLocation(hash: String, location: String): Result<Unit> = runCatching {
        require(hash.isNotBlank()) { "Invalid torrent hash." }
        require(location.isNotBlank()) { "Location cannot be empty." }
        requireClient().rpc(
            method = "torrent-set-location",
            arguments = buildJsonObject(
                "ids" to listOf(hash),
                "location" to location.trim(),
                "move" to true,
            ),
        )
    }

    override suspend fun setTorrentCategory(hash: String, category: String): Result<Unit> = Result.failure(
        UnsupportedOperationException("Transmission does not support categories.")
    )

    override suspend fun setTorrentTags(hash: String, oldTags: String, newTags: String): Result<Unit> = runCatching {
        require(hash.isNotBlank()) { "Invalid torrent hash." }
        val labels = newTags
            .split(',', ';', '|')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        requireClient().rpc(
            method = "torrent-set",
            arguments = buildJsonObject(
                "ids" to listOf(hash),
                "labels" to labels,
            ),
        )
    }

    override suspend fun addTracker(hash: String, trackerUrl: String): Result<Unit> = runCatching {
        require(hash.isNotBlank()) { "Invalid torrent hash." }
        require(trackerUrl.isNotBlank()) { "Tracker URL cannot be empty." }
        requireClient().rpc(
            method = "torrent-set",
            arguments = buildJsonObject(
                "ids" to listOf(hash),
                "trackerAdd" to listOf(trackerUrl.trim()),
            ),
        )
    }

    override suspend fun editTracker(
        hash: String,
        tracker: TorrentTracker,
        newUrl: String,
    ): Result<Unit> = runCatching {
        require(hash.isNotBlank()) { "Invalid torrent hash." }
        val trackerId = tracker.backendTrackerId
        require(trackerId >= 0) { "Tracker id is missing." }
        require(newUrl.isNotBlank()) { "New tracker URL cannot be empty." }
        requireClient().rpc(
            method = "torrent-set",
            arguments = buildJsonObject(
                "ids" to listOf(hash),
                "trackerReplace" to listOf(trackerId, newUrl.trim()),
            ),
        )
    }

    override suspend fun removeTracker(hash: String, tracker: TorrentTracker): Result<Unit> = runCatching {
        require(hash.isNotBlank()) { "Invalid torrent hash." }
        val trackerId = tracker.backendTrackerId
        require(trackerId >= 0) { "Tracker id is missing." }
        requireClient().rpc(
            method = "torrent-set",
            arguments = buildJsonObject(
                "ids" to listOf(hash),
                "trackerRemove" to listOf(trackerId),
            ),
        )
    }

    override suspend fun setTorrentSpeedLimit(
        hash: String,
        downloadLimitBytes: Long,
        uploadLimitBytes: Long,
    ): Result<Unit> = Result.failure(
        UnsupportedOperationException("Transmission does not support per-torrent speed limits in this app.")
    )

    override suspend fun setTorrentShareRatio(hash: String, ratioLimit: Double): Result<Unit> = Result.failure(
        UnsupportedOperationException("Transmission share ratio editing is not supported in this app.")
    )

    override suspend fun addTorrent(request: AddTorrentRequest): Result<Unit> = runCatching {
        require(request.urls.isNotBlank() || request.files.isNotEmpty()) {
            "Please provide a torrent URL or file."
        }
        val labels = request.tags
            .split(',', ';', '|')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        fun buildArgs(common: MutableMap<String, Any?>): JsonObject {
            if (request.savePath.isNotBlank()) {
                common["download-dir"] = request.savePath.trim()
            }
            if (request.paused) {
                common["paused"] = true
            }
            if (labels.isNotEmpty()) {
                common["labels"] = labels
            }
            return gson.toJsonTree(common).asJsonObject
        }

        val client = requireClient()
        request.urls
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { url ->
                client.rpc(
                    method = "torrent-add",
                    arguments = buildArgs(
                        mutableMapOf<String, Any?>(
                            "filename" to url,
                        ),
                    ),
                )
            }

        request.files.forEach { file ->
            client.rpc(
                method = "torrent-add",
                arguments = buildArgs(
                    mutableMapOf<String, Any?>(
                        "metainfo" to Base64.getEncoder().encodeToString(file.bytes),
                    ),
                ),
            )
        }
    }

    override suspend fun pauseTorrent(hash: String): Result<Unit> = runCatching {
        require(hash.isNotBlank()) { "Invalid torrent hash." }
        requireClient().rpc(
            method = "torrent-stop",
            arguments = buildJsonObject("ids" to listOf(hash)),
        )
    }

    override suspend fun resumeTorrent(hash: String): Result<Unit> = runCatching {
        require(hash.isNotBlank()) { "Invalid torrent hash." }
        requireClient().rpc(
            method = "torrent-start",
            arguments = buildJsonObject("ids" to listOf(hash)),
        )
    }

    override suspend fun deleteTorrent(hash: String, deleteFiles: Boolean): Result<Unit> = runCatching {
        require(hash.isNotBlank()) { "Invalid torrent hash." }
        requireClient().rpc(
            method = "torrent-remove",
            arguments = buildJsonObject(
                "ids" to listOf(hash),
                "delete-local-data" to deleteFiles,
            ),
        )
    }

    private suspend fun fetchDashboardData(client: TransmissionRpcClient): DashboardData {
        val session = client.sessionGet()
        val sessionStats = client.sessionStats()
        val torrents = client.torrentGet(
            ids = emptyList(),
            fields = DASHBOARD_FIELDS,
        ).map { torrent ->
            torrent.toTorrentInfo()
        }
        val downloadLimit = if (session.speedLimitDownEnabled) {
            session.speedLimitDown.toLong().coerceAtLeast(0L) * 1024L
        } else {
            0L
        }
        val uploadLimit = if (session.speedLimitUpEnabled) {
            session.speedLimitUp.toLong().coerceAtLeast(0L) * 1024L
        } else {
            0L
        }
        val freeSpace = session.downloadDir.takeIf { it.isNotBlank() }?.let { path ->
            client.freeSpace(path).getOrElse { 0L }
        } ?: 0L

        return DashboardData(
            transferInfo = TransferInfo(
                downloadSpeed = sessionStats.downloadSpeed.coerceAtLeast(0L),
                uploadSpeed = sessionStats.uploadSpeed.coerceAtLeast(0L),
                downloadedTotal = sessionStats.cumulativeDownloadedBytes.coerceAtLeast(0L),
                uploadedTotal = sessionStats.cumulativeUploadedBytes.coerceAtLeast(0L),
                downloadRateLimit = downloadLimit,
                uploadRateLimit = uploadLimit,
                freeSpaceOnDisk = freeSpace.coerceAtLeast(0L),
                dhtNodes = 0,
            ),
            torrents = torrents,
        )
    }

    private fun requireClient(): TransmissionRpcClient {
        return activeClient ?: throw IllegalStateException("Not connected to Transmission yet.")
    }

    private inner class TransmissionRpcClient(settings: ConnectionSettings) {
        private val gson = Gson()
        private val rpcUrls = settings.transmissionRpcUrlCandidates()
        private val authHeader = if (settings.username.isNotBlank()) {
            Credentials.basic(settings.username, settings.password)
        } else {
            null
        }
        private val client = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(18, TimeUnit.SECONDS)
            .writeTimeout(18, TimeUnit.SECONDS)
            .build()
        private var selectedUrl: String? = null
        private var sessionId: String? = null
        private var probeResult: TransmissionRpcProbeResult? = null

        suspend fun sessionGet(): TransmissionSessionInfo {
            return rpc(
                method = "session-get",
                arguments = buildJsonObject(
                    "fields" to listOf(
                        "version",
                        "download-dir",
                        "speed-limit-down-enabled",
                        "speed-limit-down",
                        "speed-limit-up-enabled",
                        "speed-limit-up",
                    ),
                ),
            ).asTransmissionSessionInfo()
        }

        suspend fun sessionStats(): TransmissionSessionStats {
            return rpc(
                method = "session-stats",
                arguments = JsonObject(),
            ).asTransmissionSessionStats()
        }

        suspend fun freeSpace(path: String): Result<Long> = runCatching {
            val arguments = rpc(
                method = "free-space",
                arguments = buildJsonObject("path" to path),
            )
            arguments.asJsonObject.get("size-bytes")?.asLong ?: 0L
        }

        suspend fun torrentGet(ids: List<String>, fields: List<String>): List<TransmissionTorrent> {
            val arguments = buildJsonObject("fields" to fields)
            if (ids.isNotEmpty()) {
                arguments.add("ids", gson.toJsonTree(ids))
            }
            val payload = rpc(
                method = "torrent-get",
                arguments = arguments,
            )
            val torrentsJson = payload.asJsonObject.getAsJsonArray("torrents")
            return gson.fromJson(
                torrentsJson,
                object : TypeToken<List<TransmissionTorrent>>() {}.type,
            ) ?: emptyList()
        }

        suspend fun rpc(method: String, arguments: JsonObject): JsonElement {
            return withContext(Dispatchers.IO) {
                val bodyJson = gson.toJson(
                    mapOf(
                        "method" to method,
                        "arguments" to arguments,
                    ),
                )
                var lastError: Throwable? = null
                val attemptFailures = mutableListOf<RpcAttemptFailure>()
                for (candidate in effectiveRpcUrls()) {
                    try {
                        val response = execute(candidate, bodyJson)
                        selectedUrl = candidate
                        probeResult = TransmissionRpcProbeResult(
                            resolvedUrl = candidate,
                            attempts = rpcUrls,
                            failureSummary = attemptFailures.joinToString(" | ") { attempt ->
                                "${attempt.url} => ${attempt.summary}"
                            },
                        )
                        return@withContext response
                    } catch (error: TransmissionRpcAttemptException) {
                        attemptFailures += RpcAttemptFailure(
                            url = error.url,
                            summary = error.summary,
                            authFailure = error.authFailure,
                        )
                        lastError = error
                    } catch (error: Throwable) {
                        attemptFailures += RpcAttemptFailure(
                            url = candidate,
                            summary = error.message?.takeIf { it.isNotBlank() }
                                ?: error::class.simpleName
                                ?: "Request failed.",
                        )
                        lastError = error
                    }
                }

                val authFailure = attemptFailures.firstOrNull { it.authFailure }
                if (authFailure != null) {
                    throw BackendConnectionError.AuthFailed(
                        backendType = ServerBackendType.TRANSMISSION,
                        detail = authFailure.summary,
                    )
                }

                if (attemptFailures.isNotEmpty()) {
                    throw BackendConnectionError.RpcPathNotFound(
                        attempts = attemptFailures.map { it.url },
                        failureSummary = attemptFailures.joinToString(" | ") { attempt ->
                            "${attempt.url} => ${attempt.summary}"
                        },
                    )
                }

                throw lastError ?: IllegalStateException("Transmission RPC failed.")
            }
        }

        private fun effectiveRpcUrls(): List<String> {
            val selected = selectedUrl
            return if (selected == null) rpcUrls else listOf(selected) + rpcUrls.filterNot { candidate ->
                candidate == selected
            }
        }

        private fun execute(url: String, bodyJson: String): JsonElement {
            val mediaType = "application/json".toMediaType()
            var requestBuilder = Request.Builder()
                .url(url)
                .post(bodyJson.toRequestBody(mediaType))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
            if (!authHeader.isNullOrBlank()) {
                requestBuilder = requestBuilder.header("Authorization", authHeader)
            }
            sessionId?.takeIf { it.isNotBlank() }?.let { value ->
                requestBuilder = requestBuilder.header("X-Transmission-Session-Id", value)
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (response.code == 409) {
                    val newSessionId = response.header("X-Transmission-Session-Id").orEmpty()
                    if (newSessionId.isBlank()) {
                        throw TransmissionRpcAttemptException(
                            url = url,
                            summary = "Session handshake failed (409 without session id).",
                        )
                    }
                    sessionId = newSessionId
                    return execute(url, bodyJson)
                }
                if (response.code == 401 || response.code == 403) {
                    throw TransmissionRpcAttemptException(
                        url = url,
                        summary = "Authentication failed (HTTP ${response.code}).",
                        authFailure = true,
                    )
                }
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string().orEmpty()
                    throw TransmissionRpcAttemptException(
                        url = url,
                        summary = when {
                            looksLikeHtml(errorBody) -> "Invalid HTML response (HTTP ${response.code})."
                            errorBody.isBlank() -> "HTTP ${response.code}."
                            else -> "HTTP ${response.code}: ${summarizePayload(errorBody)}"
                        },
                    )
                }
                val payload = response.body?.string().orEmpty()
                if (payload.isBlank()) {
                    throw TransmissionRpcAttemptException(
                        url = url,
                        summary = "Empty response body.",
                    )
                }
                val root = runCatching { JsonParser.parseString(payload).asJsonObject }.getOrElse {
                    throw TransmissionRpcAttemptException(
                        url = url,
                        summary = if (looksLikeHtml(payload)) {
                            "Invalid HTML response."
                        } else {
                            "Non-JSON response."
                        },
                    )
                }
                val result = root.get("result")?.asString.orEmpty()
                if (!result.equals("success", ignoreCase = true)) {
                    val summary = result.ifBlank { "Transmission RPC returned an error." }
                    throw TransmissionRpcAttemptException(
                        url = url,
                        summary = summarizePayload(summary),
                        authFailure = looksLikeAuthFailure(summary),
                    )
                }
                return root.get("arguments") ?: JsonObject()
            }
        }

    }

    private fun TransmissionTorrent.toTorrentInfo(): TorrentInfo {
        val labelsText = labels
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")
        val primaryTracker = trackers.firstOrNull()?.announce
            ?: trackerStats.firstOrNull()?.announce
            ?: ""
        return TorrentInfo(
            hash = hashString.trim(),
            name = name.trim(),
            category = "",
            ratio = uploadRatio,
            size = totalSize.coerceAtLeast(0L),
            progress = percentDone.coerceIn(0.0, 1.0).toFloat(),
            state = mapTransmissionState(
                status = status,
                percentDone = percentDone,
                isFinished = isFinished,
            ),
            downloadSpeed = rateDownload.coerceAtLeast(0L),
            uploadSpeed = rateUpload.coerceAtLeast(0L),
            downloaded = downloadedEver.coerceAtLeast(0L),
            uploaded = uploadedEver.coerceAtLeast(0L),
            addedOn = addedDate.coerceAtLeast(0L),
            lastActivity = activityDate.coerceAtLeast(0L),
            eta = eta.coerceAtLeast(-1L),
            seeders = peersSendingToUs.coerceAtLeast(0),
            leechers = peersGettingFromUs.coerceAtLeast(0),
            numComplete = peersSendingToUs.coerceAtLeast(0),
            numIncomplete = peersGettingFromUs.coerceAtLeast(0),
            tracker = primaryTracker.trim(),
            savePath = downloadDir.trim(),
            tags = labelsText,
        )
    }

    private fun TransmissionTorrent.toTorrentProperties(): TorrentProperties {
        val resolvedDownloadLimit = if (downloadLimited) {
            downloadLimit.toLong().coerceAtLeast(0L) * 1024L
        } else {
            0L
        }
        val resolvedUploadLimit = if (uploadLimited) {
            uploadLimit.toLong().coerceAtLeast(0L) * 1024L
        } else {
            0L
        }
        return TorrentProperties(
            savePath = downloadDir.trim(),
            creationDate = dateCreated.coerceAtLeast(0L),
            pieceSize = pieceSize.coerceAtLeast(0L),
            comment = comment.orEmpty(),
            totalSize = totalSize.coerceAtLeast(0L),
            downloadLimit = resolvedDownloadLimit,
            uploadLimit = resolvedUploadLimit,
            timeElapsed = secondsDownloading.coerceAtLeast(0L),
            seedingTime = secondsSeeding.coerceAtLeast(0L),
            shareRatio = uploadRatio,
        )
    }

    private fun TransmissionTorrent.toTorrentFiles(): List<TorrentFileInfo> {
        val fileByIndex = files.associateBy { it.name to it.length }
        return files.mapIndexed { index, file ->
            val bytesCompleted = fileByIndex[file.name to file.length]?.bytesCompleted ?: 0L
            TorrentFileInfo(
                index = index,
                name = file.name,
                size = file.length.coerceAtLeast(0L),
                progress = if (file.length > 0L) {
                    (bytesCompleted.toDouble() / file.length.toDouble()).coerceIn(0.0, 1.0).toFloat()
                } else {
                    0f
                },
                priority = fileStats.getOrNull(index)?.priority ?: 0,
            )
        }
    }

    private fun TransmissionTorrent.toTorrentTrackers(): List<TorrentTracker> {
        val statsById = trackerStats.associateBy { it.id }
        return trackers.map { tracker ->
            val stat = statsById[tracker.id]
            TorrentTracker(
                backendTrackerId = tracker.id,
                url = tracker.announce,
                status = if (stat?.hasAnnounced == true || stat?.lastAnnounceSucceeded == true) 2 else 0,
                message = stat?.lastAnnounceResult.orEmpty().ifBlank {
                    stat?.lastScrapeResult.orEmpty()
                },
                tier = tracker.tier,
                numPeers = (stat?.seederCount ?: 0) + (stat?.leecherCount ?: 0),
                numSeeds = stat?.seederCount ?: 0,
                numLeeches = stat?.leecherCount ?: 0,
                numDownloaded = stat?.downloadCount ?: 0,
            )
        }
    }

    private fun JsonElement.asTransmissionSessionInfo(): TransmissionSessionInfo {
        return Gson().fromJson(this, TransmissionSessionInfo::class.java)
    }

    private fun JsonElement.asTransmissionSessionStats(): TransmissionSessionStats {
        return Gson().fromJson(this, TransmissionSessionStats::class.java)
    }

    private fun buildJsonObject(vararg pairs: Pair<String, Any?>): JsonObject {
        val result = JsonObject()
        for ((key, value) in pairs) {
            if (value == null) continue
            result.add(key, gson.toJsonTree(value))
        }
        return result
    }

    private fun mapTransmissionState(
        status: Int,
        percentDone: Double,
        isFinished: Boolean,
    ): String {
        return when (status) {
            0 -> if (isFinished || percentDone >= 1.0) "pausedUP" else "pausedDL"
            1, 2 -> "checkUP"
            3 -> "queuedDL"
            4 -> "downloading"
            5 -> "queuedUP"
            6 -> "uploading"
            else -> if (percentDone >= 1.0) "pausedUP" else "pausedDL"
        }
    }

    private fun summarizePayload(text: String): String {
        val title = Regex("<title>(.*?)</title>", RegexOption.IGNORE_CASE)
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            .orEmpty()
        if (title.isNotBlank()) return title
        return text
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(120)
            .ifBlank { "Empty response." }
    }

    private fun looksLikeHtml(text: String): Boolean {
        val normalized = text.trim().lowercase(Locale.US)
        return normalized.startsWith("<!doctype html") ||
            normalized.startsWith("<html") ||
            normalized.contains("<title>")
    }

    private fun looksLikeAuthFailure(text: String): Boolean {
        val normalized = text.lowercase(Locale.US)
        return normalized.contains("unauthorized") ||
            normalized.contains("forbidden") ||
            normalized.contains("auth") ||
            normalized.contains("permission denied")
    }

    companion object {
        private val DASHBOARD_FIELDS = listOf(
            "hashString",
            "name",
            "labels",
            "uploadRatio",
            "totalSize",
            "percentDone",
            "status",
            "rateDownload",
            "rateUpload",
            "downloadedEver",
            "uploadedEver",
            "addedDate",
            "activityDate",
            "eta",
            "peersSendingToUs",
            "peersGettingFromUs",
            "trackers",
            "trackerStats",
            "downloadDir",
            "isFinished",
        )

        private val DETAIL_FIELDS = listOf(
            "hashString",
            "name",
            "labels",
            "uploadRatio",
            "totalSize",
            "percentDone",
            "status",
            "rateDownload",
            "rateUpload",
            "downloadedEver",
            "uploadedEver",
            "addedDate",
            "activityDate",
            "eta",
            "peersSendingToUs",
            "peersGettingFromUs",
            "trackers",
            "trackerStats",
            "downloadDir",
            "isFinished",
            "comment",
            "dateCreated",
            "pieceSize",
            "downloadLimited",
            "downloadLimit",
            "uploadLimited",
            "uploadLimit",
            "secondsDownloading",
            "secondsSeeding",
            "files",
            "fileStats",
            "magnetLink",
        )
    }
}

internal fun sanitizeTransmissionVersion(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return "-"
    return trimmed
        .replace(Regex("\\s*\\([^)]*\\)\\s*$"), "")
        .trim()
        .ifBlank { "-" }
}

internal fun parseTransmissionLabels(raw: JsonElement?): List<String> {
    if (raw == null || raw.isJsonNull) return emptyList()

    fun normalize(values: Sequence<String>): List<String> {
        val normalizedByKey = linkedMapOf<String, String>()
        values
            .map { it.trim() }
            .filter { it.isNotBlank() && it != "-" && !it.equals("null", ignoreCase = true) }
            .forEach { label ->
                val key = label.lowercase(Locale.US)
                if (!normalizedByKey.containsKey(key)) {
                    normalizedByKey[key] = label
                }
            }
        return normalizedByKey.values.toList()
    }

    return when {
        raw.isJsonArray -> normalize(raw.asJsonArray.asSequence().flatMap { element ->
            when {
                element.isJsonNull -> emptySequence()
                element.isJsonPrimitive -> sequenceOf(element.asString)
                element.isJsonObject -> {
                    val objectValue = element.asJsonObject
                    sequenceOf(
                        objectValue.get("name"),
                        objectValue.get("label"),
                        objectValue.get("value"),
                    )
                        .filterNotNull()
                        .filterNot { it.isJsonNull }
                        .map { it.asString }
                }

                else -> emptySequence()
            }
        })

        raw.isJsonPrimitive -> {
            val text = raw.asString.trim()
            val parsedJson = if (text.startsWith("[") && text.endsWith("]")) {
                runCatching { JsonParser.parseString(text) }.getOrNull()
            } else {
                null
            }
            if (parsedJson != null && parsedJson != raw) {
                parseTransmissionLabels(parsedJson)
            } else {
                normalize(text.split(',', ';', '|').asSequence())
            }
        }

        else -> emptyList()
    }
}

internal fun ConnectionSettings.transmissionRpcUrlCandidates(): List<String> {
    return buildTransmissionRpcUrlCandidates(baseUrlCandidates())
}

internal fun buildTransmissionRpcUrlCandidates(baseUrls: List<String>): List<String> {
    return baseUrls
        .flatMap { candidate ->
            runCatching {
                val uri = URI(candidate)
                val host = uri.host?.takeIf { it.isNotBlank() }
                    ?: uri.rawAuthority?.substringAfterLast('@')?.substringBefore(':')
                    ?: return@runCatching emptyList()
                val normalizedHost = if (host.contains(':') && !host.startsWith("[")) {
                    "[$host]"
                } else {
                    host
                }
                val port = if (uri.port > 0) uri.port else if (uri.scheme.equals("https", ignoreCase = true)) 443 else 80
                val basePath = uri.rawPath.orEmpty().trim().trimEnd('/')
                val candidatePaths = buildList {
                    if (basePath.endsWith("/rpc")) {
                        add(basePath)
                    }
                    if (basePath.isNotBlank() && !basePath.endsWith("/rpc")) {
                        add("$basePath/rpc")
                    }
                    add("/transmission/rpc")
                    add("/rpc")
                    add("/tr-control/rpc")
                    add("/tr/rpc")
                }
                candidatePaths.distinct().map { rpcPath ->
                    val normalizedPath = if (rpcPath.startsWith("/")) rpcPath else "/$rpcPath"
                    "${uri.scheme}://$normalizedHost:$port$normalizedPath"
                }
            }.getOrElse { emptyList() }
        }
        .distinct()
}

private data class RpcAttemptFailure(
    val url: String,
    val summary: String,
    val authFailure: Boolean = false,
)

private class TransmissionRpcAttemptException(
    val url: String,
    val summary: String,
    val authFailure: Boolean = false,
) : IOException(summary)

internal data class TransmissionSessionInfo(
    val version: String = "-",
    @SerializedName("download-dir") val downloadDir: String = "",
    @SerializedName("speed-limit-down-enabled") val speedLimitDownEnabled: Boolean = false,
    @SerializedName("speed-limit-down") val speedLimitDown: Int = 0,
    @SerializedName("speed-limit-up-enabled") val speedLimitUpEnabled: Boolean = false,
    @SerializedName("speed-limit-up") val speedLimitUp: Int = 0,
)

internal data class TransmissionSessionStats(
    val downloadSpeed: Long = 0,
    val uploadSpeed: Long = 0,
    @SerializedName("cumulative-stats") val cumulativeStats: TransmissionCumulativeStats = TransmissionCumulativeStats(),
) {
    val cumulativeDownloadedBytes: Long
        get() = cumulativeStats.downloadedBytes

    val cumulativeUploadedBytes: Long
        get() = cumulativeStats.uploadedBytes
}

internal data class TransmissionCumulativeStats(
    val downloadedBytes: Long = 0,
    val uploadedBytes: Long = 0,
)

private data class TransmissionTorrent(
    val hashString: String = "",
    val name: String = "",
    @SerializedName("labels") val labelsPayload: JsonElement? = null,
    val uploadRatio: Double = 0.0,
    val totalSize: Long = 0,
    val percentDone: Double = 0.0,
    val status: Int = 0,
    val rateDownload: Long = 0,
    val rateUpload: Long = 0,
    val downloadedEver: Long = 0,
    val uploadedEver: Long = 0,
    val addedDate: Long = 0,
    val activityDate: Long = 0,
    val eta: Long = 0,
    val peersSendingToUs: Int = 0,
    val peersGettingFromUs: Int = 0,
    val trackers: List<TransmissionTracker> = emptyList(),
    val trackerStats: List<TransmissionTrackerStat> = emptyList(),
    val downloadDir: String = "",
    val isFinished: Boolean = false,
    val comment: String? = null,
    val dateCreated: Long = 0,
    val pieceSize: Long = 0,
    val downloadLimited: Boolean = false,
    val downloadLimit: Int = 0,
    val uploadLimited: Boolean = false,
    val uploadLimit: Int = 0,
    val secondsDownloading: Long = 0,
    val secondsSeeding: Long = 0,
    val files: List<TransmissionFile> = emptyList(),
    val fileStats: List<TransmissionFileStat> = emptyList(),
    val magnetLink: String = "",
) {
    val labels: List<String>
        get() = parseTransmissionLabels(labelsPayload)
}

private data class TransmissionTracker(
    val id: Int = 0,
    val announce: String = "",
    val tier: Int = 0,
)

private data class TransmissionTrackerStat(
    val id: Int = 0,
    val announce: String = "",
    val seederCount: Int = 0,
    val leecherCount: Int = 0,
    val downloadCount: Int = 0,
    val hasAnnounced: Boolean = false,
    val lastAnnounceSucceeded: Boolean = false,
    val lastAnnounceResult: String = "",
    val lastScrapeResult: String = "",
)

private data class TransmissionFile(
    val name: String = "",
    val length: Long = 0,
    val bytesCompleted: Long = 0,
)

private data class TransmissionFileStat(
    val priority: Int = 0,
)
