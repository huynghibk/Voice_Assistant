package com.voiceassistant.engine.tts

import com.voiceassistant.data.prefs.ApiKeyStore
import com.voiceassistant.engine.audio.PcmSamples
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ElevenLabsTtsSynthesizer @Inject constructor(
    private val apiKeyStore: ApiKeyStore
) {
    suspend fun synthesize(
        text: String,
        localeHint: Locale,
        speechRate: Float
    ): Pair<FloatArray, Int> = withContext(Dispatchers.IO) {
        val apiKey = apiKeyStore.getElevenLabsApiKey()
        if (apiKey.isBlank()) error("MISSING_ELEVENLABS_KEY")
        val clean = text.trim()
        if (clean.isEmpty()) return@withContext FloatArray(0) to SAMPLE_RATE

        val voiceId = pickVoiceId(localeHint)
        val url =
            "https://api.elevenlabs.io/v1/text-to-speech/$voiceId?output_format=pcm_${SAMPLE_RATE}"
        val body = JSONObject().apply {
            put("text", clean)
            put("model_id", "eleven_multilingual_v2")
            put(
                "voice_settings",
                JSONObject()
                    .put("stability", 0.45)
                    .put("similarity_boost", 0.75)
                    .put("style", 0.2)
                    .put("use_speaker_boost", true)
                    .put("speed", speechRate.coerceIn(0.7f, 1.3f).toDouble())
            )
        }

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("xi-api-key", apiKey)
            setRequestProperty("Accept", "audio/pcm")
        }
        try {
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(body.toString())
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                val err = connection.errorStream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
                error("ElevenLabs HTTP $code: ${err.take(220)}")
            }
            val bytes = connection.inputStream?.use { it.readBytes() } ?: byteArrayOf()
            if (bytes.size < 4) error("ElevenLabs trả về audio trống.")
            val frameCount = bytes.size / 2
            val pcm = ShortArray(frameCount)
            var bi = 0
            for (i in 0 until frameCount) {
                val lo = bytes[bi].toInt() and 0xff
                val hi = bytes[bi + 1].toInt()
                pcm[i] = ((hi shl 8) or lo).toShort()
                bi += 2
            }
            val floats = PcmSamples.normalizeForPlayback(PcmSamples.pcm16ToFloat(pcm))
            floats to SAMPLE_RATE
        } finally {
            connection.disconnect()
        }
    }

    private fun pickVoiceId(locale: Locale): String =
        when (locale.language.lowercase(Locale.ROOT)) {
            "vi", "zh", "ja", "ko" -> MULTILINGUAL_VOICE_ID
            else -> MULTILINGUAL_VOICE_ID
        }

    companion object {
        private const val SAMPLE_RATE = 24_000
        private const val MULTILINGUAL_VOICE_ID = "21m00Tcm4TlvDq8ikWAM"
    }
}
