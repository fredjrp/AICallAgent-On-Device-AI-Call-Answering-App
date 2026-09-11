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
 * Captures usage metadata (prompt_tokens, completion_tokens, estimated cost)
 * for complete transparency. Kept free of Android framework dependencies for clean JVM testing.
 */
class OpenRouterClient(
    private val apiKeyProvider: () -> String,
    private val modelProvider: () -> String = { "meta-llama/llama-3.3-70b-instruct" },
    private val okHttpClient: OkHttpClient = defaultClient()
) {
    data class Message(
        val role: String,
        val content: String
    )

    data class ChatResult(
        val replyText: String,
        val promptTokens: Int = 0,
        val completionTokens: Int = 0,
        val totalTokens: Int = 0,
        val estimatedCostUsd: Double = 0.0,
        val latencyMs: Long = 0L
    )

    companion object {
        private const val API_URL = "https://openrouter.ai/api/v1/chat/completions"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        const val DEFAULT_SYSTEM_PROMPT =
            "You are an on-device AI voice agent answering a live telephone call for Front Desk. " +
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
     * Sends conversation messages to OpenRouter and returns the structured ChatResult
     * including usage tokens and latency.
     */
    @Throws(IOException::class)
    fun chatWithUsage(
        conversationHistory: List<Message>,
        systemPrompt: String = DEFAULT_SYSTEM_PROMPT
    ): ChatResult {
        val apiKey = apiKeyProvider().trim()
        if (apiKey.isEmpty()) {
            throw IllegalStateException("OpenRouter API key is missing. Please enter your API key in settings.")
        }

        val startTime = System.currentTimeMillis()
        val currentModel = modelProvider().ifBlank { "meta-llama/llama-3.3-70b-instruct" }

        val messagesJson = JSONArray()
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
            put("model", currentModel)
            put("messages", messagesJson)
            put("temperature", 0.7)
            put("max_tokens", 120) // Brief response for natural phone turn-taking
        }

        val request = Request.Builder()
            .url(API_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("HTTP-Referer", "https://github.com/fredjrp/AICallAgent-On-Device-AI-Call-Answering-App")
            .addHeader("X-Title", "AICallAgent")
            .post(requestBodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = okHttpClient.newCall(request).execute()
        val latencyMs = System.currentTimeMillis() - startTime

        response.use { resp ->
            val bodyString = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                throw IOException("OpenRouter API error ${resp.code}: $bodyString")
            }

            val json = JSONObject(bodyString)
            var replyText = ""
            val choices = json.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val firstChoice = choices.getJSONObject(0)
                val messageObj = firstChoice.optJSONObject("message")
                replyText = (messageObj?.optString("content") ?: "").trim()
            }

            // Extract usage tokens
            var promptTokens = 0
            var completionTokens = 0
            var totalTokens = 0
            val usageObj = json.optJSONObject("usage")
            if (usageObj != null) {
                promptTokens = usageObj.optInt("prompt_tokens", 0)
                completionTokens = usageObj.optInt("completion_tokens", 0)
                totalTokens = usageObj.optInt("total_tokens", promptTokens + completionTokens)
            }

            // Estimate cost based on standard blended rate (~$0.60 / 1M prompt, ~$1.80 / 1M completion)
            val estimatedCost = (promptTokens * 0.0000006) + (completionTokens * 0.0000018)

            return ChatResult(
                replyText = replyText,
                promptTokens = promptTokens,
                completionTokens = completionTokens,
                totalTokens = totalTokens,
                estimatedCostUsd = estimatedCost,
                latencyMs = latencyMs
            )
        }
    }

    /**
     * Backward-compatible simple chat call returning String reply directly.
     */
    @Throws(IOException::class)
    fun chat(
        conversationHistory: List<Message>,
        systemPrompt: String = DEFAULT_SYSTEM_PROMPT
    ): String {
        return chatWithUsage(conversationHistory, systemPrompt).replyText
    }
}
