package com.aicall.agent.pipeline

import com.aicall.agent.audio.CallAudioPlayback
import com.aicall.agent.audio.VoiceActivityDetector
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationOrchestratorTest {

    @Test
    fun testInitialOrchestratorState() {
        val stt = mockk<SpeechToText>(relaxed = true)
        val llm = mockk<OpenRouterClient>(relaxed = true)
        val tts = mockk<TextToSpeech>(relaxed = true)
        val playback = mockk<CallAudioPlayback>(relaxed = true)

        val orchestrator = ConversationOrchestrator(stt, llm, tts, playback)
        assertEquals(ConversationOrchestrator.ConversationState.IDLE, orchestrator.stateFlow.value)

        orchestrator.start("session_1")
        assertEquals(ConversationOrchestrator.ConversationState.LISTENING, orchestrator.stateFlow.value)

        orchestrator.stop()
        assertEquals(ConversationOrchestrator.ConversationState.IDLE, orchestrator.stateFlow.value)
    }
}
