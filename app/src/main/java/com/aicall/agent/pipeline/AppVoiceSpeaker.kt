package com.aicall.agent.pipeline

import android.content.Context
import android.media.AudioAttributes
import com.aicall.agent.data.BusinessKnowledgeManager
import com.aicall.agent.util.Logger
import com.aicall.agent.util.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Singleton Voice Speaker for application-level UI voice interactions and voice assistant testing.
 */
object AppVoiceSpeaker {

    private const val TAG = "AppVoiceSpeaker"
    private var engine: AndroidTtsEngine? = null

    @Synchronized
    fun getEngine(context: Context): AndroidTtsEngine {
        if (engine == null) {
            engine = AndroidTtsEngine(context.applicationContext)
        }
        return engine!!
    }

    /**
     * Speaks the given text out loud through the device loudspeaker.
     */
    fun speak(
        context: Context,
        text: String,
        onStart: () -> Unit = {},
        onDone: () -> Unit = {}
    ) {
        val tts = getEngine(context)
        CoroutineScope(Dispatchers.Main).launch {
            onStart()
            tts.speakDirect(
                text = text,
                usage = AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE,
                onDone = onDone
            )
        }
    }

    /**
     * Comprehensive assistant voice test:
     * 1. If OpenRouter API key is set, tests the live LLM with a 1-sentence greeting prompt.
     * 2. If no key is set or network is unavailable, falls back to a clean localized greeting.
     * 3. Speaks the response out loud with live subtitle callbacks.
     */
    fun testAssistant(
        context: Context,
        scope: CoroutineScope,
        onStatusChange: (status: String, isSpeaking: Boolean) -> Unit,
        onFinished: () -> Unit
    ) {
        scope.launch {
            val prefs = PreferencesManager.getInstance(context)
            val kb = BusinessKnowledgeManager.getInstance(context)
            val assistantName = kb.assistantName
            val businessName = kb.businessName

            var speechText = "Hello! I'm $assistantName, and I'm standing by to answer your calls for $businessName."

            val apiKey = prefs.openRouterApiKey.trim()
            if (apiKey.isNotEmpty()) {
                onStatusChange("Connecting to OpenRouter (${prefs.selectedModel.substringAfterLast("/")})...", true)
                try {
                    val client = OpenRouterClient(
                        apiKeyProvider = { apiKey },
                        modelProvider = { prefs.selectedModel }
                    )

                    val testPrompt = listOf(
                        OpenRouterClient.Message(
                            role = "system",
                            content = "You are $assistantName, a professional telephone voice assistant for '$businessName'. Respond with exactly one short, warm sentence introducing yourself and confirming you are ready to take incoming calls. Do not use asterisks or quotes."
                        ),
                        OpenRouterClient.Message(
                            role = "user",
                            content = "Please introduce yourself."
                        )
                    )

                    val response = withTimeoutOrNull(4000) {
                        client.chatWithUsage(testPrompt, systemPrompt = "")
                    }

                    if (response != null && response.replyText.isNotBlank()) {
                        speechText = KokoroTtsEngine.sanitizeTextForSpeech(response.replyText)
                        Logger.i(TAG, "OpenRouter test returned: \"$speechText\" (${response.latencyMs}ms)")
                    }
                } catch (e: Exception) {
                    Logger.w(TAG, "OpenRouter test ping failed (${e.message}), using local greeting fallback")
                }
            }

            // Speak speechText out loud through the device's main media/notification speaker
            onStatusChange("\"$speechText\"", true)

            val tts = getEngine(context)
            tts.speakDirect(
                text = speechText,
                usage = AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE,
                onDone = {
                    scope.launch(Dispatchers.Main) {
                        onStatusChange("", false)
                        onFinished()
                    }
                }
            )
        }
    }

    fun stop() {
        engine?.stop()
    }
}
