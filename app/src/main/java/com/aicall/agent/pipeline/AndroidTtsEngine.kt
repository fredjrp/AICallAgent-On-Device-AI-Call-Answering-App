package com.aicall.agent.pipeline

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech as PlatformTts
import android.speech.tts.UtteranceProgressListener
import com.aicall.agent.util.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.UUID

/**
 * Production-grade Android Text-To-Speech implementation that:
 * 1. Uses Android's built-in platform TTS engine (Google Speech Services / Samsung TTS).
 * 2. Provides direct audio speech playback via platform TTS.
 * 3. Implements [TextToSpeech] interface by synthesizing to WAV and extracting 16-bit PCM shorts
 *    so [com.aicall.agent.audio.CallAudioPlayback] can stream through earpiece or carrier audio route.
 * 4. 100% on-device, offline-capable, requires no external C++ binaries.
 */
class AndroidTtsEngine(private val context: Context) : TextToSpeech {

    override val engineName: String = "android-system-tts"
    override val sampleRate: Int = 22050

    private val tag = "AndroidTtsEngine"
    private var platformTts: PlatformTts? = null
    private var isInitialized = false
    private val initDeferred = CompletableDeferred<Boolean>()

    init {
        try {
            platformTts = PlatformTts(context.applicationContext) { status ->
                if (status == PlatformTts.SUCCESS) {
                    val result = platformTts?.setLanguage(Locale.US)
                    if (result == PlatformTts.LANG_MISSING_DATA || result == PlatformTts.LANG_NOT_SUPPORTED) {
                        Logger.w(tag, "US English not supported, falling back to device default locale")
                        platformTts?.setLanguage(Locale.getDefault())
                    }
                    // Speech characteristics tuned for telephone assistant
                    platformTts?.setPitch(1.05f) // Slightly friendly, warm pitch
                    platformTts?.setSpeechRate(0.98f) // Natural conversational pace
                    isInitialized = true
                    initDeferred.complete(true)
                    Logger.i(tag, "Platform TextToSpeech initialized successfully")
                } else {
                    Logger.e(tag, "Failed to initialize platform TextToSpeech (status=$status)")
                    isInitialized = false
                    initDeferred.complete(false)
                }
            }
        } catch (e: Exception) {
            Logger.e(tag, "Exception during platform TTS instantiation", tr = e)
            initDeferred.complete(false)
        }
    }

    override fun isModelLoaded(): Boolean = isInitialized

    /**
     * Speaks text directly using Android's platform TTS through the given audio stream.
     * Ideal for testing the assistant out loud on the loudspeaker, or direct in-call speech.
     */
    suspend fun speakDirect(
        text: String,
        usage: Int = AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE,
        onDone: () -> Unit = {}
    ): Boolean = withContext(Dispatchers.Main) {
        val ready = withTimeoutOrNull(2500) { initDeferred.await() } ?: isInitialized
        if (!ready || platformTts == null) {
            Logger.w(tag, "TTS not ready for speakDirect")
            return@withContext false
        }

        val sanitized = KokoroTtsEngine.sanitizeTextForSpeech(text)
        if (sanitized.isBlank()) return@withContext false

        val utteranceId = "utt_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(4)}"
        val completionDeferred = CompletableDeferred<Unit>()

        platformTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Logger.d(tag, "TTS started speaking: $utteranceId")
            }

            override fun onDone(utteranceId: String?) {
                Logger.d(tag, "TTS finished speaking: $utteranceId")
                completionDeferred.complete(Unit)
                onDone()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Logger.w(tag, "TTS error on utterance: $utteranceId")
                completionDeferred.complete(Unit)
                onDone()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                Logger.w(tag, "TTS error code $errorCode on utterance: $utteranceId")
                completionDeferred.complete(Unit)
                onDone()
            }
        })

        val params = Bundle()
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(usage)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        platformTts?.setAudioAttributes(audioAttributes)

        val result = platformTts?.speak(sanitized, PlatformTts.QUEUE_FLUSH, params, utteranceId)
        if (result == PlatformTts.SUCCESS) {
            withTimeoutOrNull(15000) { completionDeferred.await() }
            true
        } else {
            Logger.w(tag, "platformTts.speak returned error code: $result")
            false
        }
    }

    /**
     * Synthesizes text into 16-bit PCM samples so [com.aicall.agent.audio.CallAudioPlayback]
     * can stream it via AudioTrack to the carrier call / earpiece.
     */
    override suspend fun synthesize(text: String): ShortArray = withContext(Dispatchers.IO) {
        val ready = withTimeoutOrNull(3000) { initDeferred.await() } ?: isInitialized
        if (!ready || platformTts == null) {
            Logger.w(tag, "TTS not initialized, cannot synthesize")
            return@withContext ShortArray(0)
        }

        val sanitized = KokoroTtsEngine.sanitizeTextForSpeech(text)
        if (sanitized.isBlank()) {
            return@withContext ShortArray(0)
        }

        val tempWavFile = File(context.cacheDir, "tts_synth_${System.currentTimeMillis()}.wav")
        val utteranceId = "synth_${System.currentTimeMillis()}"
        val synthesisDone = CompletableDeferred<Boolean>()

        withContext(Dispatchers.Main) {
            platformTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}

                override fun onDone(id: String?) {
                    if (id == utteranceId) synthesisDone.complete(true)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) {
                    if (id == utteranceId) synthesisDone.complete(false)
                }

                override fun onError(id: String?, errorCode: Int) {
                    if (id == utteranceId) synthesisDone.complete(false)
                }
            })

            val params = Bundle()
            val result = platformTts?.synthesizeToFile(sanitized, params, tempWavFile, utteranceId)
            if (result != PlatformTts.SUCCESS) {
                Logger.w(tag, "synthesizeToFile failed with result: $result")
                synthesisDone.complete(false)
            }
        }

        val success = withTimeoutOrNull(6000) { synthesisDone.await() } ?: false
        if (!success || !tempWavFile.exists() || tempWavFile.length() <= 44) {
            Logger.w(tag, "WAV synthesis failed or produced empty file")
            tempWavFile.delete()
            return@withContext ShortArray(0)
        }

        try {
            val pcm = readWavToShortArray(tempWavFile)
            Logger.i(tag, "Synthesized ${pcm.size} PCM samples for: \"${sanitized.take(30)}...\"")
            tempWavFile.delete()
            return@withContext pcm
        } catch (e: Exception) {
            Logger.e(tag, "Failed to parse synthesized WAV file", tr = e)
            tempWavFile.delete()
            return@withContext ShortArray(0)
        }
    }

    fun stop() {
        try {
            platformTts?.stop()
        } catch (_: Exception) {}
    }

    fun shutdown() {
        try {
            platformTts?.stop()
            platformTts?.shutdown()
            platformTts = null
            isInitialized = false
        } catch (_: Exception) {}
    }

    companion object {
        /**
         * Reads 16-bit PCM samples from a standard WAV file.
         */
        fun readWavToShortArray(file: File): ShortArray {
            val bytes = file.readBytes()
            if (bytes.size <= 44) return ShortArray(0)

            val channels = (bytes[22].toInt() and 0xFF) or ((bytes[23].toInt() and 0xFF) shl 8)
            val dataOffset = 44
            val shortCount = (bytes.size - dataOffset) / 2
            if (shortCount <= 0) return ShortArray(0)

            val buffer = ByteBuffer.wrap(bytes, dataOffset, bytes.size - dataOffset).order(ByteOrder.LITTLE_ENDIAN)
            val rawShorts = ShortArray(shortCount)
            for (i in 0 until shortCount) {
                rawShorts[i] = buffer.short
            }

            // If stereo, convert to mono for telephony pipeline
            return if (channels == 2) {
                val mono = ShortArray(shortCount / 2)
                for (i in mono.indices) {
                    mono[i] = ((rawShorts[i * 2].toInt() + rawShorts[i * 2 + 1].toInt()) / 2).toShort()
                }
                mono
            } else {
                rawShorts
            }
        }
    }
}
