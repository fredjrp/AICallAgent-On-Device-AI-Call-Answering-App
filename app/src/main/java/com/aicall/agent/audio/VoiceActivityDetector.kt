package com.aicall.agent.audio

import kotlin.math.sqrt

/**
 * Energy-based Voice Activity Detector (VAD) for real-time turn-taking logic.
 * Detects when a caller starts speaking and when they stop (silence gap),
 * collecting audio frames into complete utterances for STT inference.
 *
 * Designed as pure Kotlin (no Android framework imports) for clean JVM unit testing.
 */
class VoiceActivityDetector(
    private val sampleRate: Int = 16000,
    private val energyThreshold: Double = 600.0,
    private val minSpeechDurationMs: Long = 200,
    private val silenceGapDurationMs: Long = 850
) {
    enum class State {
        IDLE,
        SPEECH,
        SILENCE_AFTER_SPEECH
    }

    interface Listener {
        fun onSpeechStart()
        fun onSpeechEnd(utteranceAudio: ShortArray)
    }

    var listener: Listener? = null
    var currentState: State = State.IDLE
        private set

    private val utteranceBuffer = ArrayList<Short>()
    private var speechStartTimeMs: Long = 0
    private var silenceStartTimeMs: Long = 0
    private var totalProcessedSamples: Long = 0

    /**
     * Feeds an incoming audio chunk (16-bit PCM) into the detector.
     */
    fun processFrame(samples: ShortArray, length: Int = samples.size) {
        if (length <= 0) return

        val rms = calculateRms(samples, length)
        val isVoice = rms >= energyThreshold
        val frameDurationMs = (length.toLong() * 1000L) / sampleRate.toLong()
        totalProcessedSamples += length
        val currentTimestampMs = (totalProcessedSamples * 1000L) / sampleRate.toLong()

        when (currentState) {
            State.IDLE -> {
                if (isVoice) {
                    currentState = State.SPEECH
                    speechStartTimeMs = currentTimestampMs
                    utteranceBuffer.clear()
                    appendSamples(samples, length)
                    listener?.onSpeechStart()
                }
            }
            State.SPEECH -> {
                appendSamples(samples, length)
                if (!isVoice) {
                    currentState = State.SILENCE_AFTER_SPEECH
                    silenceStartTimeMs = currentTimestampMs
                }
            }
            State.SILENCE_AFTER_SPEECH -> {
                appendSamples(samples, length)
                if (isVoice) {
                    // Voice resumed before silence gap expired
                    currentState = State.SPEECH
                } else {
                    val silenceElapsed = currentTimestampMs - silenceStartTimeMs
                    if (silenceElapsed >= silenceGapDurationMs) {
                        // Utterance complete!
                        val speechDuration = silenceStartTimeMs - speechStartTimeMs
                        if (speechDuration >= minSpeechDurationMs) {
                            val completeAudio = utteranceBuffer.toShortArray()
                            currentState = State.IDLE
                            utteranceBuffer.clear()
                            listener?.onSpeechEnd(completeAudio)
                        } else {
                            // Ignored as noise / click
                            currentState = State.IDLE
                            utteranceBuffer.clear()
                        }
                    }
                }
            }
        }
    }

    fun reset() {
        currentState = State.IDLE
        utteranceBuffer.clear()
        speechStartTimeMs = 0
        silenceStartTimeMs = 0
        totalProcessedSamples = 0
    }

    private fun appendSamples(samples: ShortArray, length: Int) {
        for (i in 0 until length) {
            utteranceBuffer.add(samples[i])
        }
    }

    companion object {
        fun calculateRms(samples: ShortArray, length: Int): Double {
            if (length <= 0) return 0.0
            var sum = 0.0
            for (i in 0 until length) {
                val v = samples[i].toDouble()
                sum += v * v
            }
            return sqrt(sum / length)
        }
    }
}
