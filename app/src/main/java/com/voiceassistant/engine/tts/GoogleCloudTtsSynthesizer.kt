package com.voiceassistant.engine.tts

import android.util.Base64
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
class GoogleCloudTtsSynthesizer @Inject constructor(
    private val apiKeyStore: ApiKeyStore
) {
    suspend fun synthesize(
        text: String,
        localeHint: Locale,
        speechRate: Float
    ): Pair<FloatArray, Int> = withContext(Dispatchers.IO) {
        val apiKey = apiKeyStore.getGoogleCloudTtsApiKey()
        if (apiKey.isBlank()) error("MISSING_GOOGLE_CLOUD_TTS_KEY")
        val clean = text.trim()
        if (clean.isEmpty()) return@withContext FloatArray(0) to 24_000

        val (langCode, voiceName) = pickVoice(localeHint)
        val body = JSONObject().apply {
            put("input", JSONObject().put("text", clean))
            put(
                "voice",
                JSONObject()
                    .put("languageCode", langCode)
                    .put("name", voiceName)
            )
            put(
                "audioConfig",
                JSONObject()
                    .put("audioEncoding", "LINEAR16")
                    .put("sampleRateHertz", 24_000)
                    .put("speakingRate", speechRate.coerceIn(0.5f, 2.5f).toDouble())
            )
        }

        val connection = (URL(SYNTH_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("X-Goog-Api-Key", apiKey)
        }
        try {
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(body.toString())
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val payload = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            if (code !in 200..299) {
                error("Google Cloud TTS HTTP $code: ${payload.take(220)}")
            }
            val audioContent = JSONObject(payload).optString("audioContent")
            if (audioContent.isBlank()) error("Google Cloud TTS trả về audio trống.")
            val bytes = Base64.decode(audioContent, Base64.DEFAULT)
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
            floats to 24_000
        } finally {
            connection.disconnect()
        }
    }

    private fun pickVoice(locale: Locale): Pair<String, String> =
        when (locale.language.lowercase(Locale.ROOT)) {
            "vi" -> "vi-VN" to "vi-VN-Wavenet-B"
            "zh" -> "cmn-CN" to "cmn-CN-Wavenet-B"
            "ja" -> "ja-JP" to "ja-JP-Neural2-B"
            "ko" -> "ko-KR" to "ko-KR-Neural2-A"
            "fr" -> "fr-FR" to "fr-FR-Neural2-A"
            else -> "en-US" to "en-US-Neural2-C"
        }

    companion object {
        private const val SYNTH_URL = "https://texttospeech.googleapis.com/v1/text:synthesize"
    }
}
