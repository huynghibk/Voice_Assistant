package com.voiceassistant.presentation.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voiceassistant.engine.audio.ExportFormat
import com.voiceassistant.engine.audio.ExportQuality
import com.voiceassistant.presentation.components.VaBrandMark
import com.voiceassistant.presentation.components.VaGhostButton
import com.voiceassistant.presentation.components.VaPrimaryButton
import com.voiceassistant.presentation.components.VaSectionLabel
import com.voiceassistant.presentation.components.VaStatusBar
import com.voiceassistant.presentation.components.VaVoiceChip
import com.voiceassistant.presentation.theme.LocalVaExtra
import com.voiceassistant.presentation.theme.VaDimens

@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val extras = LocalVaExtra.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = VaDimens.screenHorizontal, vertical = VaDimens.screenTop)
    ) {
        VaBrandMark()
        Spacer(Modifier.height(6.dp))
        Text(
            "Quick TTS · nghe ngay từng câu",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            state.routeLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(VaDimens.sectionGap))
        VaSectionLabel("Giọng đọc")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(VaDimens.chipGap)) {
            items(state.voices, key = { it.id }) { voice ->
                VaVoiceChip(
                    label = voice.name,
                    subtitle = if (voice.isPreset) "preset" else "cloned",
                    selected = voice.id == state.selectedVoiceId,
                    onClick = { viewModel.onVoiceSelected(voice.id) }
                )
            }
        }

        Spacer(Modifier.height(VaDimens.sectionGap))
        OutlinedTextField(
            value = state.text,
            onValueChange = viewModel::onTextChange,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            placeholder = {
                Text(
                    "Dán văn bản dài — app chia câu, stream PCM, không chờ cả đoạn…",
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

        Spacer(Modifier.height(12.dp))
        VaSectionLabel("Xuất bản")
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
                    VaPrimaryButton(text = "Dừng", onClick = viewModel::stop, modifier = Modifier.fillMaxWidth())
                } else {
                    VaPrimaryButton(text = "Phát", onClick = viewModel::play, modifier = Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.width(8.dp))
            VaGhostButton(
                text = "Xuất",
                onClick = viewModel::exportAudio,
                modifier = Modifier.weight(1f),
                enabled = !state.isPlaying
            )
            Spacer(Modifier.width(8.dp))
            VaGhostButton(
                text = "Chia sẻ",
                onClick = viewModel::shareLastExport,
                modifier = Modifier.weight(1f),
                enabled = !state.isPlaying
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}
