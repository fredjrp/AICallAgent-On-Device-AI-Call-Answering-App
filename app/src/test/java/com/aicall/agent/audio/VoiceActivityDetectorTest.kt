package com.aicall.agent.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceActivityDetectorTest {

    @Test
    fun testInitialStateIsIdle() {
        val vad = VoiceActivityDetector()
        assertEquals(VoiceActivityDetector.State.IDLE, vad.currentState)
    }

    @Test
    fun testDetectsSpeechStartOnHighEnergy() {
        val vad = VoiceActivityDetector(energyThreshold = 500.0)
        var speechStarted = false

        vad.listener = object : VoiceActivityDetector.Listener {
            override fun onSpeechStart() {
                speechStarted = true
            }
            override fun onSpeechEnd(utteranceAudio: ShortArray) {}
        }

        // Low energy noise -> still IDLE
        val silence = ShortArray(320) { 10 }
        vad.processFrame(silence)
        assertEquals(VoiceActivityDetector.State.IDLE, vad.currentState)
        assertTrue(!speechStarted)

        // High energy voice -> transitions to SPEECH
        val voice = ShortArray(320) { 3000 }
        vad.processFrame(voice)
        assertEquals(VoiceActivityDetector.State.SPEECH, vad.currentState)
        assertTrue(speechStarted)
    }

    @Test
    fun testDetectsUtteranceEndAfterSilenceGap() {
        val sampleRate = 16000
        val vad = VoiceActivityDetector(
            sampleRate = sampleRate,
            energyThreshold = 500.0,
            minSpeechDurationMs = 100,
            silenceGapDurationMs = 500 // 500ms of silence completes turn
        )

        var capturedUtterance: ShortArray? = null

        vad.listener = object : VoiceActivityDetector.Listener {
            override fun onSpeechStart() {}
            override fun onSpeechEnd(utteranceAudio: ShortArray) {
                capturedUtterance = utteranceAudio
            }
        }

        // 1. Speak for 300ms (4800 samples at 16kHz)
        val voiceChunk = ShortArray(1600) { 2500 }
        repeat(3) { vad.processFrame(voiceChunk) }
        assertEquals(VoiceActivityDetector.State.SPEECH, vad.currentState)

        // 2. Silence for 600ms (9600 samples at 16kHz)
        val silenceChunk = ShortArray(1600) { 15 }
        repeat(6) { vad.processFrame(silenceChunk) }

        // State returns to IDLE and listener called with audio
        assertEquals(VoiceActivityDetector.State.IDLE, vad.currentState)
        assertTrue(capturedUtterance != null)
        assertTrue((capturedUtterance?.size ?: 0) >= 4800)
    }
}
