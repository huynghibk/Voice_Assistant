package com.voiceassistant.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.voiceassistant.presentation.theme.LocalVaExtra

/**
 * Sentence-follow / karaoke reader: highlights the active sentence in amber and auto-scrolls.
 */
@Composable
fun KaraokeTextPanel(
    sentences: List<String>,
    activeIndex: Int,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val extras = LocalVaExtra.current

    LaunchedEffect(activeIndex, sentences.size) {
        if (activeIndex in sentences.indices) {
            runCatching { listState.animateScrollToItem(activeIndex) }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, extras.hairline, MaterialTheme.shapes.large),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(sentences, key = { index, _ -> index }) { index, sentence ->
            val active = index == activeIndex
            Text(
                text = sentence,
                style = MaterialTheme.typography.bodyLarge,
                color = if (active) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .background(if (active) extras.amberMuted else MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }
    }
}
