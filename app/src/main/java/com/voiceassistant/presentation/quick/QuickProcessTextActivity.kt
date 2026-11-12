package com.voiceassistant.presentation.quick

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voiceassistant.R
import com.voiceassistant.presentation.MainActivity
import com.voiceassistant.presentation.components.VaGhostButton
import com.voiceassistant.presentation.components.VaPrimaryButton
import com.voiceassistant.presentation.components.VaSectionLabel
import com.voiceassistant.presentation.theme.VaDimens
import com.voiceassistant.presentation.theme.VoiceAssistantTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Lightweight translucent host for [Intent.ACTION_PROCESS_TEXT].
 * Shows a themed ModalBottomSheet: detect → translate → TTS / open in app.
 */
@AndroidEntryPoint
class QuickProcessTextActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val processText = intent?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty()
        enableEdgeToEdge()
        setContent {
            VoiceAssistantTheme {
                val viewModel: QuickProcessTextViewModel = hiltViewModel()
                LaunchedEffect(processText) {
                    if (processText.isNotBlank()) viewModel.bindSourceText(processText)
                }
                QuickProcessSheet(
                    viewModel = viewModel,
                    onDismiss = { finish() },
                    onOpenApp = { finishAfterOpenApp() }
                )
            }
        }
    }

    private fun finishAfterOpenApp() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        )
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickProcessSheet(
    viewModel: QuickProcessTextViewModel,
    onDismiss: () -> Unit,
    onOpenApp: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = VaDimens.screenHorizontal)
                .padding(bottom = 28.dp)
        ) {
            Text(
                stringResource(R.string.quick_process_title),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.quick_process_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(14.dp))
            VaSectionLabel(stringResource(R.string.quick_process_source))
            Text(
                state.sourceText.ifBlank { "—" },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 120.dp)
                    .verticalScroll(rememberScrollState())
            )

            if (state.detectedLabel.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.lang_detected, state.detectedLabel),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(14.dp))
            VaSectionLabel(stringResource(R.string.quick_process_translation))
            if (state.isWorking) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(22.dp),
                        strokeWidth = 2.dp
                    )
                    Text(
                        state.statusMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    state.translatedText.ifBlank { "—" },
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp)
                        .verticalScroll(rememberScrollState())
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                state.statusMessage,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))
            VaPrimaryButton(
                text = if (state.isPlaying) {
                    stringResource(R.string.home_stop)
                } else {
                    stringResource(R.string.quick_process_listen)
                },
                onClick = viewModel::playTranslation,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isWorking &&
                    (state.translatedText.isNotBlank() || state.sourceText.isNotBlank())
            )
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                VaGhostButton(
                    text = stringResource(R.string.quick_process_open_tts),
                    onClick = {
                        viewModel.openInAppTts()
                        onOpenApp()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !state.isWorking
                )
                Spacer(Modifier.width(8.dp))
                VaGhostButton(
                    text = stringResource(R.string.quick_process_open_translate),
                    onClick = {
                        viewModel.openInAppTranslate()
                        onOpenApp()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !state.isWorking
                )
            }
        }
    }
}
