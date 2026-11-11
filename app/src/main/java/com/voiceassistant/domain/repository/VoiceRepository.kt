package com.voiceassistant.domain.repository

import com.voiceassistant.domain.model.VoiceModel
import kotlinx.coroutines.flow.Flow

interface VoiceRepository {
    fun observeVoices(): Flow<List<VoiceModel>>
    suspend fun getVoice(id: String): VoiceModel?
    suspend fun getDefaultVoice(): VoiceModel?
    suspend fun upsert(voice: VoiceModel)
    suspend fun delete(id: String)
    suspend fun setDefault(id: String)
    suspend fun seedPresetsIfEmpty()
}
