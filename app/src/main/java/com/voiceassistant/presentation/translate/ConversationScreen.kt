package com.voiceassistant.presentation.translate

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voiceassistant.R
import com.voiceassistant.data.translation.OfflineTranslationService
import com.voiceassistant.presentation.components.VaVoiceChip
import com.voiceassistant.presentation.theme.AmberAccent
import com.voiceassistant.presentation.theme.DeepCharcoal
import com.voiceassistant.presentation.theme.InkMutedOnDark
import com.voiceassistant.presentation.theme.InkMutedOnLight
import com.voiceassistant.presentation.theme.InkOnDark
import com.voiceassistant.presentation.theme.InkOnLight
import com.voiceassistant.presentation.theme.SoftCream
import com.voiceassistant.presentation.theme.SurfaceCharcoal

/**
 * Face-to-face conversation mode: 50/50 split, opposite side rotated 180°.
 */
@Composable
fun ConversationScreen(
    onClose: () -> Unit,
    viewModel: ConversationViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingSide by remember { mutableStateOf<ConversationSide?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val side = pendingSide
        pendingSide = null
        if (granted && side != null) viewModel.toggleListening(side)
    }

    fun ensureMicThen(side: ConversationSide) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.toggleListening(side)
        } else {
            pendingSide = side
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepCharcoal)
    ) {
        // Opposite person — Soft Cream, rotated 180°
        ConversationPane(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .graphicsLayer { rotationZ = 180f },
            background = SoftCream,
            ink = InkOnLight,
            muted = InkMutedOnLight,
            accent = AmberAccent,
            languages = state.languages,
            selectedLang = state.theirLang,
            onLangSelected = viewModel::onTheirLang,
            text = state.theirText,
            placeholder = stringResource(R.string.conversation_their_hint),
            isListening = state.isListening && state.listeningSide == ConversationSide.THEIRS,
            isBusy = state.isTranslating || state.isSpeaking,
            onMic = { ensureMicThen(ConversationSide.THEIRS) }
        )

        ConversationToolbar(
            autoSpeak = state.autoSpeak,
            statusMessage = state.statusMessage,
            isBusy = state.isListening || state.isTranslating || state.isSpeaking,
            onClose = {
                viewModel.stopListening()
                viewModel.stopPlayback()
                onClose()
            },
            onSwap = viewModel::swapLanguages,
            onToggleAutoSpeak = viewModel::toggleAutoSpeak
        )

        // Me — Deep Charcoal
        ConversationPane(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            background = DeepCharcoal,
            ink = InkOnDark,
            muted = InkMutedOnDark,
            accent = AmberAccent,
            languages = state.languages,
            selectedLang = state.myLang,
            onLangSelected = viewModel::onMyLang,
            text = state.myText,
            placeholder = stringResource(R.string.conversation_my_hint),
            isListening = state.isListening && state.listeningSide == ConversationSide.MINE,
            isBusy = state.isTranslating || state.isSpeaking,
            onMic = { ensureMicThen(ConversationSide.MINE) }
        )
    }
}

@Composable
private fun ConversationToolbar(
    autoSpeak: Boolean,
    statusMessage: String,
    isBusy: Boolean,
    onClose: () -> Unit,
    onSwap: () -> Unit,
    onToggleAutoSpeak: () -> Unit
) {
    Surface(
        color = SurfaceCharcoal,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.conversation_close),
                        tint = InkOnDark
                    )
                }
                IconButton(onClick = onSwap, enabled = !isBusy) {
                    Icon(
                        Icons.Outlined.SwapVert,
                        contentDescription = stringResource(R.string.conversation_swap),
                        tint = AmberAccent
                    )
                }
                IconButton(onClick = onToggleAutoSpeak) {
                    Icon(
                        if (autoSpeak) Icons.Outlined.VolumeUp else Icons.Outlined.VolumeOff,
                        contentDescription = stringResource(R.string.conversation_auto_speak),
                        tint = if (autoSpeak) AmberAccent else InkMutedOnDark
                    )
                }
            }
            if (statusMessage.isNotBlank()) {
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.labelMedium,
                    color = InkMutedOnDark,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun ConversationPane(
    modifier: Modifier = Modifier,
    background: Color,
    ink: Color,
    muted: Color,
    accent: Color,
    languages: List<OfflineTranslationService.Lang>,
    selectedLang: String,
    onLangSelected: (String) -> Unit,
    text: String,
    placeholder: String,
    isListening: Boolean,
    isBusy: Boolean,
    onMic: () -> Unit
) {
    Box(
        modifier = modifier.background(background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                languages.forEach { lang ->
                    VaVoiceChip(
                        label = lang.label,
                        selected = selectedLang == lang.id,
                        onClick = { onLangSelected(lang.id) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val body = text.ifBlank { placeholder }
                Text(
                    text = body,
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (text.isBlank()) muted else ink,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    textAlign = TextAlign.Center
                )
                if (isBusy && !isListening) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .size(22.dp)
                            .padding(bottom = 4.dp),
                        strokeWidth = 2.dp,
                        color = accent
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            FloatingActionButton(
                onClick = onMic,
                containerColor = if (isListening) accent else accent.copy(alpha = 0.9f),
                contentColor = DeepCharcoal,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Outlined.MicOff else Icons.Outlined.Mic,
                    contentDescription = stringResource(
                        if (isListening) R.string.conversation_stop_mic
                        else R.string.conversation_mic
                    )
                )
            }
        }
    }
}
