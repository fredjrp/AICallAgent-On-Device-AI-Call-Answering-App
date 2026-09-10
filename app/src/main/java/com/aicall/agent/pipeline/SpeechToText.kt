package com.aicall.agent.pipeline

/**
 * Clean abstraction boundary for on-device Speech-To-Text (Whisper).
 * Kept free of Android framework dependencies so it can be tested on a plain JVM.
 */
interface SpeechToText {
    val modelName: String
    fun isModelLoaded(): Boolean
    suspend fun transcribe(audioPcm16: ShortArray, sampleRate: Int = 16000): String
}

/**
 * On-device whisper.cpp wrapper.
 * Converts 16-bit mono PCM into float32 array [-1.0, 1.0] and passes to Whisper inference.
 */
class WhisperCppEngine(
    override val modelName: String = "whisper-base.en",
    private val nativeBridge: WhisperNativeBridge? = null
) : SpeechToText {

    interface WhisperNativeBridge {
        fun isLoaded(): Boolean
        fun fullTranscribe(samples: FloatArray): String
    }

    override fun isModelLoaded(): Boolean {
        return nativeBridge?.isLoaded() ?: false
    }

    override suspend fun transcribe(audioPcm16: ShortArray, sampleRate: Int): String {
        if (audioPcm16.isEmpty()) {
            return ""
        }

        // Convert 16-bit PCM shorts to normalized 32-bit floats for Whisper
        val floatSamples = FloatArray(audioPcm16.size)
        for (i in audioPcm16.indices) {
            floatSamples[i] = audioPcm16[i] / 32768.0f
        }

        val bridge = nativeBridge
        return if (bridge != null && bridge.isLoaded()) {
            bridge.fullTranscribe(floatSamples).trim()
        } else {
            // Fallback / simulation when testing on plain JVM or before model is loaded
            ""
        }
    }
}
