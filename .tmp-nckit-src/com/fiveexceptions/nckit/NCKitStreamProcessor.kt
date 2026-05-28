package com.fiveexceptions.nckit

import com.fiveexceptions.nckit.internal.NCKitAudioResampler

/**
 * Live / real-time denoise adapter. Accepts variable-size PCM input (e.g. from
 * [android.media.AudioRecord]), resamples to 48 kHz mono Float32, accumulates
 * 10 ms hops, and drives [NCKitProcessor.processFrame].
 *
 * Mirrors `NCKitStreamProcessor` from the iOS SDK.
 *
 * ### Threading
 * Not thread-safe — call [prepare], [process], and [flush] from a single serial thread.
 */
public class NCKitStreamProcessor(
    public val processor: NCKitProcessor,
) {
    public val frameLength: Int get() = processor.frameLength

    private var preparedFormat: NCKitPcmFormat? = null
    private val pending = ArrayList<Float>(frameLength * 4)
    private var readIdx = 0
    private val workIn = FloatArray(frameLength)
    private val workOut = FloatArray(frameLength)

    /**
     * Configure resampling for the tap's PCM format. Call again when the route changes.
     */
    public fun prepare(inputFormat: NCKitPcmFormat) {
        if (preparedFormat == inputFormat) return
        preparedFormat = inputFormat
    }

    /**
     * Resample and down-mix to 48 kHz mono Float32 without running inference.
     */
    public fun convertToTargetFormat(samples: FloatArray, format: NCKitPcmFormat): FloatArray {
        if (preparedFormat == null) prepare(format)
        if (samples.isEmpty()) return FloatArray(0)
        if (!NCKitAudioResampler.needsConversion(format.sampleRate, format.channelCount)) {
            return if (format.channelCount == 1) samples else {
                NCKitAudioResampler.downmixFloatToMono(samples, format.channelCount, interleaved = true)
            }
        }
        return NCKitAudioResampler.toMono48k(samples, format.sampleRate, format.channelCount)
    }

    /**
     * Convert 16-bit PCM shorts (interleaved) to 48 kHz mono Float32 without inference.
     */
    public fun convertToTargetFormat(samples: ShortArray, format: NCKitPcmFormat): FloatArray {
        if (preparedFormat == null) prepare(format)
        if (samples.isEmpty()) return FloatArray(0)
        return NCKitAudioResampler.shortsToMono48k(samples, format.sampleRate, format.channelCount)
    }

    /**
     * Process one buffer of Float32 PCM and return zero or more denoised hops.
     */
    public fun process(samples: FloatArray, format: NCKitPcmFormat): List<FloatArray> {
        val mono48k = convertToTargetFormat(samples, format)
        return processConverted(mono48k)
    }

    /**
     * Process one buffer of 16-bit interleaved PCM and return denoised hops.
     */
    public fun process(samples: ShortArray, format: NCKitPcmFormat): List<FloatArray> {
        val mono48k = convertToTargetFormat(samples, format)
        return processConverted(mono48k)
    }

    /**
     * Feed already-converted 48 kHz mono Float32 samples and return denoised hops.
     */
    public fun processConverted(samples: FloatArray): List<FloatArray> {
        if (samples.isEmpty()) return emptyList()
        pending.addAll(samples.toList())
        return drainCompleteHops()
    }

    /**
     * Zero-pad any partial hop and emit remaining denoised frames (call when stopping capture).
     */
    public fun flush(): List<FloatArray> {
        val hop = frameLength
        val tail = pending.size - readIdx
        if (tail > 0) {
            val padToHop = (hop - (tail % hop)) % hop
            repeat(padToHop) { pending.add(0f) }
        }
        return drainCompleteHops(flushPending = true)
    }

    public fun setAttenLim(db: Float): Unit = processor.setAttenLim(db)

    public fun setPostFilterBeta(beta: Float): Unit = processor.setPostFilterBeta(beta)

    /** Clear pending samples and format state (call when stopping a live session). */
    public fun reset() {
        preparedFormat = null
        pending.clear()
        readIdx = 0
    }

    private fun drainCompleteHops(flushPending: Boolean = false): List<FloatArray> {
        val hop = frameLength
        val output = ArrayList<FloatArray>()
        while (pending.size - readIdx >= hop) {
            val base = readIdx
            for (i in 0 until hop) workIn[i] = pending[base + i]
            processor.processFrame(workIn, workOut)
            output.add(workOut.copyOf())
            readIdx += hop
            if (readIdx > 262_144) {
                pending.subList(0, readIdx).clear()
                readIdx = 0
            }
        }
        if (flushPending) {
            pending.clear()
            readIdx = 0
        }
        return output
    }

    public companion object {
        /** Sample rate required by [NCKitProcessor] / libdf. */
        public const val TARGET_SAMPLE_RATE: Int = NCKitFileProcessor.TARGET_SAMPLE_RATE
    }
}
