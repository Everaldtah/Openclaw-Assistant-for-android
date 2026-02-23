package com.openclaw.assistant.domain.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VisionFrameMessage(
    val type: String = "vision_frame",
    val data: String,
    @SerialName("timestamp_ms") val timestampMs: Long = System.currentTimeMillis()
)

@Serializable
data class AudioMetaMessage(
    val type: String = "audio_meta",
    val format: String, // "opus" or "pcm_s16le"
    @SerialName("sample_rate") val sampleRate: Int,
    val channels: Int,
    val bitrate: Int
)
