package com.voiceassistant.presentation.timeline

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voiceassistant.domain.model.AudioClip
import com.voiceassistant.engine.audio.ExportFormat
import com.voiceassistant.engine.audio.ExportQuality
import com.voiceassistant.presentation.components.VaGhostButton
import com.voiceassistant.presentation.components.VaPrimaryButton
import com.voiceassistant.presentation.components.VaSectionLabel
import com.voiceassistant.presentation.components.VaStatusBar
import com.voiceassistant.presentation.components.VaSurfacePanel
import com.voiceassistant.presentation.components.VaVoiceChip
import com.voiceassistant.presentation.theme.LocalVaExtra
import com.voiceassistant.presentation.theme.VaDimens
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    @Suppress("UNUSED_PARAMETER") onBack: () -> Unit = {},
    embedded: Boolean = false,
    viewModel: TimelineViewModel = hiltViewModel()
) {
    val project by viewModel.project.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val selected = project.clips.firstOrNull { it.id == ui.selectedClipId }
    val extras = LocalVaExtra.current

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.importAudio(it) } }

    var playheadMs by remember { mutableFloatStateOf(0f) }
    val duration = max(project.durationMs(), 1L).toFloat()
    val pxPerMs = 0.08f

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (!embedded) {
                TopAppBar(
                    title = {
                        Column {
                            Text("Studio Timeline", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "AudioClip → một filtergraph khi Render",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = VaDimens.screenHorizontal)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VaGhostButton("Thêm audio", { importLauncher.launch("audio/*") }, Modifier.width(130.dp))
                VaGhostButton("Xóa hết", viewModel::clear, Modifier.width(110.dp))
                VaPrimaryButton(
                    text = if (ui.isRendering) "…" else "Render",
                    onClick = viewModel::render,
                    enabled = !ui.isRendering && project.clips.isNotEmpty(),
                    modifier = Modifier.width(120.dp)
                )
                VaGhostButton(
                    text = "Chia sẻ",
                    onClick = viewModel::shareLastOutput,
                    enabled = ui.lastOutput != null,
                    modifier = Modifier.width(110.dp)
                )
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "Playhead ${playheadMs.toInt()} ms",
                style = MaterialTheme.typography.labelLarge
            )
            Slider(
                value = playheadMs.coerceIn(0f, duration),
                onValueChange = { playheadMs = it },
                valueRange = 0f..duration,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )

            TrackLane("Track 0 — Voice", project.clips.filter { it.trackIndex == 0 }, ui.selectedClipId, pxPerMs, viewModel::selectClip)
            Spacer(Modifier.height(10.dp))
            TrackLane("Track 1 — BGM", project.clips.filter { it.trackIndex == 1 }, ui.selectedClipId, pxPerMs, viewModel::selectClip)

            if (selected != null) {
                Spacer(Modifier.height(16.dp))
                ClipInspector(
                    clip = selected,
                    onDelete = viewModel::deleteSelected,
                    onSplit = { viewModel.splitAtPlayhead(playheadMs.toLong()) },
                    onChange = { vol, fadeIn, fadeOut, speed, pitch ->
                        viewModel.updateSelected(vol, fadeIn, fadeOut, speed, pitch)
                    }
                )
            }

            Spacer(Modifier.height(16.dp))
            VaSectionLabel("Export")
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ExportFormat.entries.forEach { fmt ->
                    VaVoiceChip(fmt.name, ui.exportFormat == fmt, { viewModel.setFormat(fmt) })
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ExportQuality.entries.forEach { q ->
                    VaVoiceChip(q.name, ui.exportQuality == q, { viewModel.setQuality(q) })
                }
            }

            Spacer(Modifier.height(12.dp))
            VaStatusBar(ui.status)
            ui.lastOutput?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun TrackLane(
    title: String,
    clips: List<AudioClip>,
    selectedId: String?,
    pxPerMs: Float,
    onSelect: (String) -> Unit
) {
    val extras = LocalVaExtra.current
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, extras.hairline, MaterialTheme.shapes.medium)
                .horizontalScroll(rememberScrollState())
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (clips.isEmpty()) {
                Text("Trống — thêm audio hoặc gửi từ Project", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                clips.forEach { clip ->
                    val dur = ((clip.trimEndMs ?: 3000L) - clip.trimStartMs).coerceAtLeast(500L)
                    val width = (dur * pxPerMs).coerceAtLeast(56f).dp
                    val selected = clip.id == selectedId
                    Box(
                        modifier = Modifier
                            .width(width)
                            .height(52.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(LocalVaExtra.current.amberMuted)
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else extras.hairline,
                                shape = MaterialTheme.shapes.small
                            )
                            .clickable { onSelect(clip.id) }
                            .padding(8.dp)
                    ) {
                        Text(
                            clip.sourceFilePath.substringAfterLast('/').substringAfterLast('\\'),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 2
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClipInspector(
    clip: AudioClip,
    onDelete: () -> Unit,
    onSplit: () -> Unit,
    onChange: (Float, Long, Long, Float, Float) -> Unit
) {
    var volume by remember(clip.id) { mutableFloatStateOf(clip.volume) }
    var speed by remember(clip.id) { mutableFloatStateOf(clip.speed) }
    var pitch by remember(clip.id) { mutableFloatStateOf(clip.pitch) }
    var fadeIn by remember(clip.id) { mutableFloatStateOf(clip.fadeInMs.toFloat()) }
    var fadeOut by remember(clip.id) { mutableFloatStateOf(clip.fadeOutMs.toFloat()) }

    VaSurfacePanel {
        Text("Clip · offset ${clip.startOffsetInTimelineMs}ms", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        SliderLabeled("Volume", volume, 0f..2f) {
            volume = it
            onChange(volume, fadeIn.toLong(), fadeOut.toLong(), speed, pitch)
        }
        SliderLabeled("Speed", speed, 0.5f..2f) {
            speed = it
            onChange(volume, fadeIn.toLong(), fadeOut.toLong(), speed, pitch)
        }
        SliderLabeled("Pitch", pitch, 0.7f..1.4f) {
            pitch = it
            onChange(volume, fadeIn.toLong(), fadeOut.toLong(), speed, pitch)
        }
        SliderLabeled("Fade in ms", fadeIn, 0f..3000f) {
            fadeIn = it
            onChange(volume, fadeIn.toLong(), fadeOut.toLong(), speed, pitch)
        }
        SliderLabeled("Fade out ms", fadeOut, 0f..3000f) {
            fadeOut = it
            onChange(volume, fadeIn.toLong(), fadeOut.toLong(), speed, pitch)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VaGhostButton("Split", onSplit, Modifier.weight(1f))
            VaGhostButton("Xóa", onDelete, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SliderLabeled(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Text("$label  ${"%.2f".format(value)}", style = MaterialTheme.typography.labelLarge)
    Slider(
        value = value,
        onValueChange = onChange,
        valueRange = range,
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary
        )
    )
}
