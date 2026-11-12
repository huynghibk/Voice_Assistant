package com.voiceassistant.presentation.translate

import android.Manifest
import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.voiceassistant.data.translation.OfflineTranslationService
import com.voiceassistant.domain.model.VoiceModel
import com.voiceassistant.domain.playback.PlaybackController
import com.voiceassistant.domain.playback.PlaybackState
import com.voiceassistant.domain.repository.TranslationRepository
import com.voiceassistant.domain.repository.VoiceRepository
import com.voiceassistant.engine.stt.SpeechEvent
import com.voiceassistant.engine.stt.SpeechToTextEngine
import com.voiceassistant.presentation.theme.VoiceAssistantTheme
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import io.mockk.verify
import com.voiceassistant.engine.playback.TtsPlaybackStarter
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Conversation UI + turn-taking: tapping Mic interrupts TTS playback.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConversationFlowUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var playbackController: PlaybackController
    private lateinit var speechToTextEngine: SpeechToTextEngine
    private lateinit var viewModel: ConversationViewModel

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)

        mockkObject(TtsPlaybackStarter)
        every { TtsPlaybackStarter.start(any()) } just runs
        every { TtsPlaybackStarter.stop(any()) } just runs

        playbackController = mockk(relaxed = true)
        every { playbackController.state } returns MutableStateFlow(
            PlaybackState(isPlaying = true, isPaused = false, sentences = listOf("Hello"))
        )
        every { playbackController.stop() } just runs

        speechToTextEngine = mockk(relaxed = true)
        every { speechToTextEngine.isAvailable() } returns true
        every { speechToTextEngine.listen(any()) } returns flow {
            emit(SpeechEvent.Ready)
            awaitCancellation()
        }

        val translationRepository = mockk<TranslationRepository>(relaxed = true)
        coEvery { translationRepository.translate(any(), any(), any()) } returns Result.success("ok")

        val voiceRepository = mockk<VoiceRepository>(relaxed = true)
        coEvery { voiceRepository.seedPresetsIfEmpty() } just runs
        every { voiceRepository.observeVoices() } returns flowOf(
            listOf(VoiceModel(id = "en", name = "English", tags = listOf("en"), isDefault = true))
        )

        viewModel = ConversationViewModel(
            appContext = app,
            translationRepository = translationRepository,
            offlineTranslationService = OfflineTranslationService(),
            speechToTextEngine = speechToTextEngine,
            playbackController = playbackController,
            voiceRepository = voiceRepository
        )
    }

    @After
    fun tearDown() {
        unmockkObject(TtsPlaybackStarter)
    }

    @Test
    fun conversationScreenShowsSplitPlaceholdersAndMic() {
        composeRule.setContent {
            VoiceAssistantTheme(darkTheme = true) {
                ConversationScreen(onClose = {}, viewModel = viewModel)
            }
        }

        composeRule.onNodeWithText("Tap the mic and speak…").assertIsDisplayed()
        composeRule.onNodeWithText("Translation appears here…").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("Speak").assertCountEquals(2)
        composeRule.onNodeWithContentDescription("Close").assertIsDisplayed()
    }

    @Test
    fun tappingMicStopsTtsForTurnTaking() {
        composeRule.setContent {
            VoiceAssistantTheme(darkTheme = true) {
                ConversationScreen(onClose = {}, viewModel = viewModel)
            }
        }

        composeRule.onAllNodesWithContentDescription("Speak")[0].performClick()
        composeRule.waitForIdle()

        verify(atLeast = 1) { playbackController.stop() }
        verify(atLeast = 1) { TtsPlaybackStarter.stop(any()) }
    }
}
