package com.voiceassistant.domain.tts

enum class TtsBackend(val storageKey: String) {
    /** Google / Samsung on-device TTS — free, closest to Google Translate when Google TTS is installed. */
    SYSTEM_GOOGLE("system"),
    /** Sherpa / Piper ONNX packs — fully offline. */
    OFFLINE_ONNX("offline"),
    /** Google Cloud Text-to-Speech REST — requires user API key (paid / free tier). */
    CLOUD_GOOGLE("cloud_google"),
    /** ElevenLabs — requires user API key. */
    CLOUD_ELEVENLABS("cloud_elevenlabs");

    companion object {
        fun fromStorageKey(key: String?): TtsBackend =
            entries.firstOrNull { it.storageKey == key } ?: SYSTEM_GOOGLE
    }
}
