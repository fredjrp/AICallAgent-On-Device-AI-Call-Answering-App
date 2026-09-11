package com.aicall.agent.data

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import com.aicall.agent.util.Logger
import com.aicall.agent.util.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ResolvedCallerIdentity(
    val phoneNumber: String,
    val displayName: String?,
    val source: String // "contacts", "history", "truecaller", "unknown"
)

/**
 * Multi-tier Caller ID Resolver implementing the truecallerjs architecture:
 *
 * Tier 1: Local Android Contacts (ContactsContract.PhoneLookup) — 0ms, 100% offline.
 * Tier 2: Local Historical Cache (CallHistoryRepository indexed cache).
 * Tier 3: Remote Truecaller Search API v2 (via sumithemmadi/truecallerjs protocol)
 *         when an installation/auth token is configured in PreferencesManager.
 * Tier 4: Fallback to formatted number.
 */
class CallerIdResolver(private val context: Context) {

    private val tag = "CallerIdResolver"
    private val prefs = PreferencesManager.getInstance(context)
    private val historyRepo = CallHistoryRepository.getInstance(context)

    suspend fun resolve(rawPhoneNumber: String): ResolvedCallerIdentity = withContext(Dispatchers.IO) {
        val cleanNumber = rawPhoneNumber.trim()
        if (cleanNumber.isBlank()) {
            return@withContext ResolvedCallerIdentity(rawPhoneNumber, null, "unknown")
        }

        // ── TIER 1: Android System Contacts ─────────────────────────────────
        try {
            val contactName = lookupInContacts(cleanNumber)
            if (!contactName.isNullOrBlank()) {
                Logger.d(tag, "Resolved caller from Android Contacts: $contactName ($cleanNumber)")
                return@withContext ResolvedCallerIdentity(cleanNumber, contactName, "contacts")
            }
        } catch (e: Exception) {
            Logger.w(tag, "Failed looking up in ContactsContract: ${e.message}")
        }

        // ── TIER 2: Local Call History Memory Index ─────────────────────────
        try {
            val pastRecords = historyRepo.getRecordsForContact(cleanNumber)
            val pastName = pastRecords.firstOrNull { !it.callerName.isNullOrBlank() }?.callerName
            if (!pastName.isNullOrBlank()) {
                Logger.d(tag, "Resolved caller from local call history index: $pastName ($cleanNumber)")
                return@withContext ResolvedCallerIdentity(cleanNumber, pastName, "history")
            }
        } catch (e: Exception) {
            Logger.w(tag, "Failed looking up in CallHistory: ${e.message}")
        }

        // ── TIER 3: Truecaller Search API (truecallerjs architecture) ───────
        val token = prefs.truecallerToken.trim()
        if (token.isNotBlank()) {
            try {
                val truecallerName = queryTruecaller(cleanNumber, token)
                if (!truecallerName.isNullOrBlank()) {
                    Logger.i(tag, "Resolved caller from Truecaller API: $truecallerName ($cleanNumber)")
                    return@withContext ResolvedCallerIdentity(cleanNumber, truecallerName, "truecaller")
                }
            } catch (e: Exception) {
                Logger.w(tag, "Truecaller query error: ${e.message}")
            }
        }

        // ── TIER 4: Fallback ────────────────────────────────────────────────
        return@withContext ResolvedCallerIdentity(cleanNumber, null, "unknown")
    }

    private fun lookupInContacts(phoneNumber: String): String? {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
        var cursor: Cursor? = null
        return try {
            cursor = context.contentResolver.query(uri, projection, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else {
                null
            }
        } finally {
            cursor?.close()
        }
    }

    /**
     * Queries Truecaller v2 search endpoint following the truecallerjs protocol:
     * Endpoint: https://search5-noneu.truecaller.com/v2/search
     * Headers: Authorization: Bearer {token}, User-Agent: Truecaller/11.75.5
     */
    private fun queryTruecaller(phoneNumber: String, token: String): String? {
        val digitsOnly = phoneNumber.replace(Regex("[^0-9+]"), "")
        val urlStr = "https://search5-noneu.truecaller.com/v2/search?q=${Uri.encode(digitsOnly)}&type=4"
        val url = URL(urlStr)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 3500
            readTimeout = 3500
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("User-Agent", "Truecaller/11.75.5 (Android;10)")
            setRequestProperty("Accept", "application/json")
        }

        return try {
            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(jsonStr)
                val dataArr = root.optJSONArray("data")
                if (dataArr != null && dataArr.length() > 0) {
                    val firstMatch = dataArr.getJSONObject(0)
                    val name = firstMatch.optString("name").trim()
                    if (name.isNotBlank()) name else null
                } else {
                    null
                }
            } else {
                Logger.d(tag, "Truecaller returned HTTP $responseCode")
                null
            }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        @Volatile
        private var instance: CallerIdResolver? = null

        fun getInstance(context: Context): CallerIdResolver {
            return instance ?: synchronized(this) {
                instance ?: CallerIdResolver(context.applicationContext).also { instance = it }
            }
        }
    }
}
