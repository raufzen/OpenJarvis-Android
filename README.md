# 🤖 OpenJarvis Mobile (Android Client)

<div align="center">
  <h3>Official Android Companion App for the OpenJarvis Personal AI Framework</h3>

  <p>
    <a href="https://github.com/raufzen/OpenJarvis-Android/releases/latest"><img src="https://img.shields.io/github/v/release/raufzen/OpenJarvis-Android?style=for-the-badge&color=06b6d4" alt="Release"></a>
    <img src="https://img.shields.io/badge/Platform-Android%207.0%2B-blue?style=for-the-badge&logo=android" alt="Android">
    <img src="https://img.shields.io/badge/License-Apache%202.0-green?style=for-the-badge" alt="License">
    <a href="https://github.com/raufzen"><img src="https://img.shields.io/badge/Author-Rauf%20Zen-purple?style=for-the-badge&logo=github" alt="Author"></a>
  </p>
</div>

---

## 📖 Overview

**OpenJarvis Mobile** is a native Android companion application designed to connect your Android smartphone directly to your local **[OpenJarvis](https://github.com/open-jarvis/OpenJarvis)** personal AI instance. 

While the official OpenJarvis framework runs on desktop/servers with local LLMs (via Ollama & Python FastAPI), **OpenJarvis Mobile** bridges the gap to your phone with a sleek, futuristic Cyberpunk interface, hardware-accelerated WebView, speech-to-text recording, file/image upload support, and automatic server health detection.

---

## ⚡ Features

- **🌐 Instant Server Connection**: Connect to your PC, laptop, or Termux instance over local Wi-Fi, Tailscale, or ngrok (default: `http://192.168.1.100:8000`).
- **🩺 Live Health Monitoring**: Real-time status chip with latency ping and color-coded connectivity dots (Connected / Offline / Connecting).
- **🎙️ Full Voice & Multimodal Support**: Native WebChromeClient permissions for microphone voice input and camera capture.
- **📁 File & Image Uploads**: Attach images and documents directly into Jarvis for on-device multimodal reasoning.
- **🛡️ Cleartext Traffic Enabled**: Seamlessly connect to non-HTTPS local IP addresses (`http://192.168.x.x`) without security blocks.
- **⚡ Interactive Offline Dashboard**: If your server is unreachable, the app provides a built-in troubleshooting dashboard with live ping retries and setup commands.
- **🎨 Cyberpunk Dark HUD**: Deep slate `#090d16` background with vibrant cyan `#06b6d4` neon accents and animated AI Core.
- **🔄 Pull-to-Refresh & Edge-to-Edge**: Modern gesture-friendly layout built with Material 3.

---

## 📦 Download APK

Directly download the latest APK from the releases section:

👉 **[Download OpenJarvis Mobile APK (Latest Release)](https://github.com/raufzen/OpenJarvis-Android/releases/latest)**

---

## 🚀 Getting Started

### 1. Start OpenJarvis on your PC / Server
Ensure OpenJarvis is installed and running on your PC:
```bash
# Start OpenJarvis GUI / Web server
jarvis gui

# Or run the API server directly on all network interfaces
uv run jarvis api --host 0.0.0.0 --port 8000
```

### 2. Connect Your Phone
1. Connect your Android phone to the **same Wi-Fi network** as your PC.
2. Find your PC's local IP address (`ipconfig` on Windows, or `ip a` on Linux/macOS, e.g. `192.168.1.105`).
3. Open **OpenJarvis Mobile** on your phone.
4. Tap the **Settings (gear icon)** at the top right, enter your PC's IP and port (`8000`), and tap **Connect**.

---

## 🏗️ Building From Source

You can build the APK locally using Gradle:

```bash
# Clone the repository
git clone https://github.com/raufzen/OpenJarvis-Android.git
cd OpenJarvis-Android

# Build Debug APK
./gradlew assembleDebug

# Build Release APK
./gradlew assembleRelease
```

The compiled APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 📄 Architecture

```
OpenJarvis-Android/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/raufzen/openjarvis/
│   │   │   ├── MainActivity.kt        # Primary WebView & UI controller
│   │   │   ├── ServerConfig.kt        # Persistent server settings
│   │   │   └── NetworkUtils.kt        # Server ping & connectivity check
│   │   ├── assets/
│   │   │   └── offline_fallback.html  # Cyberpunk offline portal
│   │   └── res/                       # Material 3 dark themes & vectors
│   └── build.gradle.kts
├── .github/workflows/
│   └── build-apk.yml                  # Automated APK build & GitHub release
├── build.gradle.kts
└── settings.gradle.kts
```

---

## 👤 Author & Acknowledgements

- **App Developer**: [Rauf Zen](https://github.com/raufzen)
- **Base Framework**: [OpenJarvis by OpenJarvis AI Team](https://github.com/open-jarvis/OpenJarvis)
- **License**: Apache License 2.0
