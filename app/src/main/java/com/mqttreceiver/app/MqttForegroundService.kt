package com.mqttreceiver.app

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * MQTT 前台服务
 * 后台保持 MQTT 连接，数据通过 MqttDataHolder 推送到仪表盘
 */
class MqttForegroundService : Service() {

    companion object {
        private const val TAG = "MqttForegroundService"
        private const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "mqtt_receiver_channel"

        @Volatile
        var isRunning = false
            private set

        /** 连接成功回调（通知 MainActivity 跳转到仪表盘） */
        @Volatile
        var onConnectSuccess: (() -> Unit)? = null

        /** 连接失败回调 */
        @Volatile
        var onConnectError: ((String) -> Unit)? = null
    }

    private val mqttManager = MqttManager()

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(this, CHANNEL_ID)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "CONNECT" -> {
                val brokerUrl = intent.getStringExtra("broker_url") ?: return START_NOT_STICKY
                val topic = intent.getStringExtra("topic") ?: "ch6pem2"
                val clientId = intent.getStringExtra("client_id") ?: "Android_${(1000..9999).random()}"
                val username = intent.getStringExtra("username")
                val password = intent.getStringExtra("password")

                startForeground(NOTIFICATION_ID, buildNotification("正在连接..."))
                connectMqtt(brokerUrl, clientId, username, password, topic)
            }

            "DISCONNECT" -> {
                disconnectMqtt()
            }
        }
        return START_STICKY
    }

    private fun connectMqtt(brokerUrl: String, clientId: String, username: String?, password: String?, topic: String) {
        Thread {
            mqttManager.connect(
                brokerUrl = brokerUrl,
                clientId = clientId,
                username = username,
                password = password,
                topic = topic,
                onConnected = {
                    isRunning = true
                    updateNotification("已连接", "订阅: $topic")
                    logToUI("✅ 已连接到 $brokerUrl")
                    logToUI("✅ 已订阅: $topic")

                    // 通知 MainActivity 连接成功 -> 跳转仪表盘
                    onConnectSuccess?.invoke()
                },
                onDisconnected = { cause ->
                    isRunning = false
                    val reason = cause?.message ?: "未知原因"
                    updateNotification("已断开", reason)
                    logToUI("⚠️ 连接断开: $reason (自动重连中...)")
                },
                onMessage = { msgTopic, payload ->
                    // 解析数据并推送到仪表盘
                    MqttDataHolder.update(msgTopic, payload)
                    // 更新通知
                    val firstField = MqttDataHolder.parsedData.entries.firstOrNull()
                    val summary = if (firstField != null) "${firstField.key}=${firstField.value}" else payload.take(80)
                    updateNotification("📩 $msgTopic", summary)
                },
                onError = { error ->
                    isRunning = false
                    updateNotification("连接失败", error)
                    logToUI("❌ $error")
                    onConnectError?.invoke(error)
                    stopSelf()
                }
            )
        }.start()
    }

    private fun disconnectMqtt() {
        Thread {
            mqttManager.disconnect()
            isRunning = false
            MqttDataHolder.reset()
            logToUI("🔌 已断开连接")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }.start()
    }

    private fun updateNotification(title: String, content: String) {
        val notification = buildNotification(content, title)
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(content: String, title: String = "MQTT Receiver"): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun logToUI(message: String) {
        Log.d(TAG, message)
        MainActivity.logCallback?.invoke(message)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        Thread { mqttManager.disconnect() }.start()
        super.onDestroy()
    }
}
