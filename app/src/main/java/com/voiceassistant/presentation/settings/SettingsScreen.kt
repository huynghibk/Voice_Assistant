package com.voiceassistant.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.voiceassistant.presentation.components.VaBrandMark
import com.voiceassistant.presentation.components.VaSectionLabel
import com.voiceassistant.presentation.components.VaSurfacePanel
import com.voiceassistant.presentation.theme.VaDimens

@Composable
fun SettingsScreen() {
    var showHelp by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName
        }.getOrNull() ?: "1.0.0"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = VaDimens.screenHorizontal, vertical = VaDimens.screenTop)
    ) {
        VaBrandMark()
        Spacer(Modifier.height(8.dp))
        Text(
            "Cài đặt & Trợ giúp",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(VaDimens.sectionGap))
        VaSectionLabel("Cài đặt")
        VaSurfacePanel {
            SettingsRow(
                icon = { Icon(Icons.Outlined.VolumeUp, contentDescription = null) },
                title = "Giọng đọc",
                subtitle = "Chọn giọng ở tab Voices. Chưa có model Piper thì app dùng giọng hệ thống."
            )
            Spacer(Modifier.height(14.dp))
            SettingsRow(
                icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                title = "Xuất audio",
                subtitle = "Định dạng xuất (WAV/MP3…) chọn ngay trên màn hình TTS trước khi bấm Xuất."
            )
        }

        Spacer(Modifier.height(16.dp))
        VaSectionLabel("Trợ giúp")
        VaSurfacePanel {
            SettingsNavRow(
                icon = { Icon(Icons.Outlined.HelpOutline, contentDescription = null) },
                title = "Hướng dẫn sử dụng",
                subtitle = "Cách dán văn bản, phát và xuất file",
                onClick = { showHelp = !showHelp }
            )
            if (showHelp) {
                Spacer(Modifier.height(12.dp))
                HelpContent()
            }
            Spacer(Modifier.height(14.dp))
            SettingsNavRow(
                icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                title = "Giới thiệu",
                subtitle = "Phiên bản $versionName",
                onClick = { showAbout = !showAbout }
            )
            if (showAbout) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "VoiceAssistant giúp bạn nghe và xuất audio từ văn bản ngay trên máy, " +
                        "không cần gửi nội dung lên máy chủ.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun HelpContent() {
    Column {
        HelpBullet("1. Mở tab TTS, dán hoặc gõ văn bản cần đọc.")
        HelpBullet("2. Chọn giọng ở hàng Giọng đọc (hoặc clone giọng ở tab Voices).")
        HelpBullet("3. Bấm Phát để nghe. Bấm Dừng nếu muốn ngắt giữa chừng.")
        HelpBullet("4. Chọn định dạng xuất rồi bấm Xuất / Chia sẻ khi cần lưu file.")
        HelpBullet("5. Văn bản dài: dùng tab Projects để lưu và chỉnh sửa theo dự án.")
    }
}

@Composable
private fun HelpBullet(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun SettingsRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String
) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        icon()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsNavRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
    ) {
        icon()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
