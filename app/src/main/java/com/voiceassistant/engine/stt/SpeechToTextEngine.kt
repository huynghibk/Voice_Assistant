package com.voiceassistant.engine.stt

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

sealed class SpeechEvent {
    data object Ready : SpeechEvent()
    data object BeginningOfSpeech : SpeechEvent()
    data class Partial(val text: String) : SpeechEvent()
    data class Final(val text: String) : SpeechEvent()
    data class Error(val message: String, val code: Int = -1) : SpeechEvent()
    /** OEM / blocked SpeechRecognizer — UI should launch ACTION_RECOGNIZE_SPEECH. */
    data class NeedsIntentFallback(val message: String) : SpeechEvent()
    data object End : SpeechEvent()
}

// On-device / system speech recognition → text. All SpeechRecognizer work on Main.
@Singleton
class SpeechToTextEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun isAvailable(): Boolean =
        runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)

    fun hasGoogleSpeech(): Boolean = findGoogleRecognizer() != null

    fun findGoogleRecognizer(): ComponentName? {
        val probe = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        val handlers = context.packageManager.queryIntentActivities(
            probe,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        val google = handlers.firstOrNull {
            it.activityInfo.packageName == "com.google.android.googlequicksearchbox"
        } ?: return null
        return ComponentName(google.activityInfo.packageName, google.activityInfo.name)
    }

    fun createRecognizeIntent(locale: Locale = Locale.getDefault()): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            val tag = locale.toLanguageTag()
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, tag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                if (locale.language.equals("vi", ignoreCase = true)) "Nói vào micro…" else "Speak now"
            )
            // Longer window so a short phrase is not dropped as "Didn't catch that".
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1_500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2_500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2_000L)
            findGoogleRecognizer()?.let { component = it }
        }

    fun parseRecognizeResult(data: Intent?): String? {
        val texts = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).orEmpty()
        return texts.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * Emits recognition events until cancelled or session ends.
     * Must be collected on a coroutine; SpeechRecognizer callbacks run on Main.
     */
    fun listen(locale: Locale = Locale.getDefault()): Flow<SpeechEvent> = callbackFlow {
        if (!isAvailable()) {
            trySend(
                SpeechEvent.NeedsIntentFallback(
                    "Máy này không hỗ trợ SpeechRecognizer — mở nhận dạng hệ thống."
                )
            )
            close()
            return@callbackFlow
        }

        var recognizer: SpeechRecognizer? = null
        var closed = false

        fun safeClose() {
            if (!closed) {
                closed = true
                close()
            }
        }

        mainHandler.post {
            try {
                val sr = SpeechRecognizer.createSpeechRecognizer(context)
                recognizer = sr
                sr.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        trySend(SpeechEvent.Ready)
                    }

                    override fun onBeginningOfSpeech() {
                        trySend(SpeechEvent.BeginningOfSpeech)
                    }

                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit

                    override fun onEndOfSpeech() {
                        trySend(SpeechEvent.End)
                    }

                    override fun onError(error: Int) {
                        when (error) {
                            SpeechRecognizer.ERROR_CLIENT,
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                                trySend(
                                    SpeechEvent.NeedsIntentFallback(
                                        errorMessage(error) + " Đang mở nhận dạng hệ thống…"
                                    )
                                )
                            }
                            else -> {
                                trySend(SpeechEvent.Error(errorMessage(error), error))
                            }
                        }
                        safeClose()
                    }

                    override fun onResults(results: Bundle?) {
                        val texts = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            .orEmpty()
                        val best = texts.firstOrNull().orEmpty().trim()
                        if (best.isNotEmpty()) trySend(SpeechEvent.Final(best))
                        trySend(SpeechEvent.End)
                        safeClose()
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val texts = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            .orEmpty()
                        val partial = texts.firstOrNull().orEmpty().trim()
                        if (partial.isNotEmpty()) trySend(SpeechEvent.Partial(partial))
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })

                sr.startListening(createRecognizeIntent(locale))
            } catch (t: Throwable) {
                trySend(
                    SpeechEvent.NeedsIntentFallback(
                        t.message ?: "Không khởi tạo được SpeechRecognizer."
                    )
                )
                safeClose()
            }
        }

        awaitClose {
            mainHandler.post {
                runCatching {
                    recognizer?.stopListening()
                    recognizer?.cancel()
                    recognizer?.destroy()
                }
                recognizer = null
            }
        }
    }

    private fun errorMessage(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Lỗi micro / âm thanh."
        SpeechRecognizer.ERROR_CLIENT -> "Lỗi nhận dạng (client)."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Thiếu quyền micro."
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Lỗi mạng khi nhận dạng."
        SpeechRecognizer.ERROR_NO_MATCH -> "Không nhận được lời nói. Thử lại."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Nhận dạng đang bận. Thử lại."
        SpeechRecognizer.ERROR_SERVER -> "Lỗi máy chủ nhận dạng."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Không nghe thấy giọng nói."
        else -> "Nhận dạng thất bại (mã $code)."
    }
}
