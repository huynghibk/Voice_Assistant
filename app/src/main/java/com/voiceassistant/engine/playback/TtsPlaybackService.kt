package com.voiceassistant.engine.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.voiceassistant.R
import com.voiceassistant.domain.playback.PlaybackController
import com.voiceassistant.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground media playback service for TTS. Keeps reading when the screen is off
 * and exposes Play/Pause, Previous sentence, Next sentence, Stop on the system notification.
 */
@AndroidEntryPoint
class TtsPlaybackService : MediaSessionService() {

    @Inject lateinit var playbackController: PlaybackController

    private var mediaSession: MediaSession? = null
    private var ttsPlayer: TtsPlayer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        val player = TtsPlayer(playbackController)
        ttsPlayer = player
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        mediaSession = MediaSession.Builder(this, player)
            .setId("va_tts_session")
            .setSessionActivity(sessionActivity)
            .build()

        scope.launch {
            playbackController.state
                .map { it.isPlaying to it.isPaused }
                .distinctUntilChanged()
                .collect { (playing, _) ->
                    if (!playing) {
                        // Allow system to stop FGS shortly after playback ends
                        stopForeground(STOP_FOREGROUND_DETACH)
                    }
                }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // Ensure a foreground notification exists quickly on Android 14+
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.tts_notification_reading))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        ttsPlayer?.releasePlayer()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        ttsPlayer = null
        super.onDestroy()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.tts_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.tts_notification_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "va_tts_playback"
        const val NOTIFICATION_ID = 42
        const val ACTION_START = "com.voiceassistant.action.START_TTS_PLAYBACK"
    }
}
