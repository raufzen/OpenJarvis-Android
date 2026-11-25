package com.raufzen.openjarvis

import android.content.Context
import android.content.SharedPreferences

class ServerConfig(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var protocol: String
        get() = prefs.getString(KEY_PROTOCOL, "http") ?: "http"
        set(value) = prefs.edit().putString(KEY_PROTOCOL, value).apply()

    var host: String
        get() = prefs.getString(KEY_HOST, DEFAULT_HOST) ?: DEFAULT_HOST
        set(value) = prefs.edit().putString(KEY_HOST, value.trim()).apply()

    var port: Int
        get() = prefs.getInt(KEY_PORT, DEFAULT_PORT)
        set(value) = prefs.edit().putInt(KEY_PORT, value).apply()

    val baseUrl: String
        get() = "$protocol://$host:$port"

    val statusCheckUrl: String
        get() = "$baseUrl/api/v1/system/status"

    fun save(protocol: String, host: String, port: Int) {
        this.protocol = protocol
        this.host = host
        this.port = port
    }

    companion object {
        private const val PREFS_NAME = "openjarvis_config"
        private const val KEY_PROTOCOL = "server_protocol"
        private const val KEY_HOST = "server_host"
        private const val KEY_PORT = "server_port"

        const val DEFAULT_HOST = "192.168.1.100"
        const val DEFAULT_PORT = 8000
    }
}
