package com.aicall.agent.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioFileWriterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testWavFileCreationAndHeader() {
        val tempFile = tempFolder.newFile("test_audio.wav")
        val writer = AudioFileWriter(tempFile, sampleRate = 16000, channels = 1, bitsPerSample = 16)

        // 16000 samples per second = 1 second of audio (16-bit mono = 32000 bytes)
        val dummyPcm = ShortArray(16000) { (it % 1000).toShort() }
        writer.write(dummyPcm, dummyPcm.size)
        writer.close()

        assertTrue(tempFile.exists())
        assertEquals(32044L, tempFile.length()) // 44 bytes header + 32000 bytes data

        // Verify RIFF and WAVE header markers
        RandomAccessFile(tempFile, "r").use { raf ->
            val headerBytes = ByteArray(44)
            raf.readFully(headerBytes)
            val buffer = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)

            val riff = String(headerBytes, 0, 4)
            val wave = String(headerBytes, 8, 4)
            val fmt = String(headerBytes, 12, 4)
            val data = String(headerBytes, 36, 4)

            assertEquals("RIFF", riff)
            assertEquals("WAVE", wave)
            assertEquals("fmt ", fmt)
            assertEquals("data", data)

            buffer.position(24)
            val sampleRate = buffer.getInt()
            assertEquals(16000, sampleRate)

            buffer.position(40)
            val dataSize = buffer.getInt()
            assertEquals(32000, dataSize)
        }
    }
}
