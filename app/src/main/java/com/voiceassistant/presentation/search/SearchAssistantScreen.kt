package com.voiceassistant.presentation.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voiceassistant.presentation.components.VaGhostButton
import com.voiceassistant.presentation.components.VaPrimaryButton
import com.voiceassistant.presentation.components.VaScreenHeader
import com.voiceassistant.presentation.components.VaVoiceChip
import com.voiceassistant.presentation.theme.LocalVaExtra
import com.voiceassistant.presentation.theme.VaDimens
import java.text.DateFormat
import java.util.Date

@Composable
fun SearchAssistantScreen(
    onOpenSettings: () -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel()
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val extras = LocalVaExtra.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .padding(horizontal = VaDimens.screenHorizontal, vertical = VaDimens.screenTop)
    ) {
        VaScreenHeader(onOpenSettings = onOpenSettings) {
            Column {
                Text("AI Search", style = MaterialTheme.typography.displayMedium)
                Text(
                    if (state.isOnline) "Web + dự án nội bộ · đọc kết quả bằng TTS"
                    else "Offline · FTS4 trong dự án của bạn",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(12.dp))
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

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (history.isEmpty() && state.latestResponse.isBlank()) {
                item {
                    Column(modifier = Modifier.padding(vertical = 36.dp)) {
                        Text("Hỏi bất cứ điều gì", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Tìm trong dự án đã lưu, hoặc lấy ngữ cảnh web rồi để trợ lý đọc to.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            items(history.asReversed(), key = { it.id }) { item ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChatBubble(
                        text = item.prompt,
                        isUser = true,
                        meta = DateFormat.getTimeInstance(DateFormat.SHORT)
                            .format(Date(item.timestamp))
                    )
                    ChatBubble(
                        text = item.response,
                        isUser = false,
                        meta = item.source,
                        onClick = { viewModel.reusePrompt(item.prompt) }
                    )
                }
            }

            if (state.latestResponse.isNotBlank() &&
                history.none { it.response == state.latestResponse }
            ) {
                item(key = "live_response") {
                    ChatBubble(
                        text = state.latestResponse,
                        isUser = false,
                        meta = state.latestSource.ifBlank { null }
                    )
                }
            }
        }

        Text(
            state.status,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = state.prompt,
            onValueChange = viewModel::onPromptChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Prompt tìm kiếm…") },
            shape = MaterialTheme.shapes.large,
            maxLines = 4,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = extras.hairline,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                cursorColor = MaterialTheme.colorScheme.primary
            )
        )
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            VaPrimaryButton(
                text = if (state.isSearching) "…" else "Gửi",
                onClick = viewModel::submit,
                modifier = Modifier.weight(1f),
                enabled = !state.isSearching
            )
            Spacer(Modifier.width(8.dp))
            VaGhostButton(
                text = if (state.isSpeaking) "Dừng" else "Đọc",
                onClick = {
                    if (state.isSpeaking) viewModel.stopSpeaking() else viewModel.speakLatest()
                },
                modifier = Modifier.weight(1f)
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = viewModel::clearHistory) {
                Text("Xóa lịch sử", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ChatBubble(
    text: String,
    isUser: Boolean,
    meta: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    if (text.isBlank()) return
    val extras = LocalVaExtra.current
    val bg = if (isUser) extras.bubbleUser else extras.bubbleAssistant
    val fg = if (isUser) extras.onBubbleUser else extras.onBubbleAssistant
    val shape = if (isUser) {
        RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
    } else {
        RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(shape)
                .background(bg)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = fg)
        }
        if (!meta.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                meta,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}
