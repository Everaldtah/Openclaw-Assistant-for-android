package com.openclaw.assistant.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openclaw.assistant.data.AuthManager
import com.openclaw.assistant.databinding.ControlPanelBinding
import com.openclaw.assistant.manager.SecureWebSocketClient
import com.openclaw.assistant.manager.WsState
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// ───────────── ViewModel ─────────────

@HiltViewModel
class ControlPanelViewModel @Inject constructor(
    val authManager: AuthManager,
    val wsClient: SecureWebSocketClient
) : ViewModel() {

    val wsState = wsClient.state.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        WsState.DISCONNECTED
    )

    fun saveConfig(url: String, jwt: String) {
        if (url.isNotBlank()) authManager.saveServerUrl(url)
        if (jwt.isNotBlank()) authManager.saveJwt(jwt)
    }

    fun reconnect() {
        viewModelScope.launch {
            wsClient.disconnect()
            wsClient.connect()
        }
    }

    fun disconnect() = wsClient.disconnect()
}

// ───────────── Fragment ─────────────

@AndroidEntryPoint
class ControlPanelFragment : Fragment() {

    private var _binding: ControlPanelBinding? = null
    private val binding get() = _binding!!
    private val vm: ControlPanelViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ControlPanelBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Pre-fill saved values
        binding.etUrl.setText(vm.authManager.getServerUrl())

        // Observe connection state
        vm.wsState.collectLifecycle(viewLifecycleOwner) { state ->
            binding.tvStatus.text = "Status: $state"
            binding.statusDot.setColorFilter(
                when (state) {
                    WsState.CONNECTED -> android.graphics.Color.GREEN
                    WsState.CONNECTING, WsState.RECONNECTING -> android.graphics.Color.YELLOW
                    WsState.DISCONNECTED -> android.graphics.Color.RED
                }
            )
            binding.btnReconnect.isEnabled = state != WsState.CONNECTING
        }

        binding.btnSave.setOnClickListener {
            val url = binding.etUrl.text.toString().trim()
            val jwt = binding.etJwt.text.toString().trim()
            if (url.isBlank()) {
                binding.etUrl.error = "Required"
                return@setOnClickListener
            }
            vm.saveConfig(url, jwt)
            Toast.makeText(requireContext(), "Saved", Toast.LENGTH_SHORT).show()
        }

        binding.btnReconnect.setOnClickListener { vm.reconnect() }
        binding.btnDisconnect.setOnClickListener { vm.disconnect() }

        binding.switchAudio.setOnCheckedChangeListener { _, _ ->
            // AudioStreamManager is managed by OverlayService; toggle via Intent in production
        }

        binding.switchCamera.setOnCheckedChangeListener { _, isChecked ->
            // CameraFrameManager.setCaptureEnabled(isChecked) – call via service binding
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// Helper extension to avoid manual lifecycle management
private fun <T> kotlinx.coroutines.flow.Flow<T>.collectLifecycle(
    owner: androidx.lifecycle.LifecycleOwner,
    action: suspend (T) -> Unit
) {
    owner.lifecycleScope.launchWhenStarted { collect { action(it) } }
}

private val androidx.lifecycle.LifecycleOwner.lifecycleScope
    get() = androidx.lifecycle.lifecycleScope
