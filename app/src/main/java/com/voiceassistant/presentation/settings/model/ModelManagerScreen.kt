package com.voiceassistant.presentation.settings.model

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voiceassistant.R
import com.voiceassistant.domain.resource.MlKitLanguagePack
import com.voiceassistant.domain.resource.MlKitPairPack
import com.voiceassistant.domain.resource.PiperVoicePack
import com.voiceassistant.presentation.components.VaSectionLabel
import com.voiceassistant.presentation.components.VaStatusBar
import com.voiceassistant.presentation.theme.LocalVaExtra
import com.voiceassistant.presentation.theme.VaDimens

@Composable
fun ModelManagerScreen(
    onBack: () -> Unit,
    viewModel: ModelManagerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val extras = LocalVaExtra.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = VaDimens.screenHorizontal, vertical = VaDimens.screenTop)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.camera_back)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.model_manager_title),
                    style = MaterialTheme.typography.displayMedium
                )
                Text(
                    stringResource(R.string.model_manager_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        if (state.statusMessage.isNotBlank()) {
            VaStatusBar(message = state.statusMessage)
            Spacer(Modifier.height(8.dp))
        }

        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            VaSectionLabel(stringResource(R.string.model_piper_section))
            Text(
                stringResource(R.string.model_piper_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            state.piperPacks.forEach { pack ->
                PiperPackRow(pack = pack)
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(16.dp))
            VaSectionLabel(stringResource(R.string.model_mlkit_pairs_section))
            state.recommendedPairs.forEach { pair ->
                PairPackRow(
                    pair = pair,
                    busy = state.busyPackId == "${pair.sourceCode}-${pair.targetCode}",
                    onDownload = {
                        viewModel.downloadPair(pair.sourceCode, pair.targetCode)
                    }
                )
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(16.dp))
            VaSectionLabel(stringResource(R.string.model_mlkit_section))
            state.mlKitLanguages.forEach { pack ->
                MlKitPackRow(
                    pack = pack,
                    busy = state.busyPackId == pack.languageCode,
                    onDownload = { viewModel.downloadLanguage(pack.languageCode) },
                    onDelete = { viewModel.deleteLanguage(pack.languageCode) }
                )
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PiperPackRow(pack: PiperVoicePack) {
    val extras = LocalVaExtra.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, extras.hairline, MaterialTheme.shapes.medium)
            .padding(14.dp)
    ) {
        Text(pack.displayName, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(
                R.string.model_piper_meta,
                pack.qualityLabel,
                pack.estimatedSizeMb
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (pack.isReady) {
                stringResource(R.string.model_status_ready)
            } else {
                stringResource(R.string.model_status_system_tts)
            },
            style = MaterialTheme.typography.labelLarge,
            color = if (pack.isReady) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun PairPackRow(
    pair: MlKitPairPack,
    busy: Boolean,
    onDownload: () -> Unit
) {
    val extras = LocalVaExtra.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, extras.hairline, MaterialTheme.shapes.medium)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(pair.displayLabel, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.model_size_mb, pair.estimatedSizeMb),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (pair.isInstalled) {
                    stringResource(R.string.model_status_installed)
                } else {
                    stringResource(R.string.model_status_not_installed)
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        } else if (!pair.isInstalled) {
            TextButton(onClick = onDownload) {
                Text(stringResource(R.string.model_download))
            }
        }
    }
}

@Composable
private fun MlKitPackRow(
    pack: MlKitLanguagePack,
    busy: Boolean,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    val extras = LocalVaExtra.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, extras.hairline, MaterialTheme.shapes.medium)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(pack.displayName, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.model_size_mb, pack.estimatedSizeMb),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (pack.isInstalled) {
                    stringResource(R.string.model_status_installed)
                } else {
                    stringResource(R.string.model_status_not_installed)
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.width(8.dp))
        when {
            busy -> CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            pack.isInstalled -> TextButton(onClick = onDelete) {
                Text(stringResource(R.string.model_delete))
            }
            else -> TextButton(onClick = onDownload) {
                Text(stringResource(R.string.model_download))
            }
        }
    }
}
