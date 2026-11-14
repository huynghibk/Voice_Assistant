package com.voiceassistant.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voiceassistant.R
import com.voiceassistant.domain.model.AppLanguage
import com.voiceassistant.domain.tts.TtsBackend
import com.voiceassistant.presentation.components.VaBrandMark
import com.voiceassistant.presentation.components.VaGhostButton
import com.voiceassistant.presentation.components.VaPrimaryButton
import com.voiceassistant.presentation.components.VaSectionLabel
import com.voiceassistant.presentation.components.VaSurfacePanel
import com.voiceassistant.presentation.theme.LocalVaExtra
import com.voiceassistant.presentation.theme.VaDimens

@Composable
fun SettingsScreen(
    onOpenManual: () -> Unit = {},
    onOpenModelManager: () -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    var showHelp by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    val language by viewModel.language.collectAsStateWithLifecycle()
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val extras = LocalVaExtra.current
    val versionName = remember {
        runCatching {
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName
        }.getOrNull() ?: "1.0.0"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = VaDimens.screenHorizontal, vertical = VaDimens.screenTop)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.camera_back)
                )
            }
            VaBrandMark()
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(VaDimens.sectionGap))
        VaSectionLabel(stringResource(R.string.settings_section))
        VaSurfacePanel {
            SettingsRow(
                icon = { Icon(Icons.Outlined.Language, contentDescription = null) },
                title = stringResource(R.string.settings_language),
                subtitle = stringResource(R.string.settings_language_subtitle)
            )
            Spacer(Modifier.height(12.dp))
            LanguageChipRow(
                languages = viewModel.supportedLanguages,
                selected = language,
                onSelect = viewModel::onLanguageSelected
            )
            Spacer(Modifier.height(14.dp))
            SettingsRow(
                icon = { Icon(Icons.Outlined.VolumeUp, contentDescription = null) },
                title = stringResource(R.string.settings_voice),
                subtitle = stringResource(R.string.settings_voice_subtitle)
            )
            Spacer(Modifier.height(12.dp))
            SettingsRow(
                icon = { Icon(Icons.Outlined.VolumeUp, contentDescription = null) },
                title = stringResource(R.string.settings_tts_engine_title),
                subtitle = stringResource(R.string.settings_tts_engine_subtitle)
            )
            Spacer(Modifier.height(12.dp))
            TtsBackendChipRow(
                backends = viewModel.supportedTtsBackends,
                selected = ui.ttsBackend,
                labelFor = { viewModel.ttsBackendLabelRes(it) },
                onSelect = viewModel::onTtsBackendSelected
            )
            Spacer(Modifier.height(14.dp))
            SettingsRow(
                icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                title = stringResource(R.string.settings_export),
                subtitle = stringResource(R.string.settings_export_subtitle)
            )
            Spacer(Modifier.height(14.dp))
            SettingsNavRow(
                icon = { Icon(Icons.Outlined.CloudDownload, contentDescription = null) },
                title = stringResource(R.string.settings_model_manager),
                subtitle = stringResource(R.string.settings_model_manager_subtitle),
                onClick = onOpenModelManager
            )
        }

        Spacer(Modifier.height(16.dp))
        VaSectionLabel(stringResource(R.string.settings_ai_section))
        VaSurfacePanel {
            SettingsRow(
                icon = { Icon(Icons.Outlined.VpnKey, contentDescription = null) },
                title = stringResource(R.string.settings_gemini_title),
                subtitle = when {
                    ui.keyBundledInApp -> stringResource(R.string.settings_gemini_subtitle)
                    ui.hasStoredKey -> stringResource(R.string.settings_gemini_saved)
                    else -> stringResource(R.string.settings_gemini_subtitle)
                }
            )
            if (ui.hasStoredKey && ui.storedKeyMask.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = when {
                        ui.keyBundledInApp ->
                            stringResource(R.string.settings_gemini_key_bundled, ui.storedKeyMask)
                        ui.keyFromBuildConfig ->
                            stringResource(R.string.settings_gemini_key_build, ui.storedKeyMask)
                        else ->
                            stringResource(R.string.settings_gemini_key_active, ui.storedKeyMask)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = ui.apiKeyInput,
                onValueChange = viewModel::onApiKeyChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        if (ui.hasStoredKey) {
                            stringResource(R.string.settings_gemini_replace_hint)
                        } else {
                            stringResource(R.string.settings_gemini_hint)
                        }
                    )
                },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = extras.hairline
                )
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.settings_gemini_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable {
                    uriHandler.openUri("https://aistudio.google.com/apikey")
                }
            )
            if (ui.apiKeyMessage.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    ui.apiKeyMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                VaPrimaryButton(
                    text = stringResource(R.string.settings_gemini_save),
                    onClick = viewModel::saveApiKey,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                VaGhostButton(
                    text = stringResource(R.string.settings_gemini_clear),
                    onClick = viewModel::clearApiKey,
                    modifier = Modifier.weight(1f),
                    enabled = ui.hasStoredKey || ui.apiKeyInput.isNotBlank()
                )
            }
            Spacer(Modifier.height(16.dp))
            SettingsRow(
                icon = { Icon(Icons.Outlined.VpnKey, contentDescription = null) },
                title = stringResource(R.string.settings_gcloud_tts_title),
                subtitle = stringResource(R.string.settings_gcloud_tts_subtitle)
            )
            if (ui.hasGoogleCloudTtsKey && ui.googleCloudTtsMask.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    ui.googleCloudTtsMask,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = ui.googleCloudTtsInput,
                onValueChange = viewModel::onGoogleCloudTtsKeyChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.settings_gcloud_tts_hint)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = extras.hairline
                )
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.settings_gcloud_tts_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            SettingsRow(
                icon = { Icon(Icons.Outlined.VpnKey, contentDescription = null) },
                title = stringResource(R.string.settings_elevenlabs_title),
                subtitle = stringResource(R.string.settings_elevenlabs_subtitle)
            )
            if (ui.hasElevenLabsKey && ui.elevenLabsMask.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    ui.elevenLabsMask,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = ui.elevenLabsInput,
                onValueChange = viewModel::onElevenLabsKeyChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.settings_elevenlabs_hint)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = extras.hairline
                )
            )
            if (ui.ttsKeyMessage.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    ui.ttsKeyMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                VaPrimaryButton(
                    text = stringResource(R.string.settings_gemini_save),
                    onClick = {
                        if (ui.googleCloudTtsInput.isNotBlank()) viewModel.saveGoogleCloudTtsKey()
                        if (ui.elevenLabsInput.isNotBlank()) viewModel.saveElevenLabsKey()
                    },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                VaGhostButton(
                    text = stringResource(R.string.settings_gemini_clear),
                    onClick = {
                        viewModel.clearGoogleCloudTtsKey()
                        viewModel.clearElevenLabsKey()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = ui.hasGoogleCloudTtsKey || ui.hasElevenLabsKey ||
                        ui.googleCloudTtsInput.isNotBlank() || ui.elevenLabsInput.isNotBlank()
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        VaSectionLabel(stringResource(R.string.settings_help_section))
        VaSurfacePanel {
            SettingsNavRow(
                icon = { Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null) },
                title = stringResource(R.string.settings_manual),
                subtitle = stringResource(R.string.settings_manual_subtitle),
                onClick = onOpenManual
            )
            Spacer(Modifier.height(14.dp))
            SettingsNavRow(
                icon = { Icon(Icons.Outlined.HelpOutline, contentDescription = null) },
                title = stringResource(R.string.settings_help),
                subtitle = stringResource(R.string.settings_help_subtitle),
                onClick = { showHelp = !showHelp }
            )
            if (showHelp) {
                Spacer(Modifier.height(12.dp))
                HelpContent()
            }
            Spacer(Modifier.height(14.dp))
            SettingsNavRow(
                icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                title = stringResource(R.string.settings_about),
                subtitle = stringResource(R.string.settings_about_version, versionName),
                onClick = { showAbout = !showAbout }
            )
            if (showAbout) {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.settings_about_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun TtsBackendChipRow(
    backends: List<TtsBackend>,
    selected: TtsBackend,
    labelFor: (TtsBackend) -> Int,
    onSelect: (TtsBackend) -> Unit
) {
    val extras = LocalVaExtra.current
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        backends.forEach { backend ->
            val isSelected = backend == selected
            Text(
                text = stringResource(labelFor(backend)),
                style = MaterialTheme.typography.labelLarge,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(
                        if (isSelected) extras.amberMuted
                        else MaterialTheme.colorScheme.surface
                    )
                    .border(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else extras.hairline,
                        MaterialTheme.shapes.small
                    )
                    .clickable { onSelect(backend) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun LanguageChipRow(
    languages: List<AppLanguage>,
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit
) {
    val extras = LocalVaExtra.current
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        languages.forEach { lang ->
            val isSelected = lang == selected
            Text(
                text = lang.nativeLabel,
                style = MaterialTheme.typography.labelLarge,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(
                        if (isSelected) extras.amberMuted
                        else MaterialTheme.colorScheme.surface
                    )
                    .border(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else extras.hairline,
                        MaterialTheme.shapes.small
                    )
                    .clickable { onSelect(lang) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun HelpContent() {
    Column {
        HelpBullet(stringResource(R.string.settings_help_1))
        HelpBullet(stringResource(R.string.settings_help_2))
        HelpBullet(stringResource(R.string.settings_help_3))
        HelpBullet(stringResource(R.string.settings_help_4))
        HelpBullet(stringResource(R.string.settings_help_5))
    }
}

@Composable
private fun HelpBullet(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun SettingsRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String
) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        icon()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsNavRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
    ) {
        icon()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
