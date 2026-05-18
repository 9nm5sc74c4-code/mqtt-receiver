package com.mqttreceiver.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * 开机自启动广播接收器
 * 设备重启后自动恢复 MQTT 连接
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("mqtt_settings", Context.MODE_PRIVATE)

            if (!prefs.getBoolean("auto_connect", true)) return

            val brokerUrl = "${prefs.getString("broker_url", "ie1d91ad.ala.cn-hangzhou.emqxsl.cn")}:${prefs.getString("broker_port", "8883")}"
            val topic = prefs.getString("topic", "ch6pem2") ?: "ch6pem2"
            val clientId = prefs.getString("client_id", "Android_${(1000..9999).random()}") ?: ""
            val username = prefs.getString("username", "")
            val password = prefs.getString("password", "")

            val serviceIntent = Intent(context, MqttForegroundService::class.java).apply {
                putExtra("broker_url", brokerUrl)
                putExtra("topic", topic)
                putExtra("client_id", clientId)
                putExtra("username", username)
                putExtra("password", password)
                action = "CONNECT"
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
