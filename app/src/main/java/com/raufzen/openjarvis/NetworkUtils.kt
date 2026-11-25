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
import org.json.JSONObject
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

    suspend fun fetchAvailableModels(apiKey: String): Result<List<String>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(Exception("API Key cannot be empty"))
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
        val request = Request.Builder().url(url).get().build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string() ?: ""
                    return@withContext Result.failure(Exception("HTTP ${response.code}: $err"))
                }

                val bodyStr = response.body?.string() ?: ""
                val json = JSONObject(bodyStr)
                val modelsArray = json.optJSONArray("models") ?: return@withContext Result.success(emptyList())

                val extracted = mutableListOf<String>()
                for (i in 0 until modelsArray.length()) {
                    val modelObj = modelsArray.getJSONObject(i)
                    val rawName = modelObj.optString("name", "")
                    val methods = modelObj.optJSONArray("supportedGenerationMethods")

                    var canGenerate = false
                    if (methods != null) {
                        for (j in 0 until methods.length()) {
                            if (methods.getString(j) == "generateContent") {
                                canGenerate = true
                                break
                            }
                        }
                    }

                    if (canGenerate && rawName.contains("gemini")) {
                        val cleanName = rawName.removePrefix("models/")
                        extracted.add(cleanName)
                    }
                }

                // Sort models so best/newest flash is on top
                extracted.sortWith { a, b ->
                    when {
                        a.contains("3.8-flash") -> -1
                        b.contains("3.8-flash") -> 1
                        a.contains("3.5-flash") -> -1
                        b.contains("3.5-flash") -> 1
                        a.contains("flash") && !b.contains("flash") -> -1
                        !a.contains("flash") && b.contains("flash") -> 1
                        else -> b.compareTo(a)
                    }
                }

                Result.success(extracted)
            }
        } catch (e: Exception) {
            Result.failure(e)
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
