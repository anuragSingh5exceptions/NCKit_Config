package com.fiveexceptions.nckit

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.fiveexceptions.nckit.internal.NCKitAudioResampler
import com.fiveexceptions.nckit.internal.openStreamingWav
import java.io.File
import java.nio.ByteOrder

/**
 * Offline file denoiser. Decodes any [MediaExtractor]-readable source into 48 kHz mono
 * Float32, streams it through [NCKitProcessor] one hop at a time, and writes a
 * 16-bit PCM WAV. Designed for long files without holding the full PCM in memory.
 *
 * Mirrors `NCKitFileProcessor` from the iOS SDK.
 */
public object NCKitFileProcessor {

    public const val TARGET_SAMPLE_RATE: Int = NCKitAudioResampler.TARGET_SAMPLE_RATE

    private const val LOOKAHEAD_FRAMES = 2
    private const val PENDING_COMPACT_THRESHOLD = 262_144

    /**
     * Denoise an entire audio or video file end-to-end.
     *
     * **Blocking** — never call from the main thread.
     */
    @JvmStatic
    @Throws(NCKitException::class)
    public fun processFile(inputFile: File, outputFile: File, processor: NCKitProcessor) {
        val hop = processor.frameLength
        val delaySkip = hop * LOOKAHEAD_FRAMES

        val workIn = FloatArray(hop)
        val workOut = FloatArray(hop)
        val pending = ArrayList<Float>(hop * 4)
        var readIdx = 0

        var sawAnySample = false
        var fullHopFrames = 0
        var totalInputSamples = 0L
        var outputSkipRemaining = delaySkip
        var outputWritten = 0L

        val writer = openStreamingWav(outputFile, TARGET_SAMPLE_RATE)

        fun emitProcessed(samples: FloatArray) {
            var start = 0
            var count = samples.size
            if (outputSkipRemaining > 0) {
                val drop = minOf(outputSkipRemaining, count)
                start += drop
                count -= drop
                outputSkipRemaining -= drop
            }
            if (count <= 0) return
            val room = totalInputSamples - outputWritten
            if (room <= 0) return
            val take = minOf(count, room.toInt())
            writer.writeFloatSamples(samples.copyOfRange(start, start + take))
            outputWritten += take
        }

        fun flushHops() {
            while (pending.size - readIdx >= hop) {
                val base = readIdx
                for (i in 0 until hop) workIn[i] = pending[base + i]
                processor.processFrame(workIn, workOut)
                emitProcessed(workOut)
                readIdx += hop
                fullHopFrames++
                if (readIdx > PENDING_COMPACT_THRESHOLD) {
                    pending.subList(0, readIdx).clear()
                    readIdx = 0
                }
            }
        }

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(inputFile.absolutePath)
        } catch (_: Exception) {
            writer.closeOnError()
            throw NCKitException.CannotOpenInput()
        }

        try {
            val (trackIndex, inputFormat) = findAudioTrack(extractor)
                ?: throw NCKitException.UnsupportedFormat()
            extractor.selectTrack(trackIndex)

            streamMonoFloat48k(extractor, inputFormat) { chunk ->
                if (chunk.isEmpty()) return@streamMonoFloat48k
                sawAnySample = true
                totalInputSamples += chunk.size
                pending.addAll(chunk.toList())
                flushHops()
            }

            if (!sawAnySample || totalInputSamples <= 0) {
                throw NCKitException.UnsupportedFormat()
            }

            val tail = pending.size - readIdx
            if (tail > 0) {
                val padToHop = (hop - (tail % hop)) % hop
                repeat(padToHop) { pending.add(0f) }
            }
            repeat(delaySkip + hop) { pending.add(0f) }
            flushHops()

            if (fullHopFrames == 0) throw NCKitException.UnsupportedFormat()

            writer.finalizeHeader()
        } catch (e: NCKitException) {
            writer.closeOnError()
            throw e
        } catch (_: Exception) {
            writer.closeOnError()
            throw NCKitException.CannotCreateOutput()
        } finally {
            extractor.release()
        }
    }

    private fun findAudioTrack(extractor: MediaExtractor): Pair<Int, MediaFormat>? {
        for (i in 0 until extractor.trackCount) {
            val fmt = extractor.getTrackFormat(i)
            if (fmt.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                return Pair(i, fmt)
            }
        }
        return null
    }

    private fun streamMonoFloat48k(
        extractor: MediaExtractor,
        inputFormat: MediaFormat,
        onChunk: (FloatArray) -> Unit,
    ) {
        val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: throw NCKitException.ResampleFailed()
        val srcRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val srcCh = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(inputFormat, null, null, 0)
        codec.start()

        val chunkShorts = ArrayList<Short>(8192 * srcCh)
        val info = MediaCodec.BufferInfo()
        var sawEOS = false

        try {
            while (!sawEOS) {
                val inIdx = codec.dequeueInputBuffer(10_000)
                if (inIdx >= 0) {
                    val buf = codec.getInputBuffer(inIdx)!!
                    val n = extractor.readSampleData(buf, 0)
                    if (n < 0) {
                        codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    } else {
                        codec.queueInputBuffer(inIdx, 0, n, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                if (outIdx >= 0) {
                    val buf = codec.getOutputBuffer(outIdx)!!
                    buf.order(ByteOrder.LITTLE_ENDIAN)
                    val shortBuf = buf.asShortBuffer()
                    val count = shortBuf.remaining()
                    if (count > 0) {
                        val temp = ShortArray(count)
                        shortBuf.get(temp)
                        for (s in temp) chunkShorts.add(s)
                        if (chunkShorts.size >= 8192 * srcCh) {
                            emitChunk(chunkShorts, srcRate, srcCh, onChunk)
                            chunkShorts.clear()
                        }
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawEOS = true
                }
            }
            if (chunkShorts.isNotEmpty()) {
                emitChunk(chunkShorts, srcRate, srcCh, onChunk)
            }
        } finally {
            codec.stop()
            codec.release()
        }
    }

    private fun emitChunk(
        shorts: List<Short>,
        srcRate: Int,
        srcCh: Int,
        onChunk: (FloatArray) -> Unit,
    ) {
        val arr = shorts.toShortArray()
        val mono48k = NCKitAudioResampler.shortsToMono48k(arr, srcRate, srcCh)
        if (mono48k.isNotEmpty()) onChunk(mono48k)
    }
}
