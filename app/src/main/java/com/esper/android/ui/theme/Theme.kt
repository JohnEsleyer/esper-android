package com.esper.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = IndigoAccent,
    onPrimary = TextPrimary,
    primaryContainer = Color(0xFF312E81),
    secondary = ElectricViolet,
    background = SpaceBlack,
    surface = CosmicSlate,
    onSurface = TextPrimary,
    outline = BorderSlate
)

@Composable
fun EsperTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
