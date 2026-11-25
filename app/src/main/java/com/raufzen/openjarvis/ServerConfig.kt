package com.raufzen.openjarvis

import android.content.Context
import android.content.SharedPreferences

class ServerConfig(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var appMode: String
        get() = prefs.getString(KEY_APP_MODE, MODE_GEMINI) ?: MODE_GEMINI
        set(value) = prefs.edit().putString(KEY_APP_MODE, value).apply()

    var geminiApiKey: String
        get() = prefs.getString(KEY_GEMINI_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GEMINI_KEY, value.trim()).apply()

    var geminiModel: String
        get() = prefs.getString(KEY_GEMINI_MODEL, DEFAULT_GEMINI_MODEL) ?: DEFAULT_GEMINI_MODEL
        set(value) = prefs.edit().putString(KEY_GEMINI_MODEL, value).apply()

    var isVoiceTtsEnabled: Boolean
        get() = prefs.getBoolean(KEY_VOICE_TTS, true)
        set(value) = prefs.edit().putBoolean(KEY_VOICE_TTS, value).apply()

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

    fun saveServer(protocol: String, host: String, port: Int) {
        this.protocol = protocol
        this.host = host
        this.port = port
    }

    fun saveGemini(apiKey: String, model: String, voiceEnabled: Boolean) {
        this.geminiApiKey = apiKey
        this.geminiModel = model
        this.isVoiceTtsEnabled = voiceEnabled
    }

    companion object {
        private const val PREFS_NAME = "openjarvis_config"
        const val MODE_GEMINI = "gemini"
        const val MODE_LOCAL_SERVER = "local_server"

        private const val KEY_APP_MODE = "app_mode"
        private const val KEY_GEMINI_KEY = "gemini_api_key"
        private const val KEY_GEMINI_MODEL = "gemini_model"
        private const val KEY_VOICE_TTS = "voice_tts_enabled"

        private const val KEY_PROTOCOL = "server_protocol"
        private const val KEY_HOST = "server_host"
        private const val KEY_PORT = "server_port"

        const val DEFAULT_GEMINI_MODEL = "gemini-2.5-flash"
        const val DEFAULT_HOST = "192.168.1.100"
        const val DEFAULT_PORT = 8000
    }
}
