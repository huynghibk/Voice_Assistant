package com.voiceassistant.engine.audio

import android.content.Context
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

enum class ExportFormat { WAV, MP3, M4A, FLAC }

enum class ExportQuality {
    STUDIO,   // 320kbps / 24-bit 96kHz when lossless
    COMPACT   // 96–128kbps
}

@Singleton
class AudioExportEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun exportWav(
        pcm16le: ShortArray,
        sampleRateHz: Int,
        fileName: String = "quick_tts_${System.currentTimeMillis()}.wav"
    ): File = withContext(Dispatchers.IO) {
        val dir = exportsDir()
        val out = File(dir, fileName)
        writeWav(out, pcm16le, sampleRateHz)
        out
    }

    suspend fun export(
        inputWav: File,
        format: ExportFormat,
        quality: ExportQuality,
        fileName: String? = null
    ): File = withContext(Dispatchers.IO) {
        val dir = exportsDir()
        val base = fileName ?: "export_${System.currentTimeMillis()}"
        when (format) {
            ExportFormat.WAV -> {
                val out = File(dir, "$base.wav")
                if (quality == ExportQuality.STUDIO) {
                    // Upsample / high-quality PCM via FFmpeg; fallback copy
                    val ok = runFfmpeg(
                        "-i \"${inputWav.absolutePath}\" -acodec pcm_s24le -ar 96000 -y \"${out.absolutePath}\""
                    )
                    if (!ok) inputWav.copyTo(out, overwrite = true)
                } else {
                    inputWav.copyTo(out, overwrite = true)
                }
                out
            }
            ExportFormat.MP3 -> {
                val bitrate = if (quality == ExportQuality.STUDIO) "320k" else "128k"
                val out = File(dir, "$base.mp3")
                val ok = runFfmpeg(
                    "-i \"${inputWav.absolutePath}\" -codec:a libmp3lame -b:a $bitrate -y \"${out.absolutePath}\""
                )
                if (!ok) throw IllegalStateException("FFmpeg MP3 encode failed. Kiểm tra ffmpeg-kit-audio.")
                out
            }
            ExportFormat.M4A -> {
                val bitrate = if (quality == ExportQuality.STUDIO) "320k" else "96k"
                val out = File(dir, "$base.m4a")
                val ok = runFfmpeg(
                    "-i \"${inputWav.absolutePath}\" -c:a aac -b:a $bitrate -y \"${out.absolutePath}\""
                )
                if (!ok) throw IllegalStateException("FFmpeg AAC encode failed.")
                out
            }
            ExportFormat.FLAC -> {
                val out = File(dir, "$base.flac")
                val args = if (quality == ExportQuality.STUDIO) {
                    "-i \"${inputWav.absolutePath}\" -c:a flac -sample_fmt s32 -ar 96000 -y \"${out.absolutePath}\""
                } else {
                    "-i \"${inputWav.absolutePath}\" -c:a flac -y \"${out.absolutePath}\""
                }
                val ok = runFfmpeg(args)
                if (!ok) throw IllegalStateException("FFmpeg FLAC encode failed.")
                out
            }
        }
    }

    suspend fun exportPcm(
        pcm16le: ShortArray,
        sampleRateHz: Int,
        format: ExportFormat,
        quality: ExportQuality
    ): File = withContext(Dispatchers.IO) {
        val tempWav = File(context.cacheDir, "tmp_export_${System.currentTimeMillis()}.wav")
        writeWav(tempWav, pcm16le, sampleRateHz)
        try {
            if (format == ExportFormat.WAV && quality == ExportQuality.COMPACT) {
                val out = File(exportsDir(), "export_${System.currentTimeMillis()}.wav")
                tempWav.copyTo(out, overwrite = true)
                out
            } else {
                export(tempWav, format, quality)
            }
        } finally {
            tempWav.delete()
        }
    }

    private fun exportsDir(): File =
        File(context.getExternalFilesDir(null), "exports").apply { mkdirs() }

    private fun runFfmpeg(command: String): Boolean {
        return runCatching {
            val session = FFmpegKit.execute(command)
            ReturnCode.isSuccess(session.returnCode)
        }.getOrDefault(false)
    }

    private fun writeWav(file: File, pcm: ShortArray, sampleRate: Int) {
        val dataSize = pcm.size * 2
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(0)
            raf.writeBytes("RIFF")
            raf.writeIntLE(36 + dataSize)
            raf.writeBytes("WAVE")
            raf.writeBytes("fmt ")
            raf.writeIntLE(16)
            raf.writeShortLE(1)
            raf.writeShortLE(1)
            raf.writeIntLE(sampleRate)
            raf.writeIntLE(sampleRate * 2)
            raf.writeShortLE(2)
            raf.writeShortLE(16)
            raf.writeBytes("data")
            raf.writeIntLE(dataSize)
            val buffer = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            pcm.forEach { buffer.putShort(it) }
            raf.write(buffer.array())
        }
    }

    private fun RandomAccessFile.writeIntLE(value: Int) {
        write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array())
    }

    private fun RandomAccessFile.writeShortLE(value: Int) {
        write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort()).array())
    }
}
