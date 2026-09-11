package com.aicall.agent.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import com.aicall.agent.util.Logger
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/**
 * Privileged Call Audio Capture ported from chenxiaolong/BCR.
 * Captures live voice stream using AudioRecord with MediaRecorder.AudioSource.VOICE_CALL.
 *
 * HAL/Permission notes:
 * - VOICE_CALL requires CAPTURE_AUDIO_OUTPUT (protected permission granted via Shizuku ADB shell).
 * - On Qualcomm chipsets, some OEM audio HALs mix uplink and downlink audio into VOICE_CALL;
 *   others provide downlink only. If VOICE_CALL produces error (-38) or empty frames,
 *   we attempt negotiation with VOICE_DOWNLINK.
 */
class CallAudioCapture(
    private val context: Context,
    private val audioSource: Int = AudioCaptureConfig.DEFAULT_AUDIO_SOURCE
) {
    private val tag = "CallAudioCapture"
    private val isCapturing = AtomicBoolean(false)
    private var captureThread: Thread? = null
    private var audioRecord: AudioRecord? = null

    // Listeners for live audio amplitude (0..100) for UI waveforms/meters
    private val amplitudeListeners = CopyOnWriteArrayList<(Int) -> Unit>()

    fun addAmplitudeListener(listener: (Int) -> Unit) {
        amplitudeListeners.add(listener)
    }

    fun removeAmplitudeListener(listener: (Int) -> Unit) {
        amplitudeListeners.remove(listener)
    }

    /**
     * Starts audio capture into a specified WAV file for the given call session.
     */
    @SuppressLint("MissingPermission")
    fun startCapture(sessionId: String, outputFile: File, onComplete: ((File?, Long) -> Unit)? = null): Boolean {
        if (isCapturing.getAndSet(true)) {
            Logger.w(tag, "Capture is already running for session: $sessionId", sessionId)
            return false
        }

        val sampleRate = AudioCaptureConfig.SAMPLE_RATE
        val channelConfig = AudioCaptureConfig.CHANNEL_CONFIG
        val audioFormat = AudioCaptureConfig.AUDIO_FORMAT
        val minBufferSize = AudioCaptureConfig.calculateMinBufferSize(sampleRate)

        // Use 4x buffer to withstand CPU spikes under load without dropped frames
        val internalBufferSize = minBufferSize * 4

        /**
         * Multi-tier HAL fallback matrix for OEM chipset compatibility:
         *   Tier 1: VOICE_CALL        — captures full two-way call audio (requires Shizuku/priv-app)
         *   Tier 2: VOICE_DOWNLINK    — captures only the remote (downlink) audio stream
         *   Tier 3: VOICE_COMMUNICATION — echo-cancelled VoIP pipeline, widely supported
         *   Tier 4: MIC               — last-resort acoustic near-field capture
         *
         * We try each tier in order; the first one that initialises successfully wins.
         */
        val fallbackSources = buildList {
            add(audioSource) // User-configured source (default: VOICE_CALL)
            if (audioSource != MediaRecorder.AudioSource.VOICE_DOWNLINK)
                add(MediaRecorder.AudioSource.VOICE_DOWNLINK)
            if (audioSource != MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                add(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            if (audioSource != MediaRecorder.AudioSource.MIC)
                add(MediaRecorder.AudioSource.MIC)
        }

        var recordInstance: AudioRecord? = null
        var usedSource = audioSource

        for (source in fallbackSources) {
            try {
                val candidate = AudioRecord(
                    source,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    internalBufferSize
                )
                if (candidate.state == AudioRecord.STATE_INITIALIZED) {
                    recordInstance = candidate
                    usedSource = source
                    if (source != audioSource) {
                        Logger.w(tag, "HAL fallback: primary source $audioSource failed; using source $source", sessionId)
                    } else {
                        Logger.i(tag, "AudioRecord initialized with source $source", sessionId)
                    }
                    break
                } else {
                    Logger.w(tag, "AudioRecord source $source not initialized (state=${candidate.state}); trying next tier", sessionId)
                    candidate.release()
                }
            } catch (e: Exception) {
                Logger.w(tag, "AudioRecord source $source threw exception: ${e.message}; trying next tier", sessionId)
            }
        }

        if (recordInstance == null) {
            Logger.e(tag, "All AudioRecord sources exhausted — cannot capture audio for session $sessionId", sessionId)
            isCapturing.set(false)
            return false
        }

        // Non-null guaranteed by the early-return check above
        val confirmedRecord: AudioRecord = recordInstance
        audioRecord = confirmedRecord

        captureThread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val fileWriter = try {
                AudioFileWriter(outputFile, sampleRate = sampleRate)
            } catch (e: Exception) {
                Logger.e(tag, "Could not create AudioFileWriter: ${outputFile.path}", sessionId, e)
                isCapturing.set(false)
                return@Thread
            }

            val readBuffer = ShortArray(minBufferSize / 2)
            var totalSamplesRead = 0L

            try {
                confirmedRecord.startRecording()
                Logger.i(tag, "AudioRecord started recording to ${outputFile.name} (source=$usedSource)", sessionId)

                while (isCapturing.get()) {
                    val readCount = confirmedRecord.read(readBuffer, 0, readBuffer.size)
                    if (readCount > 0) {
                        fileWriter.write(readBuffer, readCount)
                        totalSamplesRead += readCount

                        // Calculate RMS amplitude for UI feedback
                        var sumSquare = 0.0
                        for (i in 0 until readCount) {
                            val sample = readBuffer[i].toDouble()
                            sumSquare += sample * sample
                        }
                        val rms = sqrt(sumSquare / readCount)
                        val normalizedAmp = ((rms / 32768.0) * 100).toInt().coerceIn(0, 100)

                        for (listener in amplitudeListeners) {
                            try {
                                listener(normalizedAmp)
                            } catch (_: Exception) {}
                        }
                    } else if (readCount < 0) {
                        Logger.w(tag, "AudioRecord read returned error code: $readCount", sessionId)
                        if (readCount == AudioRecord.ERROR_INVALID_OPERATION) {
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.e(tag, "Exception in audio capture loop", sessionId, e)
            } finally {
                try {
                    confirmedRecord.stop()
                } catch (e: Exception) {
                    Logger.w(tag, "Error stopping AudioRecord", sessionId, e)
                }
                confirmedRecord.release()
                audioRecord = null
                fileWriter.close()

                Logger.i(tag, "Capture loop finished. Total samples: $totalSamplesRead, File size: ${outputFile.length()} bytes", sessionId)
                onComplete?.invoke(outputFile, totalSamplesRead)
            }
        }, "AICallAudioCapture-$sessionId").apply {
            start()
        }

        return true
    }

    /**
     * Signals the capture loop to stop recording and close the WAV file.
     */
    fun stopCapture() {
        if (isCapturing.getAndSet(false)) {
            Logger.i(tag, "Stopping audio capture")
            captureThread?.interrupt()
            captureThread = null
        }
    }

    fun isRecording(): Boolean = isCapturing.get()
}
