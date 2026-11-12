package com.voiceassistant.engine.tts

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.RandomAccessFile
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Real speech via Android [TextToSpeech] when Piper ONNX packs are not installed.
 * Synthesizes each sentence to a temp WAV, then returns PCM16.
 */
@Singleton
class AndroidSystemTtsSynthesizer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val mutex = Mutex()
    private var tts: TextToSpeech? = null
    private var ready = false

    suspend fun synthesize(
        sentence: String,
        localeHint: Locale,
        speechRate: Float = 1.0f
    ): Pair<ShortArray, Int> =
        mutex.withLock {
            val engine = ensureEngineLocked()
            val locale = pickLocale(engine, localeHint)
            runCatching { engine.language = locale }
            // Keep pitch 1.0; rate changes tempo without “chipmunk” pitch bend from AudioTrack alone.
            engine.setSpeechRate(speechRate.coerceIn(0.5f, 2.5f))
            engine.setPitch(1.0f)

            val outFile = File(context.cacheDir, "sys_tts_${UUID.randomUUID()}.wav")
            try {
                synthesizeToFileLocked(engine, sentence, outFile)
                if (!outFile.exists() || outFile.length() < 44L) {
                    error("TTS hệ thống không tạo được file âm thanh.")
                }
                readWavPcm16(outFile)
            } finally {
                outFile.delete()
            }
        }

    /** Pre-create the engine on Main so the first Play is faster / more reliable. */
    suspend fun warmUp() {
        mutex.withLock { ensureEngineLocked() }
    }

    fun shutdown() {
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
        ready = false
    }

    private suspend fun ensureEngineLocked(): TextToSpeech {
        tts?.takeIf { ready }?.let { return it }

        // Previous attempt failed — reset and retry
        runCatching { tts?.shutdown() }
        tts = null
        ready = false

        val enginePackages = buildEngineCandidates()
        var lastError: String? = null

        for (enginePackage in enginePackages) {
            val result = CompletableDeferred<Int>()
            val created = withContext(Dispatchers.Main) {
                runCatching {
                    if (enginePackage == null) {
                        TextToSpeech(context) { status -> result.complete(status) }
                    } else {
                        TextToSpeech(context, { status -> result.complete(status) }, enginePackage)
                    }
                }.getOrElse {
                    result.complete(TextToSpeech.ERROR)
                    null
                }
            } ?: continue

            tts = created
            val status = withTimeoutOrNull(12_000) { result.await() } ?: TextToSpeech.ERROR
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                return created
            }

            lastError = "engine=${enginePackage ?: "default"} status=$status"
            runCatching { created.shutdown() }
            tts = null
        }

        error(
            "Không khởi tạo được giọng đọc hệ thống" +
                (lastError?.let { " ($it)" } ?: "") +
                ". Cài/ cập nhật Google Text-to-speech trên máy hoặc emulator."
        )
    }

    private fun buildEngineCandidates(): List<String?> {
        val installed = linkedSetOf<String>()
        // Prefer known engines when present
        listOf(
            "com.google.android.tts",
            "com.samsung.SMT",
            "com.ivona.tts",
            "com.pico.voice"
        ).forEach { pkg ->
            if (isPackageInstalled(pkg)) installed += pkg
        }
        // Also discover engines advertised by the system
        runCatching {
            val intent = Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
            val resolveFlags = if (Build.VERSION.SDK_INT >= 24) {
                PackageManager.MATCH_ALL
            } else {
                0
            }
            context.packageManager
                .queryIntentServices(intent, resolveFlags)
                .mapNotNull { it.serviceInfo?.packageName }
                .forEach { installed += it }
        }
        // null = platform default engine (last resort)
        return installed.toList() + null
    }

    private fun isPackageInstalled(packageName: String): Boolean =
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        }.getOrDefault(false)

    private fun pickLocale(engine: TextToSpeech, preferred: Locale): Locale {
        val candidates = listOf(
            preferred,
            Locale.US,
            Locale.UK,
            Locale("vi", "VN"),
            Locale.getDefault()
        )
        for (locale in candidates) {
            val result = runCatching { engine.isLanguageAvailable(locale) }.getOrDefault(-2)
            if (result >= TextToSpeech.LANG_AVAILABLE) return locale
        }
        return Locale.US
    }

    private suspend fun synthesizeToFileLocked(
        engine: TextToSpeech,
        text: String,
        outFile: File
    ) = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val utteranceId = "utt_${UUID.randomUUID()}"
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    if (cont.isActive) cont.resume(Unit)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (cont.isActive) {
                        cont.resumeWithException(
                            IllegalStateException("Tổng hợp giọng hệ thống thất bại.")
                        )
                    }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    if (cont.isActive) {
                        cont.resumeWithException(
                            IllegalStateException(
                                "Tổng hợp giọng hệ thống thất bại (code $errorCode)."
                            )
                        )
                    }
                }
            })

            cont.invokeOnCancellation {
                runCatching { engine.stop() }
            }

            val result = engine.synthesizeToFile(text, Bundle(), outFile, utteranceId)
            if (result != TextToSpeech.SUCCESS && cont.isActive) {
                cont.resumeWithException(IllegalStateException("Không thể tổng hợp văn bản."))
            }
        }
    }

    companion object {
        fun localeForVoiceId(voiceId: String?, voiceName: String?): Locale {
            val key = "${voiceId.orEmpty()} ${voiceName.orEmpty()}".lowercase(Locale.ROOT)
            return when {
                "vi" in key || "vietnam" in key || "việt" in key -> Locale("vi", "VN")
                "en" in key || "amy" in key || "ryan" in key -> Locale.US
                else -> Locale.US
            }
        }

        fun readWavPcm16(file: File): Pair<ShortArray, Int> {
            RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(12)
                raf.readFully(header)
                require(header.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF") {
                    "File TTS không phải WAV hợp lệ."
                }
                var sampleRate = 22_050
                var bitsPerSample = 16
                var channels = 1
                var dataSize = -1
                var dataOffset = -1L

                while (raf.filePointer < raf.length()) {
                    val chunkIdBytes = ByteArray(4)
                    if (raf.read(chunkIdBytes) < 4) break
                    val chunkSize = Integer.reverseBytes(raf.readInt()).toLong() and 0xffffffffL
                    val chunkId = chunkIdBytes.toString(Charsets.US_ASCII)
                    val chunkStart = raf.filePointer
                    when (chunkId) {
                        "fmt " -> {
                            val audioFormat =
                                java.lang.Short.reverseBytes(raf.readShort()).toInt() and 0xffff
                            channels =
                                java.lang.Short.reverseBytes(raf.readShort()).toInt() and 0xffff
                            sampleRate = Integer.reverseBytes(raf.readInt())
                            raf.skipBytes(6)
                            bitsPerSample =
                                java.lang.Short.reverseBytes(raf.readShort()).toInt() and 0xffff
                            require(audioFormat == 1 && bitsPerSample == 16) {
                                "Chỉ hỗ trợ PCM 16-bit từ TTS hệ thống."
                            }
                        }
                        "data" -> {
                            dataOffset = raf.filePointer
                            dataSize = chunkSize.toInt()
                        }
                    }
                    raf.seek(chunkStart + chunkSize + (chunkSize % 2))
                    if (dataOffset >= 0 && dataSize >= 0) break
                }

                require(dataOffset >= 0 && dataSize > 0) { "WAV TTS thiếu dữ liệu audio." }
                raf.seek(dataOffset)
                val bytes = ByteArray(dataSize)
                raf.readFully(bytes)
                val frameCount = dataSize / (2 * channels)
                val pcm = ShortArray(frameCount)
                var bi = 0
                for (i in 0 until frameCount) {
                    val lo = bytes[bi].toInt() and 0xff
                    val hi = bytes[bi + 1].toInt()
                    pcm[i] = ((hi shl 8) or lo).toShort()
                    bi += 2 * channels
                }
                return pcm to sampleRate
            }
        }
    }
}
