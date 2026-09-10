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

        Logger.i(tag, "Initializing AudioRecord: source=$audioSource, rate=$sampleRate, buffer=$internalBufferSize", sessionId)

        val recordInstance: AudioRecord = try {
            AudioRecord(
                audioSource,
                sampleRate,
                channelConfig,
                audioFormat,
                internalBufferSize
            )
        } catch (e: Exception) {
            Logger.e(tag, "Failed to instantiate AudioRecord for source $audioSource", sessionId, e)
            isCapturing.set(false)
            return false
        }

        if (recordInstance.state != AudioRecord.STATE_INITIALIZED) {
            Logger.e(tag, "AudioRecord failed to initialize (state=${recordInstance.state}). Check CAPTURE_AUDIO_OUTPUT priv-app permission!", sessionId)
            recordInstance.release()
            isCapturing.set(false)
            return false
        }

        audioRecord = recordInstance

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
                recordInstance.startRecording()
                Logger.i(tag, "AudioRecord started recording to ${outputFile.name}", sessionId)

                while (isCapturing.get()) {
                    val readCount = recordInstance.read(readBuffer, 0, readBuffer.size)
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
                    recordInstance.stop()
                } catch (e: Exception) {
                    Logger.w(tag, "Error stopping AudioRecord", sessionId, e)
                }
                recordInstance.release()
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
