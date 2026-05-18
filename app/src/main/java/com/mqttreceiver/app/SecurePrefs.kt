package com.mqttreceiver.app

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.IOException

/**
 * MQTT 连接凭据加密存储
 * 使用 EncryptedSharedPreferences 确保地址、端口、密码不以明文存储
 */
object SecurePrefs {

    private const val PREFS_NAME = "mqtt_secure_config"
    private var prefs: SharedPreferences? = null

    // Key 常量
    const val KEY_BROKER = "broker_url"
    const val KEY_PORT = "broker_port"
    const val KEY_USERNAME = "username"
    const val KEY_PASSWORD = "password"
    const val KEY_TOPIC = "topic"
    const val KEY_LAST_CONNECTED = "last_connected"
    const val KEY_AUTO_CONNECT = "auto_connect"

    /**
     * 初始化加密存储
     * 必须在 Application.onCreate 或首次使用前调用
     */
    fun init(context: Context) {
        if (prefs != null) return
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            prefs = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: IOException) {
            // 降级：KeyStore 不可用时使用普通 SharedPreferences
            prefs = context.getSharedPreferences(PREFS_NAME + "_fallback", Context.MODE_PRIVATE)
        }
    }

    /** 保存加密配置 */
    fun save(broker: String, port: String, username: String, password: String, topic: String, autoConnect: Boolean) {
        prefs?.edit()?.apply {
            putString(KEY_BROKER, broker)
            putString(KEY_PORT, port)
            putString(KEY_USERNAME, username)
            putString(KEY_PASSWORD, password)
            putString(KEY_TOPIC, topic)
            putBoolean(KEY_AUTO_CONNECT, autoConnect)
            putLong(KEY_LAST_CONNECTED, System.currentTimeMillis())
            apply()
        }
    }

    /** 读取配置 */
    fun getBroker(): String = prefs?.getString(KEY_BROKER, "") ?: ""
    fun getPort(): String = prefs?.getString(KEY_PORT, "8883") ?: "8883"
    fun getUsername(): String = prefs?.getString(KEY_USERNAME, "") ?: ""
    fun getPassword(): String = prefs?.getString(KEY_PASSWORD, "") ?: ""
    fun getTopic(): String = prefs?.getString(KEY_TOPIC, "ch6pem2") ?: "ch6pem2"
    fun getAutoConnect(): Boolean = prefs?.getBoolean(KEY_AUTO_CONNECT, false) ?: false
    fun getLastConnected(): Long = prefs?.getLong(KEY_LAST_CONNECTED, 0L) ?: 0L

    /** 是否有已保存的凭据 */
    fun hasCredentials(): Boolean = getBroker().isNotBlank()

    /** 清除所有保存的凭据 */
    fun clear() {
        prefs?.edit()?.clear()?.apply()
    }
}
