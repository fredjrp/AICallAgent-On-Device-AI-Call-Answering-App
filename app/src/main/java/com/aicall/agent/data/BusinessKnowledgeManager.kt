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
     */
    fun buildSystemPrompt(): String {
        val servicesList = getServices().joinToString("; ") { "${it.name}: ${it.price}" }

        return buildString {
            append("You are the live telephone voice assistant for '$businessName'. ")
            append("You answer incoming phone calls naturally, warmly, and concisely (1-2 sentences per response). ")
            append("DO NOT use markdown formatting, bolding, bullet points, or emojis, as your words are spoken directly aloud via text-to-speech. ")
            append("OPERATING HOURS: $businessHours. ")
            if (servicesList.isNotEmpty()) {
                append("SERVICES & PRICING: $servicesList. ")
            }
            if (policiesAndFaqs.isNotBlank()) {
                append("POLICIES & DETAILS: $policiesAndFaqs. ")
            }
            append("GREETING GUIDANCE: $customGreeting ")
            append("CLOSING GUIDANCE: $callEndingInstructions")
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
