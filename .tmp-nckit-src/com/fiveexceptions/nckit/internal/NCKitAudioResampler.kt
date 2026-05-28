package com.fiveexceptions.nckit.internal

import com.fiveexceptions.nckit.NCKitException

/**
 * Shared resampling / down-mix helpers for stream and file processors.
 */
internal object NCKitAudioResampler {

    const val TARGET_SAMPLE_RATE: Int = 48_000

    fun needsConversion(sampleRate: Int, channelCount: Int): Boolean =
        kotlin.math.abs(sampleRate - TARGET_SAMPLE_RATE) >= 1 || channelCount != 1

    fun linearResample(input: FloatArray, srcRate: Int, dstRate: Int): FloatArray {
        if (srcRate == dstRate) return input
        val ratio = srcRate.toDouble() / dstRate.toDouble()
        val outLength = (input.size / ratio).toInt().coerceAtLeast(0)
        return FloatArray(outLength) { i ->
            val srcPos = i * ratio
            val lo = srcPos.toInt().coerceIn(0, input.lastIndex.coerceAtLeast(0))
            val hi = (lo + 1).coerceIn(0, input.lastIndex.coerceAtLeast(0))
            val frac = (srcPos - lo).toFloat()
            input[lo] * (1f - frac) + input[hi] * frac
        }
    }

    fun downmixShortToMono(input: ShortArray, channelCount: Int): FloatArray {
        if (channelCount <= 0) throw NCKitException.UnsupportedFormat()
        val frames = input.size / channelCount
        if (frames == 0) return FloatArray(0)
        if (channelCount == 1) {
            return FloatArray(frames) { i -> input[i] / 32768f }
        }
        val scale = 1f / channelCount
        return FloatArray(frames) { i ->
            var sum = 0
            for (c in 0 until channelCount) {
                sum += input[i * channelCount + c].toInt()
            }
            (sum * scale) / 32768f
        }
    }

    fun downmixFloatToMono(input: FloatArray, channelCount: Int, interleaved: Boolean): FloatArray {
        if (channelCount <= 0) throw NCKitException.UnsupportedFormat()
        val frames = if (interleaved) input.size / channelCount else input.size
        if (frames == 0) return FloatArray(0)
        if (channelCount == 1) return input.copyOf(frames)
        val scale = 1f / channelCount
        val mono = FloatArray(frames)
        if (interleaved) {
            for (i in 0 until frames) {
                var sum = 0f
                for (c in 0 until channelCount) {
                    sum += input[i * channelCount + c]
                }
                mono[i] = sum * scale
            }
        } else {
            for (i in 0 until frames) {
                var sum = 0f
                for (c in 0 until channelCount) {
                    sum += input[c * frames + i]
                }
                mono[i] = sum * scale
            }
        }
        return mono
    }

    fun toMono48k(samples: FloatArray, sampleRate: Int, channelCount: Int): FloatArray {
        val mono = if (channelCount == 1) samples else downmixFloatToMono(samples, channelCount, interleaved = true)
        return if (sampleRate == TARGET_SAMPLE_RATE) mono else linearResample(mono, sampleRate, TARGET_SAMPLE_RATE)
    }

    fun shortsToMono48k(samples: ShortArray, sampleRate: Int, channelCount: Int): FloatArray {
        val mono = downmixShortToMono(samples, channelCount)
        return if (sampleRate == TARGET_SAMPLE_RATE) mono else linearResample(mono, sampleRate, TARGET_SAMPLE_RATE)
    }
}
