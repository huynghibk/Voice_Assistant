package com.voiceassistant.engine.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Streams PCM chunks to AudioTrack immediately so sentence N plays
 * while the TTS queue continues inferencing sentence N+1.
 *
 * Buffer size scales with [AudioRoutingManager] (larger for Bluetooth / Cast).
 */
@Singleton
class PcmStreamPlayer @Inject constructor(
    private val audioRoutingManager: AudioRoutingManager
) {

    private val mutex = Mutex()
    private var audioTrack: AudioTrack? = null
    private var currentSampleRate: Int = 0
    private var currentBufferMultiplier: Int = 0

    suspend fun playChunk(pcm16le: ShortArray, sampleRateHz: Int) = withContext(Dispatchers.IO) {
        mutex.withLock {
            ensureTrack(sampleRateHz)
            val track = audioTrack ?: return@withLock
            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                track.play()
            }
            var offset = 0
            while (offset < pcm16le.size) {
                val written = track.write(pcm16le, offset, pcm16le.size - offset)
                if (written <= 0) break
                offset += written
            }
        }
    }

    suspend fun stop() = withContext(Dispatchers.IO) {
        mutex.withLock {
            releaseTrackLocked()
        }
    }

    /** Rebuild AudioTrack if route/buffer needs changed (e.g. BT connected mid-playback). */
    suspend fun invalidateRoute() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val rate = currentSampleRate
            releaseTrackLocked()
            if (rate > 0) ensureTrack(rate)
        }
    }

    private fun ensureTrack(sampleRateHz: Int) {
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
