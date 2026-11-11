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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voiceassistant.presentation.components.VaEmptyState
import com.voiceassistant.presentation.components.VaPrimaryButton
import com.voiceassistant.presentation.components.VaTag
import com.voiceassistant.presentation.theme.LocalVaExtra
import com.voiceassistant.presentation.theme.VaDimens

@Composable
fun VoiceLibraryScreen(
    onCloneVoice: () -> Unit,
    viewModel: VoiceLibraryViewModel = hiltViewModel()
) {
    val voices by viewModel.voices.collectAsStateWithLifecycle()
    val extras = LocalVaExtra.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = VaDimens.screenHorizontal, vertical = VaDimens.screenTop)
    ) {
        Text("Thư viện Giọng", style = MaterialTheme.typography.displayMedium)
        Text(
            "Preset Piper và giọng đã clone",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        VaPrimaryButton(
            text = "Clone giọng mới",
            onClick = onCloneVoice,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        if (voices.isEmpty()) {
            VaEmptyState(
                title = "Chưa có giọng",
                body = "Seed preset sẽ xuất hiện sau lần mở đầu tiên."
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(voices, key = { it.id }) { voice ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, extras.hairline, MaterialTheme.shapes.medium)
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(voice.name, style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    voice.tags.take(3).forEach { VaTag(it) }
                                    if (!voice.isPreset) VaTag("converted", emphasized = true)
                                    if (voice.isDefault) VaTag("mặc định", emphasized = true)
                                }
                            }
                            if (!voice.isDefault) {
                                TextButton(onClick = { viewModel.setDefault(voice.id) }) {
                                    Text("Đặt mặc định")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
