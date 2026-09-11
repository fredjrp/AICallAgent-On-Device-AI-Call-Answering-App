package com.aicall.agent.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.aicall.agent.util.Logger
import org.json.JSONArray
import org.json.JSONObject

/**
 * Encrypted on-device knowledge base for business hours, services, prices, and FAQs.
 *
 * HARD REQUIREMENT:
 * This data is stored strictly on-device with zero cloud backup, zero network sync,
 * and no export endpoint. It is only read locally to assemble the system prompt
 * during live telephone calls.
 */
class BusinessKnowledgeManager(context: Context) {

    data class ServiceItem(
        val id: String,
        val name: String,
        val price: String,
        val description: String = ""
    )

    private val prefs = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "aicall_local_business_kb",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Logger.w("BusinessKnowledgeManager", "EncryptedSharedPreferences fallback for business KB", tr = e)
        context.getSharedPreferences("aicall_local_business_kb_fallback", Context.MODE_PRIVATE)
    }

    var assistantName: String
        get() = prefs.getString(KEY_ASSISTANT_NAME, "Linda") ?: "Linda"
        set(value) = prefs.edit().putString(KEY_ASSISTANT_NAME, value.trim().ifEmpty { "Linda" }).apply()

    var businessName: String
        get() = prefs.getString(KEY_BUSINESS_NAME, "Front Desk") ?: "Front Desk"
        set(value) = prefs.edit().putString(KEY_BUSINESS_NAME, value.trim()).apply()

    var businessHours: String
        get() = prefs.getString(
            KEY_BUSINESS_HOURS,
            "Monday to Friday 8:00 AM - 6:00 PM, Saturday 9:00 AM - 3:00 PM, Sunday closed"
        ) ?: ""
        set(value) = prefs.edit().putString(KEY_BUSINESS_HOURS, value.trim()).apply()

    var policiesAndFaqs: String
        get() = prefs.getString(
            KEY_POLICIES_FAQS,
            "Free parking on-site. Standard booking slot is 45 minutes. Cancellations require 24 hours advance notice."
        ) ?: ""
        set(value) = prefs.edit().putString(KEY_POLICIES_FAQS, value.trim()).apply()

    var customGreeting: String
        get() = prefs.getString(
            KEY_CUSTOM_GREETING,
            "Hello, thank you for calling Front Desk. How may I assist you today?"
        ) ?: ""
        set(value) = prefs.edit().putString(KEY_CUSTOM_GREETING, value.trim()).apply()

    var callEndingInstructions: String
        get() = prefs.getString(
            KEY_CALL_ENDING,
            "When caller is finished, politely thank them, confirm any appointment details or messages taken, and let them know the call will end."
        ) ?: ""
        set(value) = prefs.edit().putString(KEY_CALL_ENDING, value.trim()).apply()

    fun getServices(): List<ServiceItem> {
        val raw = prefs.getString(KEY_SERVICES_JSON, null)
        if (raw.isNullOrEmpty()) {
            return defaultServices()
        }
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<ServiceItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    ServiceItem(
                        id = obj.optString("id", i.toString()),
                        name = obj.optString("name"),
                        price = obj.optString("price"),
                        description = obj.optString("description")
                    )
                )
            }
            list
        } catch (_: Exception) {
            defaultServices()
        }
    }

    fun saveServices(services: List<ServiceItem>) {
        val arr = JSONArray()
        for (item in services) {
            arr.put(JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("price", item.price)
                put("description", item.description)
            })
        }
        prefs.edit().putString(KEY_SERVICES_JSON, arr.toString()).apply()
    }

    /**
     * Dynamically constructs the complete, high-fidelity system prompt incorporating
     * real on-device business parameters so the agent never hallucinates pricing/hours.
     *
     * Includes:
     *  - Few-shot dialogue anchors for the four most common small-business call scenarios.
     *  - Hard negative constraints that prevent markdown, filler, and unnatural TTS output.
     *  - Phonetic guidance for numbers and currency (avoids robot-reading digits).
     */
    fun buildSystemPrompt(): String {
        val servicesList = getServices().joinToString("; ") { "${it.name}: ${it.price}" }

        return buildString {
            // ── Core identity ──────────────────────────────────────────────
            append("You are $assistantName, the live telephone voice assistant for '$businessName'. ")
            append("You answer incoming phone calls naturally, warmly, and concisely. ")
            append("Speak in complete, natural sentences exactly as a human receptionist would. ")
            append("Each of your responses must be one to two spoken sentences maximum. ")
            append("\n\n")

            // ── Hard output constraints (TTS safety) ──────────────────────
            append("STRICT RULES — never break these:\n")
            append("- Never use markdown: no asterisks, no bullet points, no headings, no backticks.\n")
            append("- Never use filler openers like 'Certainly', 'Of course', 'Great question', or 'Absolutely'.\n")
            append("- Never repeat the caller's question back to them before answering.\n")
            append("- Never answer in more than two sentences per turn.\n")
            append("- Speak phone numbers one digit at a time. Speak prices in words, for example: two thousand five hundred shillings.\n")
            append("\n")

            // ── Live business facts ───────────────────────────────────────
            append("OPERATING HOURS: $businessHours.\n")
            if (servicesList.isNotEmpty()) {
                append("SERVICES AND PRICING: $servicesList.\n")
            }
            if (policiesAndFaqs.isNotBlank()) {
                append("POLICIES AND DETAILS: $policiesAndFaqs.\n")
            }
            append("GREETING: $customGreeting\n")
            append("CALL CLOSING GUIDANCE: $callEndingInstructions\n")
            append("\n")

            // ── Few-shot dialogue anchors ─────────────────────────────────
            append("EXAMPLE CONVERSATIONS:\n")
            append("\n")
            append("Example 1 — Hours inquiry:\n")
            append("Caller: What are your operating hours?\n")
            append("Agent: We are open $businessHours. Is there anything else I can help you with?\n")
            append("\n")
            append("Example 2 — Pricing inquiry:\n")
            append("Caller: How much does a standard appointment cost?\n")
            val firstService = getServices().firstOrNull()
            if (firstService != null) {
                append("Agent: A ${firstService.name} is ${firstService.price}. Shall I book one for you?\n")
            } else {
                append("Agent: Please hold on and I will get the pricing details for you right away.\n")
            }
            append("\n")
            append("Example 3 — Booking request:\n")
            append("Caller: I would like to book an appointment for Saturday.\n")
            append("Agent: I can note that down. Could I please get your name so I can pass it to the team?\n")
            append("\n")
            append("Example 4 — Human hand-off request:\n")
            append("Caller: Can I speak to the manager please?\n")
            append("Agent: The manager is not available right now, but I can take your name and message and make sure they call you back promptly.\n")
            append("\n")
            append("Example 5 — Out of hours:\n")
            append("Caller: Are you open right now?\n")
            append("Agent: We are currently outside our business hours, but please leave your name and I will make sure the team reaches out to you first thing.\n")
        }
    }

    fun isConfigured(): Boolean {
        return businessName.isNotBlank() && getServices().isNotEmpty()
    }

    private fun defaultServices(): List<ServiceItem> {
        return listOf(
            ServiceItem("1", "Standard Appointment", "KES 2,500", "45 min consultation"),
            ServiceItem("2", "Weekend Appointment", "KES 3,000", "Saturday priority slot"),
            ServiceItem("3", "Follow-up / Check-in", "KES 1,500", "20 min quick review")
        )
    }

    companion object {
        private const val KEY_ASSISTANT_NAME = "kb_assistant_name"
        private const val KEY_BUSINESS_NAME = "kb_business_name"
        private const val KEY_BUSINESS_HOURS = "kb_business_hours"
        private const val KEY_POLICIES_FAQS = "kb_policies_faqs"
        private const val KEY_SERVICES_JSON = "kb_services_json"
        private const val KEY_CUSTOM_GREETING = "kb_custom_greeting"
        private const val KEY_CALL_ENDING = "kb_call_ending"

        @Volatile
        private var instance: BusinessKnowledgeManager? = null

        fun getInstance(context: Context): BusinessKnowledgeManager {
            return instance ?: synchronized(this) {
                instance ?: BusinessKnowledgeManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
