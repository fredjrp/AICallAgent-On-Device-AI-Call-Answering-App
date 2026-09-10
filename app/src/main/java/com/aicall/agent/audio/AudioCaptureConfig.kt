package com.aicall.agent.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

/**
 * Audio capture parameters optimized for on-device Whisper inference (16kHz, 16-bit Mono PCM).
 */
object AudioCaptureConfig {
    const val SAMPLE_RATE = 16000 // Required by Whisper
    const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    const val BYTES_PER_SAMPLE = 2

    // Default to VOICE_CALL (privileged audio source capturing both uplink & downlink)
    const val DEFAULT_AUDIO_SOURCE = MediaRecorder.AudioSource.VOICE_CALL

    fun calculateMinBufferSize(sampleRate: Int = SAMPLE_RATE): Int {
        val minSize = AudioRecord.getMinBufferSize(sampleRate, CHANNEL_CONFIG, AUDIO_FORMAT)
        return if (minSize > 0) minSize else 1024 * 2
    }
}
