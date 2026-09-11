package com.aicall.agent.data

import android.content.Context
import com.aicall.agent.util.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CallTranscriptMessage(
    val speaker: String, // "caller" or "agent"
    val name: String,
    val text: String,
    val timestampMs: Long = System.currentTimeMillis()
)

data class CallActionItem(
    val type: String, // "call", "booking", "sms", "reminder", "note"
    val label: String,
    val title: String,
    val time: String,
    val details: String = ""
)

data class CallRecord(
    val id: String,
    val callerNumber: String,
    val callerName: String? = null,
    val tag: String = "New caller", // "Returning caller", "New caller", "VIP"
    val timestampMs: Long = System.currentTimeMillis(),
    val durationSeconds: Long = 0,
    val handledBy: String = "agent", // "agent" or "human"
    val outcome: String = "Inquiry", // "Booked", "Message", "Pricing", "Dropped"
    val summarySnippet: String = "",
    val transcript: List<CallTranscriptMessage> = emptyList(),
    val actionItems: List<CallActionItem> = emptyList(),
    val recordingPath: String? = null,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val estimatedCostUsd: Double = 0.0
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestampMs))

    val formattedDate: String
        get() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestampMs))

    val formattedDuration: String
        get() {
            val m = durationSeconds / 60
            val s = durationSeconds % 60
            return String.format("%d:%02d", m, s)
        }
}

/**
 * On-device local storage for call records, transcripts, action items, and usage costs.
 * Strictly local-only file storage with zero cloud upload.
 */
class CallHistoryRepository(private val context: Context) {

    private val tag = "CallHistoryRepository"
    private val historyFile = File(context.filesDir, "call_history_records.json")

    /**
     * In-memory index: cleaned phone number → list of call records.
     * Built once on load and kept in sync on every save so that incoming-ring
     * caller lookups are O(1) instead of O(n) list scans.
     */
    private val numberIndex = HashMap<String, MutableList<CallRecord>>()

    private val _recordsFlow = MutableStateFlow<List<CallRecord>>(emptyList())
    val recordsFlow: StateFlow<List<CallRecord>> = _recordsFlow.asStateFlow()

    init {
        loadRecords()
    }

    @Synchronized
    private fun loadRecords() {
        if (!historyFile.exists()) {
            _recordsFlow.value = emptyList()
            return
        }
        try {
            val jsonStr = historyFile.readText()
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<CallRecord>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(parseRecord(obj))
            }
            val sorted = list.sortedByDescending { it.timestampMs }
            _recordsFlow.value = sorted
            rebuildIndex(sorted)
        } catch (e: Exception) {
            Logger.e(tag, "Failed to load call history", tr = e)
            _recordsFlow.value = emptyList()
        }
    }

    /** Rebuilds the number → records index from scratch. Called after load and save. */
    private fun rebuildIndex(records: List<CallRecord>) {
        numberIndex.clear()
        for (record in records) {
            val key = cleanNumber(record.callerNumber)
            numberIndex.getOrPut(key) { mutableListOf() }.add(record)
        }
    }

    private fun cleanNumber(number: String): String =
        number.replace(Regex("[^0-9+]"), "")

    @Synchronized
    fun saveRecord(record: CallRecord) {
        val current = _recordsFlow.value.toMutableList()
        val existingIndex = current.indexOfFirst { it.id == record.id }
        if (existingIndex >= 0) {
            current[existingIndex] = record
        } else {
            current.add(0, record)
        }
        val sorted = current.sortedByDescending { it.timestampMs }
        _recordsFlow.value = sorted
        rebuildIndex(sorted)
        persistToFile(sorted)
    }

    fun getRecordById(id: String): CallRecord? {
        return _recordsFlow.value.firstOrNull { it.id == id }
    }

    /**
     * O(1) lookup using the in-memory number index.
     * Falls back to linear scan if the index is empty (e.g. first call ever).
     */
    fun getRecordsForContact(phoneNumber: String): List<CallRecord> {
        val clean = cleanNumber(phoneNumber)
        return numberIndex[clean] ?: _recordsFlow.value.filter { cleanNumber(it.callerNumber) == clean }
    }

    fun getTodayStats(): Triple<Int, String, Int> {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val todayCalls = _recordsFlow.value.filter { it.formattedDate == todayStr }
        val count = todayCalls.size
        val missed = todayCalls.count { it.durationSeconds < 5 || it.outcome.equals("Missed", true) }
        val avgSecs = if (count > 0) todayCalls.map { it.durationSeconds }.average().toLong() else 0L
        val avgFormatted = String.format("%d:%02d", avgSecs / 60, avgSecs % 60)
        return Triple(count, avgFormatted, missed)
    }

    fun getTotalCostAndTokens(): Pair<Double, Int> {
        val totalCost = _recordsFlow.value.sumOf { it.estimatedCostUsd }
        val totalTokens = _recordsFlow.value.sumOf { it.promptTokens + it.completionTokens }
        return Pair(totalCost, totalTokens)
    }

    private fun persistToFile(records: List<CallRecord>) {
        try {
            val capped = records.take(MAX_SAVED_RECORDS)
            val pruned = if (records.size > MAX_SAVED_RECORDS) records.drop(MAX_SAVED_RECORDS) else emptyList()

            // Purge deleted WAV files to protect device storage
            for (p in pruned) {
                p.recordingPath?.let { path ->
                    try {
                        val f = File(path)
                        if (f.exists()) f.delete()
                    } catch (_: Exception) {}
                }
            }

            val arr = JSONArray()
            for (r in capped) {
                arr.put(toJson(r))
            }
            historyFile.writeText(arr.toString())
        } catch (e: Exception) {
            Logger.e(tag, "Failed to persist call history", tr = e)
        }
    }

    private fun toJson(r: CallRecord): JSONObject {
        return JSONObject().apply {
            put("id", r.id)
            put("callerNumber", r.callerNumber)
            put("callerName", r.callerName ?: "")
            put("tag", r.tag)
            put("timestampMs", r.timestampMs)
            put("durationSeconds", r.durationSeconds)
            put("handledBy", r.handledBy)
            put("outcome", r.outcome)
            put("summarySnippet", r.summarySnippet)
            put("recordingPath", r.recordingPath ?: "")
            put("promptTokens", r.promptTokens)
            put("completionTokens", r.completionTokens)
            put("estimatedCostUsd", r.estimatedCostUsd)

            val transcriptArr = JSONArray()
            r.transcript.forEach { msg ->
                transcriptArr.put(JSONObject().apply {
                    put("speaker", msg.speaker)
                    put("name", msg.name)
                    put("text", msg.text)
                    put("timestampMs", msg.timestampMs)
                })
            }
            put("transcript", transcriptArr)

            val actionsArr = JSONArray()
            r.actionItems.forEach { action ->
                actionsArr.put(JSONObject().apply {
                    put("type", action.type)
                    put("label", action.label)
                    put("title", action.title)
                    put("time", action.time)
                    put("details", action.details)
                })
            }
            put("actionItems", actionsArr)
        }
    }

    private fun parseRecord(obj: JSONObject): CallRecord {
        val transcriptList = mutableListOf<CallTranscriptMessage>()
        val transcriptArr = obj.optJSONArray("transcript")
        if (transcriptArr != null) {
            for (i in 0 until transcriptArr.length()) {
                val m = transcriptArr.getJSONObject(i)
                transcriptList.add(
                    CallTranscriptMessage(
                        speaker = m.optString("speaker"),
                        name = m.optString("name"),
                        text = m.optString("text"),
                        timestampMs = m.optLong("timestampMs")
                    )
                )
            }
        }

        val actionList = mutableListOf<CallActionItem>()
        val actionArr = obj.optJSONArray("actionItems")
        if (actionArr != null) {
            for (i in 0 until actionArr.length()) {
                val a = actionArr.getJSONObject(i)
                actionList.add(
                    CallActionItem(
                        type = a.optString("type"),
                        label = a.optString("label"),
                        title = a.optString("title"),
                        time = a.optString("time"),
                        details = a.optString("details")
                    )
                )
            }
        }

        return CallRecord(
            id = obj.getString("id"),
            callerNumber = obj.getString("callerNumber"),
            callerName = obj.optString("callerName").ifEmpty { null },
            tag = obj.optString("tag", "New caller"),
            timestampMs = obj.getLong("timestampMs"),
            durationSeconds = obj.getLong("durationSeconds"),
            handledBy = obj.optString("handledBy", "agent"),
            outcome = obj.optString("outcome", "Inquiry"),
            summarySnippet = obj.optString("summarySnippet"),
            transcript = transcriptList,
            actionItems = actionList,
            recordingPath = obj.optString("recordingPath").ifEmpty { null },
            promptTokens = obj.optInt("promptTokens", 0),
            completionTokens = obj.optInt("completionTokens", 0),
            estimatedCostUsd = obj.optDouble("estimatedCostUsd", 0.0)
        )
    }

    companion object {
        const val MAX_SAVED_RECORDS = 100

        @Volatile
        private var instance: CallHistoryRepository? = null

        fun getInstance(context: Context): CallHistoryRepository {
            return instance ?: synchronized(this) {
                instance ?: CallHistoryRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
