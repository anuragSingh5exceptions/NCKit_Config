package com.fiveexceptions.nckit

import kotlin.math.*

/**
 * Post-processing helpers for consistent audio levels after denoising.
 *
 * These are pure functions — they do not involve [NCKitProcessor] or the ONNX model.
 * Both methods are safe to call from any thread.
 *
 * Mirrors `NCKitAudioNormalizer` from the iOS SDK.
 */
public object NCKitAudioNormalizer {

    /**
     * Measure the RMS level of the *speech* portion of [samples] (frames above a
     * noise gate), then apply **one constant gain** to reach [targetRmsDbfs].
     * Peaks are soft-limited with a tanh curve at [peakCeilingDbfs].
     *
     * Why one constant gain? Dynamic compressors or per-frame AGC after a neural
     * denoiser cause audible "pumping" as noise estimates drift. A single scalar
     * gain derived from speech-only measurement is stable and artefact-free.
     *
     * @param samples         Mono Float32 audio, range −1.0 … +1.0. Modified in place.
     * @param sampleRate      Sample rate of [samples]. Use 48 000 for NCKit output.
     * @param targetRmsDbfs   Target RMS level for speech frames in dBFS. Default −18 matches
     *                        the typical loudness target for spoken word.
     * @param maxGainDb        Upper bound on applied gain. Prevents extreme boosts when the
     *                        input is very quiet. Default 15 dB.
     * @param peakCeilingDbfs Soft-limit ceiling in dBTP. Default −1 leaves 1 dB of headroom.
     */
    @JvmStatic
    @JvmOverloads
    public fun applySpeechGatedMakeupGain(
        samples: FloatArray,
        sampleRate: Int,
        targetRmsDbfs: Float  = -18f,
        maxGainDb: Float      = 15f,
        peakCeilingDbfs: Float = -1f,
    ) {
        if (samples.isEmpty()) return

        val frameLen = (sampleRate * 0.020).toInt().coerceAtLeast(1) // 20 ms frames
        val noiseSilenceThreshDb = -50f                               // gate below this
        val noiseSilenceLinear   = 10f.pow(noiseSilenceThreshDb / 20f)

        // Measure RMS of speech frames (above gate)
        var sumSq       = 0.0
        var speechCount = 0
        var i           = 0
        while (i + frameLen <= samples.size) {
            var frameSumSq = 0.0
            for (j in i until i + frameLen) frameSumSq += (samples[j] * samples[j]).toDouble()
            val rms = sqrt(frameSumSq / frameLen).toFloat()
            if (rms > noiseSilenceLinear) {
                sumSq += frameSumSq
                speechCount += frameLen
            }
            i += frameLen
        }

        if (speechCount == 0) return                           // nothing to normalise

        val speechRms   = sqrt(sumSq / speechCount).toFloat()
        val targetLinear = 10f.pow(targetRmsDbfs / 20f)
        val rawGain     = targetLinear / speechRms.coerceAtLeast(1e-9f)
        val maxGainLin  = 10f.pow(maxGainDb / 20f)
        val gain        = rawGain.coerceAtMost(maxGainLin)

        // Apply gain then soft-limit
        for (k in samples.indices) {
            samples[k] *= gain
        }
        softLimitInPlace(samples, peakCeilingDbfs)
    }

    /**
     * Apply only the tanh soft-limiter portion of [applySpeechGatedMakeupGain].
     * Useful when you have your own gain staging but still want clean peak limiting.
     *
     * @param samples         Mono Float32 audio. Modified in place.
     * @param peakCeilingDbfs Soft-limit ceiling in dBTP. Default −1.
     */
    @JvmStatic
    @JvmOverloads
    public fun softLimitInPlace(
        samples: FloatArray,
        peakCeilingDbfs: Float = -1f,
    ) {
        val ceiling = 10f.pow(peakCeilingDbfs / 20f)
        // tanh soft knee — maps ±∞ into ±ceiling asymptotically
        for (i in samples.indices) {
            samples[i] = (tanh(samples[i].toDouble()) * ceiling).toFloat()
        }
    }
}
