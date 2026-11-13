package com.voiceassistant.presentation.home

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import androidx.compose.foundation.verticalScroll

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

    val speechIntentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onSpeechIntentResult(result.data)
        } else {
            viewModel.stopListening()
        }
    }

    LaunchedEffect(state.pendingSpeechIntent) {
        if (!state.pendingSpeechIntent) return@LaunchedEffect
        viewModel.onSpeechIntentConsumed()
        runCatching {
            speechIntentLauncher.launch(viewModel.createSpeechIntent())
        }.onFailure {
            viewModel.onSpeechIntentLaunchFailed()
        }
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_body),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = if (state.onboardingPiperReady) {
                                stringResource(R.string.onboarding_piper_ready)
                            } else {
                                stringResource(R.string.onboarding_piper_system)
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (!state.onboardingPiperReady) {
                            TextButton(
                                onClick = {
                                    viewModel.dismissOnboarding()
                                    onOpenModelManager()
                                },
                                enabled = !state.onboardingDownloading,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Tải giọng đọc Piper (Tiếng Việt / English)")
                            }
                        }
                        Text(
                            text = if (state.onboardingMlKitReady) {
                                stringResource(R.string.onboarding_mlkit_ready)
                            } else {
                                stringResource(R.string.onboarding_mlkit_need)
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (state.onboardingDownloading) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = stringResource(R.string.onboarding_downloading_en_vi),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
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
                val piperReady = state.piperReadyByVoiceId[voice.id] == true
                val isClone = !voice.isPreset && voice.embeddingPath != null
                VaVoiceChip(
                    label = voice.name,
                    subtitle = stringResource(
                        when {
                            isClone && piperReady -> R.string.home_voice_clone_ready
                            isClone -> R.string.home_voice_clone_needs_piper
                            piperReady -> R.string.home_voice_piper_ready
                            voice.isPreset -> R.string.home_voice_system_fallback
                            else -> R.string.home_voice_saved
                        }
                    ),
                    selected = voice.id == state.selectedVoiceId,
                    onClick = { viewModel.onVoiceSelected(voice.id) }
                )
            }
        }

        Spacer(Modifier.height(VaDimens.sectionGap))
        VaSectionLabel(stringResource(R.string.home_read_mode_section))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(VaDimens.chipGap)
        ) {
            VaVoiceChip(
                label = stringResource(R.string.home_read_original),
                selected = state.readMode == HomeReadMode.ORIGINAL,
                onClick = { viewModel.onReadModeSelected(HomeReadMode.ORIGINAL) }
            )
            VaVoiceChip(
                label = stringResource(R.string.home_translate_read),
                selected = state.readMode == HomeReadMode.TRANSLATE_AND_READ,
                onClick = { viewModel.onReadModeSelected(HomeReadMode.TRANSLATE_AND_READ) }
            )
        }

        if (state.readMode == HomeReadMode.TRANSLATE_AND_READ) {
            Spacer(Modifier.height(VaDimens.sectionGap))
            VaSectionLabel(stringResource(R.string.home_translate_section))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(VaDimens.chipGap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.home_target_lang),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                state.translateLanguages.forEach { lang ->
                    VaVoiceChip(
                        label = lang.label,
                        selected = state.selectedTargetLang == lang.id,
                        onClick = { viewModel.onTargetLang(lang.id) }
                    )
                }
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
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
                    trailingIcon = {
                        if (state.isTranslating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            IconButton(
                                onClick = ::onMicClick,
                                enabled = !state.isPlaying
                            ) {
                                Icon(
                                    imageVector = if (state.isListening) {
                                        Icons.Outlined.MicOff
                                    } else {
                                        Icons.Outlined.Mic
                                    },
                                    contentDescription = stringResource(
                                        if (state.isListening) {
                                            R.string.home_stop_record
                                        } else {
                                            R.string.translate_mic
                                        }
                                    ),
                                    tint = if (state.isListening) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    }
                                )
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = extras.hairline,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )
                if (state.translatedText.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    VaSectionLabel(stringResource(R.string.translate_result_box))
                    Text(
                        text = state.translatedText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        }

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

// Soft Auto-Guard when Piper pack is missing — continue with System TTS or open packs.
@Composable
internal fun AutoGuardSystemTtsDialog(
    onDismiss: (dontShowAgain: Boolean) -> Unit = {},
    onOpenModelManager: () -> Unit
) {
    var dontShowAgain by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { onDismiss(dontShowAgain) },
        title = { Text(stringResource(R.string.guard_system_tts_title)) },
        text = {
            Column {
                Text(stringResource(R.string.guard_system_tts_body))
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { dontShowAgain = !dontShowAgain }
                ) {
                    Checkbox(
                        checked = dontShowAgain,
                        onCheckedChange = { dontShowAgain = it }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.guard_dont_show_again),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss(dontShowAgain)
                onOpenModelManager()
            }) {
                Text(stringResource(R.string.guard_open_model_manager))
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss(dontShowAgain) }) {
                Text(stringResource(R.string.guard_continue))
            }
        }
    )
}
