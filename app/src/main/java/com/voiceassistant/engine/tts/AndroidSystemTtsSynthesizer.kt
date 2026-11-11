package com.voiceassistant.engine.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.RandomAccessFile
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Real speech fallback via Android [TextToSpeech] when Piper ONNX packs
 * are not installed. Synthesizes each sentence to a temp WAV, then returns PCM16.
 */
@Singleton
class AndroidSystemTtsSynthesizer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val mutex = Mutex()
    private val ready = CompletableDeferred<Boolean>()
    private val initStarted = AtomicBoolean(false)
    private var tts: TextToSpeech? = null

    suspend fun synthesize(sentence: String, localeHint: Locale): Pair<ShortArray, Int> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                ensureReadyLocked()
                val engine = tts ?: error("TextToSpeech không khả dụng trên máy này.")
                val locale = pickLocale(engine, localeHint)
                engine.language = locale
                engine.setSpeechRate(1.0f)
                engine.setPitch(1.0f)

                val outFile = File(context.cacheDir, "sys_tts_${UUID.randomUUID()}.wav")
                try {
                    synthesizeToFileLocked(engine, sentence, outFile)
                    readWavPcm16(outFile)
                } finally {
                    outFile.delete()
                }
            }
        }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    private suspend fun ensureReadyLocked() {
        if (initStarted.compareAndSet(false, true)) {
            tts = TextToSpeech(context) { status ->
                ready.complete(status == TextToSpeech.SUCCESS)
            }
        }
        val ok = ready.await()
        if (!ok) error("Không khởi tạo được giọng đọc hệ thống.")
    }

    private fun pickLocale(engine: TextToSpeech, preferred: Locale): Locale {
        val candidates = listOf(
            preferred,
            Locale("vi", "VN"),
            Locale.getDefault(),
            Locale.US
        )
        for (locale in candidates) {
            val result = engine.isLanguageAvailable(locale)
            if (result >= TextToSpeech.LANG_AVAILABLE) return locale
        }
        return Locale.getDefault()
    }

    private suspend fun synthesizeToFileLocked(
        engine: TextToSpeech,
        text: String,
        outFile: File
    ) = suspendCancellableCoroutine { cont ->
        val utteranceId = "utt_${UUID.randomUUID()}"
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                if (cont.isActive) cont.resume(Unit)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (cont.isActive) {
                    cont.resumeWithException(IllegalStateException("Tổng hợp giọng hệ thống thất bại."))
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                if (cont.isActive) {
                    cont.resumeWithException(
                        IllegalStateException("Tổng hợp giọng hệ thống thất bại (code $errorCode).")
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

    companion object {
        fun localeForVoiceId(voiceId: String?, voiceName: String?): Locale {
            val key = "${voiceId.orEmpty()} ${voiceName.orEmpty()}".lowercase(Locale.ROOT)
            return when {
                "vi" in key || "vietnam" in key || "việt" in key -> Locale("vi", "VN")
                "en" in key || "amy" in key || "ryan" in key -> Locale.US
                else -> Locale.getDefault()
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
                            val audioFormat = java.lang.Short.reverseBytes(raf.readShort()).toInt() and 0xffff
                            channels = java.lang.Short.reverseBytes(raf.readShort()).toInt() and 0xffff
                            sampleRate = Integer.reverseBytes(raf.readInt())
                            raf.skipBytes(6) // byteRate + blockAlign
                            bitsPerSample = java.lang.Short.reverseBytes(raf.readShort()).toInt() and 0xffff
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
                    // Downmix to mono if needed (take first channel)
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
