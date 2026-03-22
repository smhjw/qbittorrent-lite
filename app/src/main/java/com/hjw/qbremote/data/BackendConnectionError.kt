package com.hjw.qbremote.data

sealed class BackendConnectionError(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause) {
    data class WrongBackend(
        val expected: ServerBackendType,
        val detected: ServerBackendType,
        val attemptedEndpoint: String,
        val detail: String,
    ) : BackendConnectionError(
        message = "Server type mismatch: expected $expected, but target looks like $detected. $detail",
    )

    data class RpcPathNotFound(
        val attempts: List<String>,
        val failureSummary: String,
    ) : BackendConnectionError(
        message = "Transmission RPC path not found. Tried ${attempts.joinToString()}. $failureSummary",
    )

    data class AuthFailed(
        val backendType: ServerBackendType,
        val detail: String,
    ) : BackendConnectionError(
        message = "$backendType auth failed. $detail",
    )
}

data class TransmissionRpcProbeResult(
    val resolvedUrl: String,
    val attempts: List<String>,
    val failureSummary: String,
)
