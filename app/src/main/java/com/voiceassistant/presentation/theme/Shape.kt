package com.voiceassistant.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val VoiceShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

object VaDimens {
    val screenHorizontal = 20.dp
    val screenTop = 12.dp
    val sectionGap = 16.dp
    val cardPadding = 16.dp
    val chipGap = 8.dp
}
