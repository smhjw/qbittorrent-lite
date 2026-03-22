package com.hjw.qbremote.data

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransmissionBackendTest {

    @Test
    fun sanitizeTransmissionVersion_stripsTrailingBuildHash() {
        assertEquals("4.0.6", sanitizeTransmissionVersion("4.0.6 (38c164933e)"))
        assertEquals("4.0.6", sanitizeTransmissionVersion(" 4.0.6 "))
        assertEquals("-", sanitizeTransmissionVersion(""))
    }

    @Test
    fun transmissionRpcUrlCandidates_tryCommonRootPaths() {
        val settings = ConnectionSettings(
            host = "https://trs.example.com",
            port = 1667,
            useHttps = true,
        )

        val candidates = settings.transmissionRpcUrlCandidates()

        assertEquals(
            listOf(
                "https://trs.example.com:1667/transmission/rpc",
                "https://trs.example.com:1667/rpc",
                "https://trs.example.com:1667/tr-control/rpc",
                "https://trs.example.com:1667/tr/rpc",
            ),
            candidates.take(4),
        )
        assertTrue(candidates.contains("https://trs.example.com:443/transmission/rpc"))
        assertTrue(candidates.contains("https://trs.example.com:443/rpc"))
    }

    @Test
    fun transmissionRpcUrlCandidates_preserveRpcSuffixWithoutDuplicateAppend() {
        val settings = ConnectionSettings(
            host = "https://trs.example.com/control/rpc",
            port = 443,
            useHttps = true,
        )

        val candidates = settings.transmissionRpcUrlCandidates()

        assertEquals("https://trs.example.com:443/control/rpc", candidates.first())
        assertEquals(5, candidates.size)
        assertTrue(candidates.none { it.contains("/control/rpc/rpc") })
    }

    @Test
    fun transmissionRpcUrlCandidates_tryCustomPathRpcFirst() {
        val settings = ConnectionSettings(
            host = "https://trs.example.com/tr-web-control",
            port = 443,
            useHttps = true,
        )

        val candidates = settings.transmissionRpcUrlCandidates()

        assertEquals("https://trs.example.com:443/tr-web-control/rpc", candidates.first())
    }

    @Test
    fun parseTransmissionLabels_supportsArrayAndStringPayloads() {
        assertEquals(
            listOf("Favorites", "Movies"),
            parseTransmissionLabels(JsonParser.parseString("""["Favorites","Movies"]""")),
        )
        assertEquals(
            listOf("Favorites", "Movies"),
            parseTransmissionLabels(JsonParser.parseString("\"Favorites, Movies, Favorites\"")),
        )
    }

    @Test
    fun transmissionSessionModels_mapKebabCaseFields() {
        val gson = Gson()
        val session = gson.fromJson(
            """
            {
              "version": "4.0.6 (38c164933e)",
              "download-dir": "/downloads",
              "speed-limit-down-enabled": true,
              "speed-limit-down": 3072,
              "speed-limit-up-enabled": false,
              "speed-limit-up": 5120
            }
            """.trimIndent(),
            TransmissionSessionInfo::class.java,
        )
        val stats = gson.fromJson(
            """
            {
              "downloadSpeed": 123456,
              "uploadSpeed": 654321,
              "cumulative-stats": {
                "downloadedBytes": 987654321,
                "uploadedBytes": 123456789
              }
            }
            """.trimIndent(),
            TransmissionSessionStats::class.java,
        )

        assertEquals("/downloads", session.downloadDir)
        assertEquals(true, session.speedLimitDownEnabled)
        assertEquals(3072, session.speedLimitDown)
        assertEquals(false, session.speedLimitUpEnabled)
        assertEquals(5120, session.speedLimitUp)
        assertEquals(123456L, stats.downloadSpeed)
        assertEquals(654321L, stats.uploadSpeed)
        assertEquals(987654321L, stats.cumulativeDownloadedBytes)
        assertEquals(123456789L, stats.cumulativeUploadedBytes)
    }
}
