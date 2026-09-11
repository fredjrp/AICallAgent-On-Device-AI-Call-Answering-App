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
            var text = input
                .replace(Regex("[*#_`~]"), "") // Remove markdown formatting
                .replace(Regex("\\[.*?\\]\\(.*?\\)"), "") // Remove markdown links
                .replace(Regex("[\\r\\n]+"), " ")

            // Normalize currencies for speech
            // e.g. KES 2,500 or KES 2500 -> 2500 shillings
            text = text.replace(Regex("(?i)KES\\s*([0-9,]+)")) { match ->
                val num = match.groupValues[1].replace(",", "")
                "$num shillings"
            }
            text = text.replace(Regex("\\$([0-9,]+)")) { match ->
                val num = match.groupValues[1].replace(",", "")
                "$num dollars"
            }

            // Normalize international phone numbers for spoken readability
            // e.g. +254 712... -> "plus 2 5 4, 7 1 2..."
            text = text.replace(Regex("\\+([0-9]{1,3})\\s*([0-9]{3,})")) { match ->
                val country = match.groupValues[1].map { "$it " }.joinToString("")
                val rest = match.groupValues[2].chunked(3).joinToString(", ") { chunk ->
                    chunk.map { "$it " }.joinToString("")
                }
                "plus $country, $rest"
            }

            return text.replace(Regex("\\s+"), " ").trim()
        }
    }
}
