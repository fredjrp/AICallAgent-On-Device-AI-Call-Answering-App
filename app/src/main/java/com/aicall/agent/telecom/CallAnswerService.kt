package com.aicall.agent.telecom

import android.annotation.SuppressLint
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.telecom.Call
import android.telecom.InCallService
import android.telecom.VideoProfile
import androidx.core.app.NotificationCompat
import com.aicall.agent.AICallApplication
import com.aicall.agent.audio.CallAudioCapture
import com.aicall.agent.util.Logger
import com.aicall.agent.util.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Privileged InCallService.
 * Automatically answers incoming calls and captures live call audio via AudioRecord.
 */
class CallAnswerService : InCallService() {

    private val tag = "CallAnswerService"
    private var audioCapture: CallAudioCapture? = null
    private val callCallbacks = ConcurrentHashMap<Call, Call.Callback>()
    private val callSessions = ConcurrentHashMap<Call, CallSession>()

    override fun onCreate() {
        super.onCreate()
        Logger.i(tag, "CallAnswerService created and bound to Android Telecom")
        activeServiceInstance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        Logger.i(tag, "CallAnswerService destroyed")
        audioCapture?.stopCapture()
        activeServiceInstance = null
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        val sessionId = "call_${System.currentTimeMillis().toString().takeLast(6)}_${UUID.randomUUID().toString().take(4)}"
        val phoneNumber = extractPhoneNumber(call)

        val session = CallSession(
            sessionId = sessionId,
            phoneNumber = phoneNumber,
            state = call.state,
            startTimeMs = System.currentTimeMillis()
        )
        callSessions[call] = session
        _currentSessionFlow.value = session
        activeCall = call

        Logger.i(tag, "New call added: state=${call.state}, caller=$phoneNumber", sessionId)

        val callback = object : Call.Callback() {
            override fun onStateChanged(targetCall: Call, state: Int) {
                session.state = state
                _currentSessionFlow.value = session.copy(state = state)
                Logger.d(tag, "Call state changed: $state", sessionId)

                when (state) {
                    Call.STATE_RINGING -> {
                        handleIncomingRinging(targetCall, session)
                    }
                    Call.STATE_ACTIVE -> {
                        handleCallActive(targetCall, session)
                    }
                    Call.STATE_DISCONNECTING, Call.STATE_DISCONNECTED -> {
                        handleCallDisconnected(targetCall, session)
                    }
                }
            }

            override fun onDetailsChanged(targetCall: Call, details: Call.Details) {
                Logger.d(tag, "Call details updated: ${details.disconnectCause}", sessionId)
            }

            override fun onCallDestroyed(targetCall: Call) {
                Logger.i(tag, "Call destroyed by telecom framework", sessionId)
                handleCallDisconnected(targetCall, session)
                targetCall.unregisterCallback(this)
                callCallbacks.remove(targetCall)
                callSessions.remove(targetCall)
                if (activeCall == targetCall) {
                    activeCall = null
                    _currentSessionFlow.value = null
                }
            }
        }

        callCallbacks[call] = callback
        call.registerCallback(callback)

        // Handle immediate state if already ringing or active upon addition
        if (call.state == Call.STATE_RINGING) {
            handleIncomingRinging(call, session)
        } else if (call.state == Call.STATE_ACTIVE) {
            handleCallActive(call, session)
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Logger.i(tag, "onCallRemoved called for call: $call")
        val session = callSessions[call]
        if (session != null) {
            handleCallDisconnected(call, session)
        }
    }

    private fun handleIncomingRinging(call: Call, session: CallSession) {
        val prefs = PreferencesManager.getInstance(this)
        Logger.i(tag, "Incoming call ringing: ${session.phoneNumber}. AutoAnswer=${prefs.isAutoAnswerEnabled}", session.sessionId)

        notifyListeners { it.onCallRinging(session.sessionId, call, session.phoneNumber) }

        if (prefs.isAutoAnswerEnabled) {
            Logger.i(tag, "Auto-answering incoming call...", session.sessionId)
            try {
                // Auto-answer with audio only
                call.answer(VideoProfile.STATE_AUDIO_ONLY)
                notifyListeners { it.onCallAnswered(session.sessionId, call) }
            } catch (e: Exception) {
                Logger.e(tag, "Failed to auto-answer call", session.sessionId, e)
            }
        }
    }

    @SuppressLint("ForegroundServiceType")
    private fun handleCallActive(call: Call, session: CallSession) {
        Logger.i(tag, "Call is now ACTIVE. Starting in-call foreground notification and capture.", session.sessionId)

        // Promote to foreground service
        val notification = buildInCallNotification("In call with ${session.phoneNumber}")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    AICallApplication.NOTIFICATION_ID_INCALL,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(AICallApplication.NOTIFICATION_ID_INCALL, notification)
            }
        } catch (e: Exception) {
            Logger.w(tag, "Failed to startForeground: ${e.message}", session.sessionId)
        }

        notifyListeners { it.onCallActive(session.sessionId, call) }

        // Start privileged audio capture
        val prefs = PreferencesManager.getInstance(this)
        val recordingsDir = File(getExternalFilesDir(null), "recordings").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safeCaller = session.phoneNumber.replace(Regex("[^0-9+]"), "")
        val wavFile = File(recordingsDir, "call_${session.sessionId}_${safeCaller}_${timestamp}.wav")
        session.recordingPath = wavFile.absolutePath

        val capture = CallAudioCapture(this, audioSource = prefs.audioSource)
        audioCapture = capture

        val started = capture.startCapture(session.sessionId, wavFile) { file, samples ->
            session.endTimeMs = System.currentTimeMillis()
            Logger.i(tag, "Recording finished: ${file?.name}, duration=${session.durationSeconds}s, samples=$samples", session.sessionId)
            addCompletedSession(session)
        }

        if (!started) {
            Logger.e(tag, "AudioCapture failed to start for session ${session.sessionId}", session.sessionId)
        }
    }

    private fun handleCallDisconnected(call: Call, session: CallSession) {
        Logger.i(tag, "Call disconnecting/disconnected: ${session.sessionId}", session.sessionId)

        audioCapture?.stopCapture()
        audioCapture = null

        session.endTimeMs = System.currentTimeMillis()
        notifyListeners { it.onCallDisconnected(session.sessionId, call, call.details?.disconnectCause?.toString()) }

        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {}
    }

    private fun extractPhoneNumber(call: Call): String {
        return try {
            val handle: Uri? = call.details?.handle
            val schemeSpecific = handle?.schemeSpecificPart
            if (!schemeSpecific.isNullOrEmpty()) {
                schemeSpecific
            } else {
                call.details?.callerDisplayName ?: "Unknown Caller"
            }
        } catch (e: Exception) {
            "Unknown Caller"
        }
    }

    private fun buildInCallNotification(content: String): Notification {
        return NotificationCompat.Builder(this, AICallApplication.CHANNEL_ID_INCALL)
            .setContentTitle("AICallAgent Active Call")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private inline fun notifyListeners(action: (CallEventListener) -> Unit) {
        for (listener in eventListeners) {
            try {
                action(listener)
            } catch (e: Exception) {
                Logger.e(tag, "Error notifying call listener", tr = e)
            }
        }
    }

    companion object {
        @Volatile
        var activeCall: Call? = null
            private set

        @Volatile
        var activeServiceInstance: CallAnswerService? = null
            private set

        private val _currentSessionFlow = MutableStateFlow<CallSession?>(null)
        val currentSessionFlow: StateFlow<CallSession?> = _currentSessionFlow.asStateFlow()

        val completedSessions = CopyOnWriteArrayList<CallSession>()
        private val eventListeners = CopyOnWriteArrayList<CallEventListener>()

        fun addCallEventListener(listener: CallEventListener) {
            eventListeners.add(listener)
        }

        fun removeCallEventListener(listener: CallEventListener) {
            eventListeners.remove(listener)
        }

        fun answerCurrentCall() {
            activeCall?.answer(VideoProfile.STATE_AUDIO_ONLY)
        }

        fun hangUpCurrentCall() {
            activeCall?.disconnect()
        }

        private fun addCompletedSession(session: CallSession) {
            completedSessions.add(0, session)
            if (completedSessions.size > 50) {
                completedSessions.removeAt(completedSessions.size - 1)
            }
        }
    }
}
