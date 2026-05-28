package com.example.nckitconfig.sample

import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.example.nckitconfig.NoiceClear
import java.io.File
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {

    private lateinit var pickButton: Button
    private lateinit var processButton: Button
    private lateinit var playOriginalButton: Button
    private lateinit var playDenoisedButton: Button
    private lateinit var stopButton: Button
    private lateinit var selectedText: TextView
    private lateinit var statusText: TextView
    private lateinit var compareText: TextView

    private var selectedInputFile: File? = null
    private var denoisedOutputFile: File? = null
    private var mediaPlayer: MediaPlayer? = null

    private val pickMedia = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        selectedInputFile = copyUriToCache(uri)
        denoisedOutputFile = null
        val name = selectedInputFile?.name ?: "Unknown"
        selectedText.text = getString(R.string.selected_file, name)
        processButton.isEnabled = selectedInputFile != null
        playOriginalButton.isEnabled = selectedInputFile != null
        playDenoisedButton.isEnabled = false
        compareText.text = getString(R.string.compare_waiting)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        pickButton = findViewById(R.id.pickButton)
        processButton = findViewById(R.id.processButton)
        playOriginalButton = findViewById(R.id.playOriginalButton)
        playDenoisedButton = findViewById(R.id.playDenoisedButton)
        stopButton = findViewById(R.id.stopButton)
        selectedText = findViewById(R.id.selectedText)
        statusText = findViewById(R.id.statusText)
        compareText = findViewById(R.id.compareText)

        pickButton.setOnClickListener {
            pickMedia.launch(arrayOf("audio/*", "video/*"))
        }

        processButton.setOnClickListener {
            val input = selectedInputFile ?: return@setOnClickListener
            processButton.isEnabled = false
            statusText.text = getString(R.string.status_processing)

            thread {
                try {
                    val output = File(cacheDir, "${input.nameWithoutExtension}_sample_output.wav")
                    val result = NoiceClear(
                        context = this,
                        inputFile = input,
                        outputFile = output,
                    )
                    runOnUiThread {
                        denoisedOutputFile = result
                        statusText.text = getString(R.string.status_done, result.absolutePath)
                        processButton.isEnabled = true
                        playDenoisedButton.isEnabled = true
                        updateComparison(input, result)
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        statusText.text = getString(
                            R.string.status_error,
                            e.message ?: "Unknown error",
                        )
                        processButton.isEnabled = true
                        playDenoisedButton.isEnabled = false
                    }
                }
            }
        }

        playOriginalButton.setOnClickListener {
            selectedInputFile?.let(::playFile)
        }

        playDenoisedButton.setOnClickListener {
            denoisedOutputFile?.let(::playFile)
        }

        stopButton.setOnClickListener {
            stopPlayback()
        }
    }

    override fun onDestroy() {
        stopPlayback()
        super.onDestroy()
    }

    private fun copyUriToCache(uri: Uri): File {
        val inputName = queryDisplayName(uri) ?: "selected_input"
        val extension = inputName.substringAfterLast('.', "")
        val safeExt = if (extension.isBlank()) "bin" else extension
        val destination = File(cacheDir, "input_${System.currentTimeMillis()}.$safeExt")

        contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Could not open selected file" }
            destination.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return destination
    }

    private fun queryDisplayName(uri: Uri): String? {
        val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                return cursor.getString(nameIndex)
            }
        }
        return null
    }

    private fun playFile(file: File) {
        stopPlayback()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setOnCompletionListener {
                stopPlayback()
            }
            prepare()
            start()
        }
    }

    private fun stopPlayback() {
        mediaPlayer?.run {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
    }

    private fun updateComparison(original: File, denoised: File) {
        compareText.text = getString(
            R.string.compare_result,
            original.name,
            formatSize(original.length()),
            readDuration(original),
            denoised.name,
            formatSize(denoised.length()),
            readDuration(denoised),
        )
    }

    private fun readDuration(file: File): String {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: return "N/A"
            val totalSec = durationMs / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            "%02d:%02d".format(min, sec)
        } catch (_: Exception) {
            "N/A"
        } finally {
            retriever.release()
        }
    }

    private fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.1f KB", kb)
        val mb = kb / 1024.0
        return String.format("%.2f MB", mb)
    }
}
