package com.voiceassistant.presentation.settings.manual

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voiceassistant.R
import com.voiceassistant.presentation.components.VaGhostButton
import com.voiceassistant.presentation.components.VaPrimaryButton
import com.voiceassistant.presentation.theme.LocalVaExtra
import com.voiceassistant.presentation.theme.VaDimens

/**
 * Book-like user manual reader: cover/TOC → chapter pages with prev/next.
 */
@Composable
fun UserManualScreen(
    onBack: () -> Unit,
    viewModel: UserManualViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var mode by remember { mutableStateOf(ManualMode.Cover) }
    var chapterIndex by remember { mutableIntStateOf(0) }
    val scrollState = rememberScrollState()

    LaunchedEffect(chapterIndex, mode) {
        scrollState.scrollTo(0)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ManualTopBar(
            title = when (mode) {
                ManualMode.Cover -> stringResource(R.string.manual_title)
                ManualMode.Toc -> stringResource(R.string.manual_toc)
                ManualMode.Reading -> state.chapters.getOrNull(chapterIndex)?.title
                    ?: stringResource(R.string.manual_title)
            },
            onBack = {
                when (mode) {
                    ManualMode.Cover -> onBack()
                    ManualMode.Toc -> mode = ManualMode.Cover
                    ManualMode.Reading -> mode = ManualMode.Toc
                }
            }
        )

        AnimatedContent(
            targetState = mode,
            transitionSpec = {
                (slideInHorizontally { it / 4 } + fadeIn()) togetherWith
                    (slideOutHorizontally { -it / 4 } + fadeOut())
            },
            label = "manualMode",
            modifier = Modifier.weight(1f)
        ) { current ->
            when (current) {
                ManualMode.Cover -> ManualCover(
                    chapterCount = state.chapters.size,
                    onOpenToc = { mode = ManualMode.Toc },
                    onStartReading = {
                        chapterIndex = 0
                        mode = ManualMode.Reading
                    }
                )
                ManualMode.Toc -> ManualTableOfContents(
                    chapters = state.chapters,
                    onSelect = { index ->
                        chapterIndex = index
                        mode = ManualMode.Reading
                    }
                )
                ManualMode.Reading -> {
                    val chapter = state.chapters.getOrNull(chapterIndex)
                    if (chapter == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.manual_empty))
                        }
                    } else {
                        ManualReaderPage(
                            chapter = chapter,
                            chapterIndex = chapterIndex,
                            total = state.chapters.size,
                            scrollState = scrollState,
                            onPrev = {
                                if (chapterIndex > 0) chapterIndex--
                            },
                            onNext = {
                                if (chapterIndex < state.chapters.lastIndex) chapterIndex++
                                else mode = ManualMode.Toc
                            },
                            onToc = { mode = ManualMode.Toc }
                        )
                    }
                }
            }
        }
    }
}

private enum class ManualMode { Cover, Toc, Reading }

@Composable
private fun ManualTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.camera_back),
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.AutoMirrored.Outlined.MenuBook,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 12.dp)
        )
    }
}

@Composable
private fun ManualCover(
    chapterCount: Int,
    onOpenToc: () -> Unit,
    onStartReading: () -> Unit
) {
    val extras = LocalVaExtra.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = VaDimens.screenHorizontal)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(28.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, extras.hairline, MaterialTheme.shapes.large)
                .padding(horizontal = 28.dp, vertical = 40.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "VoiceAssistant",
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.manual_cover_subtitle),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Serif,
                        lineHeight = 28.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    stringResource(R.string.manual_chapter_count, chapterCount),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        VaPrimaryButton(
            text = stringResource(R.string.manual_start_reading),
            onClick = onStartReading,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        VaGhostButton(
            text = stringResource(R.string.manual_open_toc),
            onClick = onOpenToc,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ManualTableOfContents(
    chapters: List<ManualChapter>,
    onSelect: (Int) -> Unit
) {
    val extras = LocalVaExtra.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = VaDimens.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                stringResource(R.string.manual_toc_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp, top = 4.dp)
            )
        }
        items(chapters, key = { it.index }) { chapter ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, extras.hairline, MaterialTheme.shapes.medium)
                    .clickable { onSelect(chapter.index) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "%02d".format(chapter.index + 1),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Serif,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.width(40.dp)
                )
                Text(
                    chapter.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ManualReaderPage(
    chapter: ManualChapter,
    chapterIndex: Int,
    total: Int,
    scrollState: androidx.compose.foundation.ScrollState,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToc: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LinearProgressIndicator(
            progress = { (chapterIndex + 1f) / total.coerceAtLeast(1) },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = VaDimens.screenHorizontal, vertical = 16.dp)
        ) {
            Text(
                stringResource(R.string.manual_page_progress, chapterIndex + 1, total),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(10.dp))
            Text(
                chapter.title,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = FontFamily.Serif,
                    lineHeight = 34.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(18.dp))
            Text(
                cleanManualBody(chapter.body),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Serif,
                    fontSize = 17.sp,
                    lineHeight = 30.sp,
                    letterSpacing = 0.15.sp
                ),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.92f)
            )
            Spacer(Modifier.height(32.dp))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onPrev, enabled = chapterIndex > 0) {
                Icon(
                    Icons.Outlined.ChevronLeft,
                    contentDescription = stringResource(R.string.manual_prev),
                    tint = if (chapterIndex > 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            VaGhostButton(
                text = stringResource(R.string.manual_toc),
                onClick = onToc,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onNext) {
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = stringResource(R.string.manual_next),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/** Soft-clean markdown noise for comfortable reading. */
private fun cleanManualBody(raw: String): String =
    raw
        .replace(Regex("\\|[-:| ]+\\|"), "")
        .replace(Regex("^\\|.*\\|$", RegexOption.MULTILINE)) { match ->
            match.value
                .trim('|')
                .split("|")
                .joinToString(" · ") { it.trim() }
                .let { "• $it" }
        }
        .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
        .replace(Regex("`([^`]+)`"), "$1")
        .replace(Regex("^```.*$", RegexOption.MULTILINE), "")
        .replace(Regex("^---+\\s*$", RegexOption.MULTILINE), "────────")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
