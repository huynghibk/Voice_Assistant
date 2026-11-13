package com.voiceassistant.engine.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
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
        runCatching {
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
        }.onFailure { e ->
            Log.e(TAG, "MediaSession init failed — TTS still works in foreground", e)
        }
        promoteToForeground()

        scope.launch {
            playbackController.state
                .map { it.isPlaying to it.isPaused }
                .distinctUntilChanged()
                .collect { (playing, _) ->
                    if (!playing) {
                        runCatching { stopForeground(STOP_FOREGROUND_DETACH) }
                    }
                }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        promoteToForeground()
        return START_STICKY
    }

    private fun promoteToForeground() {
        val notification = buildForegroundNotification()
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onFailure { e ->
            Log.e(TAG, "startForeground failed — playback may continue without notification", e)
        }
    }

    private fun buildForegroundNotification() =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.tts_notification_reading))
            .setSmallIcon(R.drawable.ic_stat_tts)
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
        private const val TAG = "TtsPlaybackService"
        const val CHANNEL_ID = "va_tts_playback"
        const val NOTIFICATION_ID = 42
        const val ACTION_START = "com.voiceassistant.action.START_TTS_PLAYBACK"
    }
}
