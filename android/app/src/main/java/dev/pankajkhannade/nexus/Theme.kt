package dev.pankajkhannade.nexus

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF1C1917)
private val Paper = Color(0xFFF7F4EF)
private val Card = Color(0xFFFFFCF8)
private val Muted = Color(0xFF57534E)
private val Line = Color(0xFFE6DFD4)
private val Danger = Color(0xFF9F3D3D)

private val Light = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    background = Paper,
    onBackground = Ink,
    surface = Card,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEFEAE2),
    onSurfaceVariant = Muted,
    outline = Line,
    error = Danger,
)

private val Dark = darkColorScheme(
    primary = Color(0xFFD7DBE3),
    onPrimary = Color(0xFF0A0A0B),
    background = Color(0xFF0A0A0B),
    onBackground = Color(0xFFF4F4F5),
    surface = Color(0xFF141416),
    onSurface = Color(0xFFF4F4F5),
    surfaceVariant = Color(0xFF1C1C20),
    onSurfaceVariant = Color(0xFFA1A1AA),
    outline = Color(0xFF2A2A2E),
    error = Color(0xFFC46A6A),
)

private val Type = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun NexusTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Type, content = content)
}
