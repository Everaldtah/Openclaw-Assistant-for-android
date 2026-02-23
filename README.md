# OpenClaw Assistant – Android App

> A production-grade AI assistant Android app that streams **audio** (Opus) and **camera frames** (JPEG/base64) to your OpenClaw agent via a secure WebSocket, then renders responses through an animated floating avatar overlay.

---

## Table of Contents
1. [Features](#features)
2. [Architecture](#architecture)
3. [Project Structure](#project-structure)
4. [Requirements](#requirements)
5. [Build Instructions](#build-instructions)
   - [Option A: Termux (on-device)](#option-a-termux-on-device)
   - [Option B: Android Studio (desktop)](#option-b-android-studio-desktop)
6. [Configuration](#configuration)
7. [Deploy to Device](#deploy-to-device)
8. [Connect to OpenClaw Agent](#connect-to-openclaw-agent)
   - [Mock Server](#1-mock-server-local-testing)
   - [Anthropic / Claude backend](#2-anthropic--claude-backend)
   - [Proxy to real OpenClaw agent](#3-proxy-to-real-openclaw-agent)
9. [WebSocket Protocol](#websocket-protocol)
10. [Permissions](#permissions)
11. [Troubleshooting](#troubleshooting)

---

## Features

- **Floating avatar overlay** – draggable, always-on-top, 4 animated states
- **Real-time Opus audio** – native MediaCodec encoder (API 34+), 20 ms frames at 16 kHz, 32 kbps
- **Periodic camera vision** – front-camera JPEG frames every 5 s (configurable)
- **Secure WebSocket** – JWT auth header, exponential-backoff reconnection (up to 12 attempts)
- **Encrypted credential storage** – server URL + JWT in `EncryptedSharedPreferences` (AES-256-GCM)
- **Hilt DI** with KSP (no kapt, fully Kotlin 2.1 compatible)
- **Clean MVVM** – ViewModel + StateFlow + coroutines, zero memory leaks
- **Network security config** – cleartext blocked by default, localhost allowed for dev

| Avatar State | Trigger | Animation |
|---|---|---|
| **IDLE** | Disconnected | Gentle float + breathe |
| **LISTENING** | WebSocket connected | Green pulse |
| **SPEAKING** | Response received | Mouth open/close + nod |
| **THINKING** | Connecting/reconnecting | Orange spin |

---

## Architecture

```
┌─────────────────── OverlayService (Foreground) ───────────────────┐
│                                                                     │
│  AudioStreamManager          CameraFrameManager                    │
│   └─ AudioRecord (16kHz)      └─ CameraX (front cam, 5s interval) │
│   └─ OpusEncoderWrapper        └─ JPEG → Base64                    │
│       (MediaCodec)                                                  │
│                  ↓ binary / JSON text                               │
│            SecureWebSocketClient (OkHttp)                           │
│                  ↕ wss://your-agent                                 │
│            OpenClaw Agent Backend                                   │
│                  ↓ AssistantResponse JSON                           │
│              AvatarView (animated overlay)                          │
└─────────────────────────────────────────────────────────────────────┘
```

**Tech stack:**
- Kotlin 2.1.0 · AGP 8.7.3 · Gradle 8.9
- Hilt 2.52 (KSP) · CameraX 1.4.0 · Media3 1.5.0
- OkHttp 4.12.0 · kotlinx.serialization 1.7.3
- EncryptedSharedPreferences · MediaCodec Opus

---

## Project Structure

```
OpenClawAssistant/
├── app/src/main/
│   ├── java/com/openclaw/assistant/
│   │   ├── MainActivity.kt          # Setup screen (URL, JWT, permissions)
│   │   ├── OpenClawApplication.kt   # Hilt entry point
│   │   ├── data/AuthManager.kt      # Encrypted credential storage
│   │   ├── di/AppModule.kt          # Hilt providers (OkHttp, WebSocket)
│   │   ├── domain/models/           # Serializable data classes
│   │   ├── manager/
│   │   │   ├── AudioStreamManager.kt
│   │   │   ├── CameraFrameManager.kt
│   │   │   └── SecureWebSocketClient.kt
│   │   ├── service/OverlayService.kt # Foreground service + WindowManager
│   │   ├── ui/
│   │   │   ├── AvatarView.kt        # Animated avatar (4 states)
│   │   │   └── ControlPanelFragment.kt
│   │   └── util/
│   │       ├── Constants.kt
│   │       ├── NotificationHelper.kt
│   │       └── OpusEncoderWrapper.kt # MediaCodec Opus encoder
│   └── res/
│       ├── layout/                  # activity_main, floating_avatar, control_panel
│       ├── drawable/                # Vector icons + avatar graphics
│       ├── anim/                    # pulse.xml
│       ├── values/                  # colors, strings, themes (dark Material3)
│       └── xml/network_security_config.xml
├── mock_server.py                   # FastAPI bridge server (mock / Claude / proxy)
├── setup_termux.sh                  # One-click Termux build environment setup
├── build_and_deploy.sh              # Build, sign, ADB install
├── .env.template                    # Server config template
└── local.properties.template        # SDK path template
```

---

## Requirements

| Component | Version |
|---|---|
| Android | 14+ (API 34 minimum) |
| Target SDK | 35 (Android 15) |
| Kotlin | 2.1.0 |
| Java | 17 or 21 |
| Gradle | 8.9 |
| AGP | 8.7.3 |

**Backend:** Any machine running `mock_server.py` on the same network
(Python 3.9+, `pip install fastapi uvicorn`)

---

## Build Instructions

### Option A: Termux (on-device)

**One-time environment setup:**
```bash
# 1. Fix /tmp (required for build tools)
mkdir -p /data/data/com.termux/files/usr/tmp
ln -sf /data/data/com.termux/files/usr/tmp /tmp

# 2. Run the full setup script
cd /storage/emulated/0/Download/OpenClawAssistant
chmod +x setup_termux.sh
./setup_termux.sh

# 3. Reload environment
source ~/.bashrc
```

**Set SDK path:**
```bash
cp local.properties.template local.properties
# local.properties already has the Termux SDK path
```

**Build:**
```bash
chmod +x build_and_deploy.sh

# Debug build (fast, no signing needed)
./build_and_deploy.sh

# Signed release build
./build_and_deploy.sh release
```

APK output:
- Debug:   `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release.apk`

---

### Option B: Android Studio (desktop)

1. Clone this repo:
   ```bash
   git clone https://github.com/Everaldtah/Openclaw-Assistant-for-android.git
   cd Openclaw-Assistant-for-android
   ```

2. Open in **Android Studio Ladybug (2024.2)** or newer

3. Let Gradle sync complete

4. Create `local.properties` with your SDK path:
   ```
   sdk.dir=/home/you/Android/Sdk
   ```

5. **Build → Generate Signed Bundle/APK → APK → debug or release**

6. Or via terminal:
   ```bash
   ./gradlew assembleDebug
   ```

---

## Configuration

Open the app and fill in:

| Field | Example | Notes |
|---|---|---|
| **WebSocket URL** | `ws://192.168.1.42:8000/ws` | Use `wss://` in production |
| **JWT Token** | `eyJhbGciOi...` | Optional; sent as `Authorization: Bearer <token>` |

Tap **Save Configuration** then **START ASSISTANT**.

> Your settings are encrypted and persist between sessions.

---

## Deploy to Device

### Via ADB (USB or WiFi)
```bash
# USB
adb install -r app/build/outputs/apk/debug/app-debug.apk

# WiFi ADB (Android 11+)
adb connect <phone-ip>:5555
adb install -r app-debug.apk

# Launch directly
adb shell am start -n com.openclaw.assistant/.MainActivity
```

### Via file transfer
Copy the APK to your device and open it with a file manager.
Enable **"Install from unknown sources"** in Settings → Security.

---

## Connect to OpenClaw Agent

The app connects to any WebSocket server that speaks the OpenClaw protocol.
Three modes are supported via `mock_server.py`:

### 1. Mock Server (local testing)

```bash
pip install fastapi uvicorn
python mock_server.py
```

Server starts at `http://0.0.0.0:8000`
WebSocket endpoint: `ws://<your-ip>:8000/ws`

**Find your IP:**
```bash
# On phone / Termux
ip addr show wlan0 | grep "inet "

# On Linux
hostname -I | awk '{print $1}'
```

In the app, set URL to `ws://192.168.x.x:8000/ws` and tap Start.

---

### 2. Anthropic / Claude Backend

```bash
cp .env.template .env
# Edit .env:
#   OPENCLAW_MODE=anthropic
#   OPENCLAW_API_KEY=sk-ant-your-key-here

python mock_server.py
```

The server will route text to Claude claude-opus-4-6 and return responses.
> Audio transcription requires Whisper integration (see `mock_server.py` for hook point).

---

### 3. Proxy to Real OpenClaw Agent

If you already have an OpenClaw agent running a WebSocket endpoint:

```bash
# .env
OPENCLAW_MODE=proxy
OPENCLAW_AGENT_URL=ws://your-openclaw-agent:port/ws
python mock_server.py
```

All audio/vision frames are forwarded transparently to your agent.

---

### 4. Direct connection (no mock server)

If your OpenClaw agent already speaks the protocol below, point the app
directly at it: `wss://your-agent-host/ws`

---

## WebSocket Protocol

All messages after the initial connection:

**App → Server:**

| Type | Frame | Content |
|---|---|---|
| Audio chunk | Binary | Opus-encoded bytes (20 ms, 16 kHz mono, 32 kbps) OR raw PCM S16LE if Opus unavailable |
| Vision frame | Text JSON | `{"type":"vision_frame","data":"<base64-jpeg>","timestamp_ms":1234}` |
| Audio metadata | Text JSON | `{"type":"audio_meta","format":"opus","sample_rate":16000,"channels":1,"bitrate":32000}` |

**Server → App:**

```json
{
  "type": "assistant_response",
  "text": "Hello! I see you.",
  "audio_base64": null,
  "emotion": "happy"
}
```

`emotion` values: `neutral` · `happy` · `thinking` · `alert`
`audio_base64`: base64-encoded WAV/MP3 for TTS playback (null = text only)

---

## Permissions

| Permission | Purpose |
|---|---|
| `RECORD_AUDIO` | Microphone stream |
| `CAMERA` | Vision frames |
| `SYSTEM_ALERT_WINDOW` | Floating overlay |
| `FOREGROUND_SERVICE` | Keep service alive |
| `FOREGROUND_SERVICE_MICROPHONE` | Android 14 requirement |
| `FOREGROUND_SERVICE_CAMERA` | Android 14 requirement |
| `POST_NOTIFICATIONS` | Foreground service notification |
| `INTERNET` | WebSocket connection |

---

## Troubleshooting

**App crashes on start:**
- Ensure overlay permission is granted (Settings → Apps → OpenClaw → Display over other apps)

**Avatar stuck on THINKING (orange spin):**
- Check the server URL is reachable: `curl http://your-ip:8000/health`
- Ensure phone and server are on the same WiFi network
- Check Firewall/router isn't blocking port 8000

**No audio sent:**
- Grant microphone permission explicitly in device Settings
- Check notification tray – foreground service must show a persistent notification

**Build fails with "SDK not found":**
- Confirm `local.properties` has correct `sdk.dir` path
- Run `sdkmanager --list` to verify SDK packages are installed

**`/tmp` permission error in Termux:**
```bash
mkdir -p /data/data/com.termux/files/usr/tmp
ln -sf /data/data/com.termux/files/usr/tmp /tmp
export TMPDIR=/data/data/com.termux/files/usr/tmp
```

**GitHub push fails:**
- Use a Personal Access Token (not your password): GitHub → Settings → Developer Settings → PAT
- When prompted for password, paste the token

---

## License

MIT – use freely, attribution appreciated.

---

*Built with Claude Code · Target: Android 15 (API 35) · Min: Android 14 (API 34)*
