package com.aicall.agent.pipeline

/**
 * Clean abstraction boundary for on-device Text-To-Speech (Kokoro / Piper).
 * Kept free of Android framework dependencies so it can be tested on a plain JVM.
 */
interface TextToSpeech {
    val engineName: String
    val sampleRate: Int
    fun isModelLoaded(): Boolean
    suspend fun synthesize(text: String): ShortArray
}

/**
 * On-device Kokoro / Piper TTS engine wrapper.
 */
class KokoroTtsEngine(
    override val engineName: String = "kokoro-v1.0",
    override val sampleRate: Int = 22050,
    private val nativeBridge: TtsNativeBridge? = null
) : TextToSpeech {

    interface TtsNativeBridge {
        fun isLoaded(): Boolean
        fun generateSpeech(text: String): ShortArray
    }

    override fun isModelLoaded(): Boolean {
        return nativeBridge?.isLoaded() ?: false
    }

    override suspend fun synthesize(text: String): ShortArray {
        val sanitized = sanitizeTextForSpeech(text)
        if (sanitized.isEmpty()) {
            return ShortArray(0)
        }

        val bridge = nativeBridge
        return if (bridge != null && bridge.isLoaded()) {
            bridge.generateSpeech(sanitized)
        } else {
            ShortArray(0)
        }
    }

    companion object {
        fun sanitizeTextForSpeech(input: String): String {
            return input
                .replace(Regex("[*#_`~]"), "") // Remove markdown
                .replace(Regex("\\[.*?\\]\\(.*?\\)"), "") // Remove markdown links
                .replace(Regex("[\\r\\n]+"), " ")
                .trim()
        }
    }
}
