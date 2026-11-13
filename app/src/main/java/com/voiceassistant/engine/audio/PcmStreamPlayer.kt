package com.voiceassistant.engine.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.PlaybackParams
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

// Streams PCM chunks to AudioTrack via USAGE_MEDIA / STREAM_MUSIC.
// Enforces MODE_NORMAL + preferred speaker/BT/headset so audio never routes to earpiece.
@Singleton
class PcmStreamPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioRoutingManager: AudioRoutingManager
) {

    private val mutex = Mutex()
    private var audioTrack: AudioTrack? = null
    private var currentSampleRate: Int = 0
    private var currentBufferMultiplier: Int = 0
    private var currentEncoding: Int = AudioFormat.ENCODING_PCM_16BIT
    @Volatile private var playbackSpeed: Float = 1.0f
    // Piper/sherpa already apply speed at synthesis — time-stretch here causes muffled/rè audio.
    @Volatile private var allowPlaybackTimeStretch: Boolean = false

    private val interrupted = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)

    suspend fun playChunk(pcm16le: ShortArray, sampleRateHz: Int) = withContext(Dispatchers.IO) {
        interrupted.set(false)
        ensureTrackReady(sampleRateHz, AudioFormat.ENCODING_PCM_16BIT)

        var offset = 0
        while (offset < pcm16le.size) {
            if (interrupted.get()) break

            while (paused.get() && !interrupted.get()) {
                pauseTrack()
                delay(40)
            }
            if (interrupted.get()) break

            val written = writeSamples16(pcm16le, offset)
            if (written <= 0) break
            offset += written
        }
    }

    suspend fun playChunkFloat(pcmFloat: FloatArray, sampleRateHz: Int) = withContext(Dispatchers.IO) {
        interrupted.set(false)
        ensureTrackReady(sampleRateHz, AudioFormat.ENCODING_PCM_FLOAT)

        var offset = 0
        while (offset < pcmFloat.size) {
            if (interrupted.get()) break

            while (paused.get() && !interrupted.get()) {
                pauseTrack()
                delay(40)
            }
            if (interrupted.get()) break

            val written = writeSamplesFloat(pcmFloat, offset)
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
        if (allowPlaybackTimeStretch) applySpeed()
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
                ensureTrackLocked(rate, currentEncoding)
                applySpeedLocked()
            }
        }
    }

    private suspend fun ensureTrackReady(sampleRateHz: Int, encoding: Int) {
        // Force media routing: never earpiece / SCO / communication device.
        forceMediaOutputRoute()

        mutex.withLock {
            ensureTrackLocked(sampleRateHz, encoding)
            applySpeedLocked()
            val track = audioTrack ?: return@withLock
            preferRouteDevice(track)
            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                track.play()
            }
        }
    }

    private fun forceMediaOutputRoute() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        runCatching {
            if (audioManager.mode != AudioManager.MODE_NORMAL) {
                audioManager.mode = AudioManager.MODE_NORMAL
            }
            if (audioManager.isBluetoothScoOn) {
                audioManager.stopBluetoothSco()
                @Suppress("DEPRECATION")
                audioManager.isBluetoothScoOn = false
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            }
            // Speakerphone flag only affects voice-call streams; keep false for media.
            @Suppress("DEPRECATION")
            if (audioManager.isSpeakerphoneOn) {
                audioManager.isSpeakerphoneOn = false
            }
        }
    }

    private fun preferRouteDevice(track: AudioTrack) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val devices = runCatching {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        }.getOrDefault(emptyArray())
        if (devices.isEmpty()) return

        val kind = audioRoutingManager.route.value.kind
        val preferred = when (kind) {
            AudioOutputKind.BLUETOOTH -> devices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    (Build.VERSION.SDK_INT >= 31 && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
            }
            AudioOutputKind.WIRED_HEADSET -> devices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET
            }
            AudioOutputKind.USB -> devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_DEVICE }
            else -> devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        } ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }

        // Explicitly avoid TYPE_BUILTIN_EARPIECE when a speaker/headset exists.
        preferred?.let { runCatching { track.setPreferredDevice(it) } }
    }

    private suspend fun writeSamples16(pcm16le: ShortArray, offset: Int): Int =
        mutex.withLock {
            val track = audioTrack ?: return@withLock -1
            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                runCatching { track.play() }
            }
            track.write(pcm16le, offset, pcm16le.size - offset)
        }

    private suspend fun writeSamplesFloat(pcmFloat: FloatArray, offset: Int): Int =
        mutex.withLock {
            val track = audioTrack ?: return@withLock -1
            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                runCatching { track.play() }
            }
            @Suppress("NewApi")
            track.write(pcmFloat, offset, pcmFloat.size - offset, AudioTrack.WRITE_BLOCKING)
        }

    private fun pauseTrack() {
        runCatching { audioTrack?.pause() }
    }

    private fun applySpeed() {
        if (!allowPlaybackTimeStretch) return
        runCatching {
            val track = audioTrack ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                track.playbackParams = PlaybackParams()
                    .allowDefaults()
                    .setSpeed(playbackSpeed)
                    .setPitch(1.0f)
            }
        }
    }

    private fun ensureTrackLocked(sampleRateHz: Int, encoding: Int) {
        val multiplier = audioRoutingManager.route.value.bufferMultiplier.coerceIn(2, 8)
        if (audioTrack != null &&
            currentSampleRate == sampleRateHz &&
            currentBufferMultiplier == multiplier &&
            currentEncoding == encoding
        ) return

        audioTrack?.release()
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRateHz,
            AudioFormat.CHANNEL_OUT_MONO,
            encoding
        ).coerceAtLeast(2048)

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setLegacyStreamType(AudioManager.STREAM_MUSIC)
            .build()
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(encoding)
                    .setSampleRate(sampleRateHz)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf * multiplier)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        preferRouteDevice(audioTrack!!)
        currentSampleRate = sampleRateHz
        currentBufferMultiplier = multiplier
        currentEncoding = encoding
    }

    private fun applySpeedLocked() {
        if (!allowPlaybackTimeStretch) return
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
        currentEncoding = AudioFormat.ENCODING_PCM_16BIT
    }
}
