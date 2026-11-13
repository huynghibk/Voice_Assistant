package com.voiceassistant.domain.usecase

import com.voiceassistant.domain.model.VoiceModel
import com.voiceassistant.engine.audio.PlaybackPcm
import com.voiceassistant.engine.audio.PcmStreamPlayer
import com.voiceassistant.engine.tts.PcmChunk
import com.voiceassistant.engine.tts.PiperTtsEngine
import com.voiceassistant.engine.tts.VoiceConversionEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.io.File
import javax.inject.Inject

class SynthesizeSpeechUseCase @Inject constructor(
    private val piperTtsEngine: PiperTtsEngine,
    private val voiceConversionEngine: VoiceConversionEngine,
    private val pcmStreamPlayer: PcmStreamPlayer
) {

    data class Progress(
        val chunk: PcmChunk,
        val collectedPcm: ShortArray
    )

    /**
     * Streams synthesis: each sentence is inferred, optionally voice-converted,
     * played immediately, and accumulated for optional WAV export.
     */
    fun execute(text: String, voice: VoiceModel): Flow<Progress> {
        val embedding = voice.embeddingPath?.let { File(it) }
        val collected = ArrayList<Short>()

        return piperTtsEngine.synthesizeStreaming(text, voice)
            .onEach { chunk ->
                val converted = voiceConversionEngine.convert(
                    basePcm = chunk.pcmFloat,
                    sampleRateHz = chunk.sampleRateHz,
                    embeddingFile = embedding
                )
                PlaybackPcm.play(pcmStreamPlayer, converted)
                collected.addAll(PlaybackPcm.toExportPcm16(converted).toList())
            }
            .map { chunk ->
                Progress(chunk = chunk, collectedPcm = collected.toShortArray())
            }
    }

    suspend fun stop() {
        piperTtsEngine.cancel()
        pcmStreamPlayer.stop()
    }
}
