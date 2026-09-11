package com.aicall.agent.telecom

import android.telecom.Call

interface CallEventListener {
    fun onCallRinging(sessionId: String, call: Call, phoneNumber: String)
    fun onCallAnswered(sessionId: String, call: Call)
    fun onCallActive(sessionId: String, call: Call)
    fun onCallDisconnected(sessionId: String, call: Call, cause: String?)
}

data class CallSession(
    val sessionId: String,
    val phoneNumber: String,
    var state: Int,
    val startTimeMs: Long,
    var endTimeMs: Long? = null,
    var recordingPath: String? = null,
    var handledBy: String = "agent", // "agent" or "human" (Passive mode)
    var isPassiveMode: Boolean = false,
    var promptTokens: Int = 0,
    var completionTokens: Int = 0,
    var estimatedCostUsd: Double = 0.0
) {
    val durationSeconds: Long
        get() {
            val end = endTimeMs ?: System.currentTimeMillis()
            return ((end - startTimeMs) / 1000).coerceAtLeast(0)
        }
}
