package com.voiceassistant.presentation.translate

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.voiceassistant.domain.translation.LanguageIdentifier
import com.voiceassistant.presentation.components.VaGhostButton
import com.voiceassistant.presentation.components.VaPrimaryButton
import com.voiceassistant.presentation.components.VaScreenHeader
import com.voiceassistant.presentation.components.VaSectionLabel
import com.voiceassistant.presentation.components.VaStatusBar
import com.voiceassistant.presentation.components.VaVoiceChip
import com.voiceassistant.presentation.theme.AmberAccent
import com.voiceassistant.presentation.theme.DeepCharcoal
import com.voiceassistant.presentation.theme.InkOnDark
import com.voiceassistant.presentation.theme.InkOnLight
import com.voiceassistant.presentation.theme.LocalVaExtra
import com.voiceassistant.presentation.theme.SoftCream
import com.voiceassistant.presentation.theme.VaDimens

@Composable
fun TranslateHubScreen(
    onOpenCamera: () -> Unit,
    onOpenConversation: () -> Unit = {},
    onSendToTts: (String) -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenModelManager: () -> Unit = {},
    viewModel: TranslateHubViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val extras = LocalVaExtra.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.toggleListening()
    }

    fun hideKeyboard() {
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    fun ensureMic() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.toggleListening()
        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun pasteFromClipboard() {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val text = cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        viewModel.pasteSource(text)
    }

    fun copyResult() {
        val text = state.translatedText.trim()
        if (text.isEmpty()) {
            return
        }
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText("translation", text))
        viewModel.markCopied()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                detectTapGestures {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }
            }
            .padding(horizontal = VaDimens.screenHorizontal, vertical = VaDimens.screenTop)
    ) {
        VaScreenHeader(
            onOpenSettings = onOpenSettings,
            trailing = {
                TextButton(onClick = onOpenConversation) {
                    Icon(
                        Icons.Outlined.Forum,
                        contentDescription = null,
                        tint = AmberAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        stringResource(R.string.conversation_open),
                        color = AmberAccent,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        ) {
            Column {
                Text(
                    stringResource(R.string.nav_translate),
                    style = MaterialTheme.typography.displayMedium
                )
                Text(
                    stringResource(R.string.translate_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (state.showDownloadDialog) {
            AlertDialog(
                onDismissRequest = viewModel::dismissDownloadDialog,
                title = { Text(stringResource(R.string.guard_mlkit_title)) },
                text = {
                    Text(
                        state.downloadDialogMessage.ifBlank {
                            stringResource(R.string.guard_mlkit_body)
                        }
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = viewModel::confirmDownloadAndTranslate,
                        enabled = !state.isDownloadingPack
                    ) {
                        Text(
                            if (state.isDownloadingPack) {
                                stringResource(R.string.model_downloading)
                            } else {
                                stringResource(R.string.guard_download_now)
                            }
                        )
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(
                            onClick = {
                                viewModel.dismissDownloadDialog()
                                onOpenModelManager()
                            },
                            enabled = !state.isDownloadingPack
                        ) {
                            Text(stringResource(R.string.guard_open_model_manager))
                        }
                        TextButton(
                            onClick = viewModel::continueTranslateWithoutDownload,
                            enabled = !state.isDownloadingPack
                        ) {
                            Text(stringResource(R.string.guard_try_online))
                        }
                    }
                }
            )
        }

        Spacer(Modifier.height(VaDimens.sectionGap))

        // Source chips, swap, then every supported target (vi, en, ja, ko, zh, fr).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.home_source_lang),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            VaVoiceChip(
                label = stringResource(R.string.lang_auto),
                selected = state.selectedSourceLang == LanguageIdentifier.AUTO,
                onClick = { viewModel.onSourceLang(LanguageIdentifier.AUTO) }
            )
            state.languages.forEach { lang ->
                VaVoiceChip(
                    label = lang.label,
                    selected = state.selectedSourceLang == lang.id,
                    onClick = { viewModel.onSourceLang(lang.id) }
                )
            }
        }
        if (state.selectedSourceLang == LanguageIdentifier.AUTO && state.detectedLabel.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.lang_detected, state.detectedLabel),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = viewModel::swapLanguages,
                enabled = !state.isTranslating
            ) {
                Icon(
                    Icons.Outlined.SwapHoriz,
                    contentDescription = stringResource(R.string.conversation_swap),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.home_target_lang),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            state.languages.forEach { lang ->
                VaVoiceChip(
                    label = lang.label,
                    selected = state.selectedTargetLang == lang.id,
                    onClick = { viewModel.onTargetLang(lang.id) }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // ── Box 1: Source ───────────────────────────────────────────
            VaSectionLabel(stringResource(R.string.translate_source_box))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, extras.hairline, MaterialTheme.shapes.medium)
                    .padding(12.dp)
            ) {
                OutlinedTextField(
                    value = state.text,
                    onValueChange = viewModel::onTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    placeholder = {
                        Text(
                            stringResource(R.string.translate_hint),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    shape = MaterialTheme.shapes.medium,
                    trailingIcon = {
                        if (state.isTranslating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
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
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { pasteFromClipboard() }) {
                        Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text(stringResource(R.string.translate_paste))
                    }
                    TextButton(onClick = viewModel::clearSource) {
                        Text(stringResource(R.string.translate_clear))
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { ensureMic() }) {
                        Icon(
                            if (state.isListening) Icons.Outlined.MicOff else Icons.Outlined.Mic,
                            contentDescription = stringResource(R.string.translate_mic),
                            tint = if (state.isListening) AmberAccent else MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onOpenCamera) {
                        Icon(
                            Icons.Outlined.CameraAlt,
                            contentDescription = stringResource(R.string.home_scan_camera),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            VaPrimaryButton(
                text = if (state.isTranslating) {
                    stringResource(R.string.home_translating)
                } else {
                    stringResource(R.string.home_translate)
                },
                onClick = {
                    hideKeyboard()
                    viewModel.translate()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isTranslating
            )

            Spacer(Modifier.height(16.dp))

            // ── Box 2: Result ───────────────────────────────────────────
            VaSectionLabel(stringResource(R.string.translate_result_box))
            val resultBg = if (dark) DeepCharcoal else SoftCream
            val resultFg = if (dark) InkOnDark else InkOnLight
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(resultBg)
                    .border(1.5.dp, AmberAccent.copy(alpha = 0.55f), MaterialTheme.shapes.medium)
                    .padding(14.dp)
            ) {
                Text(
                    text = state.translatedText.ifBlank {
                        stringResource(R.string.translate_result_placeholder)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (state.translatedText.isBlank()) {
                        resultFg.copy(alpha = 0.45f)
                    } else {
                        resultFg
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp)
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VaGhostButton(
                        text = stringResource(R.string.translate_listen),
                        onClick = viewModel::speakTranslation,
                        modifier = Modifier.weight(1f),
                        enabled = state.translatedText.isNotBlank() && !state.isSpeaking
                    )
                    IconButton(
                        onClick = { copyResult() },
                        enabled = state.translatedText.isNotBlank()
                    ) {
                        Icon(
                            Icons.Outlined.ContentCopy,
                            contentDescription = stringResource(R.string.translate_copy),
                            tint = AmberAccent
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                VaGhostButton(
                    text = stringResource(R.string.camera_send_tts),
                    onClick = {
                        val out = state.translatedText.ifBlank { state.text }.trim()
                        if (out.isNotBlank()) onSendToTts(out)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.text.isNotBlank() || state.translatedText.isNotBlank()
                )
            }

            Spacer(Modifier.height(12.dp))
            VaStatusBar(message = state.statusMessage)
            Spacer(Modifier.height(16.dp))
        }
    }
}
