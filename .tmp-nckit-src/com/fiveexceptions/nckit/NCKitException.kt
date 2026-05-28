package com.fiveexceptions.nckit

/**
 * Every throwing method in NCKit throws a subclass of [NCKitException].
 *
 * Mirrors [NCKitError] from the iOS SDK.
 */
public sealed class NCKitException(message: String) : Exception(message) {

    /** The model tarball was not found at the expected path. [name] is the missing filename. */
    public class MissingModel(public val name: String) :
        NCKitException("Model file not found: $name")

    /** [NCKitProcessor] initialisation failed — df_create returned null. Usually a corrupted model. */
    public class LibraryInit :
        NCKitException("df_create returned null — model may be corrupted or memory insufficient")

    /**
     * A processing call received a buffer whose length does not match [NCKitProcessor.frameLength].
     * [length] is the length that was actually provided (NCKit requires 480 samples per hop at 48 kHz).
     */
    public class BadFrameLength(public val length: Int) :
        NCKitException("Expected frame length 480, got $length")

    /** [NCKitFileProcessor.processFile] could not open the input file. */
    public class CannotOpenInput :
        NCKitException("Cannot open input file — check the URI and file permissions")

    /** [NCKitFileProcessor.processFile] could not create or write the output file. */
    public class CannotCreateOutput :
        NCKitException("Cannot create output file — check the path and available storage")

    /** The source file has no audio track readable by MediaCodec. */
    public class UnsupportedFormat :
        NCKitException("Source has no decodable audio track")

    /** MediaCodec or audio resampling to 48 kHz mono Float32 failed. */
    public class ResampleFailed :
        NCKitException("Audio resampling to 48 kHz mono failed")
}
