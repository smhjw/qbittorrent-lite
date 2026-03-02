package com.hjw.qbremote.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "qb_connection")

data class ConnectionSettings(
    val host: String = "",
    val port: Int = 8080,
    val useHttps: Boolean = false,
    val username: String = "admin",
    val password: String = "",
    val refreshSeconds: Int = 3,
) {
    fun baseUrl(): String {
        val cleanHost = host.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .trimEnd('/')
        val scheme = if (useHttps) "https" else "http"
        return "$scheme://$cleanHost:$port/"
    }
}

class ConnectionStore(private val context: Context) {
    private val secureCredentials = SecureCredentialStore(context)

    private object Keys {
        val Host = stringPreferencesKey("host")
        val Port = intPreferencesKey("port")
        val UseHttps = booleanPreferencesKey("use_https")
        val Username = stringPreferencesKey("username")
        val PasswordLegacy = stringPreferencesKey("password")
        val RefreshSeconds = intPreferencesKey("refresh_seconds")
    }

    val settingsFlow: Flow<ConnectionSettings> = context.dataStore.data.map { pref ->
        pref.toSettings(secureCredentials.getPassword())
    }

    suspend fun save(settings: ConnectionSettings) {
        secureCredentials.savePassword(settings.password)
        context.dataStore.edit { pref ->
            pref[Keys.Host] = settings.host
            pref[Keys.Port] = settings.port
            pref[Keys.UseHttps] = settings.useHttps
            pref[Keys.Username] = settings.username
            pref[Keys.RefreshSeconds] = settings.refreshSeconds
            pref.remove(Keys.PasswordLegacy)
        }
    }

    suspend fun migrateLegacyPasswordIfNeeded() {
        val pref = context.dataStore.data.first()
        val legacy = pref[Keys.PasswordLegacy].orEmpty()
        if (legacy.isBlank()) return

        secureCredentials.savePassword(legacy)
        context.dataStore.edit { it.remove(Keys.PasswordLegacy) }
    }

    private fun Preferences.toSettings(securePassword: String): ConnectionSettings {
        return ConnectionSettings(
            host = this[Keys.Host] ?: "",
            port = this[Keys.Port] ?: 8080,
            useHttps = this[Keys.UseHttps] ?: false,
            username = this[Keys.Username] ?: "admin",
            password = securePassword,
            refreshSeconds = this[Keys.RefreshSeconds] ?: 3,
        )
    }
}
