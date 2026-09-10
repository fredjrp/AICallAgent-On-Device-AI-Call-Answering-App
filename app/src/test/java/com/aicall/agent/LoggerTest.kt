package com.aicall.agent.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoggerTest {

    @Test
    fun testStructuredLoggingSessionId() {
        var capturedEntry: Logger.LogEntry? = null
        val testListener: (Logger.LogEntry) -> Unit = { entry ->
            capturedEntry = entry
        }

        Logger.addListener(testListener)
        try {
            Logger.i("TestTag", "Incoming test message", sessionId = "session_xyz")
            val entry = capturedEntry
            assertTrue(entry != null)
            assertEquals("session_xyz", entry?.sessionId)
            assertEquals("TestTag", entry?.tag)
            assertEquals("Incoming test message", entry?.message)
            assertTrue(entry?.formattedText?.contains("[session_xyz]") == true)
        } finally {
            Logger.removeListener(testListener)
        }
    }
}
