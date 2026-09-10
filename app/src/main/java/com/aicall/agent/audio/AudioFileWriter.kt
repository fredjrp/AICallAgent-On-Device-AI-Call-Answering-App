package com.aicall.agent.audio

import com.aicall.agent.util.Logger
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Streams raw PCM audio into a standard RIFF/WAV file.
 * Handles writing the 44-byte WAV header and updating sizes when finalized.
 */
class AudioFileWriter(
    private val outputFile: File,
    private val sampleRate: Int = AudioCaptureConfig.SAMPLE_RATE,
    private val channels: Short = 1,
    private val bitsPerSample: Short = 16
) {
    private val tag = "AudioFileWriter"
    private var outputStream: FileOutputStream? = null
    private var totalBytesWritten: Long = 0

    init {
        val parent = outputFile.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        outputStream = FileOutputStream(outputFile)
        writeWavHeaderPlaceholder()
    }

    private fun writeWavHeaderPlaceholder() {
        val stream = outputStream ?: return
        val header = ByteArray(44) // 44-byte standard RIFF/WAV header placeholder
        stream.write(header)
    }

    fun write(buffer: ByteArray, offset: Int, count: Int) {
        val stream = outputStream ?: return
        if (count > 0) {
            stream.write(buffer, offset, count)
            totalBytesWritten += count
        }
    }

    fun write(buffer: ShortArray, count: Int) {
        val stream = outputStream ?: return
        if (count <= 0) return

        val byteBuffer = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until count) {
            byteBuffer.putShort(buffer[i])
        }
        val bytes = byteBuffer.array()
        stream.write(bytes)
        totalBytesWritten += bytes.size
    }

    fun close() {
        try {
            outputStream?.flush()
            outputStream?.close()
            outputStream = null
            updateWavHeader()
        } catch (e: Exception) {
            Logger.e(tag, "Error finalizing WAV file: ${outputFile.absolutePath}", tr = e)
        }
    }

    private fun updateWavHeader() {
        if (!outputFile.exists()) return

        try {
            RandomAccessFile(outputFile, "rw").use { raf ->
                raf.seek(0)
                val totalDataLen = totalBytesWritten + 36
                val byteRate = sampleRate * channels * (bitsPerSample / 8)
                val blockAlign = (channels * (bitsPerSample / 8)).toShort()

                val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
                // ChunkID "RIFF"
                header.put('R'.code.toByte())
                header.put('I'.code.toByte())
                header.put('F'.code.toByte())
                header.put('F'.code.toByte())
                // ChunkSize
                header.putInt(totalDataLen.toInt())
                // Format "WAVE"
                header.put('W'.code.toByte())
                header.put('A'.code.toByte())
                header.put('V'.code.toByte())
                header.put('E'.code.toByte())
                // Subchunk1ID "fmt "
                header.put('f'.code.toByte())
                header.put('m'.code.toByte())
                header.put('t'.code.toByte())
                header.put(' '.code.toByte())
                // Subchunk1Size (16 for PCM)
                header.putInt(16)
                // AudioFormat (1 for PCM)
                header.putShort(1.toShort())
                // NumChannels
                header.putShort(channels)
                // SampleRate
                header.putInt(sampleRate)
                // ByteRate
                header.putInt(byteRate)
                // BlockAlign
                header.putShort(blockAlign)
                // BitsPerSample
                header.putShort(bitsPerSample)
                // Subchunk2ID "data"
                header.put('d'.code.toByte())
                header.put('a'.code.toByte())
                header.put('t'.code.toByte())
                header.put('a'.code.toByte())
                // Subchunk2Size
                header.putInt(totalBytesWritten.toInt())

                raf.write(header.array())
            }
            Logger.i(tag, "WAV header written successfully: ${outputFile.name} ($totalBytesWritten bytes)")
        } catch (e: Exception) {
            Logger.e(tag, "Failed to update WAV header", tr = e)
        }
    }
}
