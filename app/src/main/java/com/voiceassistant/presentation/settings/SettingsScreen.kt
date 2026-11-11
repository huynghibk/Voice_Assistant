package com.voiceassistant.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.voiceassistant.presentation.components.VaBrandMark
import com.voiceassistant.presentation.components.VaSectionLabel
import com.voiceassistant.presentation.components.VaSurfacePanel
import com.voiceassistant.presentation.theme.VaDimens

@Composable
fun SettingsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = VaDimens.screenHorizontal, vertical = VaDimens.screenTop)
    ) {
        VaBrandMark()
        Spacer(Modifier.height(20.dp))

        VaSectionLabel("Thiết kế")
        VaSurfacePanel {
            Text(
                "Premium Minimalist · Charcoal / Cream / Amber",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Không neon. Typography serif cho brand, sans cho UI. Amber chỉ trên hành động chính và trạng thái đang chọn.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(16.dp))
        VaSectionLabel("Hạ tầng")
        VaSurfacePanel {
            Text(
                "FileProvider · AudioRouting · AI Search FTS/Web · Piper→VC · Timeline filtergraph",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(16.dp))
        VaSectionLabel("Models")
        VaSurfacePanel {
            Text(
                "Piper: assets/models/piper/*.onnx\nVC: assets/models/vc/converter.onnx (optional)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(28.dp))
    }
}
