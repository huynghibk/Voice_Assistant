package com.voiceassistant.engine.translation

import com.voiceassistant.data.network.NetworkMonitor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Smart router: online → Gemini; offline / Gemini failure → ML Kit fallback.
 */
class TranslationRoutingTest {

    private lateinit var gemini: GeminiOnlineEngine
    private lateinit var mlKit: MlKitOfflineEngine
    private lateinit var network: NetworkMonitor
    private lateinit var repository: TranslationRepositoryImpl

    @Before
    fun setUp() {
        gemini = mockk()
        mlKit = mockk()
        network = mockk()
        every { gemini.hasApiKey() } returns true
        repository = TranslationRepositoryImpl(gemini, mlKit, network)
    }

    @Test
    fun prefersGeminiWhenOnline() = runTest {
        every { network.isOnline() } returns true
        coEvery { gemini.translate("hello", "en", "vi") } returns Result.success("xin chào")

        val result = repository.translate("hello", "en", "vi")

        assertEquals("xin chào", result.getOrThrow())
        coVerify(exactly = 1) { gemini.translate("hello", "en", "vi") }
        coVerify(exactly = 0) { mlKit.translate(any(), any(), any()) }
    }

    @Test
    fun fallsBackToMlKitWhenOffline() = runTest {
        every { network.isOnline() } returns false
        coEvery { mlKit.translate("hello", "en", "vi") } returns Result.success("xin chào")

        val result = repository.translate("hello", "en", "vi")

        assertEquals("xin chào", result.getOrThrow())
        verify(exactly = 1) { network.isOnline() }
        coVerify(exactly = 0) { gemini.translate(any(), any(), any()) }
        coVerify(exactly = 1) { mlKit.translate("hello", "en", "vi") }
    }

    @Test
    fun fallsBackToMlKitWhenGeminiThrowsOrFails() = runTest {
        every { network.isOnline() } returns true
        coEvery { gemini.translate("hi", "en", "ja") } returns
            Result.failure(IllegalStateException("quota exceeded"))
        coEvery { mlKit.translate("hi", "en", "ja") } returns Result.success("こんにちは")

        val result = repository.translate("hi", "en", "ja")

        assertEquals("こんにちは", result.getOrThrow())
        coVerify(exactly = 1) { gemini.translate("hi", "en", "ja") }
        coVerify(exactly = 1) { mlKit.translate("hi", "en", "ja") }
    }

    @Test
    fun supportsOnlineAiRequiresNetworkAndKey() {
        every { network.isOnline() } returns true
        every { gemini.hasApiKey() } returns true
        assertTrue(repository.supportsOnlineAi())

        every { gemini.hasApiKey() } returns false
        assertTrue(!repository.supportsOnlineAi())

        every { gemini.hasApiKey() } returns true
        every { network.isOnline() } returns false
        assertTrue(!repository.supportsOnlineAi())
    }

    @Test
    fun blankTextShortCircuitsWithoutCallingEngines() = runTest {
        every { network.isOnline() } returns true

        val result = repository.translate("   ", "en", "vi")

        assertTrue(result.isSuccess)
        assertEquals("   ", result.getOrThrow())
        coVerify(exactly = 0) { gemini.translate(any(), any(), any()) }
        coVerify(exactly = 0) { mlKit.translate(any(), any(), any()) }
    }

    @Test
    fun onlineOnlySkipsMlKitAndRequiresKey() = runTest {
        every { network.isOnline() } returns true
        every { gemini.hasApiKey() } returns false

        val result = repository.translate("hi", "en", "vi", onlineOnly = true)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { gemini.translate(any(), any(), any()) }
        coVerify(exactly = 0) { mlKit.translate(any(), any(), any()) }
    }

    @Test
    fun onlineOnlyUsesGeminiOnly() = runTest {
        every { network.isOnline() } returns true
        every { gemini.hasApiKey() } returns true
        coEvery { gemini.translate("hi", "en", "vi") } returns Result.success("xin chào")

        val result = repository.translate("hi", "en", "vi", onlineOnly = true)

        assertEquals("xin chào", result.getOrThrow())
        coVerify(exactly = 1) { gemini.translate("hi", "en", "vi") }
        coVerify(exactly = 0) { mlKit.translate(any(), any(), any()) }
    }

    @Test
    fun returnsFailureWhenBothEnginesFail() = runTest {
        every { network.isOnline() } returns true
        coEvery { gemini.translate(any(), any(), any()) } returns
            Result.failure(RuntimeException("online down"))
        coEvery { mlKit.translate(any(), any(), any()) } returns
            Result.failure(RuntimeException("offline down"))

        val result = repository.translate("text", "en", "vi")

        assertTrue(result.isFailure)
        assertEquals("offline down", result.exceptionOrNull()?.message)
    }
}
