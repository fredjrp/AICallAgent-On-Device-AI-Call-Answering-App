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
    var recordingPath: String? = null
) {
    val durationSeconds: Long
        get() {
            val end = endTimeMs ?: System.currentTimeMillis()
            return ((end - startTimeMs) / 1000).coerceAtLeast(0)
        }
}
