package com.voiceassistant.presentation.translate

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Forum
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import com.voiceassistant.presentation.theme.LocalVaExtra
import com.voiceassistant.presentation.theme.VaDimens

/**
 * Dedicated Translate hub: text translation + camera OCR scan entry.
 */
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

    fun hideKeyboard() {
        focusManager.clearFocus()
        keyboardController?.hide()
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
                IconButton(onClick = onOpenCamera) {
                    Icon(
                        Icons.Outlined.CameraAlt,
                        contentDescription = stringResource(R.string.home_scan_camera),
                        tint = MaterialTheme.colorScheme.primary
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
        VaSectionLabel(stringResource(R.string.home_translate_section))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
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
            IconButton(onClick = viewModel::swapLanguages) {
                Icon(
                    Icons.Outlined.SwapHoriz,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        if (state.selectedSourceLang == LanguageIdentifier.AUTO && state.detectedLabel.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.lang_detected, state.detectedLabel),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(6.dp))
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
        OutlinedTextField(
            value = state.text,
            onValueChange = viewModel::onTextChange,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            placeholder = {
                Text(
                    stringResource(R.string.translate_hint),
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            textStyle = MaterialTheme.typography.bodyLarge,
            shape = MaterialTheme.shapes.large,
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

        Spacer(Modifier.height(12.dp))
        VaStatusBar(message = state.statusMessage)

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
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            VaGhostButton(
                text = stringResource(R.string.camera_capture),
                onClick = onOpenCamera,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.size(8.dp))
            VaGhostButton(
                text = stringResource(R.string.camera_send_tts),
                onClick = {
                    val out = state.translatedText.ifBlank { state.text }.trim()
                    if (out.isNotBlank()) onSendToTts(out)
                },
                modifier = Modifier.weight(1f),
                enabled = state.text.isNotBlank() || state.translatedText.isNotBlank()
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}
