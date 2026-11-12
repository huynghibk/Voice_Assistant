package com.voiceassistant.engine.translation

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.voiceassistant.domain.translation.LanguageIdentifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ML Kit on-device language identification.
 * Maps BCP-47 tags to app language ids; `"und"` / blank → [LanguageIdentifier.FALLBACK].
 */
@Singleton
class MlKitLanguageIdentifier @Inject constructor() : LanguageIdentifier {

    private val client by lazy {
        LanguageIdentification.getClient(
            LanguageIdentificationOptions.Builder()
                .setConfidenceThreshold(0.5f)
                .build()
        )
    }

    override suspend fun identify(text: String): String = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return@withContext LanguageIdentifier.FALLBACK
        val raw = runCatching {
            Tasks.await(client.identifyLanguage(trimmed))
        }.getOrNull().orEmpty()
        normalizeTag(raw)
    }

    companion object {
        /** Maps ML Kit / BCP-47 tags; `"und"` and blank → `"en"`. */
        internal fun normalizeTag(raw: String): String = LanguageCodes.fromLanguageIdTag(raw)
    }
}
