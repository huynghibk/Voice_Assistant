package com.voiceassistant.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class VaExtraColors(
    val hairline: Color,
    val surfaceRaised: Color,
    val amberMuted: Color,
    val bubbleUser: Color,
    val bubbleAssistant: Color,
    val onBubbleUser: Color,
    val onBubbleAssistant: Color
)

val LocalVaExtra = staticCompositionLocalOf {
    VaExtraColors(
        hairline = HairlineDark,
        surfaceRaised = SurfaceRaised,
        amberMuted = AmberMuted,
        bubbleUser = AmberAccent,
        bubbleAssistant = SurfaceCharcoal,
        onBubbleUser = DeepCharcoal,
        onBubbleAssistant = InkOnDark
    )
}

private val DarkColors = darkColorScheme(
    primary = AmberAccent,
    onPrimary = DeepCharcoal,
    primaryContainer = AmberMuted,
    onPrimaryContainer = AmberAccent,
    secondary = CreamRaised,
    onSecondary = DeepCharcoal,
    background = DeepCharcoal,
    onBackground = InkOnDark,
    surface = SurfaceCharcoal,
    onSurface = InkOnDark,
    surfaceVariant = SurfaceRaised,
    onSurfaceVariant = InkMutedOnDark,
    outline = HairlineDark,
    outlineVariant = HairlineDark,
    error = DangerQuiet,
    onError = InkOnDark
)

private val LightColors = lightColorScheme(
    primary = AmberAccent,
    onPrimary = DeepCharcoal,
    primaryContainer = AmberMuted,
    onPrimaryContainer = DeepCharcoal,
    secondary = SurfaceCharcoal,
    onSecondary = SoftCream,
    background = SoftCream,
    onBackground = InkOnLight,
    surface = CreamSurface,
    onSurface = InkOnLight,
    surfaceVariant = CreamRaised,
    onSurfaceVariant = InkMutedOnLight,
    outline = HairlineLight,
    outlineVariant = HairlineLight,
    error = DangerQuiet,
    onError = SoftCream
)

@Composable
fun VoiceAssistantTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val extras = if (darkTheme) {
        VaExtraColors(
            hairline = HairlineDark,
            surfaceRaised = SurfaceRaised,
            amberMuted = AmberMuted,
            bubbleUser = AmberAccent,
            bubbleAssistant = SurfaceCharcoal,
            onBubbleUser = DeepCharcoal,
            onBubbleAssistant = InkOnDark
        )
    } else {
        VaExtraColors(
            hairline = HairlineLight,
            surfaceRaised = CreamRaised,
            amberMuted = AmberMuted,
            bubbleUser = AmberAccent,
            bubbleAssistant = CreamRaised,
            onBubbleUser = DeepCharcoal,
            onBubbleAssistant = InkOnLight
        )
    }

    CompositionLocalProvider(LocalVaExtra provides extras) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = VoiceTypography,
            shapes = VoiceShapes,
            content = content
        )
    }
}
