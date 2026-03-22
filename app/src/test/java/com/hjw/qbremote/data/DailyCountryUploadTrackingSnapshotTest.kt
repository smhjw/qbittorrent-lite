package com.hjw.qbremote.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyCountryUploadTrackingSnapshotTest {

    @Test
    fun legacySnapshotWithRecentSamples_deserializesWithoutFailure() {
        val json = """
            {
              "scope-a": {
                "date": "2026-03-14",
                "totalsByCountry": {
                  "us": 2048
                },
                "peerSnapshots": {
                  "peer-1": {
                    "key": "peer-1",
                    "peerAddress": "1.2.3.4:51413",
                    "countryCode": "us",
                    "countryName": "United States",
                    "uploadedBytes": 1024
                  }
                },
                "lastSeenByTorrent": {
                  "hash-1": 4096
                },
                "recentSamples": [
                  {
                    "sampledTotals": {
                      "US": 1024
                    },
                    "peerCountsByCountry": {
                      "US": 2
                    }
                  }
                ]
              }
            }
        """.trimIndent()

        val type = object : TypeToken<Map<String, DailyCountryUploadTrackingSnapshot>>() {}.type
        val parsed = Gson().fromJson<Map<String, DailyCountryUploadTrackingSnapshot>>(json, type)
        val snapshot = parsed.getValue("scope-a")

        assertEquals("2026-03-14", snapshot.date)
        assertEquals(2048L, snapshot.totalsByCountry.getValue("us"))
        assertEquals(4096L, snapshot.lastSeenByTorrent.getValue("hash-1"))
        assertEquals("United States", snapshot.peerSnapshots.getValue("peer-1").countryName)
        assertTrue(snapshot.peerSnapshots.isNotEmpty())
    }
}
