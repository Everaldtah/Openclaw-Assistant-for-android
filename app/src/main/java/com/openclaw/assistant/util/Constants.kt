package com.openclaw.assistant.util

object Constants {
    // Default to localhost for development; override in app config
    const val WS_URL_DEFAULT = "ws://10.0.2.2:8000/ws"

    // Audio config
    const val AUDIO_SAMPLE_RATE = 16000
    const val AUDIO_CHANNEL = 1
    const val OPUS_BITRATE = 32000
    // 20ms frame: 320 samples × 2 bytes = 640 bytes for 16kHz mono
    const val OPUS_FRAME_SAMPLES = 320
    const val OPUS_FRAME_BYTES = OPUS_FRAME_SAMPLES * 2

    // Camera config (interval between frames sent to backend)
    const val CAMERA_INTERVAL_MS = 5000L

    // Notification
    const val NOTIFICATION_ID = 1001
    const val CHANNEL_ID = "openclaw_foreground"

    // WebSocket reconnect
    const val WS_RECONNECT_BASE_MS = 1000L
    const val WS_RECONNECT_MAX_MS = 30_000L
    const val WS_RECONNECT_MAX_ATTEMPTS = 12
}
