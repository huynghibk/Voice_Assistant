package com.voiceassistant.data.repository

import com.voiceassistant.data.local.db.VoiceModelDao
import com.voiceassistant.data.mapper.toDomain
import com.voiceassistant.data.mapper.toEntity
import com.voiceassistant.domain.model.VoiceModel
import com.voiceassistant.domain.repository.VoiceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceRepositoryImpl @Inject constructor(
    private val dao: VoiceModelDao
) : VoiceRepository {

    override fun observeVoices(): Flow<List<VoiceModel>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getVoice(id: String): VoiceModel? =
        dao.getById(id)?.toDomain()

    override suspend fun getDefaultVoice(): VoiceModel? =
        dao.getDefault()?.toDomain()

    override suspend fun upsert(voice: VoiceModel) {
        dao.upsert(voice.toEntity())
    }

    override suspend fun delete(id: String) {
        dao.delete(id)
    }

    override suspend fun setDefault(id: String) {
        dao.clearDefault()
        dao.setDefault(id)
    }

    override suspend fun listVoices(): List<VoiceModel> =
        dao.listAll().map { it.toDomain() }

    override suspend fun seedPresetsIfEmpty() {
        val existing = dao.listAll().associateBy { it.id }
        val defaultId = existing.values.firstOrNull { it.isDefault }?.id
        val firstInstall = existing.isEmpty()
        // Upsert built-in voices so labels stay current; never reset user's default voice.
        val presets = listOf(
            VoiceModel(
                id = "preset_vi_demo",
                name = "Tiếng Việt · MMS",
                tags = listOf("system", "vi", "female"),
                modelAssetPath = "models/sherpa/vits-mms-vie/model.onnx",
                isDefault = false,
                isPreset = true
            ),
            VoiceModel(
                id = "preset_vi_piper_25h",
                name = "Tiếng Việt · Piper 25h",
                tags = listOf("system", "vi", "female"),
                modelAssetPath = "models/piper/vi_VN-25hours_single-low.onnx",
                isDefault = firstInstall,
                isPreset = true
            ),
            VoiceModel(
                id = "preset_en_amy",
                name = "English · Amy",
                tags = listOf("system", "en", "female"),
                modelAssetPath = "models/piper/en_US-amy-low.onnx",
                isDefault = false,
                isPreset = true
            ),
            VoiceModel(
                id = "preset_en_ryan",
                name = "English · Ryan",
                tags = listOf("system", "en", "male"),
                modelAssetPath = "models/piper/en_US-ryan-low.onnx",
                isDefault = false,
                isPreset = true
            ),
            VoiceModel(
                id = "preset_zh_melo",
                name = "中文 · Melo (zh+en)",
                tags = listOf("system", "zh", "zh-en", "bilingual"),
                modelAssetPath = "models/sherpa/vits-melo-tts-zh_en/model.onnx",
                isDefault = false,
                isPreset = true
            )
        )
        val resolvedDefaultId = when {
            firstInstall -> "preset_vi_piper_25h"
            defaultId != null -> defaultId
            else -> "preset_vi_piper_25h"
        }
        dao.upsertAll(
            presets.map { preset ->
                preset.copy(isDefault = preset.id == resolvedDefaultId).toEntity()
            }
        )
    }
}
