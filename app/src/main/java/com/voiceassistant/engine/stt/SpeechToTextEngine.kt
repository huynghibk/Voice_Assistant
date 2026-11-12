package com.voiceassistant.engine.stt

import android.content.Context
import android.content.Intent
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
    data class Error(val message: String) : SpeechEvent()
    data object End : SpeechEvent()
}

/**
 * On-device / system speech recognition → text for the TTS input box.
 */
@Singleton
class SpeechToTextEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * Emits recognition events until cancelled or session ends.
     * Must be collected; cancellation stops listening.
     */
    fun listen(locale: Locale = Locale.getDefault()): Flow<SpeechEvent> = callbackFlow {
        if (!isAvailable()) {
            trySend(SpeechEvent.Error("Máy này không hỗ trợ nhận dạng giọng nói."))
            close()
            return@callbackFlow
        }

        var recognizer: SpeechRecognizer? = null
        mainHandler.post {
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
                    trySend(SpeechEvent.Error(errorMessage(error)))
                    close()
                }

                override fun onResults(results: Bundle?) {
                    val texts = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        .orEmpty()
                    val best = texts.firstOrNull().orEmpty().trim()
                    if (best.isNotEmpty()) trySend(SpeechEvent.Final(best))
                    trySend(SpeechEvent.End)
                    close()
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

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }
            sr.startListening(intent)
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
