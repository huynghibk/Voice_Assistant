package com.voiceassistant.engine.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Studio-style recorder with optional NoiseSuppressor + AGC,
 * peak-normalize, and WAV output suitable for voice cloning (1–3s+).
 */
@Singleton
class StudioRecorder @Inject constructor(
    @ApplicationContext private val context: Context
) {

    data class RecorderState(
        val isRecording: Boolean = false,
        val elapsedMs: Long = 0L,
        val peakLevel: Float = 0f,
        val outputFile: File? = null,
        val error: String? = null
    )

    private val _state = MutableStateFlow(RecorderState())
    val state: StateFlow<RecorderState> = _state.asStateFlow()

    private val stopFlag = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var agc: AutomaticGainControl? = null

    fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun recordToWav(
        maxDurationMs: Long = 8_000L,
        fileName: String = "clone_sample_${System.currentTimeMillis()}.wav"
    ): File = withContext(Dispatchers.IO) {
        if (!hasMicPermission()) {
            throw SecurityException("RECORD_AUDIO permission required")
        }
        stopFlag.set(false)
        val dir = File(context.filesDir, "voice_samples").apply { mkdirs() }
        val outFile = File(dir, fileName)

        val sampleRate = SAMPLE_RATE
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, encoding)
        val bufferSize = minBuf * 2

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            channelConfig,
            encoding,
            bufferSize
        )
        audioRecord = recorder

        if (NoiseSuppressor.isAvailable()) {
            noiseSuppressor = NoiseSuppressor.create(recorder.audioSessionId)?.also { it.enabled = true }
        }
        if (AutomaticGainControl.isAvailable()) {
            agc = AutomaticGainControl.create(recorder.audioSessionId)?.also { it.enabled = true }
        }

        val pcm = ArrayList<Short>((sampleRate * maxDurationMs / 1000).toInt())
        val shortBuf = ShortArray(bufferSize / 2)
        val startedAt = System.currentTimeMillis()

        recorder.startRecording()
        _state.value = RecorderState(isRecording = true, outputFile = outFile)

        try {
            while (isActive && !stopFlag.get()) {
                val elapsed = System.currentTimeMillis() - startedAt
                if (elapsed >= maxDurationMs) break
                val read = recorder.read(shortBuf, 0, shortBuf.size)
                if (read > 0) {
                    var peak = 0
                    for (i in 0 until read) {
                        pcm += shortBuf[i]
                        peak = maxOf(peak, abs(shortBuf[i].toInt()))
                    }
                    _state.value = RecorderState(
                        isRecording = true,
                        elapsedMs = elapsed,
                        peakLevel = peak / Short.MAX_VALUE.toFloat(),
                        outputFile = outFile
                    )
                }
            }
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
            audioRecord = null
            noiseSuppressor?.release()
            agc?.release()
            noiseSuppressor = null
            agc = null
        }

        val normalized = normalize(pcm.toShortArray())
        writeWav(outFile, normalized, sampleRate)
        _state.value = RecorderState(
            isRecording = false,
            elapsedMs = System.currentTimeMillis() - startedAt,
            outputFile = outFile
        )
        outFile
    }

    fun stop() {
        stopFlag.set(true)
    }

    private fun normalize(pcm: ShortArray, targetPeak: Float = 0.9f): ShortArray {
        if (pcm.isEmpty()) return pcm
        var peak = 1
        for (s in pcm) peak = maxOf(peak, abs(s.toInt()))
        val scale = (Short.MAX_VALUE * targetPeak) / peak
        if (scale >= 0.98f && scale <= 1.02f) return pcm
        return ShortArray(pcm.size) { i ->
            (pcm[i] * scale).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    private fun writeWav(file: File, pcm: ShortArray, sampleRate: Int) {
        val dataSize = pcm.size * 2
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(0)
            raf.writeBytes("RIFF")
            writeIntLE(raf, 36 + dataSize)
            raf.writeBytes("WAVE")
            raf.writeBytes("fmt ")
            writeIntLE(raf, 16)
            writeShortLE(raf, 1)
            writeShortLE(raf, 1)
            writeIntLE(raf, sampleRate)
            writeIntLE(raf, sampleRate * 2)
            writeShortLE(raf, 2)
            writeShortLE(raf, 16)
            raf.writeBytes("data")
            writeIntLE(raf, dataSize)
            val buf = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
            pcm.forEach { buf.putShort(it) }
            raf.write(buf.array())
        }
    }

    private fun writeIntLE(raf: RandomAccessFile, v: Int) {
        raf.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array())
    }

    private fun writeShortLE(raf: RandomAccessFile, v: Int) {
        raf.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v.toShort()).array())
    }

    companion object {
        const val SAMPLE_RATE = 22_050
    }
}

fun File.readWavPcm16Mono(): Pair<ShortArray, Int> {
    RandomAccessFile(this, "r").use { raf ->
        val header = ByteArray(44)
        raf.readFully(header)
        val sampleRate = ByteBuffer.wrap(header, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val dataSize = ByteBuffer.wrap(header, 40, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val bytes = ByteArray(dataSize)
        raf.readFully(bytes)
        val shorts = ShortArray(dataSize / 2)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in shorts.indices) shorts[i] = buf.short
        return shorts to sampleRate
    }
}

fun rmsLevel(pcm: ShortArray): Float {
    if (pcm.isEmpty()) return 0f
    var sum = 0.0
    val n = min(pcm.size, 8_000)
    for (i in 0 until n) {
        val v = pcm[i] / Short.MAX_VALUE.toDouble()
        sum += v * v
    }
    return sqrt(sum / n).toFloat()
}
