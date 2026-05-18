# MQTT Receiver - Android APP

## 📱 简介

一个轻量级安卓 MQTT 数据接收器，安装即用。

### ✨ 功能

| 功能 | 说明 |
|------|------|
| 🔌 MQTT 连接 | 支持 MQTT 3.1/3.1.1 协议，任意 Broker |
| 📡 主题订阅 | 支持通配符 `#` / `+` |
| 🔐 认证 | 支持用户名/密码认证 |
| 🏃 后台运行 | 前台服务保活，APP 退出仍接收 |
| 🔄 自动重连 | 断线自动重连 |
| 📋 消息日志 | 实时显示所有消息，可复制 |
| 🚀 开机自启 | 设备重启自动恢复连接 |
| 🔔 通知栏 | 显示最新消息摘要 |

### 📸 界面预览

```
┌─────────────────────────────┐
│  MQTT Receiver              │
├─────────────────────────────┤
│  📝 连接设置                 │
│  ┌─────────────────────────┐│
│  │ Broker: 192.168.1.100   ││
│  │ Port:   1883             ││
│  │ Topic:  #                ││
│  │ ClientID: Android_1234   ││
│  │ Username: (可选)         ││
│  │ Password: (可选)         ││
│  │ [开机自动连接 ✓]         ││
│  │ [💾 保存设置]           ││
│  └─────────────────────────┘│
│                             │
│  ● 未连接    [ 连接 ]       │
│                             │
│  📋 消息日志           [清除]│
│  ┌─────────────────────────┐│
│  │ [10:30:01] 📩 test → hi ││
│  │ [10:30:05] 📩 test → ok ││
│  └─────────────────────────┘│
└─────────────────────────────┘
```

---

## 🔨 构建方法

### 方法一：Android Studio（推荐）

1. 安装 [Android Studio](https://developer.android.com/studio)
2. 打开本项目目录 `mqtt-receiver-android/`
3. 等待 Gradle Sync 完成
4. `Build` → `Build APK(s)`
5. APK 在 `app/build/outputs/apk/debug/` 

### 方法二：命令行构建

```bash
# 先安装 Android SDK Command-Line Tools
# https://developer.android.com/studio#command-line-tools-only

# 设置环境变量
export ANDROID_HOME=/path/to/android-sdk
export PATH=$PATH:$ANDROID_HOME/tools/bin:$ANDROID_HOME/platform-tools

# 接受许可
sdkmanager --licenses

# 安装必需组件
sdkmanager "platforms;android-34" "build-tools;34.0.0"

# 构建
cd mqtt-receiver-android
./gradlew assembleDebug

# APK 在 app/build/outputs/apk/debug/app-debug.apk
```

### 方法三：在线构建（不需要安装任何东西）

将项目上传到 GitHub，用 [GitHub Actions](https://github.com/features/actions) 自动构建。

---

## 📲 安装使用

1. 把 APK 传到手机安装
2. **首次打开允许通知权限**（Android 13+ 需要）
3. 填入 MQTT Broker 地址和端口
4. 填入要订阅的主题（默认 `#` 表示所有）
5. 点击 **连接**

---

## ⚙️ 配置说明

| 参数 | 默认值 | 说明 |
|------|--------|------|
| Broker 地址 | tcp://192.168.1.100 | 不含端口，自动加 tcp:// |
| 端口 | 1883 | MQTT 默认端口 |
| 订阅主题 | # | `#`=全部, `sensor/#`=子主题 |
| Client ID | 随机生成 | 不填自动生成唯一ID |
| 用户名/密码 | 空 | MQTT Broker 如需认证填写 |
| 开机自启 | 开启 | 重启手机后自动连接 |

---

## 🛠 技术栈

- **语言:** Kotlin
- **MQTT 库:** Eclipse Paho `org.eclipse.paho.client.mqttv3`
- **UI:** Material Design 3 + ViewBinding
- **后台:** Android Foreground Service
- **最低版本:** Android 8.0 (API 26)
- **目标版本:** Android 14 (API 34)

## 📁 项目结构

```
mqtt-receiver-android/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradle/wrapper/
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/
        │   ├── layout/activity_main.xml
        │   ├── values/{colors,strings,themes}.xml
        │   ├── xml/network_security_config.xml
        │   └── mipmap-anydpi-v26/
        └── java/com/mqttreceiver/app/
            ├── MainActivity.kt          # 主界面
            ├── MqttManager.kt           # MQTT 连接封装
            ├── MqttForegroundService.kt # 后台服务
            ├── BootReceiver.kt          # 开机自启
            └── NotificationHelper.kt    # 通知管理
```
