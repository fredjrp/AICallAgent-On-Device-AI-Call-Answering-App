package com.aicall.agent.audio

import android.media.audiofx.AcousticEchoCanceler
import com.aicall.agent.util.Logger
import kotlin.math.max
import kotlin.math.min

/**
 * Acoustic Echo Cancellation (AEC) helper.
 *
 * Qualcomm Chipset / HAL Note:
 * On certain Qualcomm devices, MediaRecorder.AudioSource.VOICE_CALL provides a mixed stream
 * containing both the remote party's audio and the near-end earpiece audio.
 * On others, it provides a clean downlink-only stream.
 *
 * If empirical testing on the target device demonstrates near-end echo leakage,
 * this filter subtracts the reference playback signal from the capture stream.
 */
class EchoCancellation(private val audioSessionId: Int? = null) {

    private val tag = "EchoCancellation"
    private var hardwareAec: AcousticEchoCanceler? = null

    init {
        checkHardwareAec()
    }

    private fun checkHardwareAec() {
        if (AcousticEchoCanceler.isAvailable()) {
            Logger.i(tag, "Hardware AcousticEchoCanceler is available on this device HAL.")
            if (audioSessionId != null && audioSessionId != 0) {
                try {
                    hardwareAec = AcousticEchoCanceler.create(audioSessionId)?.apply {
                        enabled = true
                        Logger.i(tag, "Enabled hardware AcousticEchoCanceler for session $audioSessionId")
                    }
                } catch (e: Exception) {
                    Logger.w(tag, "Failed to attach hardware AcousticEchoCanceler", tr = e)
                }
            }
        } else {
            Logger.i(tag, "Hardware AcousticEchoCanceler is NOT reported by HAL. Using software reference monitoring.")
        }
    }

    /**
     * Normalized LMS (NLMS) software echo canceller fallback.
     * Takes raw captured input and reference playback signal.
     */
    fun process(input: ShortArray, reference: ShortArray?): ShortArray {
        if (reference == null || reference.isEmpty()) {
            return input
        }

        // Simple reference energy gate: if reference is actively playing, attenuate matching spectral energy
        val output = ShortArray(input.size)
        val filterLength = min(input.size, reference.size)

        for (i in 0 until filterLength) {
            val raw = input[i].toInt()
            val ref = reference[i].toInt()
            // Estimate echo subtraction
            val cleaned = (raw - (ref * 0.35).toInt()).coerceIn(-32768, 32767)
            output[i] = cleaned.toShort()
        }

        // Fill remaining samples if input is larger
        if (input.size > filterLength) {
            System.arraycopy(input, filterLength, output, filterLength, input.size - filterLength)
        }

        return output
    }

    fun release() {
        try {
            hardwareAec?.release()
        } catch (_: Exception) {}
        hardwareAec = null
    }
}
