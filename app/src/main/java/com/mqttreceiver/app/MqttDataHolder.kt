package com.mqttreceiver.app

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken

/**
 * MQTT 数据持有器
 * 解析 JSON 数据并通知仪表盘更新
 */
object MqttDataHolder {

    private val gson = Gson()

    /** 当前所有解析后的数据 (字段名 → 值) */
    @Volatile
    var parsedData: Map<String, Any> = emptyMap()
        private set

    /** 原始 JSON 字符串 */
    @Volatile
    var rawJson: String = ""
        private set

    /** 数据更新时间戳 */
    @Volatile
    var lastUpdateTime: String = ""
        private set

    /** 接收到的数据主题 */
    @Volatile
    var topic: String = ""
        private set

    /** 数据回调：每当有新数据时通知 */
    @Volatile
    var onDataChanged: ((Map<String, Any>) -> Unit)? = null

    /** 历史数据（限制条数） */
    private val _history = mutableListOf<Map<String, Any>>()
    val history: List<Map<String, Any>> get() = _history.toList()
    private const val MAX_HISTORY = 50

    /**
     * 更新收到的 MQTT 数据
     * @param topic 主题
     * @param payload JSON 字符串
     */
    fun update(topic: String, payload: String) {
        this.topic = topic
        this.rawJson = payload

        try {
            // 解析 JSON
            val jsonElement = JsonParser.parseString(payload)
            if (jsonElement.isJsonObject) {
                val jsonObject = jsonElement.asJsonObject
                val map = mutableMapOf<String, Any>()

                for (key in jsonObject.keySet()) {
                    val value = jsonObject.get(key)
                    map[key] = when {
                        // 全面支持数字和布尔类型
                        value.isJsonPrimitive && value.asJsonPrimitive.isNumber -> value.asDouble
                        value.isJsonPrimitive && value.asJsonPrimitive.isBoolean -> value.asBoolean
                        value.isJsonPrimitive -> value.asString
                        value.isJsonArray -> value.asJsonArray.toString()
                        value.isJsonObject -> value.asJsonObject.toString()
                        else -> value.toString()
                    }
                }

                parsedData = map
                _history.add(map)
                if (_history.size > MAX_HISTORY) {
                    _history.removeAt(0)
                }
                onDataChanged?.invoke(map)
            }
        } catch (e: Exception) {
            // 非 JSON 数据，直接存文本
            parsedData = mapOf("text" to payload)
            onDataChanged?.invoke(parsedData)
        }

        lastUpdateTime = java.text.SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss",
            java.util.Locale.getDefault()
        ).format(java.util.Date())
    }

    /** 重置数据 */
    fun reset() {
        parsedData = emptyMap()
        rawJson = ""
        lastUpdateTime = ""
        topic = ""
        _history.clear()
    }
}
