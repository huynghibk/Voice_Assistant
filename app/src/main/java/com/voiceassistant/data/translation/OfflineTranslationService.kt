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

    /**
     * @param id stable UI / preference id (zh-CN vs zh-TW are distinct)
     * @param code ML Kit TranslateLanguage code used for the actual model
     */
    data class Lang(val id: String, val code: String, val label: String)

    /** Languages the UI can show and that offline translation can target. */
    val supported = listOf(
        Lang("vi", TranslateLanguage.VIETNAMESE, "Tiếng Việt"),
        Lang("en", TranslateLanguage.ENGLISH, "English"),
        Lang("zh-CN", TranslateLanguage.CHINESE, "简体中文"),
        Lang("zh-TW", TranslateLanguage.CHINESE, "繁體中文"),
        Lang("ja", TranslateLanguage.JAPANESE, "日本語"),
        Lang("ko", TranslateLanguage.KOREAN, "한국어"),
        Lang("fr", TranslateLanguage.FRENCH, "Français")
    )

    private val cache = mutableMapOf<String, Translator>()

    suspend fun translate(text: String, source: String, target: String): String {
        if (text.isBlank()) return text
        val sourceCode = resolveMlKitCode(source)
        val targetCode = resolveMlKitCode(target)
        if (sourceCode == targetCode) return text
        val translator = getTranslator(sourceCode, targetCode)
        ensureModel(translator)
        return suspendCancellableCoroutine { cont ->
            translator.translate(text)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
    }

    private fun resolveMlKitCode(idOrCode: String): String =
        supported.firstOrNull { it.id == idOrCode || it.code == idOrCode }?.code
            ?: idOrCode

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
