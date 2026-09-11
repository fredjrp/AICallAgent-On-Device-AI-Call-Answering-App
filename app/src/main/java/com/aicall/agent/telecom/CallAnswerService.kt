package com.aicall.agent.telecom

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.InCallService
import android.telecom.VideoProfile
import androidx.core.app.NotificationCompat
import com.aicall.agent.AICallApplication
import com.aicall.agent.audio.CallAudioCapture
import com.aicall.agent.audio.CallAudioPlayback
import com.aicall.agent.data.BusinessKnowledgeManager
import com.aicall.agent.data.CallActionItem
import com.aicall.agent.data.CallHistoryRepository
import com.aicall.agent.data.CallRecord
import com.aicall.agent.pipeline.AndroidTtsEngine
import com.aicall.agent.pipeline.ConversationOrchestrator
import com.aicall.agent.pipeline.KokoroTtsEngine
import com.aicall.agent.pipeline.OpenRouterClient
import com.aicall.agent.pipeline.WhisperCppEngine
import com.aicall.agent.util.Logger
import com.aicall.agent.util.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
 * Privileged InCallService implementing Section 2.8 pickup logic:
 * 1. Incoming call rings -> launch InCallActivity with ringing countdown.
 * 2. If human picks up manually during the ring window: switch to PASSIVE MODE.
 *    Captures & transcribes call audio, files as `handledBy: human`, no AI voice playback.
 * 3. If countdown expires with no human pickup: auto-answer and engage full AI pipeline.
 * 4. At call end: files complete CallRecord to CallHistoryRepository (local-only, no mock data).
 */
class CallAnswerService : InCallService() {

    private val tag = "CallAnswerService"
    private var audioCapture: CallAudioCapture? = null
    private var orchestrator: ConversationOrchestrator? = null
    private val callCallbacks = ConcurrentHashMap<Call, Call.Callback>()
    private val callSessions = ConcurrentHashMap<Call, CallSession>()
    private val ringTimers = ConcurrentHashMap<Call, Runnable>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    override fun onCreate() {
        super.onCreate()
        Logger.i(tag, "CallAnswerService created and bound to Android Telecom")
        activeServiceInstance = this
        com.aicall.agent.shizuku.ShizukuStatusMonitor.startMonitoring()

        val prefs = PreferencesManager.getInstance(this)
        val openRouterClient = OpenRouterClient(
            apiKeyProvider = { prefs.openRouterApiKey },
            modelProvider = { prefs.selectedModel }
        )
        val androidTts = AndroidTtsEngine(this)
        val tts = KokoroTtsEngine(fallbackEngine = androidTts)
        val stt = WhisperCppEngine()
        val playback = CallAudioPlayback(this)

        orchestrator = ConversationOrchestrator(
            speechToText = stt,
            openRouterClient = openRouterClient,
            textToSpeech = tts,
            audioPlayback = playback
        )
        activeOrchestrator = orchestrator
    }

    override fun onDestroy() {
        super.onDestroy()
        Logger.i(tag, "CallAnswerService destroyed")
        audioCapture?.stopCapture()
        orchestrator?.stop()
        activeOrchestrator = null
        activeServiceInstance = null
        com.aicall.agent.shizuku.ShizukuStatusMonitor.stopMonitoring()
    }

    /**
     * Phase 3 — Telecom Audio Route Sync.
     *
     * The Android Telecom framework notifies us whenever audio routing changes
     * (e.g. Bluetooth connected, speaker toggled by system, headset plugged in).
     * We enforce ROUTE_EARPIECE here so the AI voice is always played through
     * the internal earpiece for natural telephone call acoustics, unless the user
     * has explicitly chosen a different route via the in-call UI speaker toggle.
     */
    override fun onCallAudioStateChanged(audioState: android.telecom.CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        val prefs = PreferencesManager.getInstance(this)
        // Only enforce earpiece if the user has NOT toggled speaker-on in the UI.
        // We use a lightweight flag stored in PreferencesManager.
        val userWantsSpeaker = prefs.speakerphoneEnabled
        if (!userWantsSpeaker &&
            audioState.route != android.telecom.CallAudioState.ROUTE_EARPIECE &&
            audioState.supportedRouteMask and android.telecom.CallAudioState.ROUTE_EARPIECE != 0
        ) {
            try {
                setAudioRoute(android.telecom.CallAudioState.ROUTE_EARPIECE)
                Logger.i(tag, "Audio route enforced: ROUTE_EARPIECE (was route=${audioState.route})")
            } catch (e: Exception) {
                Logger.w(tag, "Failed to set audio route to EARPIECE: ${e.message}")
            }
        }
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

        // Resolve caller name asynchronously (Contacts -> History -> Truecaller -> Unknown)
        serviceScope.launch {
            try {
                val resolver = com.aicall.agent.data.CallerIdResolver.getInstance(this@CallAnswerService)
                val identity = resolver.resolve(phoneNumber)
                if (!identity.displayName.isNullOrBlank()) {
                    session.callerName = identity.displayName
                    _currentSessionFlow.value = session.copy(callerName = identity.displayName)
                    Logger.i(tag, "Caller identified: ${identity.displayName} via ${identity.source}", sessionId)
                }
            } catch (e: Exception) {
                Logger.w(tag, "Caller resolution error: ${e.message}", sessionId)
            }
        }

        // Launch In-Call UI Activity with full-screen intent (wakes lock screen & works when app is closed)
        showIncomingCallUi(call, session)

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
                ringTimers.remove(targetCall)?.let { mainHandler.removeCallbacks(it) }
                if (activeCall == targetCall) {
                    activeCall = null
                    // Keep session in DISCONNECTED state for 25 seconds so InCallActivity can display AfterCallSummarySheet
                    mainHandler.postDelayed({
                        if (activeCall == null) {
                            _currentSessionFlow.value = null
                        }
                    }, 25000L)
                }
            }
        }

        callCallbacks[call] = callback
        call.registerCallback(callback)

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
        Logger.i(tag, "Incoming call ringing: ${session.phoneNumber}. AutoAnswer=${prefs.isAutoAnswerEnabled}, Paused=${prefs.isAgentPaused}", session.sessionId)

        notifyListeners { it.onCallRinging(session.sessionId, call, session.phoneNumber) }

        // If agent is paused or auto-answer disabled: let human phone dialer ring without auto-pickup
        if (prefs.isAgentPaused || !prefs.isAutoAnswerEnabled) {
            Logger.i(tag, "Agent is paused/disabled; leaving call to ring normally for human.", session.sessionId)
            return
        }

        // Section 2.8: Ring delay window
        val rings = prefs.answerDelayRings.coerceAtLeast(1)
        val delayMillis = rings * 3000L // ~3 seconds per ring cycle

        Logger.i(tag, "Scheduled ring window: $rings rings ($delayMillis ms)", session.sessionId)

        val autoAnswerRunnable = Runnable {
            ringTimers.remove(call)
            if (call.state == Call.STATE_RINGING) {
                Logger.i(tag, "Ring window expired. Auto-answering call with AI Agent.", session.sessionId)
                try {
                    session.handledBy = "agent"
                    session.isPassiveMode = false
                    _currentSessionFlow.value = session.copy(handledBy = "agent", isPassiveMode = false)
                    call.answer(VideoProfile.STATE_AUDIO_ONLY)
                    notifyListeners { it.onCallAnswered(session.sessionId, call) }
                } catch (e: Exception) {
                    Logger.e(tag, "Failed to auto-answer call", session.sessionId, e)
                }
            }
        }

        ringTimers[call] = autoAnswerRunnable
        mainHandler.postDelayed(autoAnswerRunnable, delayMillis)
    }

    @SuppressLint("ForegroundServiceType")
    private fun handleCallActive(call: Call, session: CallSession) {
        Logger.i(tag, "Call is now ACTIVE: ${session.sessionId}", session.sessionId)

        // Cancel pending auto-answer timer if active (indicates manual human pickup before timer expired)
        val pendingTimer = ringTimers.remove(call)
        if (pendingTimer != null) {
            mainHandler.removeCallbacks(pendingTimer)
            // If the timer was still pending and call became active, a human picked up manually!
            session.handledBy = "human"
            session.isPassiveMode = true
            Logger.i(tag, "Human answered manually during ring window! Engaging PASSIVE MODE.", session.sessionId)
        }
        _currentSessionFlow.value = session.copy(state = Call.STATE_ACTIVE)

        // Promote to foreground service
        val isPassive = session.isPassiveMode
        val notificationTitle = if (isPassive) "Human Call (Transcribing)" else "AI Agent Call Active"
        val notification = buildInCallNotification("$notificationTitle with ${session.phoneNumber}")
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

        // Validate Shizuku privileged access
        if (!com.aicall.agent.shizuku.ShizukuAudioAccess.isShizukuRunning()) {
            Logger.e(tag, "Shizuku is not running! Cannot capture audio output.", session.sessionId)
            postShizukuAlert("Shizuku Not Running", "Open Shizuku app and tap Start before calls can be captured.")
        } else if (!com.aicall.agent.shizuku.ShizukuAudioAccess.hasShizukuPermission()) {
            Logger.w(tag, "Shizuku permission not granted for AICallAgent", session.sessionId)
            postShizukuAlert("Shizuku Permission Required", "Grant AICallAgent permission in Shizuku.")
        }

        // Start Conversation Orchestrator loop
        val kb = BusinessKnowledgeManager.getInstance(this)
        val dynamicPrompt = kb.buildSystemPrompt()
        orchestrator?.start(
            sessionId = session.sessionId,
            isPassive = session.isPassiveMode,
            dynamicSystemPrompt = dynamicPrompt
        )

        // Start audio capture
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
            fileCallRecord(session)
        }

        if (!started) {
            Logger.e(tag, "AudioCapture failed to start for session ${session.sessionId}", session.sessionId)
        }
    }

    private fun postShizukuAlert(title: String, message: String) {
        try {
            val notificationManager = getSystemService(android.app.NotificationManager::class.java)
            val notification = NotificationCompat.Builder(this, AICallApplication.CHANNEL_ID_ALERTS)
                .setContentTitle(title)
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            notificationManager.notify(AICallApplication.NOTIFICATION_ID_SHIZUKU_ALERT, notification)
        } catch (e: Exception) {
            Logger.w(tag, "Failed to post alert notification: ${e.message}")
        }
    }

    private fun handleCallDisconnected(call: Call, session: CallSession) {
        Logger.i(tag, "Call disconnecting/disconnected: ${session.sessionId}", session.sessionId)

        ringTimers.remove(call)?.let { mainHandler.removeCallbacks(it) }

        audioCapture?.stopCapture()
        audioCapture = null

        orchestrator?.stop()

        session.endTimeMs = System.currentTimeMillis()
        notifyListeners { it.onCallDisconnected(session.sessionId, call, call.details?.disconnectCause?.toString()) }

        val record = fileCallRecord(session)

        // Keep session in DISCONNECTED state so InCallActivity transitions to AfterCallSummarySheet
        session.state = Call.STATE_DISCONNECTED
        _currentSessionFlow.value = session.copy(state = Call.STATE_DISCONNECTED)

        // Launch Truecaller post-call summary banner (works over lockscreen & when other apps are open)
        showAfterCallSummaryUi(record.id)

        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {}
    }

    private fun showIncomingCallUi(call: Call, session: CallSession) {
        try {
            val inCallIntent = Intent(this, com.aicall.agent.ui.InCallActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val fullScreenPendingIntent = PendingIntent.getActivity(this, 1001, inCallIntent, flags)

            val callerTitle = if (!session.callerName.isNullOrBlank()) session.callerName!! else session.phoneNumber
            val notification = NotificationCompat.Builder(this, AICallApplication.CHANNEL_ID_INCALL)
                .setContentTitle(callerTitle)
                .setContentText("Incoming call • Front Desk standing by")
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(fullScreenPendingIntent)
                .build()

            val notificationManager = getSystemService(android.app.NotificationManager::class.java)
            notificationManager?.notify(AICallApplication.NOTIFICATION_ID_INCALL, notification)

            startActivity(inCallIntent)
        } catch (e: Exception) {
            Logger.w(tag, "Failed to launch incoming call UI: ${e.message}")
        }
    }

    private fun showAfterCallSummaryUi(recordId: String) {
        try {
            val intent = Intent(this, com.aicall.agent.ui.InCallActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
                putExtra(com.aicall.agent.ui.InCallActivity.EXTRA_SHOW_SUMMARY, true)
                putExtra(com.aicall.agent.ui.InCallActivity.EXTRA_RECORD_ID, recordId)
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getActivity(this, 1002, intent, flags)

            val notification = NotificationCompat.Builder(this, AICallApplication.CHANNEL_ID_INCALL)
                .setContentTitle("Call Finished")
                .setContentText("Tap to view Front Desk call summary")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(pendingIntent, true)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            val notificationManager = getSystemService(android.app.NotificationManager::class.java)
            notificationManager?.notify(AICallApplication.NOTIFICATION_ID_INCALL, notification)

            startActivity(intent)
        } catch (e: Exception) {
            Logger.w(tag, "Could not launch after-call summary: ${e.message}")
        }
    }

    private fun fileCallRecord(session: CallSession): CallRecord {
        val repo = CallHistoryRepository.getInstance(this)
        val currentTranscript = orchestrator?.getCurrentTranscript() ?: emptyList()
        val currentActions = orchestrator?.getCurrentActions() ?: emptyList()

        val outcome = when {
            session.durationSeconds < 5 -> "Dropped"
            currentActions.any { it.type == "booking" } -> "Booked"
            currentActions.any { it.type == "reminder" } -> "Message"
            currentActions.any { it.type == "note" } -> "Pricing"
            else -> "Inquiry"
        }

        val summary = if (currentTranscript.isNotEmpty()) {
            val lastMsg = currentTranscript.last().text
            if (lastMsg.length > 65) lastMsg.take(65) + "…" else lastMsg
        } else {
            if (session.handledBy == "human") "Handled manually by user" else "Call completed"
        }

        val record = CallRecord(
            id = session.sessionId,
            callerNumber = session.phoneNumber,
            callerName = session.callerName,
            tag = if (repo.getRecordsForContact(session.phoneNumber).isNotEmpty()) "Returning caller" else "New caller",
            timestampMs = session.startTimeMs,
            durationSeconds = session.durationSeconds,
            handledBy = session.handledBy,
            outcome = outcome,
            summarySnippet = summary,
            transcript = currentTranscript,
            actionItems = currentActions,
            recordingPath = session.recordingPath,
            promptTokens = orchestrator?.totalPromptTokens ?: 0,
            completionTokens = orchestrator?.totalCompletionTokens ?: 0,
            estimatedCostUsd = orchestrator?.totalEstimatedCost ?: 0.0
        )

        repo.saveRecord(record)
        addCompletedSession(session)
        Logger.i(tag, "Call record filed to repository: ${record.id}, handledBy=${record.handledBy}, outcome=$outcome")
        return record
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
        val inCallIntent = Intent(this, com.aicall.agent.ui.InCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, inCallIntent, flags)

        return NotificationCompat.Builder(this, AICallApplication.CHANNEL_ID_INCALL)
            .setContentTitle("AICallAgent Active")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
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

        @Volatile
        var activeOrchestrator: ConversationOrchestrator? = null
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
            activeCall?.let { call ->
                val service = activeServiceInstance
                service?.ringTimers?.remove(call)?.let { timer ->
                    service.mainHandler.removeCallbacks(timer)
                }
                val session = service?.callSessions?.get(call)
                if (session != null) {
                    session.handledBy = "human"
                    session.isPassiveMode = true
                    _currentSessionFlow.value = session.copy(handledBy = "human", isPassiveMode = true)
                }
                call.answer(VideoProfile.STATE_AUDIO_ONLY)
            }
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
