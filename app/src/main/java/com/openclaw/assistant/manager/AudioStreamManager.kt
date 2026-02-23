package com.openclaw.assistant.manager

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.openclaw.assistant.domain.models.AudioMetaMessage
import com.openclaw.assistant.util.Constants
import com.openclaw.assistant.util.OpusEncoderWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioStreamManager @Inject constructor() {

    private var audioRecord: AudioRecord? = null
    private var encodingJob: Job? = null
    private var opus: OpusEncoderWrapper? = null

    // Min buffer >= 2 Opus frames to avoid underrun
    private val minBufSize = maxOf(
        AudioRecord.getMinBufferSize(
            Constants.AUDIO_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ),
        Constants.OPUS_FRAME_BYTES * 4
    )

    fun startStreaming(scope: CoroutineScope, wsClient: SecureWebSocketClient) {
        if (encodingJob?.isActive == true) return

        opus = OpusEncoderWrapper()

        // Announce audio format to server
        val meta = AudioMetaMessage(
            format = if (opus!!.isOpusActive) "opus" else "pcm_s16le",
            sampleRate = Constants.AUDIO_SAMPLE_RATE,
            channels = Constants.AUDIO_CHANNEL,
            bitrate = Constants.OPUS_BITRATE
        )
        wsClient.sendVisionFrame(Json.encodeToString(AudioMetaMessage.serializer(), meta))

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                Constants.AUDIO_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufSize
            ).apply { startRecording() }
        } catch (e: SecurityException) {
            Log.e(TAG, "Audio permission denied: ${e.message}")
            return
        }

        encodingJob = scope.launch(Dispatchers.IO) {
            // Read exactly one Opus frame at a time (320 samples = 20ms)
            val frame = ShortArray(Constants.OPUS_FRAME_SAMPLES)
            while (isActive && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val read = audioRecord?.read(frame, 0, frame.size) ?: 0
                if (read > 0) {
                    val encoded = opus?.encode(if (read == frame.size) frame else frame.copyOf(read))
                    encoded?.let { wsClient.sendAudioChunk(it) }
                }
            }
        }
        Log.i(TAG, "Audio streaming started (Opus=${opus!!.isOpusActive})")
    }

    fun stop() {
        encodingJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        opus?.release()
        opus = null
        Log.i(TAG, "Audio streaming stopped")
    }

    companion object {
        private const val TAG = "AudioStreamManager"
    }
}
