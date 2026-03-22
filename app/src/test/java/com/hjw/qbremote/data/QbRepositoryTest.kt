package com.hjw.qbremote.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QbRepositoryTest {

    @Test
    fun detectTransmissionMismatchForHtmlLoginPage() {
        val mismatch = detectTransmissionMismatchForQbResponse(
            endpointLabel = "api/v2/auth/login",
            responseText = """
                <!DOCTYPE html>
                <html>
                <head><title>Transmission Web Control</title></head>
                <body>Transmission Web Control</body>
                </html>
            """.trimIndent(),
            sessionHeader = "",
        )

        assertNotNull(mismatch)
        assertEquals(ServerBackendType.QBITTORRENT, mismatch?.expected)
        assertEquals(ServerBackendType.TRANSMISSION, mismatch?.detected)
        assertTrue(mismatch?.detail?.contains("Transmission Web Control") == true)
    }

    @Test
    fun detectTransmissionMismatchForSessionHeader() {
        val mismatch = detectTransmissionMismatchForQbResponse(
            endpointLabel = "api/v2/auth/login",
            responseText = "",
            sessionHeader = "abc123",
        )

        assertNotNull(mismatch)
        assertEquals("api/v2/auth/login", mismatch?.attemptedEndpoint)
        assertTrue(mismatch?.detail?.contains("Unexpected response.") == true)
    }
}
