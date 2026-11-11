package com.voiceassistant.data.translation

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class OfflineTranslationService @Inject constructor() {

    data class Lang(val code: String, val label: String)

    val supported = listOf(
        Lang(TranslateLanguage.VIETNAMESE, "Tiếng Việt"),
        Lang(TranslateLanguage.ENGLISH, "English"),
        Lang(TranslateLanguage.CHINESE, "中文"),
        Lang(TranslateLanguage.JAPANESE, "日本語"),
        Lang(TranslateLanguage.KOREAN, "한국어"),
        Lang(TranslateLanguage.FRENCH, "Français")
    )

    private val cache = mutableMapOf<String, Translator>()

    suspend fun translate(text: String, source: String, target: String): String {
        if (text.isBlank() || source == target) return text
        val translator = getTranslator(source, target)
        ensureModel(translator)
        return suspendCancellableCoroutine { cont ->
            translator.translate(text)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
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

    private suspend fun ensureModel(translator: Translator) {
        suspendCancellableCoroutine { cont ->
            val conditions = DownloadConditions.Builder().build()
            translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
    }
}
