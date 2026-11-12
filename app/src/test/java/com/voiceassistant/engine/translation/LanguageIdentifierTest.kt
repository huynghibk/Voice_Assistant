package com.voiceassistant.engine.translation

import com.voiceassistant.domain.translation.LanguageIdentifier
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * MlKitLanguageIdentifier maps unknown / `"und"` tags to English fallback.
 */
class LanguageIdentifierTest {

    private val identifier = MlKitLanguageIdentifier()

    @Test
    fun emptyTextReturnsEnglishFallback() = runTest {
        assertEquals(LanguageIdentifier.FALLBACK, identifier.identify(""))
        assertEquals("en", identifier.identify("   "))
    }

    @Test
    fun undTagMapsToEnglish() {
        assertEquals("en", MlKitLanguageIdentifier.normalizeTag("und"))
        assertEquals("en", MlKitLanguageIdentifier.normalizeTag("UND"))
        assertEquals("en", MlKitLanguageIdentifier.normalizeTag(""))
        assertEquals("en", LanguageCodes.fromLanguageIdTag("und"))
    }

    @Test
    fun knownTagsMapToAppLanguageIds() {
        assertEquals("vi", MlKitLanguageIdentifier.normalizeTag("vi"))
        assertEquals("ja", MlKitLanguageIdentifier.normalizeTag("ja-JP"))
        assertEquals("zh-CN", MlKitLanguageIdentifier.normalizeTag("zh"))
        assertEquals("zh-TW", MlKitLanguageIdentifier.normalizeTag("zh-Hant"))
    }
}
