package com.voiceassistant.engine.playback

import android.content.Context
import com.voiceassistant.R
import com.voiceassistant.domain.model.VoiceModel
import com.voiceassistant.domain.playback.PlaybackController
import com.voiceassistant.domain.playback.PlaybackState
import com.voiceassistant.domain.playback.SentenceProgress
import com.voiceassistant.domain.repository.VoiceRepository
import com.voiceassistant.domain.tts.MultiLanguageTtsPlanner
import com.voiceassistant.domain.tts.TtsPlannedSentence
import com.voiceassistant.engine.audio.PlaybackPcm
import com.voiceassistant.engine.audio.PcmStreamPlayer
import com.voiceassistant.engine.tts.PiperTtsEngine
import com.voiceassistant.engine.tts.VoiceConversionEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the TTS play loop (survives Activity/ViewModel). MediaSessionService observes [state].
 */
@Singleton
class LocalPlaybackController @Inject constructor(
    private val multiLanguageTtsPlanner: MultiLanguageTtsPlanner,
    private val voiceRepository: VoiceRepository,
    private val piperTtsEngine: PiperTtsEngine,
    private val voiceConversionEngine: VoiceConversionEngine,
    private val pcmStreamPlayer: PcmStreamPlayer,
    @ApplicationContext private val context: Context
) : PlaybackController {

    private val defaultScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Override in unit tests so play-loop launches share the TestScope scheduler. */
    @Volatile
    internal var testScope: CoroutineScope? = null

    private val scope: CoroutineScope
        get() = testScope ?: defaultScope
    private val startMutex = Mutex()

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _sentenceProgress = MutableStateFlow(SentenceProgress())
    override val sentenceProgress: StateFlow<SentenceProgress> = _sentenceProgress.asStateFlow()

    private var playJob: Job? = null
    private var activeVoice: VoiceModel? = null
    private var plannedSentences: List<TtsPlannedSentence> = emptyList()
    private val resumeFromIndex = AtomicInteger(0)

    private val collected = ArrayList<Short>()
    @Volatile private var sampleRateHz: Int = PiperTtsEngine.SAMPLE_RATE

    init {
        scope.launch {
            state.collect { s ->
                _sentenceProgress.value = SentenceProgress(
                    sentenceIndex = s.currentSentenceIndex,
                    sentenceCount = s.sentenceCount,
                    sentence = s.currentSentence,
                    isPlaying = s.isPlaying && !s.isPaused
                )
            }
        }
    }

    override fun start(text: String, voice: VoiceModel, title: String) {
        scope.launch {
            startMutex.withLock {
                stopInternal()
                val catalog = voiceRepository.listVoices()
                val planned = multiLanguageTtsPlanner.plan(text, voice, catalog)
                plannedSentences = planned
                val sentences = planned.map { it.text }
                if (sentences.isEmpty()) {
                    _state.update {
                        it.copy(statusMessage = "Không có câu để đọc.", isPlaying = false)
                    }
                    return@withLock
                }
                activeVoice = voice
                collected.clear()
                resumeFromIndex.set(0)
                pcmStreamPlayer.setPlaybackSpeed(1.0f)
                _state.value = PlaybackState(
                    sentences = sentences,
                    currentSentenceIndex = 0,
                    isPlaying = true,
                    isPaused = false,
                    speed = _state.value.speed,
                    title = title.ifBlank { "VoiceAssistant" },
                    statusMessage = "Đang đọc…"
                )
                launchPlayLoop(fromIndex = 0)
            }
        }
    }

    override fun pause() {
        if (!_state.value.isPlaying) return
        pcmStreamPlayer.setPaused(true)
        _state.update { it.copy(isPaused = true, statusMessage = "Tạm dừng") }
    }

    override fun resume() {
        if (!_state.value.isPlaying) return
        pcmStreamPlayer.setPaused(false)
        _state.update { it.copy(isPaused = false, statusMessage = "Đang đọc…") }
        // If loop died while paused, restart from current index
        if (playJob?.isActive != true) {
            launchPlayLoop(fromIndex = _state.value.currentSentenceIndex.coerceAtLeast(0))
        }
    }

    override fun stop() {
        scope.launch { startMutex.withLock { stopInternal() } }
    }

    override fun skipToNextSentence() {
        val state = _state.value
        if (!state.isPlaying || state.sentences.isEmpty()) return
        val next = (state.currentSentenceIndex + 1).coerceAtMost(state.sentences.lastIndex)
        jumpTo(next)
    }

    override fun skipToPreviousSentence() {
        val state = _state.value
        if (!state.isPlaying || state.sentences.isEmpty()) return
        val prev = (state.currentSentenceIndex - 1).coerceAtLeast(0)
        jumpTo(prev)
    }

    override fun setSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.5f)
        _state.update { it.copy(speed = clamped) }
    }

    override fun getCollectedPcm(): ShortArray = collected.toShortArray()

    override fun getSampleRate(): Int = sampleRateHz

    private fun jumpTo(index: Int) {
        resumeFromIndex.set(index)
        pcmStreamPlayer.interruptCurrentChunk()
        piperTtsEngine.cancel()
        playJob?.cancel()
        _state.update {
            it.copy(
                currentSentenceIndex = index,
                isPaused = false,
                statusMessage = "Chuyển câu…"
            )
        }
        pcmStreamPlayer.setPaused(false)
        launchPlayLoop(fromIndex = index)
    }

    private fun launchPlayLoop(fromIndex: Int) {
        val voice = activeVoice ?: return
        val sentences = _state.value.sentences
        if (sentences.isEmpty()) return

        playJob = scope.launch {
            piperTtsEngine.clearCancel()
            var index = fromIndex.coerceIn(0, sentences.lastIndex)
            try {
                while (index < sentences.size) {
                    if (resumeFromIndex.get() != index && resumeFromIndex.get() > index) {
                        // skip requested ahead
                        index = resumeFromIndex.get()
                        continue
                    }
                    _state.update {
                        it.copy(
                            currentSentenceIndex = index,
                            isPlaying = true,
                            isPaused = false,
                            statusMessage = "Đang phát… ${sentences[index].take(60)}"
                        )
                    }
                    val plan = plannedSentences.getOrNull(index)
                    val chunk = try {
                        piperTtsEngine.synthesizeSentence(
                            sentence = sentences[index],
                            voice = plan?.voice ?: voice,
                            sentenceIndex = index,
                            sentenceCount = sentences.size,
                            speechRate = _state.value.speed,
                            pauseAfterMs = plan?.pauseAfterMs ?: 250
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val errorMsg = when {
                            e.message?.contains("SYSTEM_TTS_VIETNAMESE_UNSUPPORTED") == true ->
                                context.getString(R.string.error_missing_google_tts_piper)
                            e.message?.startsWith("TTS_FAILED:") == true ->
                                context.getString(R.string.error_tts_failed, e.message?.removePrefix("TTS_FAILED: ") ?: "Unknown error")
                            e.message?.contains("Piper ONNX") == true ->
                                context.getString(R.string.error_piper_onnx_failed, e.message ?: "Unknown error")
                            else ->
                                e.message ?: "Tổng hợp thất bại."
                        }
                        _state.update {
                            it.copy(
                                isPlaying = false,
                                isPaused = false,
                                statusMessage = errorMsg
                            )
                        }
                        return@launch
                    }
                    // Empty PCM means System TTS already spoke via speak() — skip AudioTrack.
                    if (chunk.pcmFloat.isNotEmpty()) {
                        val embedding = voice.embeddingPath?.let { File(it) }
                        val converted = voiceConversionEngine.convert(
                            basePcm = chunk.pcmFloat,
                            sampleRateHz = chunk.sampleRateHz,
                            embeddingFile = embedding
                        )
                        sampleRateHz = converted.sampleRateHz
                        PlaybackPcm.play(pcmStreamPlayer, converted)
                        collected.addAll(PlaybackPcm.toExportPcm16(converted).toList())
                    }
                    if (resumeFromIndex.get() != index) {
                        // interrupted by skip — loop will restart or continue at new index
                        index = resumeFromIndex.get()
                        continue
                    }
                    index++
                    resumeFromIndex.set(index)
                }
                _state.update {
                    it.copy(
                        isPlaying = false,
                        isPaused = false,
                        currentSentenceIndex = if (sentences.isEmpty()) -1 else sentences.lastIndex,
                        statusMessage = "Hoàn tất."
                    )
                }
            } catch (e: CancellationException) {
                // skip / stop — state updated by caller
                throw e
            } catch (e: Exception) {
                runCatching { pcmStreamPlayer.stop() }
                _state.update {
                    it.copy(
                        isPlaying = false,
                        isPaused = false,
                        statusMessage = e.message ?: "Đã dừng."
                    )
                }
            }
        }
    }

    private suspend fun stopInternal() {
        resumeFromIndex.set(Int.MAX_VALUE)
        playJob?.cancel()
        playJob = null
        piperTtsEngine.cancel()
        pcmStreamPlayer.interruptCurrentChunk()
        pcmStreamPlayer.setPaused(false)
        pcmStreamPlayer.stop()
        activeVoice = null
        plannedSentences = emptyList()
        _state.update {
            it.copy(
                isPlaying = false,
                isPaused = false,
                currentSentenceIndex = -1,
                sentences = emptyList(),
                statusMessage = "Đã dừng."
            )
        }
    }
}
