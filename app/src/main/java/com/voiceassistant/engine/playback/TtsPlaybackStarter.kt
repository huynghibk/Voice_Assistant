package com.voiceassistant.engine.playback

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat

object TtsPlaybackStarter {
    fun start(context: Context) {
        val intent = Intent(context, TtsPlaybackService::class.java).apply {
            action = TtsPlaybackService.ACTION_START
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, TtsPlaybackService::class.java))
    }
}
