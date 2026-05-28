package com.example.nckitconfig

import android.content.Context
import com.fiveexceptions.nckit.NCKitFileProcessor
import com.fiveexceptions.nckit.NCKitModelLocator
import com.fiveexceptions.nckit.NCKitProcessor
import java.io.File

/**
 * Public entry point for noise cancellation in this library.
 *
 * This function accepts any media file that Android can decode via MediaExtractor:
 * - audio files
 * - video files with an audio track
 *
 * The output is always a denoised WAV file.
 */
@Throws(Exception::class)
@JvmOverloads
fun NoiceClear(
    context: Context,
    inputFile: File,
    outputFile: File = defaultOutputFile(context, inputFile),
    attenLimDb: Float = 100f,
    postFilterBeta: Float = 0f,
): File {
    require(inputFile.exists() && inputFile.isFile) {
        "Input file does not exist: ${inputFile.absolutePath}"
    }
    require(inputFile.canRead()) {
        "Input file is not readable: ${inputFile.absolutePath}"
    }

    outputFile.parentFile?.mkdirs()

    val modelFile = NCKitModelLocator.modelTarGzFile(context.applicationContext)
    NCKitProcessor(
        modelFile = modelFile,
        attenLimDb = attenLimDb,
        postFilterBeta = postFilterBeta,
    ).use { processor ->
        NCKitFileProcessor.processFile(
            inputFile = inputFile,
            outputFile = outputFile,
            processor = processor,
        )
    }

    return outputFile
}

private fun defaultOutputFile(context: Context, inputFile: File): File {
    val cleanName = inputFile.nameWithoutExtension.ifBlank { "input" }
    return File(context.cacheDir, "${cleanName}_denoised.wav")
}
