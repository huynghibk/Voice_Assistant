package com.voiceassistant.engine.tts

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
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

// Real speech via Android TextToSpeech when Piper ONNX packs are not installed.
// Synthesizes each sentence to a temp WAV, then returns PCM16.
@Singleton
class AndroidSystemTtsSynthesizer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val mutex = Mutex()
    private var tts: TextToSpeech? = null
    private var ready = false
    private var enginePackage: String? = null

    suspend fun synthesize(
        sentence: String,
        localeHint: Locale,
        speechRate: Float = 1.0f
    ): Pair<ShortArray, Int> =
        mutex.withLock {
            val isVietnamese = localeHint.language.equals("vi", ignoreCase = true)
            if (isVietnamese) {
                ensurePreferredEngineLocked(GOOGLE_TTS_PACKAGE)
            }
            val engine = ensureEngineLocked()
            configureEngineLocked(engine, localeHint, speechRate)

            // Prefer file synth for AudioTrack path; timeout → direct speak() (OPPO-safe).
            val outFile = File(context.cacheDir, "sys_tts_${UUID.randomUUID()}.wav")
            val pcm = try {
                val ok = withTimeoutOrNull(SYNTH_TIMEOUT_MS) {
                    synthesizeToFileLocked(engine, sentence, outFile)
                    true
                }
                if (ok == true && outFile.exists() && outFile.length() >= 44L) {
                    readWavPcm16(outFile)
                } else {
                    null
                }
            } finally {
                outFile.delete()
            }

            if (pcm != null) return@withLock pcm

            // Direct speak() — plays through USAGE_MEDIA immediately.
            speakLocked(engine, sentence)
            // Empty PCM: LocalPlaybackController skips AudioTrack (already audible).
            ShortArray(0) to 22_050
        }

    /**
     * Speak via system TTS engine audio path (USAGE_MEDIA). Used when file synth hangs
     * or as an explicit fallback. Returns after utterance ends or timeout.
     */
    suspend fun speak(
        sentence: String,
        localeHint: Locale,
        speechRate: Float = 1.0f
    ): Boolean =
        mutex.withLock {
            val isVietnamese = localeHint.language.equals("vi", ignoreCase = true)
            if (isVietnamese) {
                ensurePreferredEngineLocked(GOOGLE_TTS_PACKAGE)
            }
            val engine = ensureEngineLocked()
            configureEngineLocked(engine, localeHint, speechRate)
            speakLocked(engine, sentence)
            true
        }

    private fun configureEngineLocked(
        engine: TextToSpeech,
        localeHint: Locale,
        speechRate: Float
    ) {
        val isVietnamese = localeHint.language.equals("vi", ignoreCase = true)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setLegacyStreamType(AudioManager.STREAM_MUSIC)
            .build()
        runCatching { engine.setAudioAttributes(audioAttributes) }

        val locale = if (isVietnamese) {
            Locale.forLanguageTag("vi-VN")
        } else {
            pickLocale(engine, localeHint)
        }
        val setLangResult = runCatching { engine.setLanguage(locale) }
            .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        if (isVietnamese &&
            (setLangResult == TextToSpeech.LANG_MISSING_DATA ||
                setLangResult == TextToSpeech.LANG_NOT_SUPPORTED)
        ) {
            openInstallTtsData()
            error(
                "Thiếu gói dữ liệu giọng đọc tiếng Việt. " +
                    "Vui lòng cài đặt gói giọng Google Tiếng Việt trong màn hình vừa mở."
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            runCatching {
                val matchingVoices = engine.voices
                    ?.filter { it.locale.language.equals(locale.language, ignoreCase = true) }
                    .orEmpty()
                val naturalVoice = matchingVoices.firstOrNull {
                    it.name.contains("network", ignoreCase = true)
                } ?: matchingVoices.firstOrNull {
                    it.locale.country.equals(locale.country, ignoreCase = true)
                } ?: matchingVoices.firstOrNull()
                if (naturalVoice != null) engine.voice = naturalVoice
            }
        }
        engine.setSpeechRate(speechRate.coerceIn(0.5f, 2.5f))
        engine.setPitch(1.0f)
    }

    private suspend fun speakLocked(engine: TextToSpeech, sentence: String) =
        withContext(Dispatchers.Main) {
            withTimeoutOrNull(SPEAK_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    val utteranceId = "speak_${UUID.randomUUID()}"
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) = Unit
                        override fun onDone(utteranceId: String?) {
                            if (cont.isActive) cont.resume(Unit)
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            if (cont.isActive) cont.resume(Unit)
                        }
                        override fun onError(utteranceId: String?, errorCode: Int) {
                            if (cont.isActive) cont.resume(Unit)
                        }
                    })
                    cont.invokeOnCancellation { runCatching { engine.stop() } }
                    val params = Bundle()
                    val result = engine.speak(sentence, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
                    if (result != TextToSpeech.SUCCESS && cont.isActive) {
                        cont.resumeWithException(
                            IllegalStateException("Không thể phát bằng giọng hệ thống.")
                        )
                    }
                }
            } ?: run {
                runCatching { engine.stop() }
                Unit
            }
        }

    // Pre-create the engine on Main so the first Play is faster / more reliable.
    suspend fun warmUp() {
        mutex.withLock { ensureEngineLocked() }
    }

    fun stopSpeaking() {
        runCatching { tts?.stop() }
    }

    fun shutdown() {
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
        ready = false
        enginePackage = null
    }

    private fun openInstallTtsData() {
        val installIntent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (isPackageInstalled(GOOGLE_TTS_PACKAGE)) {
                setPackage(GOOGLE_TTS_PACKAGE)
            }
        }
        runCatching { context.startActivity(installIntent) }
    }

    private suspend fun ensurePreferredEngineLocked(preferredPackage: String) {
        if (ready && tts != null && enginePackage == preferredPackage) return
        if (!isPackageInstalled(preferredPackage)) return
        runCatching { tts?.shutdown() }
        tts = null
        ready = false
        enginePackage = null
        val (engine, status) = tryInitEngine(preferredPackage)
        if (status == TextToSpeech.SUCCESS && engine != null) {
            tts = engine
            ready = true
            enginePackage = preferredPackage
        } else {
            runCatching { engine?.shutdown() }
        }
    }

    private suspend fun ensureEngineLocked(): TextToSpeech {
        tts?.takeIf { ready }?.let { return it }

        runCatching { tts?.shutdown() }
        tts = null
        ready = false
        enginePackage = null

        // Prefer Google TTS first so Vietnamese reads naturally (not letter-spelling).
        val preferred = linkedSetOf<String>()
        if (isPackageInstalled(GOOGLE_TTS_PACKAGE)) preferred += GOOGLE_TTS_PACKAGE
        preferred.addAll(buildEngineCandidates().filterNotNull())

        var lastError = "no engine"

        // Try default only after preferred packages if Google is missing.
        if (preferred.isEmpty()) {
            val (defaultTts, defaultStatus) = tryInitEngine(null)
            if (defaultStatus == TextToSpeech.SUCCESS && defaultTts != null) {
                tts = defaultTts
                ready = true
                enginePackage = null
                return defaultTts
            }
            lastError = "engine=default status=$defaultStatus"
            runCatching { defaultTts?.shutdown() }
        }

        for (pkg in preferred) {
            val (fallbackTts, fallbackStatus) = tryInitEngine(pkg)
            if (fallbackStatus == TextToSpeech.SUCCESS && fallbackTts != null) {
                tts = fallbackTts
                ready = true
                enginePackage = pkg
                return fallbackTts
            }
            lastError = "engine=$pkg status=$fallbackStatus"
            runCatching { fallbackTts?.shutdown() }
        }

        // Last resort: platform default
        val (defaultTts, defaultStatus) = tryInitEngine(null)
        if (defaultStatus == TextToSpeech.SUCCESS && defaultTts != null) {
            tts = defaultTts
            ready = true
            return defaultTts
        }

        error(
            "Không khởi tạo được giọng đọc hệ thống ($lastError). " +
                "Cài/ cập nhật Google Text-to-speech trên máy."
        )
    }

    private suspend fun tryInitEngine(enginePackage: String?): Pair<TextToSpeech?, Int> {
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
        } ?: return Pair(null, TextToSpeech.ERROR)

        val status = withTimeoutOrNull(10_000) { result.await() } ?: TextToSpeech.ERROR
        return Pair(created, status)
    }

    private fun buildEngineCandidates(): List<String?> {
        val installed = linkedSetOf<String>()
        listOf(
            GOOGLE_TTS_PACKAGE,
            "com.samsung.SMT",
            "com.ivona.tts",
            "com.pico.voice"
        ).forEach { pkg ->
            if (isPackageInstalled(pkg)) installed += pkg
        }
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
            Locale.forLanguageTag("vi-VN"),
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
        private const val GOOGLE_TTS_PACKAGE = "com.google.android.tts"
        const val SYNTH_TIMEOUT_MS = 4_000L
        private const val SPEAK_TIMEOUT_MS = 60_000L

        fun localeForVoiceId(voiceId: String?, voiceName: String?): Locale {
            val key = "${voiceId.orEmpty()} ${voiceName.orEmpty()}".lowercase(Locale.ROOT)
            return when {
                "vi" in key || "vietnam" in key || "việt" in key -> Locale.forLanguageTag("vi-VN")
                "zh" in key || "chinese" in key || "melo" in key -> Locale.forLanguageTag("zh-CN")
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
