package com.mqttreceiver.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.mqttreceiver.app.databinding.ActivityRawJsonBinding

/**
 * 原始 JSON 数据查看页面
 * 从仪表盘进入，展示完整的 JSON 原始数据
 */
class RawJsonActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRawJsonBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRawJsonBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "原始 JSON 数据"

        setupUI()
        refreshData()
    }

    private fun setupUI() {
        binding.btnCopy.setOnClickListener {
            val text = binding.tvJsonRaw.text.toString()
            if (text.isNotEmpty()) {
                val clipboard = getSystemService(ClipboardManager::class.java)
                val clip = ClipData.newPlainText("MQTT Raw JSON", text)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnRefresh.setOnClickListener {
            refreshData()
        }

        binding.btnShare.setOnClickListener {
            val text = binding.tvJsonRaw.text.toString()
            if (text.isNotEmpty()) {
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, text)
                    type = "text/plain"
                }
                startActivity(Intent.createChooser(sendIntent, "分享 JSON 数据"))
            }
        }
    }

    private fun refreshData() {
        val rawJson = MqttDataHolder.rawJson
        if (rawJson.isNotEmpty()) {
            binding.tvJsonRaw.text = rawJson
            binding.tvUpdateTime.text = "更新于: ${MqttDataHolder.lastUpdateTime}"
            binding.tvTopic.text = "主题: ${MqttDataHolder.topic}"
            binding.tvStatus.text = "${MqttDataHolder.parsedData.size} 个字段"
        } else {
            binding.tvJsonRaw.text = "暂无数据\n\n连接 MQTT 后数据将自动显示在这里"
            binding.tvUpdateTime.text = ""
            binding.tvTopic.text = ""
            binding.tvStatus.text = "等待数据..."
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onResume() {
        super.onResume()
        refreshData()
    }
}
