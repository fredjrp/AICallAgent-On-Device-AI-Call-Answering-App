package com.aicall.agent.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRouterClientTest {

    @Test
    fun testMissingApiKeyThrowsException() {
        val client = OpenRouterClient(apiKeyProvider = { "" })
        try {
            client.chat(listOf(OpenRouterClient.Message("user", "Hello")))
            assertTrue("Expected IllegalStateException for missing API key", false)
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("API key is missing") == true)
        }
    }

    @Test
    fun testTextSanitizationInTts() {
        val input = "Hello **world**! Here is a [link](https://example.com). *Awesome*."
        val sanitized = KokoroTtsEngine.sanitizeTextForSpeech(input)
        assertEquals("Hello world! Here is a . Awesome.", sanitized)
    }
}
