package com.fiveexceptions.nckit

import java.io.File

/**
 * Primary noise-cancellation engine. Wraps the Rust `libdf` C API
 * (`df_create`, `df_process_frame`, `df_free`) via JNI.
 *
 * Manages all STFT, ERB feature extraction, GRU hidden state, and deep-filter
 * inference internally — you only provide and consume Float32 sample arrays.
 *
 * ### Thread safety
 * [processFrame] is **not** thread-safe. Call it from a single dedicated thread
 * or coroutine context. [setAttenLim] and [setPostFilterBeta] are safe from any thread.
 *
 * ### Lifecycle
 * [NCKitProcessor] holds ~30 MB of native state. Create one instance and reuse it
 * for the duration of a session. Call [close] (or use `use {}`) when done.
 *
 * Mirrors `NCKitProcessor` from the iOS SDK.
 *
 * @param modelFile Filesystem path to `NCKit_model.tar.gz` (or compatible export).
 *                  Use [NCKitModelLocator.modelTarGzFile] to resolve the embedded model.
 */
public class NCKitProcessor @JvmOverloads constructor(
    modelFile: File,
    public val attenLimDb: Float = 100f,
    public val postFilterBeta: Float = 0f,
) : AutoCloseable {

    private var nativeHandle: Long

    /**
     * The number of Float32 samples consumed per [processFrame] call.
     * Always `480` for NCKit (10 ms at 48 kHz).
     */
    public val frameLength: Int

    init {
        if (!modelFile.exists()) {
            throw NCKitException.MissingModel(modelFile.name)
        }
        nativeHandle = nativeCreate(modelFile.absolutePath, attenLimDb, postFilterBeta)
        if (nativeHandle == 0L) {
            throw NCKitException.LibraryInit()
        }
        frameLength = nativeGetFrameLength(nativeHandle)
    }

    /**
     * Process exactly [frameLength] mono Float32 samples at 48 kHz.
     *
     * - Both arrays must have length ≥ [frameLength].
     * - `input` and `output` may be the same array (in-place processing is allowed).
     * - Returns the estimated local SNR for this frame in dB.
     *
     * **Not thread-safe.** Call from a single serial context.
     *
     * @throws NCKitException.BadFrameLength if either array is shorter than [frameLength].
     */
    public fun processFrame(input: FloatArray, output: FloatArray): Float {
        if (input.size < frameLength || output.size < frameLength) {
            val bad = minOf(input.size, output.size)
            throw NCKitException.BadFrameLength(bad)
        }
        return nativeProcessFrame(nativeHandle, input, output)
    }

    public fun setAttenLim(db: Float) {
        nativeSetAttenLim(nativeHandle, db)
    }

    public fun setPostFilterBeta(beta: Float) {
        nativeSetPostFilterBeta(nativeHandle, beta)
    }

    override fun close() {
        if (nativeHandle != 0L) {
            nativeDestroy(nativeHandle)
            nativeHandle = 0L
        }
    }

    private external fun nativeCreate(path: String, attenLim: Float, postFilterBeta: Float): Long
    private external fun nativeGetFrameLength(handle: Long): Int
    private external fun nativeProcessFrame(handle: Long, input: FloatArray, output: FloatArray): Float
    private external fun nativeSetAttenLim(handle: Long, db: Float)
    private external fun nativeSetPostFilterBeta(handle: Long, beta: Float)
    private external fun nativeDestroy(handle: Long)

    public companion object {
        init {
            System.loadLibrary("dfn3kit_jni")
        }
    }
}
