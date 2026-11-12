package com.voiceassistant.engine.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.PlaybackParams
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Streams PCM chunks to AudioTrack. Supports pause/resume, speed via PlaybackParams,
 * and mid-chunk interrupt for sentence skip.
 */
@Singleton
class PcmStreamPlayer @Inject constructor(
    private val audioRoutingManager: AudioRoutingManager
) {

    private val mutex = Mutex()
    private var audioTrack: AudioTrack? = null
    private var currentSampleRate: Int = 0
    private var currentBufferMultiplier: Int = 0
    @Volatile private var playbackSpeed: Float = 1.0f

    private val interrupted = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)

    suspend fun playChunk(pcm16le: ShortArray, sampleRateHz: Int) = withContext(Dispatchers.IO) {
        interrupted.set(false)
        ensureTrackReady(sampleRateHz)

        var offset = 0
        while (offset < pcm16le.size) {
            if (interrupted.get()) break

            while (paused.get() && !interrupted.get()) {
                pauseTrack()
                delay(40)
            }
            if (interrupted.get()) break

            val written = writeSamples(pcm16le, offset)
            if (written <= 0) break
            offset += written
        }
    }

    fun interruptCurrentChunk() {
        interrupted.set(true)
    }

    fun setPaused(value: Boolean) {
        paused.set(value)
        if (value) pauseTrack()
    }

    fun setPlaybackSpeed(speed: Float) {
        playbackSpeed = speed.coerceIn(0.5f, 2.5f)
        applySpeed()
    }

    suspend fun stop() = withContext(Dispatchers.IO) {
        interrupted.set(true)
        paused.set(false)
        mutex.withLock { releaseTrackLocked() }
    }

    suspend fun invalidateRoute() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val rate = currentSampleRate
            releaseTrackLocked()
            if (rate > 0) {
                ensureTrackLocked(rate)
                applySpeedLocked()
            }
        }
    }

    private suspend fun ensureTrackReady(sampleRateHz: Int) {
        mutex.withLock {
            ensureTrackLocked(sampleRateHz)
            applySpeedLocked()
            val track = audioTrack ?: return@withLock
            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                track.play()
            }
        }
    }

    private suspend fun writeSamples(pcm16le: ShortArray, offset: Int): Int =
        mutex.withLock {
            val track = audioTrack ?: return@withLock -1
            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                runCatching { track.play() }
            }
            track.write(pcm16le, offset, pcm16le.size - offset)
        }

    private fun pauseTrack() {
        runCatching { audioTrack?.pause() }
    }

    private fun applySpeed() {
        runCatching {
            // Best-effort without mutex — applySpeedLocked used when holding lock
            val track = audioTrack ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                track.playbackParams = PlaybackParams()
                    .allowDefaults()
                    .setSpeed(playbackSpeed)
                    .setPitch(1.0f)
            }
        }
    }

    private fun ensureTrackLocked(sampleRateHz: Int) {
        val multiplier = audioRoutingManager.route.value.bufferMultiplier.coerceIn(2, 8)
        if (audioTrack != null &&
            currentSampleRate == sampleRateHz &&
            currentBufferMultiplier == multiplier
        ) return

        audioTrack?.release()
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRateHz,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(2048)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRateHz)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf * multiplier)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        currentSampleRate = sampleRateHz
        currentBufferMultiplier = multiplier
    }

    private fun applySpeedLocked() {
        val track = audioTrack ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching {
                track.playbackParams = PlaybackParams()
                    .allowDefaults()
                    .setSpeed(playbackSpeed)
                    .setPitch(1.0f)
            }
        }
    }

    private fun releaseTrackLocked() {
        audioTrack?.let { track ->
            runCatching {
                track.pause()
                track.flush()
                track.stop()
            }
            track.release()
        }
        audioTrack = null
        currentSampleRate = 0
        currentBufferMultiplier = 0
    }
}
