package com.mqttreceiver.app

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.mqttreceiver.app.databinding.ActivityDashboardBinding

class DataDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding

    // 根据字段名分配不同的颜色主题
    private val colorThemes = listOf(
        intArrayOf("#1A73E8", "#E8F0FE"), // 蓝色
        intArrayOf("#34A853", "#E6F4EA"), // 绿色
        intArrayOf("#FBBC04", "#FEF7E0"), // 黄色
        intArrayOf("#EA4335", "#FCE8E6"), // 红色
        intArrayOf("#9334E6", "#F3E8FD"), // 紫色
        intArrayOf("#FF6D01", "#FEE8D6"), // 橙色
        intArrayOf("#00ACC1", "#E0F7FA"), // 青色
        intArrayOf("#E91E63", "#FCE4EC"), // 粉色
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()

        MqttDataHolder.onDataChanged = { dataMap ->
            runOnUiThread { updateDashboard(dataMap) }
        }

        if (MqttDataHolder.parsedData.isNotEmpty()) {
            updateDashboard(MqttDataHolder.parsedData)
            updateConnectionInfo()
        }
    }

    private fun setupUI() {
        binding.btnDisconnect.setOnClickListener { disconnectAndExit() }
        binding.btnClearData.setOnClickListener {
            MqttDataHolder.reset()
            binding.dataCardContainer.removeAllViews()
            binding.emptyStateCard.visibility = View.VISIBLE
            binding.tvTopic.text = "主题: --"
            binding.tvUpdateTime.text = ""
            binding.tvValueCount.text = "0 个字段"
        }

        binding.cardViewJson.setOnClickListener {
            val intent = Intent(this, RawJsonActivity::class.java)
            startActivity(intent)
        }
    }

    private fun updateDashboard(data: Map<String, Any>) {
        binding.tvTopic.text = "主题: ${MqttDataHolder.topic}"
        binding.tvUpdateTime.text = "更新: ${MqttDataHolder.lastUpdateTime}"
        binding.tvValueCount.text = "${data.size} 个字段"
        binding.emptyStateCard.visibility = View.GONE

        val visibleCount = data.count { !it.key.startsWith("_") }
        binding.dataCardContainer.removeAllViews()

        var themeIndex = 0
        for ((key, value) in data) {
            if (key.startsWith("_")) continue
            addBeautifiedCard(key, value, colorThemes[themeIndex % colorThemes.size])
            themeIndex++
        }

        if (visibleCount == 0) {
            binding.emptyStateCard.visibility = View.VISIBLE
        }
    }

    private fun addBeautifiedCard(key: String, value: Any, theme: IntArray) {
        val accentColor = Color.parseColor(theme[0])
        val bgColor = Color.parseColor(theme[1])

        val card = layoutInflater.inflate(R.layout.item_data_card, binding.dataCardContainer, false) as com.google.android.material.card.MaterialCardView

        val tvKey = card.findViewById<TextView>(R.id.tv_key)
        val tvValue = card.findViewById<TextView>(R.id.tv_value)
        val tvUnit = card.findViewById<TextView>(R.id.tv_unit)
        val keyDot = card.findViewById<View>(R.id.key_dot)

        keyDot?.setBackgroundColor(accentColor)

        tvKey.text = key

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
            tvUnit.visibility = if (unit.isNotEmpty()) View.VISIBLE else View.GONE

            val alertColor = getAlertColor(key, numValue)
            if (alertColor != null) {
                tvValue.setTextColor(alertColor)
                card.setCardBackgroundColor(Color.parseColor("#FFF5F5"))
            } else {
                tvValue.setTextColor(accentColor)
                card.setCardBackgroundColor(Color.parseColor("#FFFFFF"))
            }
        } else {
            tvValue.text = value.toString()
            tvUnit.visibility = View.GONE
            tvValue.setTextColor(Color.parseColor("#5F6368"))
            card.setCardBackgroundColor(Color.parseColor("#FFFFFF"))
        }

        binding.dataCardContainer.addView(card)
    }

    private fun guessUnit(key: String, value: Double): String {
        val k = key.lowercase()
        return when {
            k.contains("temp") || k.contains("temperature") -> "°C"
            k.contains("volt") || k.endsWith("_v") || k.contains("voltage") -> "V"
            k.contains("current") || k.endsWith("_a") || k.contains("amp") -> "A"
            k.contains("power") || k.endsWith("_w") || k.contains("watt") -> "W"
            k.contains("press") || k.endsWith("_p") || k.contains("pressure") -> "MPa"
            k.contains("level") || k.contains("percent") -> "%"
            k.contains("humid") || k.contains("humi") -> "%"
            k.contains("cond") || k.contains("conduct") -> "μS/cm"
            k.contains("ph") -> "pH"
            k.contains("freq") || k.contains("hz") -> "Hz"
            k.contains("flow") -> "m³/h"
            k.contains("speed") || k.contains("rpm") -> "r/min"
            k.matches(Regex("^ch\\d+")) -> "V"
            k.contains("energy") || k.contains("kwh") -> "kWh"
            k.contains("h2") || k.contains("hydrogen") -> "ppm"
            k.contains("o2") || k.contains("oxygen") -> "%"
            k.contains("co2") -> "ppm"
            else -> ""
        }
    }

    private fun getAlertColor(key: String, value: Double): Int? {
        val k = key.lowercase()
        val red = Color.parseColor("#D93025")
        val orange = Color.parseColor("#E37400")
        return when {
            k.contains("temp") && value > 80 -> red
            k.contains("temp") && value > 60 -> orange
            (k.contains("volt") || k.matches(Regex("^ch\\d+"))) && value > 400 -> red
            (k.contains("volt") || k.matches(Regex("^ch\\d+"))) && value > 250 -> orange
            k.contains("press") && value > 1.5 -> red
            k.contains("press") && value > 1.0 -> orange
            k.contains("level") && value > 95 -> orange
            k.contains("level") && value < 5 -> red
            else -> null
        }
    }

    private fun updateConnectionInfo() {
        binding.tvConnectionInfo.text = "已连接: ${SecurePrefs.getBroker()}"
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
