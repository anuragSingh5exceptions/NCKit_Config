package com.fiveexceptions.nckit.internal

import com.fiveexceptions.nckit.NCKitException
import java.io.File
import java.io.RandomAccessFile

/**
 * Append-only 16-bit PCM WAV writer. Mirrors iOS `StreamingWavInt16Writer`.
 */
internal class NCKitStreamingWavWriter(
    dest: File,
    private val sampleRate: Int,
) {
    private val raf: RandomAccessFile
    private var totalPcmBytes: Int = 0

    init {
        if (dest.exists()) dest.delete()
        dest.parentFile?.mkdirs()
        raf = RandomAccessFile(dest, "rw")
        raf.setLength(44) // placeholder header
    }

    fun writeFloatSamples(samples: FloatArray) {
        for (s in samples) {
            val pcm = (s.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            raf.write(pcm.toInt() and 0xFF)
            raf.write((pcm.toInt() shr 8) and 0xFF)
            totalPcmBytes += 2
        }
    }

    fun finalizeHeader() {
        val dataSize = totalPcmBytes
        val chunkSize = 36 + dataSize
        raf.seek(0)
        fun writeInt(v: Int) {
            raf.write(byteArrayOf(
                (v and 0xFF).toByte(),
                ((v shr 8) and 0xFF).toByte(),
                ((v shr 16) and 0xFF).toByte(),
                ((v shr 24) and 0xFF).toByte(),
            ))
        }
        fun writeShort(v: Int) {
            raf.write(byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte()))
        }
        fun writeAscii(s: String) = raf.write(s.toByteArray(Charsets.US_ASCII))

        writeAscii("RIFF"); writeInt(chunkSize)
        writeAscii("WAVE"); writeAscii("fmt ")
        writeInt(16); writeShort(1); writeShort(1)
        writeInt(sampleRate)
        writeInt(sampleRate * 2)
        writeShort(2); writeShort(16)
        writeAscii("data"); writeInt(dataSize)
        raf.close()
    }

    fun closeOnError() {
        try {
            raf.close()
        } catch (_: Exception) {
        }
    }
}

internal fun openStreamingWav(dest: File, sampleRate: Int): NCKitStreamingWavWriter {
    return try {
        NCKitStreamingWavWriter(dest, sampleRate)
    } catch (_: Exception) {
        throw NCKitException.CannotCreateOutput()
    }
}
