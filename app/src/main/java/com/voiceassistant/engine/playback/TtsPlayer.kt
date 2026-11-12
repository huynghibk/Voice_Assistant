package com.voiceassistant.engine.playback

import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.voiceassistant.domain.playback.PlaybackController
import com.voiceassistant.domain.playback.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Media3 player bridge over [PlaybackController] so MediaSession can show
 * Play/Pause, Previous sentence, Next sentence, Stop on the notification / lock screen.
 */
@UnstableApi
class TtsPlayer(
    private val playbackController: PlaybackController,
    looper: Looper = Looper.getMainLooper()
) : SimpleBasePlayer(looper) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(looper)

    init {
        scope.launch {
            playbackController.state.collect {
                mainHandler.post { invalidateState() }
            }
        }
    }

    fun releasePlayer() {
        scope.cancel()
    }

    override fun getState(): State {
        val s = playbackController.state.value
        val items = s.sentences.mapIndexed { index, sentence ->
            MediaItem.Builder()
                .setMediaId("sentence_$index")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(s.title.ifBlank { "VoiceAssistant" })
                        .setArtist("TTS")
                        .setSubtitle(sentence.take(80))
                        .setDisplayTitle(sentence.take(80))
                        .build()
                )
                .build()
        }
        val playlist = items.map { MediaItemData.Builder(it.mediaId!!).setMediaItem(it).build() }
        val index = s.currentSentenceIndex.coerceIn(-1, (items.size - 1).coerceAtLeast(-1))
        val playbackState = when {
            !s.isPlaying && items.isEmpty() -> Player.STATE_IDLE
            !s.isPlaying -> Player.STATE_ENDED
            else -> Player.STATE_READY
        }
        val builder = State.Builder()
            .setAvailableCommands(
                Player.Commands.Builder()
                    .addAll(
                        Player.COMMAND_PLAY_PAUSE,
                        Player.COMMAND_STOP,
                        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                        Player.COMMAND_SEEK_TO_NEXT,
                        Player.COMMAND_SEEK_TO_PREVIOUS,
                        Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                        Player.COMMAND_GET_TIMELINE,
                        Player.COMMAND_GET_METADATA
                    )
                    .build()
            )
            .setPlayWhenReady(s.isPlaying && !s.isPaused, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(playbackState)
        if (playlist.isNotEmpty() && index >= 0) {
            builder.setPlaylist(playlist)
                .setCurrentMediaItemIndex(index)
        } else if (playlist.isNotEmpty()) {
            builder.setPlaylist(playlist)
                .setCurrentMediaItemIndex(0)
        }
        return builder.build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady) {
            val s = playbackController.state.value
            if (s.isPlaying && s.isPaused) {
                playbackController.resume()
            } else if (!s.isPlaying && s.sentences.isNotEmpty()) {
                // Cannot restart without voice — just resume if possible
                playbackController.resume()
            } else {
                playbackController.resume()
            }
        } else {
            playbackController.pause()
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        playbackController.stop()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int
    ): ListenableFuture<*> {
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> playbackController.skipToNextSentence()
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> playbackController.skipToPreviousSentence()
            else -> {
                if (mediaItemIndex >= 0) {
                    val s = playbackController.state.value
                    when {
                        mediaItemIndex > s.currentSentenceIndex ->
                            playbackController.skipToNextSentence()
                        mediaItemIndex < s.currentSentenceIndex ->
                            playbackController.skipToPreviousSentence()
                    }
                }
            }
        }
        return Futures.immediateVoidFuture()
    }
}
