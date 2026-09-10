package com.aicall.agent.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import com.aicall.agent.util.Logger
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Handles AudioTrack playback strictly routed to the device's earpiece speaker.
 *
 * Physical acoustic loop mechanism:
 * The synthesized voice plays out of the internal earpiece speaker at a calibrated volume.
 * Because of the phone's physical form factor, the primary microphone picks up this acoustic
 * signal naturally as near-end speech and feeds it back into the live carrier call uplink
 * without loudspeaker howling or room echo.
 */
class CallAudioPlayback(private val context: Context) {

    private val tag = "CallAudioPlayback"
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var audioTrack: AudioTrack? = null
    private val isPlaying = AtomicBoolean(false)
    private val isCancelled = AtomicBoolean(false)

    /**
     * Prepares AudioTrack with USAGE_VOICE_COMMUNICATION and routes to BUILTIN_EARPIECE.
     */
    fun initTrack(sampleRate: Int = 22050): Boolean {
        release()

        val minBufSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()

        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(minBufSize * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: Exception) {
            Logger.e(tag, "Failed to build AudioTrack", tr = e)
            return false
        }

        // Force routing to BUILTIN_EARPIECE (not speakerphone, not bluetooth)
        forceEarpieceRouting(track)

        audioTrack = track
        return true
    }

    private fun forceEarpieceRouting(track: AudioTrack) {
        val am = audioManager ?: return
        try {
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            am.isSpeakerphoneOn = false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                val earpiece = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                if (earpiece != null) {
                    val success = track.setPreferredDevice(earpiece)
                    Logger.i(tag, "Routed AudioTrack to BUILTIN_EARPIECE (success=$success)")
                } else {
                    Logger.w(tag, "BUILTIN_EARPIECE not found in audio device list; relying on mode=IN_COMMUNICATION")
                }
            }
        } catch (e: Exception) {
            Logger.w(tag, "Error setting preferred earpiece route", tr = e)
        }
    }

    /**
     * Plays the given 16-bit PCM samples through the earpiece.
     * Blocks until playback completes or is cancelled (barge-in).
     */
    fun playAudio(pcmSamples: ShortArray, sampleRate: Int = 22050): Boolean {
        if (!initTrack(sampleRate)) {
            return false
        }

        val track = audioTrack ?: return false
        isPlaying.set(true)
        isCancelled.set(false)

        try {
            track.play()
            var offset = 0
            val chunkSize = 1024

            while (offset < pcmSamples.size && !isCancelled.get()) {
                val count = (pcmSamples.size - offset).coerceAtMost(chunkSize)
                val written = track.write(pcmSamples, offset, count)
                if (written < 0) {
                    Logger.w(tag, "AudioTrack write returned error: $written")
                    break
                }
                offset += written
            }

            if (!isCancelled.get()) {
                // Allow audio buffer to drain
                Thread.sleep(100)
            }
        } catch (e: Exception) {
            Logger.e(tag, "Error during earpiece playback", tr = e)
            return false
        } finally {
            isPlaying.set(false)
            release()
        }

        return !isCancelled.get()
    }

    /**
     * Cancels current playback immediately (used when the user interrupts / speaks).
     */
    fun stop() {
        isCancelled.set(true)
        isPlaying.set(false)
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.stop()
        } catch (_: Exception) {}
    }

    fun release() {
        try {
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
    }

    fun isPlaying(): Boolean = isPlaying.get()
}
