package com.fiveexceptions.nckit

/**
 * Describes PCM layout for [NCKitStreamProcessor.prepare].
 *
 * Mirrors `AVAudioFormat` usage on iOS (sample rate + channel count).
 */
public data class NCKitPcmFormat(
    val sampleRate: Int,
    val channelCount: Int,
) {
    init {
        require(sampleRate > 0) { "sampleRate must be positive" }
        require(channelCount > 0) { "channelCount must be positive" }
    }
}
