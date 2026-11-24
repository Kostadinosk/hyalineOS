package os.hyaline.core.theme

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

fun parseHexColor(hex: String, fallback: Color = Color(0xFF00A8E8)): Color =
    runCatching { Color(AndroidColor.parseColor(hex)) }.getOrDefault(fallback)

/**
 * WCAG luminance contrast helper matching the CSS contrast-color() behavior in the Web Studio.
 */
fun contrastingForeground(background: Color): Color =
    if (background.luminance() > 0.38f) Color(0xFF071A2B) else Color.White

fun os1WallpaperBrush(preset: String): Brush = when (preset) {
    "aqua-bokeh" -> Brush.linearGradient(listOf(Color(0xFF38EF7D), Color(0xFF11998E), Color(0xFF0F4C75)))
    "emerald-meadow" -> Brush.verticalGradient(listOf(Color(0xFF38BDF8), Color(0xFF34D399), Color(0xFF059669)))
    "sunset-prism" -> Brush.linearGradient(listOf(Color(0xFF38BDF8), Color(0xFF818CF8), Color(0xFFF472B6)))
    "midnight-glass" -> Brush.verticalGradient(listOf(Color(0xFF0C243B), Color(0xFF050D17)))
    else -> Brush.linearGradient(listOf(Color(0xFF6EE7FF), Color(0xFF1EA6E8), Color(0xFF0B5EA8)))
}

fun os2WallpaperBrush(style: Os2Style): Brush = when (style) {
    Os2Style.STEALTH_OBSIDIAN_VAULT -> Brush.verticalGradient(listOf(Color(0xFF0A0C10), Color(0xFF040507)))
    Os2Style.CHAMELEON_PERSONA_SPACES -> Brush.linearGradient(listOf(Color(0xFF1B1035), Color(0xFF0C1328), Color(0xFF050814)))
    Os2Style.METRO_AERO_PRO -> Brush.linearGradient(listOf(Color(0xFF0B2447), Color(0xFF08152B), Color(0xFF040914)))
    Os2Style.SMOKED_AERO_DESKTOP -> Brush.linearGradient(listOf(Color(0xFF0D263D), Color(0xFF071422), Color(0xFF030910)))
}

/**
 * Authentic Windows Metro x Frutiger Aero glass tile modifier supporting all 4 OS 1 presets.
 */
fun Modifier.hyalineGlassTile(
    style: Os1Style,
    tint: Color,
    glassOpacity: Float,
    cornerRadiusDp: Int,
): Modifier {
    val shape = RoundedCornerShape(
        if (style == Os1Style.METRO_AERO_GADGETS) (cornerRadiusDp + 4).dp else cornerRadiusDp.dp,
    )
    val surfaceBrush = when (style) {
        Os1Style.AERO_GLASS_METRO -> Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.34f),
                tint.copy(alpha = glassOpacity.coerceIn(0.18f, 0.9f)),
                tint.copy(alpha = (glassOpacity * 0.88f).coerceIn(0.15f, 0.85f)),
            ),
        )
        Os1Style.GLYSSY_NEO_METRO -> Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.42f),
                tint.copy(alpha = 0.92f),
                tint.copy(alpha = 0.78f),
            ),
        )
        Os1Style.MINIMAL_FROSTED_AERO -> Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = (glassOpacity * 0.48f).coerceIn(0.14f, 0.42f)),
                Color.White.copy(alpha = (glassOpacity * 0.28f).coerceIn(0.1f, 0.3f)),
            ),
        )
        Os1Style.METRO_AERO_GADGETS -> Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.44f),
                tint.copy(alpha = (glassOpacity * 0.9f).coerceIn(0.2f, 0.85f)),
                Color.Black.copy(alpha = 0.14f),
            ),
        )
    }
    return this
        .clip(shape)
        .background(surfaceBrush)
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.68f), Color.White.copy(alpha = 0.2f)),
            ),
            shape = shape,
        )
}

@Composable
fun HyalineTheme(
    darkOs2: Boolean = false,
    accentHex: String = "#00a8e8",
    content: @Composable () -> Unit,
) {
    val accent = parseHexColor(accentHex)
    val scheme = if (darkOs2) {
        darkColorScheme(
            primary = accent,
            background = Color(0xFF06111D),
            surface = Color(0xFF0D2034),
            onPrimary = contrastingForeground(accent),
            onBackground = Color(0xFFF0F9FF),
            onSurface = Color(0xFFE0F2FE),
        )
    } else {
        lightColorScheme(
            primary = accent,
            background = Color(0xFF0B5EA8),
            surface = Color(0xFFEAF6FF),
            onPrimary = contrastingForeground(accent),
            onBackground = Color.White,
            onSurface = Color(0xFF082032),
        )
    }

    MaterialTheme(
        colorScheme = scheme,
        content = content,
    )
}
