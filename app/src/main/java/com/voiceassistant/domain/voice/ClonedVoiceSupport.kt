package com.voiceassistant.domain.voice

import com.voiceassistant.domain.model.VoiceModel
import com.voiceassistant.domain.resource.ResourcePackManager
import java.io.File

object ClonedVoiceSupport {
    private val langTags = setOf("vi", "en", "zh")

    fun isUserClone(voice: VoiceModel): Boolean =
        !voice.isPreset && !voice.embeddingPath.isNullOrBlank()

    fun hasEmbeddingOnDisk(voice: VoiceModel): Boolean =
        voice.embeddingPath?.let { File(it).isFile } == true

    fun languageTags(voice: VoiceModel): List<String> =
        voice.tags.filter { tag -> langTags.any { tag.equals(it, ignoreCase = true) } }

    fun basePiperReady(
        voice: VoiceModel,
        catalog: List<VoiceModel>,
        resourcePackManager: ResourcePackManager
    ): Boolean {
        if (!isUserClone(voice)) {
            return resourcePackManager.isPiperPathReady(voice.modelAssetPath)
        }
        if (!hasEmbeddingOnDisk(voice)) return false
        val presets = catalog.filter { it.isPreset && !it.modelAssetPath.isNullOrBlank() }
        val langs = languageTags(voice)
        val pool = if (langs.isNotEmpty()) {
            presets.filter { preset ->
                preset.tags.any { tag -> langs.any { lang -> tag.equals(lang, ignoreCase = true) } }
            }
        } else {
            presets
        }
        return pool.any { resourcePackManager.isPiperPathReady(it.modelAssetPath) }
    }
}
