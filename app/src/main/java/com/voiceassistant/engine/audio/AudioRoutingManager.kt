package com.voiceassistant.engine.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.voiceassistant.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class AudioOutputKind {
    BUILTIN_SPEAKER,
    WIRED_HEADSET,
    BLUETOOTH,
    USB,
    CAST_REMOTE,
    UNKNOWN
}

data class AudioRouteState(
    val kind: AudioOutputKind = AudioOutputKind.BUILTIN_SPEAKER,
    val deviceName: String = "",
    /** Multiplier over AudioTrack min buffer — larger for Bluetooth A2DP latency (~100–200ms). */
    val bufferMultiplier: Int = 2,
    val castAvailable: Boolean = false,
    val castRouteName: String? = null
)

/**
 * Routes TTS PCM to the active output (speaker / wired / Bluetooth / Cast)
 * and emits pause events when headphones are unplugged (ACTION_AUDIO_BECOMING_NOISY).
 */
@Singleton
class AudioRoutingManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mediaRouter: MediaRouter? = runCatching { MediaRouter.getInstance(context) }.getOrNull()

    private val _route = MutableStateFlow(resolveRoute())
    val route: StateFlow<AudioRouteState> = _route.asStateFlow()

    private val _becomingNoisy = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val becomingNoisy: SharedFlow<Unit> = _becomingNoisy.asSharedFlow()

    private val handler = Handler(Looper.getMainLooper())
    private var started = false

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            refresh()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            refresh()
        }
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                _becomingNoisy.tryEmit(Unit)
            }
        }
    }

    private val mediaRouterCallback = object : MediaRouter.Callback() {
        override fun onRouteSelected(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
        override fun onRouteUnselected(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
    }

    val castSelector: MediaRouteSelector = MediaRouteSelector.Builder()
        .addControlCategory(MediaControlIntent.CATEGORY_LIVE_AUDIO)
        .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
        .build()

    fun start() {
        if (started) return
        started = true
        runCatching { audioManager.registerAudioDeviceCallback(deviceCallback, handler) }
        runCatching { registerNoisyReceiver() }
        runCatching {
            mediaRouter?.addCallback(
                castSelector,
                mediaRouterCallback,
                MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY
            )
        }
        // Prefer media stream for A2DP when Bluetooth is connected
        runCatching { audioManager.mode = AudioManager.MODE_NORMAL }
        refresh()
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { audioManager.unregisterAudioDeviceCallback(deviceCallback) }
        runCatching { context.unregisterReceiver(noisyReceiver) }
        runCatching { mediaRouter?.removeCallback(mediaRouterCallback) }
    }

    fun refresh() {
        _route.value = resolveRoute()
    }

    fun selectedCastRoute(): MediaRouter.RouteInfo? =
        mediaRouter?.selectedRoute?.takeIf { !it.isDefault && it.matchesSelector(castSelector) }

    private fun registerNoisyReceiver() {
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        // API 33+ (and target 34 emulator) requires an explicit export flag.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(noisyReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(noisyReceiver, filter)
        }
    }

    private fun resolveRoute(): AudioRouteState {
        val devices = runCatching {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        }.getOrDefault(emptyArray())
        val castRoute = selectedCastRoute()
        if (castRoute != null) {
            return AudioRouteState(
                kind = AudioOutputKind.CAST_REMOTE,
                deviceName = castRoute.name?.toString() ?: "Cast",
                bufferMultiplier = 4,
                castAvailable = true,
                castRouteName = castRoute.name?.toString()
            )
        }

        val bluetooth = devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                (Build.VERSION.SDK_INT >= 31 && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
        if (bluetooth != null || runCatching { audioManager.isBluetoothA2dpOn }.getOrDefault(false)) {
            val name = bluetooth?.productName?.toString()
                ?: "Tai nghe Bluetooth"
            return AudioRouteState(
                kind = AudioOutputKind.BLUETOOTH,
                deviceName = name,
                bufferMultiplier = 4, // ~100–200ms Bluetooth latency compensation
                castAvailable = hasCastRoutes()
            )
        }

        val wired = devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
        if (wired != null || runCatching { audioManager.isWiredHeadsetOn }.getOrDefault(false)) {
            return AudioRouteState(
                kind = AudioOutputKind.WIRED_HEADSET,
                deviceName = wired?.productName?.toString() ?: "Tai nghe có dây",
                bufferMultiplier = 2,
                castAvailable = hasCastRoutes()
            )
        }

        val usb = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_DEVICE }
        if (usb != null) {
            return AudioRouteState(
                kind = AudioOutputKind.USB,
                deviceName = usb.productName?.toString() ?: "USB audio",
                bufferMultiplier = 3,
                castAvailable = hasCastRoutes()
            )
        }

        return AudioRouteState(
            kind = AudioOutputKind.BUILTIN_SPEAKER,
            deviceName = context.getString(R.string.home_route_speaker),
            bufferMultiplier = 2,
            castAvailable = hasCastRoutes()
        )
    }

    private fun hasCastRoutes(): Boolean =
        runCatching {
            mediaRouter?.routes?.any { !it.isDefault && it.matchesSelector(castSelector) } == true
        }.getOrDefault(false)
}
