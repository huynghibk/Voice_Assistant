package com.voiceassistant.presentation.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voiceassistant.R
import com.voiceassistant.engine.audio.ExportFormat
import com.voiceassistant.engine.audio.ExportQuality
import com.voiceassistant.presentation.components.KaraokeTextPanel
import com.voiceassistant.presentation.components.VaBrandMark
import com.voiceassistant.presentation.components.VaGhostButton
import com.voiceassistant.presentation.components.VaPrimaryButton
import com.voiceassistant.presentation.components.VaScreenHeader
import com.voiceassistant.presentation.components.VaSectionLabel
import com.voiceassistant.presentation.components.VaStatusBar
import com.voiceassistant.presentation.components.VaVoiceChip
import com.voiceassistant.presentation.theme.LocalVaExtra
import com.voiceassistant.presentation.theme.VaDimens

@Composable
fun HomeScreen(
    pendingScannerText: String? = null,
    onPendingTextConsumed: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenModelManager: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val extras = LocalVaExtra.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.startListening()
    }

    LaunchedEffect(pendingScannerText) {
        if (!pendingScannerText.isNullOrBlank()) {
            viewModel.applyIncomingText(pendingScannerText)
            onPendingTextConsumed()
        }
    }

    fun hideKeyboard() {
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    fun onMicClick() {
        hideKeyboard()
        if (state.isListening) {
            viewModel.stopListening()
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.startListening()
        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(focusManager, keyboardController) {
                detectTapGestures(
                    onTap = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }
                )
            }
            .padding(horizontal = VaDimens.screenHorizontal, vertical = VaDimens.screenTop)
    ) {
        VaScreenHeader(onOpenSettings = onOpenSettings) {
            Column {
                VaBrandMark()
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.home_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    state.routeLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (state.showSystemTtsDialog) {
            AutoGuardSystemTtsDialog(
                onDismiss = viewModel::dismissSystemTtsDialog,
                onOpenModelManager = {
                    viewModel.dismissSystemTtsDialog()
                    onOpenModelManager()
                }
            )
        }

        if (state.showOnboarding) {
            AlertDialog(
                onDismissRequest = viewModel::dismissOnboarding,
                title = { Text(stringResource(R.string.onboarding_title)) },
                text = {
                    Column {
                        Text(stringResource(R.string.onboarding_body))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (state.onboardingPiperReady) {
                                stringResource(R.string.onboarding_piper_ready)
                            } else {
                                stringResource(R.string.onboarding_piper_system)
                            }
                        )
                        Text(
                            if (state.onboardingMlKitReady) {
                                stringResource(R.string.onboarding_mlkit_ready)
                            } else {
                                stringResource(R.string.onboarding_mlkit_need)
                            }
                        )
                        if (state.onboardingDownloading) {
                            Spacer(Modifier.height(12.dp))
                            CircularProgressIndicator()
                        }
                    }
                },
                confirmButton = {
                    if (!state.onboardingMlKitReady) {
                        TextButton(
                            onClick = viewModel::downloadStarterMlKit,
                            enabled = !state.onboardingDownloading
                        ) {
                            Text(stringResource(R.string.onboarding_download_en_vi))
                        }
                    } else {
                        TextButton(onClick = viewModel::dismissOnboarding) {
                            Text(stringResource(R.string.onboarding_done))
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = viewModel::dismissOnboarding,
                        enabled = !state.onboardingDownloading
                    ) {
                        Text(stringResource(R.string.onboarding_skip))
                    }
                }
            )
        }

        Spacer(Modifier.height(VaDimens.sectionGap))
        VaSectionLabel(stringResource(R.string.home_voices))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(VaDimens.chipGap)) {
            items(state.voices, key = { it.id }) { voice ->
                VaVoiceChip(
                    label = voice.name,
                    subtitle = stringResource(
                        if (voice.isPreset) R.string.home_voice_preset
                        else R.string.home_voice_saved
                    ),
                    selected = voice.id == state.selectedVoiceId,
                    onClick = { viewModel.onVoiceSelected(voice.id) }
                )
            }
        }

        Spacer(Modifier.height(VaDimens.sectionGap))
        if (state.isPlaying && state.karaokeSentences.isNotEmpty()) {
            VaSectionLabel(stringResource(R.string.home_karaoke))
            KaraokeTextPanel(
                sentences = state.karaokeSentences,
                activeIndex = state.karaokeIndex,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        } else {
            OutlinedTextField(
                value = state.text,
                onValueChange = viewModel::onTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                placeholder = {
                    Text(
                        stringResource(R.string.home_text_hint),
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge,
                shape = MaterialTheme.shapes.large,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = extras.hairline,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )
        }

        Spacer(Modifier.height(12.dp))
        VaPrimaryButton(
            text = if (state.isListening) {
                stringResource(R.string.home_stop_record)
            } else {
                stringResource(R.string.home_record)
            },
            onClick = ::onMicClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isPlaying
        )

        Spacer(Modifier.height(12.dp))
        VaSectionLabel(stringResource(R.string.home_speed))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(VaDimens.chipGap)
        ) {
            viewModel.speedPresets.forEach { speed ->
                val label = if (speed == 1.0f) "1.0x" else "${speed}x"
                VaVoiceChip(
                    label = label,
                    selected = state.playbackSpeed == speed,
                    onClick = { viewModel.setSpeed(speed) }
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        VaSectionLabel(stringResource(R.string.home_export))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(VaDimens.chipGap)
        ) {
            ExportFormat.entries.forEach { fmt ->
                VaVoiceChip(
                    label = fmt.name,
                    selected = state.exportFormat == fmt,
                    onClick = { viewModel.onExportFormat(fmt) }
                )
            }
            ExportQuality.entries.forEach { q ->
                VaVoiceChip(
                    label = q.name,
                    selected = state.exportQuality == q,
                    onClick = { viewModel.onExportQuality(q) }
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        VaStatusBar(message = state.statusMessage, accent = state.progressLabel.ifBlank { null })

        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            AnimatedContent(
                targetState = state.isPlaying,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "playBtn",
                modifier = Modifier.weight(1.2f)
            ) { playing ->
                if (playing) {
                    VaPrimaryButton(
                        text = stringResource(R.string.home_stop),
                        onClick = {
                            hideKeyboard()
                            viewModel.stop()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    VaPrimaryButton(
                        text = stringResource(R.string.home_play),
                        onClick = {
                            hideKeyboard()
                            viewModel.play()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isListening
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            VaGhostButton(
                text = stringResource(R.string.home_export),
                onClick = {
                    hideKeyboard()
                    viewModel.exportAudio()
                },
                modifier = Modifier.weight(1f),
                enabled = !state.isPlaying && !state.isListening
            )
            Spacer(Modifier.width(8.dp))
            VaGhostButton(
                text = stringResource(R.string.home_share),
                onClick = {
                    hideKeyboard()
                    viewModel.shareLastExport()
                },
                modifier = Modifier.weight(1f),
                enabled = !state.isPlaying && !state.isListening
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Soft Auto-Guard when Piper pack is missing — continue with System TTS or open packs. */
@Composable
internal fun AutoGuardSystemTtsDialog(
    onDismiss: () -> Unit,
    onOpenModelManager: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.guard_system_tts_title)) },
        text = { Text(stringResource(R.string.guard_system_tts_body)) },
        confirmButton = {
            TextButton(onClick = onOpenModelManager) {
                Text(stringResource(R.string.guard_open_model_manager))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.guard_continue))
            }
        }
    )
}
