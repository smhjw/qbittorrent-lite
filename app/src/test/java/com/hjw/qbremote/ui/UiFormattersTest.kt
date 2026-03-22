package com.hjw.qbremote.ui

import com.hjw.qbremote.data.model.CountryUploadRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiFormattersTest {

    @Test
    fun mergeCountryUploadRecordsForDisplay_mergesTwIntoCnAndDropsZeroes() {
        val merged = mergeCountryUploadRecordsForDisplay(
            listOf(
                CountryUploadRecord(countryCode = "TW", countryName = "Taiwan", uploadedBytes = 512),
                CountryUploadRecord(countryCode = "CN", countryName = "China", uploadedBytes = 1024),
                CountryUploadRecord(countryCode = "US", countryName = "United States", uploadedBytes = 256),
                CountryUploadRecord(countryCode = "JP", countryName = "Japan", uploadedBytes = 0),
            ),
        )

        assertEquals(2, merged.size)
        assertEquals("CN", merged[0].countryCode)
        assertEquals(1536L, merged[0].uploadedBytes)
        assertEquals("US", merged[1].countryCode)
        assertTrue(merged.none { it.countryCode == "TW" || it.countryCode == "JP" })
    }

    @Test
    fun buildMagnetUri_includesHashNameAndTrackers() {
        val magnet = buildMagnetUri(
            hash = "ABC123",
            name = "Ubuntu ISO",
            trackerUrls = listOf("udp://tracker.example:80/announce"),
        )

        assertTrue(magnet.startsWith("magnet:?xt=urn:btih:ABC123"))
        assertTrue(magnet.contains("dn=Ubuntu%20ISO"))
        assertTrue(magnet.contains("tr=udp%3A%2F%2Ftracker.example%3A80%2Fannounce"))
    }

    @Test
    fun buildTorrentExportFileName_sanitizesReservedCharacters() {
        val fileName = buildTorrentExportFileName(
            torrentName = "ubuntu:24.04/?release",
            hash = "HASH123",
        )

        assertEquals("ubuntu_24.04__release.torrent", fileName)
    }

    @Test
    fun formatTrackerSiteName_prefersReadableDomainLabel() {
        assertEquals(
            "happyfappy",
            formatTrackerSiteName("https://tracker.happyfappy.net/announce", "unknown"),
        )
        assertEquals(
            "example",
            formatTrackerSiteName("https://announce.example.co.uk/announce", "unknown"),
        )
        assertEquals(
            "unknown",
            formatTrackerSiteName("", "unknown"),
        )
    }
}
