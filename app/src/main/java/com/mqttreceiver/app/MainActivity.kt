package com.mqttreceiver.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.mqttreceiver.app.databinding.ActivityMainBinding

/**
 * 登录/连接界面
 * 第一级界面：用户在此输入 MQTT 连接信息，连接成功后才进入仪表盘
 */
class MainActivity : AppCompatActivity() {

    companion object {
        /** 日志回调（供 MqttForegroundService 使用） */
        var logCallback: ((String) -> Unit)? = null
    }

    private lateinit var binding: ActivityMainBinding
    private var isPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 初始化加密存储
        SecurePrefs.init(this)

        setupUI()
        loadSavedConfig()
        requestNotificationPermission()
    }

    private fun setupUI() {
        // 密码可见性切换
        binding.ivTogglePassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            binding.etPassword.transformationMethod = if (isPasswordVisible) {
                binding.ivTogglePassword.setImageResource(android.R.drawable.ic_menu_view)
                HideReturnsTransformationMethod.getInstance()
            } else {
                binding.ivTogglePassword.setImageResource(android.R.drawable.ic_menu_info_details)
                PasswordTransformationMethod.getInstance()
            }
            // 光标移到末尾
            binding.etPassword.setSelection(binding.etPassword.text?.length ?: 0)
        }

        // 连接按钮
        binding.btnConnect.setOnClickListener { onConnectClick() }

        // 清除凭据
        binding.btnClearConfig.setOnClickListener {
            SecurePrefs.clear()
            binding.etBroker.setText("")
            binding.etPort.setText("8883")
            binding.etUsername.setText("")
            binding.etPassword.setText("")
            binding.etTopic.setText("ch6pem2")
            Toast.makeText(this, "已清除保存的凭据", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadSavedConfig() {
        if (SecurePrefs.hasCredentials()) {
            binding.etBroker.setText(SecurePrefs.getBroker())
            binding.etPort.setText(SecurePrefs.getPort())
            binding.etUsername.setText(SecurePrefs.getUsername())
            binding.etPassword.setText(SecurePrefs.getPassword())
            binding.etTopic.setText(SecurePrefs.getTopic())
            binding.swAutoConnect.isChecked = SecurePrefs.getAutoConnect()

            val lastTime = SecurePrefs.getLastConnected()
            if (lastTime > 0) {
                val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(lastTime))
                binding.tvLastConnect.text = "上次连接: $date"
                binding.tvLastConnect.visibility = View.VISIBLE
            }
        }

        // 如果开启了自动连接且有已保存的凭据，自动连接
        if (SecurePrefs.getAutoConnect() && SecurePrefs.hasCredentials()) {
            binding.btnConnect.postDelayed({
                if (!MqttForegroundService.isRunning) {
                    onConnectClick()
                }
            }, 500)
        }
    }

    private fun onConnectClick() {
        val broker = binding.etBroker.text.toString().trim()
        val port = binding.etPort.text.toString().trim()
        val username = binding.etUsername.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val topic = binding.etTopic.text.toString().trim().ifEmpty { "ch6pem2" }
        val autoConnect = binding.swAutoConnect.isChecked

        // 基本校验
        if (broker.isBlank()) {
            binding.etBroker.error = "请输入 Broker 地址"
            return
        }
        if (port.isBlank()) {
            binding.etPort.error = "请输入端口"
            return
        }

        // 加密保存凭据
        SecurePrefs.save(broker, port, username, password, topic, autoConnect)

        // 设置连接回调（先清空再设置，避免残留）
        MqttForegroundService.onConnectSuccess = null
        MqttForegroundService.onConnectError = null
        MqttForegroundService.onConnectSuccess = {
            runOnUiThread {
                setConnectingState(false)
                MqttForegroundService.onConnectSuccess = null
                MqttForegroundService.onConnectError = null
                val intent = Intent(this, DataDashboardActivity::class.java)
                startActivity(intent)
            }
        }
        MqttForegroundService.onConnectError = { error ->
            runOnUiThread {
                setConnectingState(false)
                binding.tvStatus.text = "❌ 连接失败: $error"
                binding.tvStatus.setTextColor(getColor(android.R.color.holo_red_dark))
                Toast.makeText(this@MainActivity, "连接失败: $error", Toast.LENGTH_LONG).show()
            }
        }

        // 启动前台服务连接 MQTT
        val intent = Intent(this, MqttForegroundService::class.java).apply {
            putExtra("broker_url", "$broker:$port")
            putExtra("topic", topic)
            putExtra("client_id", "Android_${(1000..9999).random()}")
            putExtra("username", username)
            putExtra("password", password)
            action = "CONNECT"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        setConnectingState(true)
    }

    private fun setConnectingState(connecting: Boolean) {
        if (connecting) {
            binding.btnConnect.text = "连接中..."
            binding.btnConnect.isEnabled = false
            binding.progressBar.visibility = View.VISIBLE
            binding.tvStatus.text = "正在连接..."
            binding.tvStatus.setTextColor(getColor(android.R.color.holo_orange_dark))
            disableInputs(true)
        } else {
            binding.btnConnect.text = "🔌 连接 MQTT Broker"
            binding.btnConnect.isEnabled = true
            binding.progressBar.visibility = View.GONE
            binding.tvStatus.text = "输入连接信息开始"
            binding.tvStatus.setTextColor(getColor(android.R.color.darker_gray))
            disableInputs(false)
        }
    }

    private fun disableInputs(disabled: Boolean) {
        binding.etBroker.isEnabled = !disabled
        binding.etPort.isEnabled = !disabled
        binding.etUsername.isEnabled = !disabled
        binding.etPassword.isEnabled = !disabled
        binding.etTopic.isEnabled = !disabled
        binding.swAutoConnect.isEnabled = !disabled
        binding.btnClearConfig.isEnabled = !disabled
    }

    /**
     * 连接成功回调（由 MqttForegroundService 调用）
     * 跳转到仪表盘
     */
    fun onConnectionSuccess() {
        runOnUiThread {
            setConnectingState(false)
            val intent = Intent(this, DataDashboardActivity::class.java)
            startActivity(intent)
        }
    }

    /**
     * 连接失败回调
     */
    fun onConnectionError(error: String) {
        runOnUiThread {
            setConnectingState(false)
            binding.tvStatus.text = "❌ 连接失败: $error"
            binding.tvStatus.setTextColor(getColor(android.R.color.holo_red_dark))
            Toast.makeText(this, "连接失败: $error", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        // 从仪表盘返回时重置状态
        if (!MqttForegroundService.isRunning) {
            setConnectingState(false)
            binding.tvStatus.text = "已断开，可重新连接"
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                registerForActivityResult(ActivityResultContracts.RequestPermission()) { }.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
