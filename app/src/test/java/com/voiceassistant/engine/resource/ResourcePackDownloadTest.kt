package com.voiceassistant.engine.resource

import com.voiceassistant.domain.resource.ResourcePackManager
import com.voiceassistant.presentation.settings.model.ModelManagerViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ResourcePackDownloadTest {

    private val testDispatcher = StandardTestDispatcher()
    private val resourcePackManager = mockk<ResourcePackManager>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { resourcePackManager.downloadMlKitLanguage(any(), any()) } just runs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun modelManagerDownloadsOverCellularWithoutWifiRestriction() = runTest(testDispatcher) {
        val viewModel = ModelManagerViewModel(resourcePackManager)
        advanceUntilIdle()

        viewModel.downloadLanguage("vi")
        advanceUntilIdle()

        coVerify(exactly = 1) {
            resourcePackManager.downloadMlKitLanguage("vi", requireWifi = false)
        }
    }

    @Test
    fun modelManagerPairDownloadAllowsCellularData() = runTest(testDispatcher) {
        val viewModel = ModelManagerViewModel(resourcePackManager)
        advanceUntilIdle()

        viewModel.downloadPair("en", "vi")
        advanceUntilIdle()

        coVerify(exactly = 1) {
            resourcePackManager.downloadMlKitLanguage("en", requireWifi = false)
        }
        coVerify(exactly = 1) {
            resourcePackManager.downloadMlKitLanguage("vi", requireWifi = false)
        }
    }
}
