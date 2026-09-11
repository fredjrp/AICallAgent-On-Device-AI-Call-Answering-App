package com.aicall.agent.util

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaRecorder
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Manages encrypted and general preferences for AICallAgent.
 * Enforces secure storage for OpenRouter API keys and call configuration.
 */
class PreferencesManager(context: Context) {

    private val sharedPreferences: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "aicall_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Logger.w("PreferencesManager", "Falling back to standard shared preferences due to Keystore error", tr = e)
        context.getSharedPreferences("aicall_fallback_prefs", Context.MODE_PRIVATE)
    }

    var openRouterApiKey: String
        get() = sharedPreferences.getString(KEY_OPENROUTER_API_KEY, "") ?: ""
        set(value) = sharedPreferences.edit().putString(KEY_OPENROUTER_API_KEY, value.trim()).apply()

    var systemPrompt: String
        get() = sharedPreferences.getString(KEY_SYSTEM_PROMPT, DEFAULT_SYSTEM_PROMPT) ?: DEFAULT_SYSTEM_PROMPT
        set(value) = sharedPreferences.edit().putString(KEY_SYSTEM_PROMPT, value.trim()).apply()

    var selectedModel: String
        get() = sharedPreferences.getString(KEY_SELECTED_MODEL, "meta-llama/llama-3.3-70b-instruct") ?: "meta-llama/llama-3.3-70b-instruct"
        set(value) = sharedPreferences.edit().putString(KEY_SELECTED_MODEL, value.trim()).apply()

    var isAutoAnswerEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_AUTO_ANSWER_ENABLED, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_AUTO_ANSWER_ENABLED, value).apply()

    var isAgentPaused: Boolean
        get() = sharedPreferences.getBoolean(KEY_AGENT_PAUSED, false)
        set(value) = sharedPreferences.edit().putBoolean(KEY_AGENT_PAUSED, value).apply()

    var answerDelayRings: Int
        get() = sharedPreferences.getInt(KEY_ANSWER_DELAY_RINGS, 2)
        set(value) = sharedPreferences.edit().putInt(KEY_ANSWER_DELAY_RINGS, value).apply()

    var speakThroughEarpiece: Boolean
        get() = sharedPreferences.getBoolean(KEY_SPEAK_THROUGH_EARPIECE, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_SPEAK_THROUGH_EARPIECE, value).apply()

    var saveTranscripts: Boolean
        get() = sharedPreferences.getBoolean(KEY_SAVE_TRANSCRIPTS, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_SAVE_TRANSCRIPTS, value).apply()

    var isOnboardingCompleted: Boolean
        get() = sharedPreferences.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        set(value) = sharedPreferences.edit().putBoolean(KEY_ONBOARDING_COMPLETED, value).apply()

    var audioSource: Int
        get() = sharedPreferences.getInt(KEY_AUDIO_SOURCE, MediaRecorder.AudioSource.VOICE_CALL)
        set(value) = sharedPreferences.edit().putInt(KEY_AUDIO_SOURCE, value).apply()

    var isEchoCancellationEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_ECHO_CANCELLATION, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_ECHO_CANCELLATION, value).apply()

    var autoDisconnectAfterSeconds: Int
        get() = sharedPreferences.getInt(KEY_AUTO_DISCONNECT_SECONDS, 120)
        set(value) = sharedPreferences.edit().putInt(KEY_AUTO_DISCONNECT_SECONDS, value).apply()

    var dailyBriefingHour: Int
        get() = sharedPreferences.getInt(KEY_DAILY_BRIEFING_HOUR, 18) // 6:00 PM default
        set(value) = sharedPreferences.edit().putInt(KEY_DAILY_BRIEFING_HOUR, value).apply()

    companion object {
        private const val KEY_OPENROUTER_API_KEY = "key_openrouter_api_key"
        private const val KEY_SYSTEM_PROMPT = "key_system_prompt"
        private const val KEY_SELECTED_MODEL = "key_selected_model"
        private const val KEY_AUTO_ANSWER_ENABLED = "key_auto_answer_enabled"
        private const val KEY_AGENT_PAUSED = "key_agent_paused"
        private const val KEY_ANSWER_DELAY_RINGS = "key_answer_delay_rings"
        private const val KEY_SPEAK_THROUGH_EARPIECE = "key_speak_through_earpiece"
        private const val KEY_SAVE_TRANSCRIPTS = "key_save_transcripts"
        private const val KEY_ONBOARDING_COMPLETED = "key_onboarding_completed"
        private const val KEY_AUDIO_SOURCE = "key_audio_source"
        private const val KEY_ECHO_CANCELLATION = "key_echo_cancellation"
        private const val KEY_AUTO_DISCONNECT_SECONDS = "key_auto_disconnect_seconds"
        private const val KEY_DAILY_BRIEFING_HOUR = "key_daily_briefing_hour"

        const val DEFAULT_SYSTEM_PROMPT =
            "You are a helpful and polite voice AI phone assistant for Front Desk. " +
            "Answer the caller concisely, take messages, provide necessary info, and let them know the team will get back to them."

        @Volatile
        private var instance: PreferencesManager? = null

        fun getInstance(context: Context): PreferencesManager {
            return instance ?: synchronized(this) {
                instance ?: PreferencesManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
