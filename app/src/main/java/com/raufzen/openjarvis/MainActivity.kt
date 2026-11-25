package com.raufzen.openjarvis

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.webkit.*
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.raufzen.openjarvis.databinding.ActivityMainBinding
import com.raufzen.openjarvis.databinding.DialogServerConfigBinding
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var config: ServerConfig
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var backPressedTime: Long = 0

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val results = WebChromeClient.FileChooserParams.parseResult(result.resultCode, data)
            fileUploadCallback?.onReceiveValue(results)
        } else {
            fileUploadCallback?.onReceiveValue(null)
        }
        fileUploadCallback = null
    }

    private val voiceInputLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = matches?.firstOrNull() ?: ""
            if (spokenText.isNotBlank()) {
                val clean = spokenText.replace("'", "\\'").replace("\n", " ")
                binding.webView.evaluateJavascript("setVoiceInputText('$clean');", null)
            }
        }
    }

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Permissions handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        config = ServerConfig(this)
        tts = TextToSpeech(this, this)

        setupTopBar()
        setupWebView()
        setupSwipeRefresh()
        setupBackNavigation()
        requestAppPermissions()

        // Background auto-refresh available models if key is present
        if (config.geminiApiKey.isNotBlank()) {
            lifecycleScope.launch {
                val res = NetworkUtils.fetchAvailableModels(config.geminiApiKey)
                res.onSuccess { models ->
                    if (models.isNotEmpty()) {
                        config.cachedModels = models
                        if (!models.contains(config.geminiModel)) {
                            config.geminiModel = models.first()
                        }
                    }
                }
            }
        }

        checkServerAndLoad()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            isTtsReady = true
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    private fun setupTopBar() {
        binding.btnSettings.setOnClickListener {
            showServerConfigDialog()
        }

        binding.btnRefresh.setOnClickListener {
            checkServerAndLoad()
        }

        binding.statusChip.setOnClickListener {
            showServerConfigDialog()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webSettings = binding.webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.databaseEnabled = true
        webSettings.allowFileAccess = true
        webSettings.allowContentAccess = true
        webSettings.useWideViewPort = true
        webSettings.loadWithOverviewMode = true
        webSettings.cacheMode = WebSettings.LOAD_DEFAULT
        webSettings.mediaPlaybackRequiresUserGesture = false
        webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        binding.webView.addJavascriptInterface(AndroidBridge(), "AndroidBridge")

        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.progressBar.progress = newProgress
                } else {
                    binding.progressBar.visibility = View.GONE
                    binding.swipeRefresh.isRefreshing = false
                }
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = filePathCallback

                val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }

                try {
                    filePickerLauncher.launch(intent)
                } catch (e: Exception) {
                    fileUploadCallback = null
                    return false
                }
                return true
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.grant(request.resources)
            }
        }

        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                binding.progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false

                if (url?.contains("jarvis_gemini.html") == true) {
                    updateStatus(ConnectionStatus.CONNECTED, "Gemini Active")
                    binding.webView.evaluateJavascript("syncConfig();", null)
                } else if (url?.startsWith("file:///android_asset/offline_fallback.html") == true) {
                    updateStatus(ConnectionStatus.OFFLINE, "Server Offline")
                    binding.webView.evaluateJavascript("setServerUrl('${config.baseUrl}');", null)
                } else {
                    updateStatus(ConnectionStatus.CONNECTED, "Server Online")
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true && config.appMode == ServerConfig.MODE_LOCAL_SERVER) {
                    loadOfflineFallback()
                }
            }
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeResources(R.color.jarvis_primary, R.color.jarvis_secondary)
        binding.swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.jarvis_surface)
        binding.swipeRefresh.setOnRefreshListener {
            checkServerAndLoad()
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                } else {
                    if (System.currentTimeMillis() - backPressedTime < 2000) {
                        finish()
                    } else {
                        backPressedTime = System.currentTimeMillis()
                        Toast.makeText(this@MainActivity, "Press back again to exit", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun requestAppPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            requestPermissionsLauncher.launch(missing.toTypedArray())
        }
    }

    private fun checkServerAndLoad() {
        binding.progressBar.visibility = View.VISIBLE

        if (config.appMode == ServerConfig.MODE_GEMINI) {
            // Standalone Gemini AI Mode (Works on 4G Mobile Data without Wi-Fi!)
            updateStatus(ConnectionStatus.CONNECTED, "Gemini Active")
            binding.webView.loadUrl("file:///android_asset/jarvis_gemini.html")
            return
        }

        // Local PC Server Mode (Requires Local Network/Wi-Fi)
        updateStatus(ConnectionStatus.CONNECTING, "Connecting...")

        lifecycleScope.launch {
            val result = NetworkUtils.pingServer(config.baseUrl)
            if (result.isReachable) {
                updateStatus(ConnectionStatus.CONNECTED, "Server Online")
                binding.webView.loadUrl(config.baseUrl)
            } else {
                updateStatus(ConnectionStatus.OFFLINE, "Server Offline")
                loadOfflineFallback()
            }
        }
    }

    private fun loadOfflineFallback() {
        binding.webView.loadUrl("file:///android_asset/offline_fallback.html")
    }

    private fun updateStatus(status: ConnectionStatus, label: String? = null) {
        when (status) {
            ConnectionStatus.CONNECTED -> {
                binding.viewStatusDot.setBackgroundResource(R.drawable.dot_connected)
                binding.tvStatusText.text = label ?: getString(R.string.status_connected)
                binding.tvStatusText.setTextColor(ContextCompat.getColor(this, R.color.jarvis_accent))
            }
            ConnectionStatus.OFFLINE -> {
                binding.viewStatusDot.setBackgroundResource(R.drawable.dot_offline)
                binding.tvStatusText.text = label ?: getString(R.string.status_offline)
                binding.tvStatusText.setTextColor(ContextCompat.getColor(this, R.color.jarvis_danger))
            }
            ConnectionStatus.CONNECTING -> {
                binding.viewStatusDot.setBackgroundResource(R.drawable.dot_connecting)
                binding.tvStatusText.text = label ?: getString(R.string.status_connecting)
                binding.tvStatusText.setTextColor(ContextCompat.getColor(this, R.color.jarvis_primary))
            }
        }
    }

    private fun showServerConfigDialog() {
        val dialogBinding = DialogServerConfigBinding.inflate(LayoutInflater.from(this))

        // App Mode Setup
        if (config.appMode == ServerConfig.MODE_GEMINI) {
            dialogBinding.rbModeGemini.isChecked = true
            dialogBinding.containerGeminiSettings.visibility = View.VISIBLE
            dialogBinding.containerServerSettings.visibility = View.GONE
        } else {
            dialogBinding.rbModeServer.isChecked = true
            dialogBinding.containerGeminiSettings.visibility = View.GONE
            dialogBinding.containerServerSettings.visibility = View.VISIBLE
        }

        dialogBinding.rgAppMode.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rbModeGemini) {
                dialogBinding.containerGeminiSettings.visibility = View.VISIBLE
                dialogBinding.containerServerSettings.visibility = View.GONE
            } else {
                dialogBinding.containerGeminiSettings.visibility = View.GONE
                dialogBinding.containerServerSettings.visibility = View.VISIBLE
            }
        }

        // Gemini Settings
        dialogBinding.etGeminiKey.setText(config.geminiApiKey)
        dialogBinding.switchVoice.isChecked = config.isVoiceTtsEnabled

        // Model List Adapter Setup
        val currentModels = config.cachedModels.toMutableList()
        if (!currentModels.contains(config.geminiModel)) {
            currentModels.add(0, config.geminiModel)
        }
        val modelAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, currentModels)
        dialogBinding.spGeminiModel.adapter = modelAdapter
        val selectedIdx = currentModels.indexOf(config.geminiModel)
        if (selectedIdx >= 0) {
            dialogBinding.spGeminiModel.setSelection(selectedIdx)
        }
        dialogBinding.tvModelCount.text = "${currentModels.size} models available"

        // Function to fetch models dynamically from Google API using entered key
        fun fetchAndPopulateModels(key: String, onComplete: ((List<String>) -> Unit)? = null) {
            if (key.isBlank()) return
            dialogBinding.tvModelCount.text = "Loading models from your API key..."
            dialogBinding.tvModelCount.setTextColor(ContextCompat.getColor(this, R.color.jarvis_primary))

            lifecycleScope.launch {
                val res = NetworkUtils.fetchAvailableModels(key)
                res.onSuccess { models ->
                    if (models.isNotEmpty()) {
                        config.cachedModels = models
                        modelAdapter.clear()
                        modelAdapter.addAll(models)
                        modelAdapter.notifyDataSetChanged()

                        val bestIdx = when {
                            models.contains(config.geminiModel) -> models.indexOf(config.geminiModel)
                            models.any { it.contains("3.8-flash") } -> models.indexOfFirst { it.contains("3.8-flash") }
                            models.any { it.contains("flash") } -> models.indexOfFirst { it.contains("flash") }
                            else -> 0
                        }
                        dialogBinding.spGeminiModel.setSelection(bestIdx)
                        dialogBinding.tvModelCount.text = "✓ ${models.size} models loaded from your API key"
                        dialogBinding.tvModelCount.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.jarvis_accent))
                        onComplete?.invoke(models)
                    }
                }.onFailure { err ->
                    dialogBinding.tvModelCount.text = "Error loading models: ${err.localizedMessage}"
                    dialogBinding.tvModelCount.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.jarvis_danger))
                }
            }
        }

        // Auto-fetch if key exists
        if (config.geminiApiKey.isNotBlank()) {
            fetchAndPopulateModels(config.geminiApiKey)
        }

        dialogBinding.btnFetchModels.setOnClickListener {
            val key = dialogBinding.etGeminiKey.text.toString().trim()
            if (key.isBlank()) {
                Toast.makeText(this, "Please enter your Gemini API key first", Toast.LENGTH_SHORT).show()
            } else {
                fetchAndPopulateModels(key)
            }
        }

        dialogBinding.btnGetKey.setOnClickListener {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))
            startActivity(browserIntent)
        }

        dialogBinding.btnTestGemini.setOnClickListener {
            val key = dialogBinding.etGeminiKey.text.toString().trim()
            val selectedModel = dialogBinding.spGeminiModel.selectedItem?.toString() ?: ServerConfig.DEFAULT_GEMINI_MODEL

            dialogBinding.tvTestResult.visibility = View.VISIBLE
            dialogBinding.tvTestResult.text = "Testing Google Gemini API..."
            dialogBinding.tvTestResult.setTextColor(ContextCompat.getColor(this, R.color.jarvis_primary))

            lifecycleScope.launch {
                val testRes = NetworkUtils.testGeminiApiKey(key, selectedModel)
                if (testRes.isReachable) {
                    dialogBinding.tvTestResult.text = "✓ Gemini API Active ($selectedModel, ${testRes.latencyMs}ms)"
                    dialogBinding.tvTestResult.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.jarvis_accent))
                    // Also refresh models
                    fetchAndPopulateModels(key)
                } else {
                    dialogBinding.tvTestResult.text = "✗ Failed: ${testRes.errorMessage ?: "Invalid API Key"}"
                    dialogBinding.tvTestResult.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.jarvis_danger))
                }
            }
        }

        // Server Settings
        if (config.protocol == "https") {
            dialogBinding.rbHttps.isChecked = true
        } else {
            dialogBinding.rbHttp.isChecked = true
        }
        dialogBinding.etHost.setText(config.host)
        dialogBinding.etPort.setText(config.port.toString())

        dialogBinding.btnPresetLocal.setOnClickListener {
            dialogBinding.etHost.setText("127.0.0.1")
            dialogBinding.etPort.setText("8000")
        }
        dialogBinding.btnPresetLan.setOnClickListener {
            dialogBinding.etHost.setText(ServerConfig.DEFAULT_HOST)
            dialogBinding.etPort.setText("8000")
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnTestConnection.setOnClickListener {
            val proto = if (dialogBinding.rbHttps.isChecked) "https" else "http"
            val host = dialogBinding.etHost.text.toString().trim()
            val port = dialogBinding.etPort.text.toString().toIntOrNull() ?: 8000
            val testUrl = "$proto://$host:$port"

            dialogBinding.tvTestResult.visibility = View.VISIBLE
            dialogBinding.tvTestResult.text = "Pinging $testUrl..."
            dialogBinding.tvTestResult.setTextColor(ContextCompat.getColor(this, R.color.jarvis_primary))

            lifecycleScope.launch {
                val testResult = NetworkUtils.pingServer(testUrl)
                if (testResult.isReachable) {
                    dialogBinding.tvTestResult.text = "✓ Online (${testResult.latencyMs}ms)"
                    dialogBinding.tvTestResult.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.jarvis_accent))
                } else {
                    dialogBinding.tvTestResult.text = "✗ Unreachable: ${testResult.errorMessage ?: "Timeout"}"
                    dialogBinding.tvTestResult.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.jarvis_danger))
                }
            }
        }

        dialogBinding.btnDialogCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnDialogSave.setOnClickListener {
            if (dialogBinding.rbModeGemini.isChecked) {
                config.appMode = ServerConfig.MODE_GEMINI
                val key = dialogBinding.etGeminiKey.text.toString().trim()
                val selectedModel = dialogBinding.spGeminiModel.selectedItem?.toString() ?: ServerConfig.DEFAULT_GEMINI_MODEL
                config.saveGemini(key, selectedModel, dialogBinding.switchVoice.isChecked)
            } else {
                config.appMode = ServerConfig.MODE_LOCAL_SERVER
                val proto = if (dialogBinding.rbHttps.isChecked) "https" else "http"
                val host = dialogBinding.etHost.text.toString().trim()
                val port = dialogBinding.etPort.text.toString().toIntOrNull() ?: 8000
                config.saveServer(proto, host, port)
            }

            dialog.dismiss()
            checkServerAndLoad()
        }

        dialog.show()
    }

    inner class AndroidBridge {
        @JavascriptInterface
        fun getApiKey(): String = config.geminiApiKey

        @JavascriptInterface
        fun getModel(): String = config.geminiModel

        @JavascriptInterface
        fun setModel(model: String) {
            if (model.isNotBlank()) {
                config.geminiModel = model
            }
        }

        @JavascriptInterface
        fun getCachedModels(): String {
            val arr = JSONArray(config.cachedModels)
            return arr.toString()
        }

        @JavascriptInterface
        fun isVoiceEnabled(): Boolean = config.isVoiceTtsEnabled

        @JavascriptInterface
        fun setVoiceEnabled(enabled: Boolean) {
            config.isVoiceTtsEnabled = enabled
        }

        @JavascriptInterface
        fun openSettings() {
            runOnUiThread {
                showServerConfigDialog()
            }
        }

        @JavascriptInterface
        fun startVoiceInput() {
            runOnUiThread {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Jarvis is listening...")
                }
                try {
                    voiceInputLauncher.launch(intent)
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Voice input not supported on this device", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun speak(text: String) {
            if (!config.isVoiceTtsEnabled || !isTtsReady || text.isBlank()) return
            runOnUiThread {
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_reply")
            }
        }

        @JavascriptInterface
        fun retry() {
            runOnUiThread {
                checkServerAndLoad()
            }
        }

        @JavascriptInterface
        fun showToast(msg: String) {
            runOnUiThread {
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    enum class ConnectionStatus {
        CONNECTED, OFFLINE, CONNECTING
    }
}
