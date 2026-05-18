package com.mqttreceiver.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.mqttreceiver.app.databinding.ActivityDashboardBinding

/**
 * 数据仪表盘 Activity
 * 第二级界面：连接成功后显示实时 MQTT 数据（参照 Web 版 mqtt_dashboard）
 */
class DataDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()

        // 注册数据更新回调
        MqttDataHolder.onDataChanged = { dataMap ->
            runOnUiThread { updateDashboard(dataMap) }
        }

        // 如果已经有数据，立即显示
        if (MqttDataHolder.parsedData.isNotEmpty()) {
            updateDashboard(MqttDataHolder.parsedData)
            updateConnectionInfo()
        }
    }

    private fun setupUI() {
        binding.btnDisconnect.setOnClickListener { disconnectAndExit() }

        binding.btnToggleJson.setOnClickListener {
            val visible = binding.jsonRawLayout.isVisible
            binding.jsonRawLayout.visibility = if (visible) View.GONE else View.VISIBLE
            binding.btnToggleJson.text = if (visible) "▼ 展开原始 JSON" else "▲ 收起原始 JSON"
        }

        binding.btnClearData.setOnClickListener {
            MqttDataHolder.reset()
            binding.dataCardContainer.removeAllViews()
            binding.tvJsonRaw.text = ""
            binding.tvTopic.text = ""
            binding.tvUpdateTime.text = ""
            binding.tvValueCount.text = "0"
        }
    }

    private fun updateDashboard(data: Map<String, Any>) {
        binding.tvTopic.text = "主题: ${MqttDataHolder.topic}"
        binding.tvUpdateTime.text = "更新: ${MqttDataHolder.lastUpdateTime}"
        binding.tvValueCount.text = "${data.size} 个字段"

        binding.tvJsonRaw.text = MqttDataHolder.rawJson

        // 动态生成数据卡片
        binding.dataCardContainer.removeAllViews()
        for ((key, value) in data) {
            if (key.startsWith("_")) continue  // 跳过元数据
            addDataCard(key, value)
        }
    }

    private fun addDataCard(key: String, value: Any) {
        val card = layoutInflater.inflate(R.layout.item_data_card, binding.dataCardContainer, false)

        val tvKey = card.findViewById<TextView>(R.id.tv_key)
        val tvValue = card.findViewById<TextView>(R.id.tv_value)
        val tvUnit = card.findViewById<TextView>(R.id.tv_unit)

        tvKey.text = key

        // 尝试提取数值
        val numValue = when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        }

        if (numValue != null) {
            tvValue.text = if (numValue == numValue.toLong().toDouble()) {
                numValue.toLong().toString()
            } else {
                String.format("%.2f", numValue)
            }

            val unit = guessUnit(key, numValue)
            tvUnit.text = unit
            tvUnit.isVisible = unit.isNotEmpty()

            tvValue.setTextColor(getThresholdColor(key, numValue))
        } else {
            tvValue.text = value.toString()
            tvUnit.isVisible = false
            tvValue.setTextColor(Color.parseColor("#555555"))
        }

        binding.dataCardContainer.addView(card)
    }

    private fun guessUnit(key: String, value: Double): String {
        val k = key.lowercase()
        return when {
            k.contains("temp") || k.contains("temperature") -> "°C"
            k.contains("volt") || k.endsWith("_v") -> "V"
            k.contains("current") || k.endsWith("_a") || k.contains("amp") -> "A"
            k.contains("power") -> "W"
            k.contains("press") -> "MPa"
            k.contains("level") -> "%"
            k.contains("humid") -> "%"
            k.contains("cond") -> "μS/cm"
            k.contains("ph") -> "pH"
            k.contains("freq") -> "Hz"
            k.contains("flow") -> "m³/h"
            k.contains("speed") -> "r/min"
            k.matches(Regex("^ch\\d+")) -> "V"
            else -> ""
        }
    }

    private fun getThresholdColor(key: String, value: Double): Int {
        val k = key.lowercase()
        val orange = Color.parseColor("#FF8C00")
        return when {
            k.contains("temp") && value > 80 -> Color.RED
            k.contains("temp") && value > 60 -> orange
            (k.contains("volt") || k.matches(Regex("^ch\\d+"))) && value > 400 -> Color.RED
            (k.contains("volt") || k.matches(Regex("^ch\\d+"))) && value > 250 -> orange
            k.contains("press") && value > 1.5 -> Color.RED
            k.contains("press") && value > 1.0 -> orange
            else -> Color.parseColor("#1B5E20")
        }
    }

    private fun updateConnectionInfo() {
        binding.tvConnectionInfo.text = "已连接: ${SecurePrefs.getBroker()}:${SecurePrefs.getPort()}"
    }

    private fun disconnectAndExit() {
        startService(Intent(this, MqttForegroundService::class.java).apply { action = "DISCONNECT" })
        Toast.makeText(this, "已断开连接", Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        MqttDataHolder.onDataChanged = null
    }
}
