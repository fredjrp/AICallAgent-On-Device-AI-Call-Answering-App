package com.aicall.agent.pipeline

import com.aicall.agent.audio.CallAudioPlayback
import com.aicall.agent.audio.VoiceActivityDetector
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
 * Coordinates the full conversational turn-taking loop:
 * Audio Ingest -> VAD -> STT (Whisper) -> LLM (OpenRouter) -> TTS (Kokoro/Piper) -> Earpiece Playback.
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

    private val _stateFlow = MutableStateFlow(ConversationState.IDLE)
    val stateFlow: StateFlow<ConversationState> = _stateFlow.asStateFlow()

    private val conversationHistory = CopyOnWriteArrayList<OpenRouterClient.Message>()

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

    fun start(sessionId: String) {
        currentSessionId = sessionId
        conversationHistory.clear()
        vad.reset()
        _stateFlow.value = ConversationState.LISTENING
        Logger.i(tag, "Conversation loop started for session: $sessionId", sessionId)
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

    /**
     * Feeds incoming PCM audio from CallAudioCapture into the orchestrator.
     */
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
                conversationHistory.add(OpenRouterClient.Message("user", transcript))

                // 2. LLM (OpenRouter API)
                _stateFlow.value = ConversationState.THINKING
                Logger.i(tag, "Querying OpenRouter API for response...", sessionId)
                val reply = openRouterClient.chat(conversationHistory.toList())

                if (reply.isBlank()) {
                    Logger.w(tag, "Received empty reply from LLM.", sessionId)
                    _stateFlow.value = ConversationState.LISTENING
                    return@launch
                }

                Logger.i(tag, "AI Agent reply: \"$reply\"", sessionId)
                conversationHistory.add(OpenRouterClient.Message("assistant", reply))

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
}
