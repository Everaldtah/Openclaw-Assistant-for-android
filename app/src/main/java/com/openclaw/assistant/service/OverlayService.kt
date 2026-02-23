package com.openclaw.assistant.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import com.openclaw.assistant.databinding.FloatingAvatarBinding
import com.openclaw.assistant.manager.AudioStreamManager
import com.openclaw.assistant.manager.CameraFrameManager
import com.openclaw.assistant.manager.SecureWebSocketClient
import com.openclaw.assistant.manager.WsState
import com.openclaw.assistant.ui.AvatarState
import com.openclaw.assistant.util.Constants
import com.openclaw.assistant.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@AndroidEntryPoint
class OverlayService : Service(), LifecycleOwner {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val lifecycleRegistry = LifecycleRegistry(this)

    @Inject lateinit var webSocketClient: SecureWebSocketClient
    @Inject lateinit var audioManager: AudioStreamManager
    @Inject lateinit var cameraManager: CameraFrameManager

    private lateinit var windowManager: WindowManager
    private lateinit var binding: FloatingAvatarBinding
    private lateinit var params: WindowManager.LayoutParams

    override val lifecycle: Lifecycle get() = lifecycleRegistry

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(Constants.NOTIFICATION_ID, NotificationHelper.createNotification(this))
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        binding = FloatingAvatarBinding.inflate(LayoutInflater.from(this))

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 300
        }

        windowManager.addView(binding.root, params)
        setupDrag()
        setupClickListeners()
        observeWebSocketState()

        webSocketClient.connect()
        audioManager.startStreaming(serviceScope, webSocketClient)
        cameraManager.startCapture(this, this, serviceScope, webSocketClient)

        return START_STICKY
    }

    private fun setupDrag() {
        var ix = 0; var iy = 0; var tx = 0f; var ty = 0f
        binding.root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    ix = params.x; iy = params.y
                    tx = event.rawX; ty = event.rawY; true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = (ix + event.rawX - tx).toInt()
                    params.y = (iy + event.rawY - ty).toInt()
                    windowManager.updateViewLayout(binding.root, params); true
                }
                MotionEvent.ACTION_UP -> {
                    // snap to screen edge if dragged far enough (optional UX)
                    false
                }
                else -> false
            }
        }
    }

    private fun setupClickListeners() {
        binding.avatar.setOnClickListener {
            Toast.makeText(this, "OpenClaw – status: ${webSocketClient.state.value}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeWebSocketState() {
        webSocketClient.state.onEach { state ->
            binding.avatar.setState(
                when (state) {
                    WsState.CONNECTED -> AvatarState.LISTENING
                    WsState.CONNECTING, WsState.RECONNECTING -> AvatarState.THINKING
                    WsState.DISCONNECTED -> AvatarState.IDLE
                }
            )
        }.launchIn(serviceScope)

        webSocketClient.responses.onEach { _ ->
            binding.avatar.setState(AvatarState.SPEAKING)
            binding.avatar.postDelayed({ binding.avatar.setState(AvatarState.LISTENING) }, 3000L)
        }.launchIn(serviceScope)
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        serviceScope.cancel()
        webSocketClient.disconnect()
        audioManager.stop()
        cameraManager.stop()
        if (::binding.isInitialized) {
            try { windowManager.removeView(binding.root) } catch (_: Exception) {}
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
