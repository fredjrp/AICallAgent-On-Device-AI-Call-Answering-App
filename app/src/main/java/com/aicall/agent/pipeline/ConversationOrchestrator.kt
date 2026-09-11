package com.aicall.agent.pipeline

import com.aicall.agent.audio.CallAudioPlayback
import com.aicall.agent.audio.VoiceActivityDetector
import com.aicall.agent.data.CallActionItem
import com.aicall.agent.data.CallRecord
import com.aicall.agent.data.CallTranscriptMessage
import com.aicall.agent.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Coordinates conversational turn-taking loop:
 * Audio Ingest -> VAD -> STT -> LLM (OpenRouter) -> TTS -> Earpiece Playback.
 *
 * Supports PASSIVE_MODE for human-answered calls (2.8):
 * - If isPassiveMode = true: captures & transcribes audio, saving the transcript and filing
 *   the call without invoking LLM or TTS audio playback.
 * - Streams live transcript messages and action items for the Live tab UI feed.
 */
class ConversationOrchestrator(
    private val speechToText: SpeechToText,
    private val openRouterClient: OpenRouterClient,
    private val textToSpeech: TextToSpeech,
    private val audioPlayback: CallAudioPlayback,
    private val vad: VoiceActivityDetector = VoiceActivityDetector()
) {
    enum class ConversationState {
        IDLE,
        LISTENING,
        TRANSCRIBING,
        THINKING,
        SYNTHESIZING,
        SPEAKING
    }

    private val tag = "ConversationOrchestrator"
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private var activeJob: Job? = null
    private var currentSessionId: String? = null
    private var isPassiveMode: Boolean = false
    private var systemPrompt: String = OpenRouterClient.DEFAULT_SYSTEM_PROMPT

    private val _stateFlow = MutableStateFlow(ConversationState.IDLE)
    val stateFlow: StateFlow<ConversationState> = _stateFlow.asStateFlow()

    // Live messages for the active call UI feed
    private val _liveMessages = MutableStateFlow<List<CallTranscriptMessage>>(emptyList())
    val liveMessages: StateFlow<List<CallTranscriptMessage>> = _liveMessages.asStateFlow()

    // Live background actions (contact lookup, calendar check, SMS confirmation)
    private val _liveActions = MutableStateFlow<List<CallActionItem>>(emptyList())
    val liveActions: StateFlow<List<CallActionItem>> = _liveActions.asStateFlow()

    private val conversationHistory = CopyOnWriteArrayList<OpenRouterClient.Message>()

    var totalPromptTokens: Int = 0
        private set
    var totalCompletionTokens: Int = 0
        private set
    var totalEstimatedCost: Double = 0.0
        private set

    init {
        setupVad()
    }

    private fun setupVad() {
        vad.listener = object : VoiceActivityDetector.Listener {
            override fun onSpeechStart() {
                val session = currentSessionId
                Logger.d(tag, "Caller speech started (barge-in check)", session)
                if (_stateFlow.value == ConversationState.SPEAKING) {
                    Logger.i(tag, "Caller interrupted playback. Stopping speech immediately.", session)
                    audioPlayback.stop()
                    _stateFlow.value = ConversationState.LISTENING
                }
            }

            override fun onSpeechEnd(utteranceAudio: ShortArray) {
                val session = currentSessionId ?: return
                Logger.i(tag, "Caller speech completed. Processing utterance of ${utteranceAudio.size} samples...", session)
                processUtterance(utteranceAudio, session)
            }
        }
    }

    fun start(sessionId: String, isPassive: Boolean = false, dynamicSystemPrompt: String = OpenRouterClient.DEFAULT_SYSTEM_PROMPT) {
        currentSessionId = sessionId
        isPassiveMode = isPassive
        systemPrompt = dynamicSystemPrompt
        conversationHistory.clear()
        _liveMessages.value = emptyList()
        _liveActions.value = emptyList()
        totalPromptTokens = 0
        totalCompletionTokens = 0
        totalEstimatedCost = 0.0
        vad.reset()
        _stateFlow.value = ConversationState.LISTENING
        Logger.i(tag, "Conversation loop started. PassiveMode=$isPassive, session: $sessionId", sessionId)
    }

    fun setPassiveMode(passive: Boolean) {
        isPassiveMode = passive
        if (passive) {
            audioPlayback.stop()
            Logger.i(tag, "Switched into PASSIVE mode (human-answered call). Disabling AI voice playback.")
        }
    }

    fun stop() {
        val session = currentSessionId
        Logger.i(tag, "Conversation loop stopped", session)
        currentSessionId = null
        audioPlayback.stop()
        activeJob?.cancel()
        activeJob = null
        vad.reset()
        _stateFlow.value = ConversationState.IDLE
    }

    fun feedAudioFrame(samples: ShortArray, length: Int) {
        if (_stateFlow.value == ConversationState.IDLE) return
        vad.processFrame(samples, length)
    }

    private fun processUtterance(audio: ShortArray, sessionId: String) {
        activeJob?.cancel()
        activeJob = scope.launch {
            try {
                // 1. STT (Whisper on-device)
                _stateFlow.value = ConversationState.TRANSCRIBING
                Logger.i(tag, "Transcribing caller speech via on-device Whisper...", sessionId)
                val transcript = speechToText.transcribe(audio)

                if (transcript.isBlank()) {
                    Logger.d(tag, "Empty or unintelligible transcript. Returning to listening.", sessionId)
                    _stateFlow.value = ConversationState.LISTENING
                    return@launch
                }

                Logger.i(tag, "Caller said: \"$transcript\"", sessionId)
                val callerMsg = CallTranscriptMessage(
                    speaker = "caller",
                    name = "Caller",
                    text = transcript,
                    timestampMs = System.currentTimeMillis()
                )
                _liveMessages.value = _liveMessages.value + callerMsg
                conversationHistory.add(OpenRouterClient.Message("user", transcript))

                // If in passive mode: do NOT query OpenRouter or speak. Just transcribe & record.
                if (isPassiveMode) {
                    Logger.d(tag, "Passive mode: skipping LLM & TTS for human call.", sessionId)
                    _stateFlow.value = ConversationState.LISTENING
                    return@launch
                }

                // 2. LLM (OpenRouter API)
                _stateFlow.value = ConversationState.THINKING
                Logger.i(tag, "Querying OpenRouter API for response...", sessionId)

                val result = openRouterClient.chatWithUsage(
                    conversationHistory = conversationHistory.toList(),
                    systemPrompt = systemPrompt
                )

                totalPromptTokens += result.promptTokens
                totalCompletionTokens += result.completionTokens
                totalEstimatedCost += result.estimatedCostUsd

                val reply = result.replyText
                if (reply.isBlank()) {
                    Logger.w(tag, "Received empty reply from LLM.", sessionId)
                    _stateFlow.value = ConversationState.LISTENING
                    return@launch
                }

                Logger.i(tag, "AI Agent reply: \"$reply\" (latency=${result.latencyMs}ms, tokens=${result.totalTokens})", sessionId)
                val agentMsg = CallTranscriptMessage(
                    speaker = "agent",
                    name = "Front Desk",
                    text = reply,
                    timestampMs = System.currentTimeMillis()
                )
                _liveMessages.value = _liveMessages.value + agentMsg
                conversationHistory.add(OpenRouterClient.Message("assistant", reply))

                // Parse any heuristic action tag (e.g. booking, callback, pricing)
                detectAndAddAction(transcript, reply)

                // 3. TTS (Kokoro/Piper on-device)
                _stateFlow.value = ConversationState.SYNTHESIZING
                Logger.i(tag, "Synthesizing voice reply via on-device TTS...", sessionId)
                val speechPcm = textToSpeech.synthesize(reply)

                if (speechPcm.isEmpty()) {
                    Logger.w(tag, "TTS generated 0 audio samples.", sessionId)
                    _stateFlow.value = ConversationState.LISTENING
                    return@launch
                }

                // 4. Playback (Earpiece acoustic coupling)
                _stateFlow.value = ConversationState.SPEAKING
                Logger.i(tag, "Playing response through earpiece (${speechPcm.size} samples)...", sessionId)
                audioPlayback.playAudio(speechPcm, sampleRate = textToSpeech.sampleRate)

                Logger.i(tag, "Playback completed. Ready for caller response.", sessionId)
                _stateFlow.value = ConversationState.LISTENING
            } catch (e: Exception) {
                Logger.e(tag, "Error in conversation processing pipeline", sessionId, e)
                _stateFlow.value = ConversationState.LISTENING
            }
        }
    }

    private fun detectAndAddAction(callerText: String, agentText: String) {
        val lower = (callerText + " " + agentText).lowercase()
        when {
            lower.contains("book") || lower.contains("appointment") || lower.contains("schedule") -> {
                _liveActions.value = _liveActions.value + CallActionItem(
                    type = "booking",
                    label = "Calendar · Appointment",
                    title = "Booking discussed with caller",
                    time = "Just now"
                )
            }
            lower.contains("price") || lower.contains("cost") || lower.contains("charge") || lower.contains("kes") -> {
                _liveActions.value = _liveActions.value + CallActionItem(
                    type = "note",
                    label = "Inquiry · Pricing",
                    title = "Pricing quote provided",
                    time = "Just now"
                )
            }
            lower.contains("message") || lower.contains("call back") || lower.contains("callback") -> {
                _liveActions.value = _liveActions.value + CallActionItem(
                    type = "reminder",
                    label = "Callback Reminder",
                    title = "Message taken for manager",
                    time = "Just now"
                )
            }
        }
    }

    fun getCurrentTranscript(): List<CallTranscriptMessage> = _liveMessages.value
    fun getCurrentActions(): List<CallActionItem> = _liveActions.value
}
