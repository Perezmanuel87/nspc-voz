package es.nspc.voz.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF059669),
    onPrimary = Color.White,
    secondary = Color(0xFF1F2937),
    background = Color(0xFFF9FAFB),
    surface = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF10B981),
    onPrimary = Color.Black,
    secondary = Color(0xFFE5E7EB),
    background = Color(0xFF0F172A),
    surface = Color(0xFF1E293B),
)

@Composable
fun NspcVozTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
