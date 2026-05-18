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
 * MQTT 鍓嶅彴鏈嶅姟
 * 鍦ㄥ悗鍙颁繚鎸?MQTT 杩炴帴锛屽嵆浣?APP 閫€鍑轰篃鑳芥寔缁帴鏀舵秷鎭? */
class MqttForegroundService : Service() {

    companion object {
        private const val TAG = "MqttForegroundService"
        private const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "mqtt_receiver_channel"

        @Volatile
        var isRunning = false
            private set
    }

    private val mqttManager = MqttManager()
    private var lastMessageTopic = ""
    private var lastMessagePayload = ""

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(this, CHANNEL_ID)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "CONNECT" -> {
                val brokerUrl = intent.getStringExtra("broker_url") ?: return START_NOT_STICKY
                val topic = intent.getStringExtra("topic") ?: "#"
                val clientId = intent.getStringExtra("client_id") ?: "Android_${(1000..9999).random()}"
                val username = intent.getStringExtra("username")
                val password = intent.getStringExtra("password")

                startForeground(NOTIFICATION_ID, buildNotification("姝ｅ湪杩炴帴..."))
                connectMqtt(brokerUrl, clientId, username, password, topic)
            }

            "DISCONNECT" -> {
                disconnectMqtt()
            }
        }

        return START_STICKY
    }

    private fun connectMqtt(
        brokerUrl: String,
        clientId: String,
        username: String?,
        password: String?,
        topic: String
    ) {
        // 鍦ㄥ悗鍙扮嚎绋嬭繛鎺?        Thread {
            mqttManager.connect(
                brokerUrl = brokerUrl,
                clientId = clientId,
                username = username,
                password = password,
                topic = topic,
                onConnected = {
                    isRunning = true
                    updateNotification("宸茶繛鎺?- $brokerUrl", "璁㈤槄: $topic")
                    logToUI("鉁?宸茶繛鎺ュ埌 $brokerUrl")
                    logToUI("鉁?宸茶闃? $topic")
                },
                onDisconnected = { cause ->
                    isRunning = false
                    val reason = cause?.message ?: "鏈煡鍘熷洜"
                    updateNotification("宸叉柇寮€", reason)
                    logToUI("鈿狅笍 杩炴帴鏂紑: $reason (鑷姩閲嶈繛涓?..)")
                },
                onMessage = { msgTopic, payload ->
                    lastMessageTopic = msgTopic
                    lastMessagePayload = payload
                    updateNotification(
                        "$msgTopic",
                        if (payload.length > 100) payload.take(100) + "..." else payload
                    )
                    logToUI("馃摡 $msgTopic 鈫?$payload")
                },
                onError = { error ->
                    isRunning = false
                    updateNotification("杩炴帴澶辫触", error)
                    logToUI("鉂?$error")
                    stopSelf()
                }
            )
        }.start()
    }

    private fun disconnectMqtt() {
        Thread {
            mqttManager.disconnect()
            isRunning = false
            logToUI("馃攲 宸叉柇寮€杩炴帴")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }.start()
    }

    private fun updateNotification(title: String, content: String) {
        val notification = buildNotification(content, title)
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(
        content: String,
        title: String = "MQTT Receiver"
    ): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
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
