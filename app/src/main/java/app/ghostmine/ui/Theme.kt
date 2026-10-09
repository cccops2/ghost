package app.ghostmine.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object G {
    val Bg = Color(0xFF09090B)
    val Card = Color(0xFF141417)
    val CardHi = Color(0xFF1D1D22)
    val Line = Color(0xFF26262C)
    val Dim = Color(0xFF8B8B95)
    val Accent = Color(0xFF7DF0D6)
    val OnAccent = Color(0xFF04120F)
    val Warn = Color(0xFFFFB86B)
}

@Composable
fun GhostTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = G.Accent, onPrimary = G.OnAccent, background = G.Bg, surface = G.Card,
            onSurface = Color.White, onBackground = Color.White,
        ),
        content = content,
    )
}
