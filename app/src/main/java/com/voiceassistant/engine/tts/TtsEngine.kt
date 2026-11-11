package com.voiceassistant.engine.tts

import com.voiceassistant.domain.model.VoiceModel
import kotlinx.coroutines.flow.Flow

data class PcmChunk(
    val sentenceIndex: Int,
    val sentenceCount: Int,
    val sentence: String,
    val pcm16le: ShortArray,
    val sampleRateHz: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PcmChunk) return false
        return sentenceIndex == other.sentenceIndex &&
            sentenceCount == other.sentenceCount &&
            sentence == other.sentence &&
            sampleRateHz == other.sampleRateHz &&
            pcm16le.contentEquals(other.pcm16le)
    }

    override fun hashCode(): Int {
        var result = sentenceIndex
        result = 31 * result + sentenceCount
        result = 31 * result + sentence.hashCode()
        result = 31 * result + sampleRateHz
        result = 31 * result + pcm16le.contentHashCode()
        return result
    }
}

interface TtsEngine {
    /**
     * Split text, synthesize each sentence on a background queue,
     * and emit PCM chunks as soon as each sentence is ready (for streaming playback).
     */
    fun synthesizeStreaming(text: String, voice: VoiceModel): Flow<PcmChunk>

    fun cancel()
}
