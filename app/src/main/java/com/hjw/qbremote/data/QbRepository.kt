package com.hjw.qbremote.data

import com.hjw.qbremote.data.model.DashboardData
import com.hjw.qbremote.data.model.TorrentInfo
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import kotlinx.coroutines.delay
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

class QbRepository {
    private var api: QbApi? = null
    private var activeSettings: ConnectionSettings? = null

    private var syncRid: Long = 0
    private val torrentCache = linkedMapOf<String, TorrentInfo>()

    suspend fun connect(settings: ConnectionSettings): Result<Unit> = runCatching {
        require(settings.host.isNotBlank()) { "Host cannot be empty." }
        require(settings.port in 1..65535) { "Port must be between 1 and 65535." }

        val newApi = buildApi(settings.baseUrl())
        loginWithRetry(newApi, settings)

        api = newApi
        activeSettings = settings
        syncRid = 0
        torrentCache.clear()
    }

    suspend fun fetchDashboard(): Result<DashboardData> = runCatching {
        try {
            val syncData = executeWithRetry { liveApi -> liveApi.syncMainData(syncRid) }
            applySyncData(syncData.rid, syncData.fullUpdate, syncData.torrents, syncData.torrentsRemoved)

            DashboardData(
                transferInfo = syncData.serverState,
                torrents = torrentCache.values.sortedByDescending { it.addedOn },
            )
        } catch (e: HttpException) {
            if (e.code() != 404) throw e
            fallbackFullRefresh()
        }
    }

    suspend fun pauseTorrent(hash: String): Result<Unit> = runCatching {
        require(hash.isNotBlank()) { "Invalid torrent hash." }
        executeWithRetry { liveApi ->
            liveApi.pauseTorrents(hash).ensureSuccess("Pause failed.")
        }
    }

    suspend fun resumeTorrent(hash: String): Result<Unit> = runCatching {
        require(hash.isNotBlank()) { "Invalid torrent hash." }
        executeWithRetry { liveApi ->
            liveApi.resumeTorrents(hash).ensureSuccess("Resume failed.")
        }
    }

    suspend fun deleteTorrent(hash: String, deleteFiles: Boolean): Result<Unit> = runCatching {
        require(hash.isNotBlank()) { "Invalid torrent hash." }
        executeWithRetry { liveApi ->
            liveApi.deleteTorrents(hash, deleteFiles).ensureSuccess("Delete failed.")
        }
    }

    private suspend fun fallbackFullRefresh(): DashboardData {
        val transfer = executeWithRetry { liveApi -> liveApi.transferInfo() }
        val torrents = executeWithRetry { liveApi ->
            liveApi.torrentsInfo().sortedByDescending { it.addedOn }
        }

        torrentCache.clear()
        torrents.forEach { torrent ->
            if (torrent.hash.isNotBlank()) {
                torrentCache[torrent.hash] = torrent
            }
        }
        syncRid = 0

        return DashboardData(transferInfo = transfer, torrents = torrents)
    }

    private fun applySyncData(
        rid: Long,
        fullUpdate: Boolean,
        torrents: Map<String, TorrentInfo>,
        removed: List<String>,
    ) {
        if (fullUpdate || syncRid == 0L) {
            torrentCache.clear()
        }

        torrents.forEach { (hash, torrent) ->
            val effectiveHash = torrent.hash.ifBlank { hash }
            torrentCache[effectiveHash] = torrent.copy(hash = effectiveHash)
        }
        removed.forEach { hash -> torrentCache.remove(hash) }

        syncRid = rid
    }

    private suspend fun <T> executeWithRetry(
        maxAttempts: Int = 3,
        initialDelayMs: Long = 400L,
        action: suspend (QbApi) -> T,
    ): T {
        var attempt = 0
        var nextDelayMs = initialDelayMs

        while (attempt < maxAttempts) {
            attempt++
            try {
                return action(requireApi())
            } catch (error: Throwable) {
                val shouldRetry = when (error) {
                    is HttpException -> {
                        if (error.code() == 401 || error.code() == 403) {
                            relogin()
                            true
                        } else {
                            error.code() in 500..599
                        }
                    }
                    is IOException -> true
                    else -> false
                }

                if (!shouldRetry || attempt >= maxAttempts) {
                    throw error
                }

                delay(nextDelayMs)
                nextDelayMs = (nextDelayMs * 2).coerceAtMost(4000L)
            }
        }

        throw IllegalStateException("Request failed after retries.")
    }

    private suspend fun loginWithRetry(
        liveApi: QbApi,
        settings: ConnectionSettings,
    ) {
        var attempt = 0
        var nextDelayMs = 400L

        while (attempt < 3) {
            attempt++
            try {
                val loginResponse = liveApi.login(settings.username, settings.password)
                val loginText = loginResponse.body()?.trim().orEmpty()
                val loginOk = loginResponse.isSuccessful && loginText.equals("Ok.", ignoreCase = true)
                if (!loginOk) {
                    throw IllegalStateException("Login failed. Check host, credentials, and WebUI settings.")
                }
                return
            } catch (error: IOException) {
                if (attempt >= 3) throw error
                delay(nextDelayMs)
                nextDelayMs = (nextDelayMs * 2).coerceAtMost(4000L)
            }
        }
    }

    private suspend fun relogin() {
        val liveApi = requireApi()
        val settings = activeSettings ?: throw IllegalStateException(
            "Connection settings are missing for session recovery."
        )
        loginWithRetry(liveApi, settings)
    }

    private fun buildApi(baseUrl: String): QbApi {
        val logger = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .cookieJar(SessionCookieJar())
            .addInterceptor(logger)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(QbApi::class.java)
    }

    private fun requireApi(): QbApi {
        return api ?: throw IllegalStateException("Not connected to qBittorrent yet.")
    }

    private fun Response<*>.ensureSuccess(defaultMessage: String) {
        if (!isSuccessful) {
            val extra = errorBody()?.string()?.takeIf { it.isNotBlank() } ?: code().toString()
            throw IllegalStateException("$defaultMessage ($extra)")
        }
    }

    private class SessionCookieJar : CookieJar {
        private val cookieStore = mutableMapOf<String, List<Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookieStore[url.host] = cookies
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return cookieStore[url.host].orEmpty()
        }
    }
}
