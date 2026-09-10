package com.aicall.agent.telecom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallSessionTest {

    @Test
    fun testCallSessionDurationCalculation() {
        val startTime = System.currentTimeMillis() - 5000 // 5 seconds ago
        val session = CallSession(
            sessionId = "test_session_123",
            phoneNumber = "+254712345678",
            state = 4, // STATE_ACTIVE
            startTimeMs = startTime
        )

        // Session is ongoing
        assertTrue(session.durationSeconds >= 5)

        // Session ends
        session.endTimeMs = startTime + 12000 // 12 seconds
        assertEquals(12L, session.durationSeconds)
    }

    @Test
    fun testCallSessionFields() {
        val session = CallSession(
            sessionId = "session_abc",
            phoneNumber = "+1234567890",
            state = 2, // STATE_RINGING
            startTimeMs = 1000L
        )

        assertEquals("session_abc", session.sessionId)
        assertEquals("+1234567890", session.phoneNumber)
        assertEquals(2, session.state)

        session.recordingPath = "/sdcard/test.wav"
        assertEquals("/sdcard/test.wav", session.recordingPath)
    }
}
