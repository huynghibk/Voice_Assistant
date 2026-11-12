package com.voiceassistant.data.ingress

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cross-entry bridge so PROCESS_TEXT / Quick sheet can open the main app on TTS or Translate.
 */
@Singleton
class PendingTextIngress @Inject constructor() {

    private val _tts = MutableStateFlow<String?>(null)
    private val _translate = MutableStateFlow<String?>(null)

    val pendingTts: StateFlow<String?> = _tts.asStateFlow()
    val pendingTranslate: StateFlow<String?> = _translate.asStateFlow()

    fun offerTts(text: String) {
        val t = text.trim()
        if (t.isNotEmpty()) _tts.value = t
    }

    fun offerTranslate(text: String) {
        val t = text.trim()
        if (t.isNotEmpty()) _translate.value = t
    }

    fun consumeTts(): String? = _tts.value?.also { _tts.value = null }

    fun consumeTranslate(): String? = _translate.value?.also { _translate.value = null }
}
