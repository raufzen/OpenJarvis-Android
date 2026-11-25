package com.raufzen.openjarvis

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.webkit.*
import android.widget.RadioButton
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.raufzen.openjarvis.databinding.ActivityMainBinding
import com.raufzen.openjarvis.databinding.DialogServerConfigBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var config: ServerConfig
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private var isConnected = false
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

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle permissions
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        config = ServerConfig(this)

        setupTopBar()
        setupWebView()
        setupSwipeRefresh()
        setupBackNavigation()
        requestAppPermissions()

        checkServerAndLoad()
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

                if (url?.startsWith("file:///android_asset/") == true) {
                    updateStatus(ConnectionStatus.OFFLINE)
                    binding.webView.evaluateJavascript(
                        "setServerUrl('${config.baseUrl}');",
                        null
                    )
                } else {
                    updateStatus(ConnectionStatus.CONNECTED)
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
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
        updateStatus(ConnectionStatus.CONNECTING)
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val result = NetworkUtils.pingServer(config.baseUrl)
            if (result.isReachable) {
                updateStatus(ConnectionStatus.CONNECTED)
                binding.webView.loadUrl(config.baseUrl)
            } else {
                updateStatus(ConnectionStatus.OFFLINE)
                loadOfflineFallback()
            }
        }
    }

    private fun loadOfflineFallback() {
        binding.webView.loadUrl("file:///android_asset/offline_fallback.html")
    }

    private fun updateStatus(status: ConnectionStatus) {
        when (status) {
            ConnectionStatus.CONNECTED -> {
                binding.viewStatusDot.setBackgroundResource(R.drawable.dot_connected)
                binding.tvStatusText.text = getString(R.string.status_connected)
                binding.tvStatusText.setTextColor(ContextCompat.getColor(this, R.color.jarvis_accent))
                isConnected = true
            }
            ConnectionStatus.OFFLINE -> {
                binding.viewStatusDot.setBackgroundResource(R.drawable.dot_offline)
                binding.tvStatusText.text = getString(R.string.status_offline)
                binding.tvStatusText.setTextColor(ContextCompat.getColor(this, R.color.jarvis_danger))
                isConnected = false
            }
            ConnectionStatus.CONNECTING -> {
                binding.viewStatusDot.setBackgroundResource(R.drawable.dot_connecting)
                binding.tvStatusText.text = getString(R.string.status_connecting)
                binding.tvStatusText.setTextColor(ContextCompat.getColor(this, R.color.jarvis_primary))
            }
        }
    }

    private fun showServerConfigDialog() {
        val dialogBinding = DialogServerConfigBinding.inflate(LayoutInflater.from(this))

        if (config.protocol == "https") {
            dialogBinding.rbHttps.isChecked = true
        } else {
            dialogBinding.rbHttp.isChecked = true
        }

        dialogBinding.etHost.setText(config.host)
        dialogBinding.etPort.setText(config.port.toString())

        // Quick presets
        dialogBinding.btnPresetLocal.setOnClickListener {
            dialogBinding.etHost.setText("127.0.0.1")
            dialogBinding.etPort.setText("8000")
        }
        dialogBinding.btnPresetEmulator.setOnClickListener {
            dialogBinding.etHost.setText("10.0.2.2")
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
            val proto = if (dialogBinding.rbHttps.isChecked) "https" else "http"
            val host = dialogBinding.etHost.text.toString().trim()
            val port = dialogBinding.etPort.text.toString().toIntOrNull() ?: 8000

            config.save(proto, host, port)
            dialog.dismiss()
            checkServerAndLoad()
        }

        dialog.show()
    }

    inner class AndroidBridge {
        @JavascriptInterface
        fun retry() {
            runOnUiThread {
                checkServerAndLoad()
            }
        }

        @JavascriptInterface
        fun openSettings() {
            runOnUiThread {
                showServerConfigDialog()
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
