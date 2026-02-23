package com.openclaw.assistant.domain.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AssistantResponse(
    val type: String = "assistant_response",
    val text: String = "",
    @SerialName("audio_base64") val audioBase64: String? = null,
    val emotion: String = "neutral" // neutral, happy, thinking, alert
)
