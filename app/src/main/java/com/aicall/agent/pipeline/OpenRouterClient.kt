package com.aicall.agent.pipeline

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * HTTPS client for OpenRouter's Chat Completion API.
 * This is the ONLY network call in the entire AICallAgent pipeline.
 *
 * Kept free of Android framework dependencies for clean JVM testability.
 */
class OpenRouterClient(
    private val apiKeyProvider: () -> String,
    private val model: String = "meta-llama/llama-3.3-70b-instruct",
    private val okHttpClient: OkHttpClient = defaultClient()
) {
    data class Message(
        val role: String,
        val content: String
    )

    companion object {
        private const val API_URL = "https://openrouter.ai/api/v1/chat/completions"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        const val DEFAULT_SYSTEM_PROMPT =
            "You are an on-device AI voice agent answering a live telephone call. " +
            "Keep your responses concise, helpful, and natural (strictly 1 to 2 short sentences). " +
            "Never use markdown formatting, bullet points, asterisks, or emojis, as your text is fed directly into a text-to-speech synthesizer."

        fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build()
        }
    }

    /**
     * Sends conversation messages to OpenRouter and returns the text response.
     */
    @Throws(IOException::class)
    fun chat(
        conversationHistory: List<Message>,
        systemPrompt: String = DEFAULT_SYSTEM_PROMPT
    ): String {
        val apiKey = apiKeyProvider().trim()
        if (apiKey.isEmpty()) {
            throw IllegalStateException("OpenRouter API key is missing. Please enter your API key in settings.")
        }

        val messagesJson = JSONArray()
        // Always include system prompt first
        messagesJson.put(JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        })

        for (msg in conversationHistory) {
            messagesJson.put(JSONObject().apply {
                put("role", msg.role)
                put("content", msg.content)
            })
        }

        val requestBodyJson = JSONObject().apply {
            put("model", model)
            put("messages", messagesJson)
            put("temperature", 0.7)
            put("max_tokens", 120) // Kept brief for telephony audio latency
        }

        val request = Request.Builder()
            .url(API_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("HTTP-Referer", "https://github.com/chenxiaolong/BCR")
            .addHeader("X-Title", "AICallAgent")
            .post(requestBodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = okHttpClient.newCall(request).execute()
        response.use { resp ->
            val bodyString = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                throw IOException("OpenRouter API error ${resp.code}: $bodyString")
            }

            val json = JSONObject(bodyString)
            val choices = json.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val firstChoice = choices.getJSONObject(0)
                val messageObj = firstChoice.optJSONObject("message")
                val text = messageObj?.optString("content") ?: ""
                return text.trim()
            }
            return ""
        }
    }
}
