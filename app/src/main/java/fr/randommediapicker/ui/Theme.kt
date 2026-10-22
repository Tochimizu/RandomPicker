package fr.randommediapicker.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import fr.randommediapicker.domain.ThemeMode

val DarkColors = darkColorScheme(
    primary = Color(0xFFBAC3FF), onPrimary = Color(0xFF17245F),
    secondary = Color(0xFF85D6C7), background = Color(0xFF10131A),
    surface = Color(0xFF171B25), surfaceVariant = Color(0xFF282D3C),
    onBackground = Color(0xFFE6E8F2), onSurface = Color(0xFFE6E8F2),
)
private val LightColors = lightColorScheme(
    primary = Color(0xFF4355A6), secondary = Color(0xFF146C60),
    background = Color(0xFFF5F6FF), surface = Color(0xFFFBF8FF),
)

@Composable
fun PickerTheme(mode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) {
    val dark = mode == ThemeMode.DARK || (mode == ThemeMode.SYSTEM && isSystemInDarkTheme())
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
