package com.openclaw.assistant.manager

import android.util.Log
import com.openclaw.assistant.data.AuthManager
import com.openclaw.assistant.domain.models.AssistantResponse
import com.openclaw.assistant.domain.models.VisionFrameMessage
import com.openclaw.assistant.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.pow

enum class WsState { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING }

@Singleton
class SecureWebSocketClient @Inject constructor(
    private val client: OkHttpClient,
    private val authManager: AuthManager
) : WebSocketListener() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var webSocket: WebSocket? = null
    private var reconnectAttempts = 0
    private var isShuttingDown = false

    private val _responses = MutableSharedFlow<AssistantResponse>(extraBufferCapacity = 32)
    val responses = _responses.asSharedFlow()

    private val _state = MutableStateFlow(WsState.DISCONNECTED)
    val state = _state.asStateFlow()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun connect() {
        isShuttingDown = false
        reconnectAttempts = 0
        doConnect()
    }

    private fun doConnect() {
        val url = authManager.getServerUrl().ifBlank { Constants.WS_URL_DEFAULT }
        Log.i(TAG, "Connecting to $url")
        _state.value = WsState.CONNECTING
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, this)
    }

    fun sendAudioChunk(bytes: ByteArray) {
        webSocket?.send(ByteString.of(*bytes))
    }

    fun sendVisionFrame(base64: String) {
        val msg = json.encodeToString(VisionFrameMessage.serializer(), VisionFrameMessage(data = base64))
        webSocket?.send(msg)
    }

    fun disconnect() {
        isShuttingDown = true
        webSocket?.close(1000, "Client shutdown")
        _state.value = WsState.DISCONNECTED
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        Log.i(TAG, "Connected")
        reconnectAttempts = 0
        _state.value = WsState.CONNECTED
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        try {
            val response = json.decodeFromString<AssistantResponse>(text)
            scope.launch { _responses.emit(response) }
        } catch (e: Exception) {
            Log.w(TAG, "Could not parse message: $text")
        }
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        Log.e(TAG, "WebSocket failure: ${t.message}")
        _state.value = WsState.RECONNECTING
        if (!isShuttingDown) scheduleReconnect()
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        webSocket.close(1000, null)
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        _state.value = WsState.DISCONNECTED
        if (!isShuttingDown) scheduleReconnect()
    }

    private fun scheduleReconnect() {
        if (reconnectAttempts >= Constants.WS_RECONNECT_MAX_ATTEMPTS) {
            Log.e(TAG, "Max reconnect attempts reached")
            _state.value = WsState.DISCONNECTED
            return
        }
        // Exponential backoff: 1s, 2s, 4s, 8s … capped at 30s
        val delayMs = min(
            Constants.WS_RECONNECT_MAX_MS,
            Constants.WS_RECONNECT_BASE_MS * (2.0.pow(reconnectAttempts.toDouble())).toLong()
        )
        reconnectAttempts++
        Log.i(TAG, "Reconnecting in ${delayMs}ms (attempt $reconnectAttempts)")
        scope.launch {
            delay(delayMs)
            if (!isShuttingDown) doConnect()
        }
    }

    fun destroy() {
        disconnect()
        scope.cancel()
    }

    companion object {
        private const val TAG = "SecureWsClient"
    }
}
