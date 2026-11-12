package com.voiceassistant.engine.playback

import com.voiceassistant.domain.model.VoiceModel
import com.voiceassistant.domain.tts.TextSplitter
import com.voiceassistant.engine.audio.PcmStreamPlayer
import com.voiceassistant.engine.tts.PcmChunk
import com.voiceassistant.engine.tts.PiperTtsEngine
import com.voiceassistant.engine.tts.VoiceConversionEngine
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackControllerTest {

    private val voice = VoiceModel(id = "en_us", name = "English", tags = listOf("en"), isDefault = true)

    private fun TestScope.buildController(
        holdSynthesis: CompletableDeferred<Unit> = CompletableDeferred()
    ): Pair<LocalPlaybackController, CompletableDeferred<Unit>> {
        val piper = mockk<PiperTtsEngine>(relaxed = true)
        val vc = mockk<VoiceConversionEngine>(relaxed = true)
        val pcm = mockk<PcmStreamPlayer>(relaxed = true)

        every { pcm.setPlaybackSpeed(any()) } just runs
        every { pcm.interruptCurrentChunk() } just runs
        every { pcm.setPaused(any()) } just runs
        coEvery { pcm.playChunk(any(), any()) } just runs
        coEvery { pcm.stop() } just runs
        every { piper.cancel() } just runs
        every { piper.clearCancel() } just runs
        every { vc.convert(any(), any(), any()) } answers {
            VoiceConversionEngine.ConversionResult(
                pcm16le = firstArg(),
                sampleRateHz = secondArg(),
                usedConversion = false
            )
        }
        // Park every sentence until the test releases — avoids racing past indices.
        coEvery {
            piper.synthesizeSentence(any(), any(), any(), any(), any())
        } coAnswers {
            holdSynthesis.await()
            PcmChunk(
                sentenceIndex = arg(2),
                sentenceCount = arg(3),
                sentence = firstArg(),
                pcm16le = shortArrayOf(1, 2, 3),
                sampleRateHz = PiperTtsEngine.SAMPLE_RATE
            )
        }

        val controller = LocalPlaybackController(
            textSplitter = TextSplitter(),
            piperTtsEngine = piper,
            voiceConversionEngine = vc,
            pcmStreamPlayer = pcm
        ).also { it.testScope = this }
        return controller to holdSynthesis
    }

    @Test
    fun emptyTextDoesNotCrashAndStaysIdle() = runTest {
        val (controller, _) = buildController()

        controller.start("   ", voice, title = "test")
        advanceUntilIdle()

        val state = controller.state.value
        assertFalse(state.isPlaying)
        assertTrue(
            "Expected empty-text status, was: ${state.statusMessage}",
            state.statusMessage.contains("Không có câu")
        )
        assertTrue(state.sentences.isEmpty())
        assertEquals(-1, state.currentSentenceIndex)
    }

    @Test
    fun startTracksCurrentSentenceIndex() = runTest {
        val (controller, hold) = buildController()

        controller.start("One. Two. Three.", voice, title = "test")
        advanceUntilIdle()

        assertTrue(controller.state.value.isPlaying)
        assertEquals(3, controller.state.value.sentenceCount)
        assertEquals(0, controller.state.value.currentSentenceIndex)

        hold.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun nextAndPreviousSentenceUpdateIndex() = runTest {
        val (controller, hold) = buildController()

        controller.start("Alpha. Beta. Gamma.", voice)
        advanceUntilIdle()
        assertEquals(0, controller.state.value.currentSentenceIndex)

        controller.skipToNextSentence()
        advanceUntilIdle()
        assertEquals(1, controller.state.value.currentSentenceIndex)

        controller.skipToNextSentence()
        advanceUntilIdle()
        assertEquals(2, controller.state.value.currentSentenceIndex)

        // At last sentence — stays clamped
        controller.skipToNextSentence()
        advanceUntilIdle()
        assertEquals(2, controller.state.value.currentSentenceIndex)

        controller.skipToPreviousSentence()
        advanceUntilIdle()
        assertEquals(1, controller.state.value.currentSentenceIndex)

        hold.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun setSpeedUpdatesStateAndClamps() = runTest {
        val (controller, _) = buildController()

        controller.setSpeed(1.5f)
        assertEquals(1.5f, controller.state.value.speed, 0.001f)

        controller.setSpeed(9f)
        assertEquals(2.5f, controller.state.value.speed, 0.001f)

        controller.setSpeed(0.1f)
        assertEquals(0.5f, controller.state.value.speed, 0.001f)
    }

    @Test
    fun nextPreviousOnEmptyListDoNotCrash() = runTest {
        val (controller, _) = buildController()

        controller.skipToNextSentence()
        controller.skipToPreviousSentence()
        advanceUntilIdle()

        assertFalse(controller.state.value.isPlaying)
        assertEquals(-1, controller.state.value.currentSentenceIndex)
    }

    @Test
    fun setSpeedAppliesToPlayerEvenWhenIdle() = runTest {
        val piper = mockk<PiperTtsEngine>(relaxed = true)
        val vc = mockk<VoiceConversionEngine>(relaxed = true)
        val pcm = mockk<PcmStreamPlayer>(relaxed = true)
        every { pcm.setPlaybackSpeed(any()) } just runs

        val controller = LocalPlaybackController(TextSplitter(), piper, vc, pcm)
        controller.testScope = this
        controller.setSpeed(1.2f)

        verify { pcm.setPlaybackSpeed(1.2f) }
        assertEquals(1.2f, controller.state.value.speed, 0.001f)
    }
}
