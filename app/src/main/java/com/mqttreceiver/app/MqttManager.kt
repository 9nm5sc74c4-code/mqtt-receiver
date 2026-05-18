package com.mqttreceiver.app

import android.util.Log
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.nio.charset.StandardCharsets
import javax.net.ssl.SSLSocketFactory

/**
 * MQTT 连接管理器
 * 封装 Eclipse Paho MQTT 客户端的连接、订阅、消息接收逻辑
 */
class MqttManager {

    companion object {
        private const val TAG = "MqttManager"
        private const val CONNECTION_TIMEOUT = 30
        private const val KEEP_ALIVE = 60
    }

    private var mqttClient: MqttClient? = null

    fun connect(
        brokerUrl: String,
        clientId: String,
        username: String?,
        password: String?,
        topic: String,
        onConnected: (() -> Unit)? = null,
        onDisconnected: ((Throwable?) -> Unit)? = null,
        onMessage: ((String, String) -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        try {
            disconnect()

            // 拼装完整 URI：根据端口自动选择协议
            val serverUri = buildServerUri(brokerUrl)

            Log.d(TAG, "Connecting to $serverUri with clientId=$clientId")

            mqttClient = MqttClient(serverUri, clientId, MemoryPersistence())

            val options = MqttConnectOptions().apply {
                isCleanSession = true
                connectionTimeout = CONNECTION_TIMEOUT
                keepAliveInterval = KEEP_ALIVE
                isAutomaticReconnect = true
                maxInflight = 10

                // SSL 配置（端口 8883 自动启用）
                if (serverUri.startsWith("ssl://")) {
                    socketFactory = SSLSocketFactory.getDefault()
                }

                // 用户名密码认证
                if (!username.isNullOrBlank()) {
                    userName = username
                    password?.toCharArray()?.let { this.password = it }
                }

                // 遗嘱消息
                setWill("$topic/status", "offline".toByteArray(), 0, false)
            }

            mqttClient?.setCallback(object : MqttCallbackExtended {
                override fun connectComplete(reconnect: Boolean, serverURI: String) {
                    val msg = if (reconnect) "自动重连成功" else "连接成功"
                    Log.i(TAG, "$msg -> $serverURI")
                    onConnected?.invoke()
                    onMessage?.invoke("[系统]", msg)
                    subscribe(topic, onMessage, onError)
                }

                override fun connectionLost(cause: Throwable?) {
                    Log.w(TAG, "连接断开: ${cause?.message}")
                    onDisconnected?.invoke(cause)
                    onMessage?.invoke("[系统]", "连接断开: ${cause?.message ?: "未知原因"}（将自动重连）")
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    topic ?: return
                    message ?: return
                    val payload = String(message.payload, StandardCharsets.UTF_8)
                    Log.d(TAG, "收到消息 [$topic]: $payload")
                    onMessage?.invoke(topic, payload)
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })

            mqttClient?.connect(options)

        } catch (e: MqttException) {
            val errorMsg = "MQTT 连接失败: ${e.message} (code: ${e.reasonCode})"
            Log.e(TAG, errorMsg, e)
            onError?.invoke(errorMsg)
        } catch (e: Exception) {
            val errorMsg = "连接异常: ${e.message}"
            Log.e(TAG, errorMsg, e)
            onError?.invoke(errorMsg)
        }
    }

    /**
     * 根据端口自动拼装完整 MQTT URI：
     *  8883 → ssl://host:8883
     *  其他  → tcp://host:port
     * 如果已含协议头则直接使用
     */
    private fun buildServerUri(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("tcp://") || trimmed.startsWith("ssl://")) {
            return trimmed
        }
        // 格式: host:port 或 host
        return if (trimmed.endsWith(":8883") || trimmed.contains(":8883")) {
            if (trimmed.startsWith("ssl://")) trimmed else "ssl://$trimmed"
        } else {
            if (trimmed.startsWith("tcp://")) trimmed else "tcp://$trimmed"
        }
    }

    private fun subscribe(
        topic: String,
        onMessage: ((String, String) -> Unit)?,
        onError: ((String) -> Unit)?
    ) {
        try {
            mqttClient?.subscribe(topic, 1) { _, message ->
                val payload = String(message.payload, StandardCharsets.UTF_8)
                onMessage?.invoke(topic, payload)
            }
            val msg = "已订阅主题: $topic"
            Log.i(TAG, msg)
            onMessage?.invoke("[系统]", msg)
        } catch (e: MqttException) {
            val errorMsg = "订阅失败: ${e.message}"
            Log.e(TAG, errorMsg, e)
            onError?.invoke(errorMsg)
        }
    }

    fun disconnect() {
        try {
            mqttClient?.disconnect()
            mqttClient?.close()
        } catch (e: Exception) {
            Log.e(TAG, "断开连接异常: ${e.message}")
        }
        mqttClient = null
    }

    val isConnected: Boolean
        get() = mqttClient?.isConnected == true
}
