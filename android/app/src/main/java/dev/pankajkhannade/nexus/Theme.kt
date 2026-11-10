package dev.pankajkhannade.nexus

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Nexus design system (docs/design.md section 3.5). Light, high contrast only in Phase 1.
 * Contrast ratios below are WCAG ratios against the colour they sit on.
 */
object NexusColors {
    val Background = Color(0xFFFFFFFF)
    val SurfaceContainer = Color(0xFFF4F4F5) // always paired with an Outline border
    val OnSurface = Color(0xFF1A1A1A) // 17.4:1 on white
    val OnSurfaceVariant = Color(0xFF3F3F46) // 10.4:1 on white
    val Outline = Color(0xFF6B6B6B) // 5.3:1 on white, meets 3:1 for boundaries
    val Primary = Color(0xFF0B5CAD) // white text 6.7:1
    val Call = Color(0xFF0F6B3A) // white text 6.6:1, call buttons only
    val Danger = Color(0xFFB3261E)
    val DangerContainer = Color(0xFFFDECEA)
    val OnDangerContainer = Color(0xFF8C1D18) // 8.0:1
    val WarningContainer = Color(0xFFFFF4D6)
    val OnWarningContainer = Color(0xFF7A4B00) // 6.8:1
    val NeutralContainer = Color(0xFFF4F4F5) // "No warning signs", deliberately not green
    val SuccessContainer = Color(0xFFE7F4EC) // setup done only, never a safety verdict
    val OnSuccessContainer = Color(0xFF0F5132)
}

/** Parent mode is used daily by the parent; Child mode is for setup and family settings. */
enum class UiMode { Parent, Child }

/** Minimum touch targets. Parent 64dp, child 48dp; primary actions are taller. */
@Immutable
data class NexusSizes(val minTarget: Dp, val primaryTarget: Dp, val speakTarget: Dp)

private val ParentSizes = NexusSizes(minTarget = 64.dp, primaryTarget = 72.dp, speakTarget = 88.dp)
private val ChildSizes = NexusSizes(minTarget = 48.dp, primaryTarget = 56.dp, speakTarget = 64.dp)

val LocalNexusSizes = staticCompositionLocalOf { ChildSizes }

private val NoTrim = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun style(size: Int, weight: FontWeight, lineHeight: Double): TextStyle {
    val fontSize: TextUnit = size.sp
    return TextStyle(
        fontSize = fontSize,
        fontWeight = weight,
        lineHeight = (size * lineHeight).sp,
        lineHeightStyle = NoTrim, // keeps Devanagari matras from clipping
    )
}

/** Type scale: no text under 18sp in parent mode, or under 16sp anywhere. */
fun nexusTypography(mode: UiMode): Typography {
    val parent = mode == UiMode.Parent
    fun pick(p: Int, c: Int) = if (parent) p else c
    return Typography(
        displaySmall = style(pick(34, 30), FontWeight.SemiBold, 1.3),
        headlineMedium = style(pick(30, 26), FontWeight.SemiBold, 1.35),
        titleLarge = style(pick(26, 22), FontWeight.SemiBold, 1.4),
        titleMedium = style(pick(22, 18), FontWeight.Medium, 1.45),
        bodyLarge = style(pick(20, 18), FontWeight.Normal, 1.5),
        bodyMedium = style(pick(18, 16), FontWeight.Normal, 1.5),
        bodySmall = style(pick(18, 16), FontWeight.Normal, 1.5),
        labelLarge = style(pick(22, 18), FontWeight.SemiBold, 1.3),
        labelMedium = style(pick(18, 16), FontWeight.SemiBold, 1.3),
        labelSmall = style(pick(18, 16), FontWeight.Medium, 1.3),
    )
}

private val Light = lightColorScheme(
    primary = NexusColors.Primary,
    onPrimary = Color.White,
    background = NexusColors.Background,
    onBackground = NexusColors.OnSurface,
    surface = NexusColors.Background,
    onSurface = NexusColors.OnSurface,
    surfaceVariant = NexusColors.SurfaceContainer,
    onSurfaceVariant = NexusColors.OnSurfaceVariant,
    surfaceContainer = NexusColors.SurfaceContainer,
    surfaceContainerHigh = NexusColors.SurfaceContainer,
    surfaceContainerHighest = NexusColors.SurfaceContainer,
    outline = NexusColors.Outline,
    outlineVariant = NexusColors.Outline,
    error = NexusColors.Danger,
    errorContainer = NexusColors.DangerContainer,
    onErrorContainer = NexusColors.OnDangerContainer,
)

@Composable
fun NexusTheme(mode: UiMode = UiMode.Child, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNexusSizes provides if (mode == UiMode.Parent) ParentSizes else ChildSizes) {
        MaterialTheme(colorScheme = Light, typography = nexusTypography(mode), content = content)
    }
}

val CardShape = RoundedCornerShape(16.dp)
val CardBorder = BorderStroke(1.5.dp, NexusColors.Outline)

enum class ButtonKind { Primary, Secondary, Call, Quiet }

/** The single source of button target sizes. Full width, label always text (never icon-only). */
@Composable
fun BigButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    enabled: Boolean = true,
) {
    val sizes = LocalNexusSizes.current
    val min = if (kind == ButtonKind.Primary || kind == ButtonKind.Call) sizes.primaryTarget else sizes.minTarget
    val m = modifier.fillMaxWidth().heightIn(min = min)
    val text: @Composable () -> Unit = {
        Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 4.dp))
    }
    when (kind) {
        ButtonKind.Primary -> Button(onClick, m, enabled = enabled, shape = CardShape) { text() }
        ButtonKind.Call -> Button(
            onClick, m, enabled = enabled, shape = CardShape,
            colors = ButtonDefaults.buttonColors(containerColor = NexusColors.Call, contentColor = Color.White),
        ) { text() }
        ButtonKind.Secondary -> OutlinedButton(
            onClick, m, enabled = enabled, shape = CardShape,
            border = BorderStroke(2.dp, NexusColors.Outline),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = NexusColors.OnSurface),
        ) { text() }
        ButtonKind.Quiet -> TextButton(onClick, m, enabled = enabled, shape = CardShape) { text() }
    }
}
