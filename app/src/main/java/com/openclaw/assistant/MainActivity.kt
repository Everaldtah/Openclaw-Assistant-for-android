package com.openclaw.assistant

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.openclaw.assistant.data.AuthManager
import com.openclaw.assistant.databinding.ActivityMainBinding
import com.openclaw.assistant.service.OverlayService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    @Inject lateinit var authManager: AuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Pre-fill saved values
        binding.etServerUrl.setText(authManager.getServerUrl())
        binding.etJwt.setText(authManager.getJwt())

        binding.btnSaveConfig.setOnClickListener {
            val url = binding.etServerUrl.text.toString().trim()
            val jwt = binding.etJwt.text.toString().trim()

            if (url.isBlank()) {
                binding.etServerUrl.error = "Server URL required"
                return@setOnClickListener
            }
            if (!url.startsWith("ws://") && !url.startsWith("wss://")) {
                binding.etServerUrl.error = "URL must start with ws:// or wss://"
                return@setOnClickListener
            }

            authManager.saveServerUrl(url)
            if (jwt.isNotBlank()) authManager.saveJwt(jwt)
            Toast.makeText(this, "Configuration saved", Toast.LENGTH_SHORT).show()
        }

        binding.btnStart.setOnClickListener {
            when {
                !Settings.canDrawOverlays(this) -> {
                    Toast.makeText(this, "Grant overlay permission to continue", Toast.LENGTH_LONG).show()
                    startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    )
                }
                authManager.getServerUrl().isBlank() -> {
                    Toast.makeText(this, "Enter server URL first", Toast.LENGTH_SHORT).show()
                }
                else -> requestPermissions()
            }
        }

        binding.btnStop.setOnClickListener {
            stopService(Intent(this, OverlayService::class.java))
            Toast.makeText(this, "Assistant stopped", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        binding.tvOverlayStatus.text = if (Settings.canDrawOverlays(this))
            "Overlay: Granted" else "Overlay: Not granted – tap START"
    }

    private fun requestPermissions() {
        val perms = arrayOf(
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.POST_NOTIFICATIONS
        )
        requestPermissions(perms, RC_PERMISSIONS)
    }

    @Deprecated("Deprecated in API 33 but required for minSdk 34 compat")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RC_PERMISSIONS) {
            startForegroundService(Intent(this, OverlayService::class.java))
            Toast.makeText(this, "OpenClaw started – check for floating avatar", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    companion object {
        private const val RC_PERMISSIONS = 100
    }
}
