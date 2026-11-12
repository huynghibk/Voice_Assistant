package com.voiceassistant.presentation.camera

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.common.InputImage
import com.voiceassistant.R
import com.voiceassistant.domain.translation.LanguageIdentifier
import com.voiceassistant.presentation.components.VaGhostButton
import com.voiceassistant.presentation.components.VaPrimaryButton
import com.voiceassistant.presentation.components.VaSectionLabel
import com.voiceassistant.presentation.components.VaSurfacePanel
import com.voiceassistant.presentation.components.VaVoiceChip
import com.voiceassistant.presentation.theme.VaDimens
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun CameraScannerScreen(
    onBack: () -> Unit,
    onSendToTts: (String) -> Unit,
    viewModel: CameraScannerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = VaDimens.screenHorizontal, vertical = VaDimens.screenTop)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.camera_back),
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                stringResource(R.string.camera_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(Modifier.height(8.dp))
        VaSectionLabel(stringResource(R.string.home_translate_section))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                stringResource(R.string.home_source_lang),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
            VaVoiceChip(
                label = stringResource(R.string.lang_auto),
                selected = state.selectedSourceLang == LanguageIdentifier.AUTO,
                onClick = { viewModel.onSourceLang(LanguageIdentifier.AUTO) }
            )
            state.languages.forEach { lang ->
                VaVoiceChip(
                    label = lang.label,
                    selected = state.selectedSourceLang == lang.id,
                    onClick = { viewModel.onSourceLang(lang.id) }
                )
            }
        }
        if (state.selectedSourceLang == LanguageIdentifier.AUTO && state.detectedLabel.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.lang_detected, state.detectedLabel),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                stringResource(R.string.home_target_lang),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
            state.languages.forEach { lang ->
                VaVoiceChip(
                    label = lang.label,
                    selected = state.selectedTargetLang == lang.id,
                    onClick = { viewModel.onTargetLang(lang.id) }
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (!hasCameraPermission) {
                VaSurfacePanel(modifier = Modifier.align(Alignment.Center)) {
                    Text(
                        stringResource(R.string.camera_permission),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    VaPrimaryButton(
                        text = stringResource(R.string.camera_grant),
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                val capturing = remember { AtomicBoolean(false) }
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).also { previewView ->
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                            cameraProviderFuture.addListener({
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also { previewUseCase ->
                                    previewUseCase.setSurfaceProvider(previewView.surfaceProvider)
                                }
                                val analysis = ImageAnalysis.Builder()
                                    .setTargetResolution(Size(1280, 720))
                                    .setBackpressureStrategy(
                                        ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                                    )
                                    .build()
                                analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                    var closed = false
                                    try {
                                        if (viewModel.consumeCaptureRequest() &&
                                            capturing.compareAndSet(false, true)
                                        ) {
                                            try {
                                                // Copy frame before closing ImageProxy
                                                val bitmap = imageProxy.toBitmap()
                                                val rotation =
                                                    imageProxy.imageInfo.rotationDegrees
                                                imageProxy.close()
                                                closed = true
                                                val input =
                                                    InputImage.fromBitmap(bitmap, rotation)
                                                viewModel.processFrame(input)
                                            } finally {
                                                capturing.set(false)
                                            }
                                        }
                                    } finally {
                                        if (!closed) imageProxy.close()
                                    }
                                }
                                runCatching {
                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        analysis
                                    )
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                if (state.isProcessing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(10.dp))
                            Text(
                                stringResource(R.string.camera_processing),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        if (state.statusMessage.isNotBlank()) {
            Text(
                state.statusMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
        }
        val previewText = state.translatedText.ifBlank { state.ocrText }
        if (previewText.isNotBlank()) {
            VaSurfacePanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
            ) {
                Text(
                    previewText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        VaPrimaryButton(
            text = stringResource(R.string.camera_capture),
            onClick = viewModel::requestCapture,
            modifier = Modifier.fillMaxWidth(),
            enabled = hasCameraPermission && !state.isProcessing
        )
        Spacer(Modifier.height(8.dp))
        VaGhostButton(
            text = stringResource(R.string.camera_send_tts),
            onClick = {
                if (previewText.isNotBlank()) onSendToTts(previewText.trim())
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = previewText.isNotBlank() && !state.isProcessing
        )
        Spacer(Modifier.height(8.dp))
    }
}
