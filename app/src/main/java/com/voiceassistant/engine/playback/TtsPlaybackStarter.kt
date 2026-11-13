package com.voiceassistant.engine.playback

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

object TtsPlaybackStarter {
    private const val TAG = "TtsPlaybackStarter"

    fun start(context: Context) {
        val intent = Intent(context, TtsPlaybackService::class.java).apply {
            action = TtsPlaybackService.ACTION_START
        }
        runCatching { ContextCompat.startForegroundService(context, intent) }
            .onFailure { e ->
                // FGS is optional for in-app TTS; do not crash the whole app if it cannot start.
                Log.w(TAG, "Could not start TtsPlaybackService", e)
            }
    }

    fun stop(context: Context) {
        runCatching { context.stopService(Intent(context, TtsPlaybackService::class.java)) }
    }
}
