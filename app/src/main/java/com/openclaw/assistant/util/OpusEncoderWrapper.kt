package com.openclaw.assistant.util

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import com.openclaw.assistant.util.Constants.AUDIO_CHANNEL
import com.openclaw.assistant.util.Constants.AUDIO_SAMPLE_RATE
import com.openclaw.assistant.util.Constants.OPUS_BITRATE

/**
 * Opus encoder using Android's built-in MediaCodec software encoder.
 * Available on API 29+ (c2.android.opus.encoder).
 * Falls back to raw PCM (little-endian) when unavailable.
 */
class OpusEncoderWrapper {

    private var codec: MediaCodec? = null
    val isOpusActive: Boolean get() = codec != null

    init {
        tryInitOpus()
    }

    private fun tryInitOpus() {
        try {
            val c = MediaCodec.createEncoderByType(MIME_OPUS)
            val fmt = MediaFormat.createAudioFormat(MIME_OPUS, AUDIO_SAMPLE_RATE, AUDIO_CHANNEL).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, OPUS_BITRATE)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, Constants.OPUS_FRAME_BYTES * 4)
                // Opus-specific complexity (0-10); 10 = best quality
                setInteger(KEY_COMPLEXITY, 7)
            }
            c.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            c.start()
            codec = c
            Log.i(TAG, "Opus MediaCodec encoder started")
        } catch (e: Exception) {
            Log.w(TAG, "Opus encoder unavailable, using raw PCM fallback: ${e.message}")
            codec = null
        }
    }

    /**
     * Encode a frame of PCM 16-bit mono samples.
     * Returns Opus-compressed bytes, or raw PCM bytes as fallback.
     */
    fun encode(pcm: ShortArray): ByteArray? {
        val c = codec ?: return pcm.toLEByteArray()

        return try {
            val inputBytes = pcm.toLEByteArray()

            // Feed input buffer
            val inIdx = c.dequeueInputBuffer(CODEC_TIMEOUT_US)
            if (inIdx < 0) return null
            val inBuf = c.getInputBuffer(inIdx) ?: return null
            inBuf.clear()
            inBuf.put(inputBytes)
            c.queueInputBuffer(inIdx, 0, inputBytes.size, System.nanoTime() / 1000L, 0)

            // Drain output buffer
            val info = MediaCodec.BufferInfo()
            val outIdx = c.dequeueOutputBuffer(info, CODEC_TIMEOUT_US)
            if (outIdx < 0) return null
            val outBuf = c.getOutputBuffer(outIdx) ?: run {
                c.releaseOutputBuffer(outIdx, false)
                return null
            }
            val result = ByteArray(info.size)
            outBuf.position(info.offset)
            outBuf.get(result)
            c.releaseOutputBuffer(outIdx, false)
            result
        } catch (e: Exception) {
            Log.e(TAG, "Encode error: ${e.message}")
            null
        }
    }

    fun release() {
        try {
            codec?.stop()
            codec?.release()
        } catch (_: Exception) {}
        codec = null
    }

    private fun ShortArray.toLEByteArray(): ByteArray {
        val out = ByteArray(size * 2)
        for (i in indices) {
            out[i * 2] = (this[i].toInt() and 0xFF).toByte()
            out[i * 2 + 1] = (this[i].toInt() ushr 8 and 0xFF).toByte()
        }
        return out
    }

    companion object {
        private const val TAG = "OpusEncoder"
        private const val MIME_OPUS = "audio/opus"
        private const val CODEC_TIMEOUT_US = 20_000L // 20ms
        private const val KEY_COMPLEXITY = "complexity"
    }
}
