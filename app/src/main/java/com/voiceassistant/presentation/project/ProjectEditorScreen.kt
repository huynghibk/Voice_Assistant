package com.voiceassistant.presentation.project

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voiceassistant.engine.audio.ExportFormat
import com.voiceassistant.engine.audio.ExportQuality
import com.voiceassistant.presentation.components.KaraokeTextPanel
import com.voiceassistant.presentation.components.VaGhostButton
import com.voiceassistant.presentation.components.VaPrimaryButton
import com.voiceassistant.presentation.components.VaSectionLabel
import com.voiceassistant.presentation.components.VaStatusBar
import com.voiceassistant.presentation.components.VaVoiceChip
import com.voiceassistant.presentation.theme.LocalVaExtra
import com.voiceassistant.presentation.theme.VaDimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectEditorScreen(
    @Suppress("UNUSED_PARAMETER") projectId: String,
    onBack: () -> Unit,
    onOpenTimeline: () -> Unit,
    viewModel: ProjectEditorViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val extras = LocalVaExtra.current

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::importDocument) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Soạn thảo", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = VaDimens.screenHorizontal)
                .verticalScroll(rememberScrollState())
        ) {
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::onTitleChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Tiêu đề") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = fieldColors(extras.hairline)
            )
            Spacer(Modifier.height(12.dp))

            VaSectionLabel("Giọng")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(VaDimens.chipGap)) {
                items(state.voices, key = { it.id }) { voice ->
                    VaVoiceChip(
                        label = voice.name,
                        selected = voice.id == state.selectedVoiceId,
                        onClick = { viewModel.onVoiceSelected(voice.id) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            if (state.isPlaying && state.karaokeSentences.isNotEmpty()) {
                VaSectionLabel("Đang đọc")
                KaraokeTextPanel(
                    sentences = state.karaokeSentences,
                    activeIndex = state.karaokeIndex,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                )
            } else {
                OutlinedTextField(
                    value = state.content,
                    onValueChange = viewModel::onContentChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    placeholder = { Text("Nội dung văn bản…") },
                    shape = MaterialTheme.shapes.large,
                    colors = fieldColors(extras.hairline)
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VaGhostButton(
                    text = "Import",
                    onClick = {
                        importLauncher.launch(
                            arrayOf(
                                "text/*",
                                "application/pdf",
                                "application/epub+zip",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                "*/*"
                            )
                        )
                    },
                    modifier = Modifier.width(110.dp)
                )
                VaGhostButton(
                    text = "Dịch",
                    onClick = viewModel::translate,
                    enabled = !state.busy,
                    modifier = Modifier.width(100.dp)
                )
                VaGhostButton(
                    text = "Timeline",
                    onClick = onOpenTimeline,
                    modifier = Modifier.width(120.dp)
                )
            }

            Spacer(Modifier.height(14.dp))
            VaSectionLabel("Dịch offline")
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                state.languages.forEach { lang ->
                    VaVoiceChip(
                        label = "Từ ${lang.label}",
                        selected = state.sourceLang == lang.id,
                        onClick = { viewModel.onSourceLang(lang.id) }
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                state.languages.forEach { lang ->
                    VaVoiceChip(
                        label = "Sang ${lang.label}",
                        selected = state.targetLang == lang.id,
                        onClick = { viewModel.onTargetLang(lang.id) }
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            VaSectionLabel("Xuất bản")
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ExportFormat.entries.forEach { fmt ->
                    VaVoiceChip(
                        label = fmt.name,
                        selected = state.exportFormat == fmt,
                        onClick = { viewModel.onExportFormat(fmt) }
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ExportQuality.entries.forEach { q ->
                    VaVoiceChip(
                        label = q.name,
                        selected = state.exportQuality == q,
                        onClick = { viewModel.onExportQuality(q) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            VaSectionLabel("Tốc độ")
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
            VaStatusBar(state.statusMessage)

            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                VaGhostButton(
                    text = if (state.saved) "Đã lưu" else "Lưu",
                    onClick = viewModel::save,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                VaPrimaryButton(
                    text = if (state.isPlaying) "Dừng" else "Phát",
                    onClick = { if (state.isPlaying) viewModel.stop() else viewModel.play() },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                VaGhostButton(
                    text = "Xuất file",
                    onClick = viewModel::exportAudio,
                    modifier = Modifier.weight(1f),
                    enabled = !state.busy && !state.isPlaying
                )
                Spacer(Modifier.width(8.dp))
                VaGhostButton(
                    text = "→ Timeline",
                    onClick = viewModel::sendLastAudioToTimeline,
                    modifier = Modifier.weight(1f),
                    enabled = !state.isPlaying
                )
            }
            Spacer(Modifier.height(8.dp))
            VaGhostButton(
                text = "Chia sẻ (FileProvider)",
                onClick = viewModel::shareLastExport,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isPlaying && state.lastExportPath != null
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun fieldColors(hairline: androidx.compose.ui.graphics.Color) =
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = hairline,
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        cursorColor = MaterialTheme.colorScheme.primary
    )
