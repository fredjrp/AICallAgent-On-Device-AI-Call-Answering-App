package com.aicall.agent.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Structured Logger for AICallAgent.
 * Enforces session-id tagging for tracing a call lifecycle across components,
 * and maintains an in-memory event buffer for real-time UI diagnostics.
 */
object Logger {
    private const val GLOBAL_TAG = "AICallAgent"
    private const val MAX_LOG_HISTORY = 300

    data class LogEntry(
        val timestamp: Long,
        val level: String,
        val tag: String,
        val sessionId: String?,
        val message: String,
        val throwable: Throwable? = null
    ) {
        val formattedTime: String
            get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timestamp))

        val formattedText: String
            get() = buildString {
                append("[$formattedTime] ")
                if (!sessionId.isNullOrEmpty()) {
                    append("[$sessionId] ")
                }
                append("[$level/$tag]: ")
                append(message)
                if (throwable != null) {
                    append("\n")
                    append(Log.getStackTraceString(throwable))
                }
            }
    }

    private val logHistory = mutableListOf<LogEntry>()
    private val listeners = CopyOnWriteArrayList<(LogEntry) -> Unit>()

    fun addListener(listener: (LogEntry) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (LogEntry) -> Unit) {
        listeners.remove(listener)
    }

    fun getRecentLogs(): List<LogEntry> = synchronized(logHistory) {
        logHistory.toList()
    }

    fun clearLogs() = synchronized(logHistory) {
        logHistory.clear()
    }

    fun d(tag: String, message: String, sessionId: String? = null) {
        log("DEBUG", tag, message, sessionId, null) { t, m -> Log.d(t, m) }
    }

    fun i(tag: String, message: String, sessionId: String? = null) {
        log("INFO", tag, message, sessionId, null) { t, m -> Log.i(t, m) }
    }

    fun w(tag: String, message: String, sessionId: String? = null, tr: Throwable? = null) {
        log("WARN", tag, message, sessionId, tr) { t, m -> Log.w(t, m, tr) }
    }

    fun e(tag: String, message: String, sessionId: String? = null, tr: Throwable? = null) {
        log("ERROR", tag, message, sessionId, tr) { t, m -> Log.e(t, m, tr) }
    }

    private inline fun log(
        level: String,
        tag: String,
        message: String,
        sessionId: String?,
        tr: Throwable?,
        platformLog: (String, String) -> Unit
    ) {
        val formattedMessage = if (!sessionId.isNullOrEmpty()) "[$sessionId] $message" else message
        platformLog("$GLOBAL_TAG:$tag", formattedMessage)

        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            sessionId = sessionId,
            message = message,
            throwable = tr
        )

        synchronized(logHistory) {
            if (logHistory.size >= MAX_LOG_HISTORY) {
                logHistory.removeAt(0)
            }
            logHistory.add(entry)
        }

        for (listener in listeners) {
            try {
                listener(entry)
            } catch (_: Exception) {
            }
        }
    }
}
