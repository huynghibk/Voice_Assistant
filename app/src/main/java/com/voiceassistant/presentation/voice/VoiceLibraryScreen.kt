package com.voiceassistant.presentation.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voiceassistant.R
import com.voiceassistant.domain.model.VoiceModel
import com.voiceassistant.presentation.components.VaEmptyState
import com.voiceassistant.presentation.components.VaPrimaryButton
import com.voiceassistant.presentation.components.VaScreenHeader
import com.voiceassistant.presentation.components.VaSectionLabel
import com.voiceassistant.presentation.components.VaTag
import com.voiceassistant.presentation.theme.LocalVaExtra
import com.voiceassistant.presentation.theme.VaDimens

@Composable
fun VoiceLibraryScreen(
    onCloneVoice: () -> Unit,
    onOpenSettings: () -> Unit = {},
    viewModel: VoiceLibraryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val extras = LocalVaExtra.current
    var renameTarget by remember { mutableStateOf<VoiceModel?>(null) }
    var deleteTarget by remember { mutableStateOf<VoiceModel?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = VaDimens.screenHorizontal, vertical = VaDimens.screenTop)
    ) {
        VaScreenHeader(onOpenSettings = onOpenSettings) {
            Column {
                Text("Thư viện Giọng", style = MaterialTheme.typography.displayMedium)
                Text(
                    "Preset Piper và giọng đã clone",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        VaPrimaryButton(
            text = "Clone giọng mới",
            onClick = onCloneVoice,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        LazyColumn(
            contentPadding = PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                VaSectionLabel(stringResource(R.string.voice_library_clones_section))
            }
            if (state.clones.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.voice_library_no_clones),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                items(state.clones, key = { it.id }) { voice ->
                    CloneVoiceRow(
                        voice = voice,
                        hairline = extras.hairline,
                        onRename = { renameTarget = voice },
                        onDelete = { deleteTarget = voice },
                        onSetDefault = { viewModel.setDefault(voice.id) }
                    )
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                VaSectionLabel(stringResource(R.string.voice_library_presets_section))
            }
            if (state.presets.isEmpty()) {
                item {
                    VaEmptyState(
                        title = "Chưa có giọng",
                        body = "Seed preset sẽ xuất hiện sau lần mở đầu tiên."
                    )
                }
            } else {
                items(state.presets, key = { it.id }) { voice ->
                    PresetVoiceRow(
                        voice = voice,
                        hairline = extras.hairline,
                        onSetDefault = { viewModel.setDefault(voice.id) }
                    )
                }
            }
        }
    }

    renameTarget?.let { voice ->
        RenameCloneDialog(
            initialName = voice.name,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                viewModel.rename(voice.id, newName)
                renameTarget = null
            }
        )
    }

    deleteTarget?.let { voice ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.voice_library_delete_title)) },
            text = {
                Text(stringResource(R.string.voice_library_delete_body, voice.name))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteClone(voice.id)
                        deleteTarget = null
                    }
                ) {
                    Text(stringResource(R.string.voice_library_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.voice_library_cancel))
                }
            }
        )
    }
}

@Composable
private fun CloneVoiceRow(
    voice: VoiceModel,
    hairline: androidx.compose.ui.graphics.Color,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onSetDefault: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, hairline, MaterialTheme.shapes.medium)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(voice.name, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    VaTag("clone", emphasized = true)
                    voice.tags.filter { it in listOf("vi", "en", "zh", "user") }.take(2).forEach { VaTag(it) }
                    if (voice.isDefault) VaTag("mặc định", emphasized = true)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onRename) {
                Text(stringResource(R.string.voice_library_rename))
            }
            TextButton(onClick = onDelete) {
                Text(
                    stringResource(R.string.voice_library_delete),
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (!voice.isDefault) {
                TextButton(onClick = onSetDefault) {
                    Text("Đặt mặc định")
                }
            }
        }
    }
}

@Composable
private fun PresetVoiceRow(
    voice: VoiceModel,
    hairline: androidx.compose.ui.graphics.Color,
    onSetDefault: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, hairline, MaterialTheme.shapes.medium)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(voice.name, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    voice.tags.take(3).forEach { VaTag(it) }
                    if (voice.isDefault) VaTag("mặc định", emphasized = true)
                }
            }
            if (!voice.isDefault) {
                TextButton(onClick = onSetDefault) {
                    Text("Đặt mặc định")
                }
            }
        }
    }
}

@Composable
private fun RenameCloneDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.voice_library_rename_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }) {
                Text(stringResource(R.string.voice_library_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.voice_library_cancel))
            }
        }
    )
}
