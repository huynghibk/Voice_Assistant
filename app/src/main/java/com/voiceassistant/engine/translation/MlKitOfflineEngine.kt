package com.voiceassistant.engine.translation

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.voiceassistant.domain.engine.TranslationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Offline translation via Google ML Kit. Downloads language models when needed.
 */
@Singleton
class MlKitOfflineEngine @Inject constructor() : TranslationEngine {

    companion object {
        private const val MODEL_DOWNLOAD_TIMEOUT_MS = 120_000L
        private const val TRANSLATE_TIMEOUT_MS = 45_000L
    }

    private val cache = mutableMapOf<String, Translator>()

    override suspend fun translate(
        text: String,
        sourceLang: String,
        targetLang: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (text.isBlank()) return@runCatching text
            val source = LanguageCodes.toMlKit(sourceLang)
            val target = LanguageCodes.toMlKit(targetLang)
            if (source == target) return@runCatching text

            val translator = getTranslator(source, target)
            ensureModelDownloaded(translator)
            val raw = translateWith(translator, text)
            OfflineTranslationPolisher.polish(raw, targetLang)
        }
    }

    private fun getTranslator(source: String, target: String): Translator {
        val key = "$source->$target"
        return cache.getOrPut(key) {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(source)
                .setTargetLanguage(target)
                .build()
            Translation.getClient(options)
        }
    }

    private suspend fun ensureModelDownloaded(translator: Translator) {
        try {
            withTimeout(MODEL_DOWNLOAD_TIMEOUT_MS) {
                suspendCancellableCoroutine<Unit> { cont ->
                    val conditions = DownloadConditions.Builder().build()
                    translator.downloadModelIfNeeded(conditions)
                        .addOnSuccessListener { cont.resume(Unit) }
                        .addOnFailureListener { cont.resumeWithException(it) }
                }
            }
        } catch (_: TimeoutCancellationException) {
            throw IllegalStateException("Tải gói dịch offline quá thời gian chờ. Thử Wi‑Fi hoặc dịch trực tuyến.")
        }
    }

    private suspend fun translateWith(translator: Translator, text: String): String {
        try {
            return withTimeout(TRANSLATE_TIMEOUT_MS) {
                suspendCancellableCoroutine<String> { cont ->
                    translator.translate(text)
                        .addOnSuccessListener { cont.resume(it) }
                        .addOnFailureListener { cont.resumeWithException(it) }
                }
            }
        } catch (_: TimeoutCancellationException) {
            throw IllegalStateException("Dịch offline quá thời gian chờ.")
        }
    }
}
