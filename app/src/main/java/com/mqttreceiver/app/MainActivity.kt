package com.mqttreceiver.app

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.mqttreceiver.app.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private var autoSaveEnabled = true  // 加载时短暂关闭，避免覆盖

    companion object {
        private const val PREFS_NAME = "mqtt_settings"
        private const val KEY_BROKER = "broker_url"
        private const val KEY_PORT = "broker_port"
        private const val KEY_TOPIC = "topic"
        private const val KEY_CLIENT_ID = "client_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_AUTO_CONNECT = "auto_connect"
        private const val NOTIFICATION_PERMISSION = 1001

        // 默认值
        private const val DEFAULT_BROKER = "ie1d91ad.ala.cn-hangzhou.emqxsl.cn"
        private const val DEFAULT_PORT = "8883"
        private const val DEFAULT_TOPIC = "ch6pem2"

        var logCallback: ((String) -> Unit)? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        logCallback = { msg -> appendLog(msg) }

        autoSaveEnabled = false
        loadSettings()
        autoSaveEnabled = true

        setupUI()
        requestNotificationPermission()
    }

    private fun setupUI() {
        binding.btnConnect.setOnClickListener {
            if (MqttForegroundService.isRunning) {
                disconnectMqtt()
            } else {
                connectMqtt()
            }
        }

        binding.btnClearLog.setOnClickListener {
            binding.tvLog.text = ""
        }

        // 输入框改动即自动保存
        setupAutoSave()

        binding.etTopic.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && binding.etTopic.text.isNullOrBlank()) {
                binding.etTopic.setText(DEFAULT_TOPIC)
            }
        }

        binding.tvLog.movementMethod = ScrollingMovementMethod()
    }

    /** 所有输入框失焦时自动保存 */
    private fun setupAutoSave() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (autoSaveEnabled) saveSettings()
            }
        }

        binding.etBroker.addTextChangedListener(watcher)
        binding.etPort.addTextChangedListener(watcher)
        binding.etTopic.addTextChangedListener(watcher)
        binding.etClientId.addTextChangedListener(watcher)
        binding.etUsername.addTextChangedListener(watcher)
        binding.etPassword.addTextChangedListener(watcher)

        binding.swAutoConnect.setOnCheckedChangeListener { _, _ ->
            if (autoSaveEnabled) saveSettings()
        }
    }

    private fun loadSettings() {
        binding.etBroker.setText(prefs.getString(KEY_BROKER, DEFAULT_BROKER))
        binding.etPort.setText(prefs.getString(KEY_PORT, DEFAULT_PORT))
        binding.etTopic.setText(prefs.getString(KEY_TOPIC, DEFAULT_TOPIC))
        binding.etClientId.setText(prefs.getString(KEY_CLIENT_ID, "Android_${(1000..9999).random()}"))
        binding.etUsername.setText(prefs.getString(KEY_USERNAME, ""))
        binding.etPassword.setText(prefs.getString(KEY_PASSWORD, ""))
        binding.swAutoConnect.isChecked = prefs.getBoolean(KEY_AUTO_CONNECT, true)
    }

    private fun saveSettings() {
        prefs.edit().apply {
            putString(KEY_BROKER, binding.etBroker.text.toString().trim())
            putString(KEY_PORT, binding.etPort.text.toString().trim())
            putString(KEY_TOPIC, binding.etTopic.text.toString().trim().ifEmpty { DEFAULT_TOPIC })
            putString(KEY_CLIENT_ID, binding.etClientId.text.toString().trim())
            putString(KEY_USERNAME, binding.etUsername.text.toString().trim())
            putString(KEY_PASSWORD, binding.etPassword.text.toString().trim())
            putBoolean(KEY_AUTO_CONNECT, binding.swAutoConnect.isChecked)
            apply()
        }
    }

    private fun connectMqtt() {
        val broker = binding.etBroker.text.toString().trim()
        val port = binding.etPort.text.toString().trim()
        val topic = binding.etTopic.text.toString().trim().ifEmpty { DEFAULT_TOPIC }
        val clientId = binding.etClientId.text.toString().trim().ifEmpty { "Android_${(1000..9999).random()}" }
        val username = binding.etUsername.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        saveSettings()

        val intent = Intent(this, MqttForegroundService::class.java).apply {
            putExtra("broker_url", "$broker:$port")
            putExtra("topic", topic)
            putExtra("client_id", clientId)
            putExtra("username", username)
            putExtra("password", password)
            action = "CONNECT"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        appendLog("正在连接 $broker:$port (${if (port == "8883") "SSL" else "TCP"}) ...")
        updateConnectionUI(true)
    }

    private fun disconnectMqtt() {
        val intent = Intent(this, MqttForegroundService::class.java).apply {
            action = "DISCONNECT"
        }
        startService(intent)
        appendLog("正在断开连接...")
        updateConnectionUI(false)
    }

    private fun updateConnectionUI(connected: Boolean) {
        if (connected) {
            binding.btnConnect.text = "断开连接"
            binding.btnConnect.setBackgroundColor(getColor(android.R.color.holo_red_dark))
            binding.tvStatus.text = "● 连接中..."
            binding.tvStatus.setTextColor(getColor(android.R.color.holo_orange_dark))
            binding.cardSettings.visibility = View.GONE
        } else {
            binding.btnConnect.text = "连接"
            binding.btnConnect.setBackgroundColor(getColor(android.R.color.holo_green_dark))
            binding.tvStatus.text = "● 未连接"
            binding.tvStatus.setTextColor(getColor(android.R.color.darker_gray))
            binding.cardSettings.visibility = View.VISIBLE
        }
    }

    private fun appendLog(message: String) {
        runOnUiThread {
            val timestamp = dateFormat.format(Date())
            val newLine = "[$timestamp] $message\n"
            binding.tvLog.append(newLine)

            binding.tvLog.post {
                val layout = binding.tvLog.layout ?: return@post
                val scrollAmount = layout.getLineTop(binding.tvLog.lineCount)
                if (scrollAmount > binding.tvLog.height) {
                    binding.tvLog.scrollTo(0, scrollAmount - binding.tvLog.height)
                }
            }

            // 限制日志最多 1000 行
            val text = binding.tvLog.text.toString()
            val lines = text.split("\n")
            if (lines.size > 1000) {
                binding.tvLog.text = lines.takeLast(1000).joinToString("\n")
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateConnectionUI(MqttForegroundService.isRunning)
    }

    override fun onDestroy() {
        super.onDestroy()
        logCallback = null
    }
}
