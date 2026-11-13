package com.voiceassistant.engine.audio

import com.voiceassistant.engine.tts.VoiceConversionEngine

object PlaybackPcm {

    suspend fun play(
        player: PcmStreamPlayer,
        result: VoiceConversionEngine.ConversionResult
    ) {
        when {
            !result.usedConversion && result.pcmFloat != null ->
                player.playChunkFloat(result.pcmFloat, result.sampleRateHz)
            result.pcm16le != null ->
                player.playChunk(result.pcm16le, result.sampleRateHz)
        }
    }

    fun toExportPcm16(result: VoiceConversionEngine.ConversionResult): ShortArray =
        when {
            result.pcm16le != null -> result.pcm16le
            result.pcmFloat != null -> PcmSamples.floatToPcm16(result.pcmFloat)
            else -> shortArrayOf()
        }
}
