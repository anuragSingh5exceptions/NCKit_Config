package com.fiveexceptions.nckit

import android.content.Context
import java.io.File

/**
 * Resolves the bundled NCKit model tarball to a filesystem path for [NCKitProcessor].
 *
 * Mirrors [NCKitModelLocator] from the iOS SDK (`modelTarGzURL`).
 *
 * The model (`NCKit_model.tar.gz`, ~7.6 MB) ships inside the AAR as an asset with a
 * `.dfn3` extension so Android's asset packager does not strip gzip compression.
 */
public object NCKitModelLocator {

    private const val MODEL_RESOURCE_NAME = "NCKit_model"
    private const val LEGACY_ASSET_NAME = "DeepFilterNet3_onnx.dfn3"
    private const val EXTRACTED_FILE_NAME = "nckit_model.tar.gz"

    @Volatile private var cachedFile: File? = null

    /**
     * Returns a [File] pointing to `nckit_model.tar.gz` on the local filesystem.
     *
     * On the first call the tarball is copied from the AAR's `assets/` directory into
     * [Context.getCacheDir]; subsequent calls return the cached path.
     *
     * Thread-safe: concurrent first calls are serialised by `synchronized`.
     *
     * @throws NCKitException.MissingModel if the tarball is absent from the AAR.
     */
    @JvmStatic
    public fun modelFile(context: Context): File = modelTarGzFile(context)

    /**
     * Alias matching the iOS API name [modelTarGzURL].
     */
    @JvmStatic
    public fun modelTarGzFile(context: Context): File {
        cachedFile?.let { return it }
        return synchronized(this) {
            cachedFile ?: run {
                val dest = File(context.cacheDir, EXTRACTED_FILE_NAME)
                if (!dest.exists() || dest.length() == 0L) {
                    extractFromAssets(context, dest)
                }
                cachedFile = dest
                dest
            }
        }
    }

    private fun extractFromAssets(context: Context, dest: File) {
        val assetName = resolveAssetName(context)
        try {
            context.assets.open(assetName).use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (_: Exception) {
            throw NCKitException.MissingModel("$MODEL_RESOURCE_NAME.tar.gz")
        }
        if (!dest.exists() || dest.length() == 0L) {
            throw NCKitException.MissingModel("$MODEL_RESOURCE_NAME.tar.gz")
        }
    }

    private fun resolveAssetName(context: Context): String {
        val candidates = listOf(
            "$MODEL_RESOURCE_NAME.dfn3",
            "$MODEL_RESOURCE_NAME.tar.gz",
            LEGACY_ASSET_NAME,
        )
        for (name in candidates) {
            try {
                context.assets.open(name).close()
                return name
            } catch (_: Exception) {
                // try next
            }
        }
        throw NCKitException.MissingModel("$MODEL_RESOURCE_NAME.tar.gz")
    }
}
