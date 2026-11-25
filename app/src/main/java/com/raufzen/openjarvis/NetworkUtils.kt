package com.raufzen.openjarvis

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class ServerCheckResult(
    val isReachable: Boolean,
    val statusCode: Int = 0,
    val latencyMs: Long = 0,
    val errorMessage: String? = null
)

object NetworkUtils {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun isDeviceConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun pingServer(url: String): ServerCheckResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val request = Request.Builder()
                .url(url)
                .head()
                .build()

            client.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - startTime
                ServerCheckResult(
                    isReachable = response.isSuccessful || response.code in 200..404,
                    statusCode = response.code,
                    latencyMs = latency
                )
            }
        } catch (e: Exception) {
            try {
                val getRequest = Request.Builder()
                    .url(url)
                    .get()
                    .build()

                client.newCall(getRequest).execute().use { response ->
                    val latency = System.currentTimeMillis() - startTime
                    ServerCheckResult(
                        isReachable = true,
                        statusCode = response.code,
                        latencyMs = latency
                    )
                }
            } catch (innerEx: Exception) {
                ServerCheckResult(
                    isReachable = false,
                    errorMessage = innerEx.localizedMessage ?: "Connection timed out"
                )
            }
        }
    }

    suspend fun testGeminiApiKey(apiKey: String, model: String): ServerCheckResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext ServerCheckResult(
                isReachable = false,
                errorMessage = "API key cannot be empty"
            )
        }

        val startTime = System.currentTimeMillis()
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val jsonPayload = """{"contents":[{"role":"user","parts":[{"text":"Hi"}]}]}"""
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = jsonPayload.toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - startTime
                if (response.isSuccessful) {
                    ServerCheckResult(
                        isReachable = true,
                        statusCode = response.code,
                        latencyMs = latency
                    )
                } else {
                    val errorBody = response.body?.string() ?: ""
                    ServerCheckResult(
                        isReachable = false,
                        statusCode = response.code,
                        errorMessage = "HTTP ${response.code}: $errorBody"
                    )
                }
            }
        } catch (e: Exception) {
            ServerCheckResult(
                isReachable = false,
                errorMessage = e.localizedMessage ?: "Network error connecting to Google Gemini"
            )
        }
    }
}
