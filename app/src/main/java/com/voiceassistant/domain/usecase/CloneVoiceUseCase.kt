package com.voiceassistant.domain.usecase

import android.content.Context
import com.voiceassistant.domain.model.VoiceModel
import com.voiceassistant.domain.repository.VoiceRepository
import com.voiceassistant.engine.audio.StudioRecorder
import com.voiceassistant.engine.tts.SpeakerEmbeddingExtractor
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

class CloneVoiceUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val studioRecorder: StudioRecorder,
    private val embeddingExtractor: SpeakerEmbeddingExtractor,
    private val voiceRepository: VoiceRepository
) {
    suspend fun recordAndClone(
        displayName: String,
        tags: List<String> = listOf("cloned", "user"),
        maxDurationMs: Long = 5_000L
    ): VoiceModel {
        val sample = studioRecorder.recordToWav(maxDurationMs = maxDurationMs)
        return cloneFromSample(sample, displayName, tags)
    }

    suspend fun cloneFromSample(
        sampleWav: File,
        displayName: String,
        tags: List<String> = listOf("cloned", "user")
    ): VoiceModel {
        val id = "clone_${UUID.randomUUID()}"
        val embDir = File(context.filesDir, "embeddings").apply { mkdirs() }
        val embFile = File(embDir, "$id.bin")
        embeddingExtractor.extractFromWav(sampleWav, embFile)

        val voice = VoiceModel(
            id = id,
            name = displayName.ifBlank { "Giọng đã clone" },
            tags = tags,
            embeddingPath = embFile.absolutePath,
            sampleAudioPath = sampleWav.absolutePath,
            modelAssetPath = null,
            isDefault = false,
            isPreset = false
        )
        voiceRepository.upsert(voice)
        return voice
    }

    fun stopRecording() = studioRecorder.stop()
}
