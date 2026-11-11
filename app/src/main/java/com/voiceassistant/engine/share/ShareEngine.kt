package com.voiceassistant.engine.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shares audio via Intent.ACTION_SEND using FileProvider content:// URIs.
 * Never exposes raw file:// paths (FileUriExposedException on API 24+).
 */
@Singleton
class ShareEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun createShareAudioIntent(
        file: File,
        mimeType: String = guessMime(file),
        chooserTitle: String = "Chia sẻ âm thanh"
    ): Intent {
        require(file.exists()) { "File không tồn tại: ${file.absolutePath}" }
        val uri = toContentUri(file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            putExtra(Intent.EXTRA_TEXT, "Chia sẻ từ Voice Assistant — ${file.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newUri(
                context.contentResolver,
                file.name,
                uri
            )
        }
        return Intent.createChooser(send, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun toContentUri(file: File): Uri =
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

    fun shareAudio(file: File, mimeType: String = guessMime(file)) {
        context.startActivity(createShareAudioIntent(file, mimeType))
    }

    private fun guessMime(file: File): String = when (file.extension.lowercase()) {
        "mp3" -> "audio/mpeg"
        "m4a", "aac" -> "audio/mp4"
        "flac" -> "audio/flac"
        "wav" -> "audio/wav"
        "ogg" -> "audio/ogg"
        else -> "audio/*"
    }
}
