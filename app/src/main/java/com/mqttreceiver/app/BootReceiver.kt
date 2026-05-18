package com.mqttreceiver.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * 开机自启动广播接收器
 * 使用 SecurePrefs 读取加密存储的凭据
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            SecurePrefs.init(context)

            if (!SecurePrefs.getAutoConnect()) return

            val brokerUrl = "${SecurePrefs.getBroker()}:${SecurePrefs.getPort()}"
            val topic = SecurePrefs.getTopic()
            val username = SecurePrefs.getUsername()
            val password = SecurePrefs.getPassword()

            if (brokerUrl.isBlank() || brokerUrl == ":") return

            val serviceIntent = Intent(context, MqttForegroundService::class.java).apply {
                putExtra("broker_url", brokerUrl)
                putExtra("topic", topic)
                putExtra("client_id", "Android_${(1000..9999).random()}")
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
