package app.terminalssh.secure.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.terminalssh.secure.R

// Iranian-premium identity: deep graphite, turquoise from the app mark,
// cyan for motion/connection progress and emerald for healthy secure state.
val Ink = Color(0xFF07090C)
val Surface1 = Color(0xFF101317)
val Surface2 = Color(0xFF171B20)
val Surface3 = Color(0xFF20262D)
val Stroke = Color(0xFF2A3037)
val TextPrimary = Color(0xFFF4F7F8)
val TextSecondary = Color(0xFF9AA4AC)
val Turquoise = Color(0xFF35D7AE)
val Emerald = Color(0xFF34D399)
val Cyan = Color(0xFF5AC8FA)
val Amber = Color(0xFFFFC66D)
val Danger = Color(0xFFFF7082)

private val Scheme = darkColorScheme(
    primary = Turquoise,
    onPrimary = Ink,
    primaryContainer = Color(0xFF0C3A31),
    onPrimaryContainer = Color(0xFFC7FFF0),
    secondary = Cyan,
    onSecondary = Ink,
    secondaryContainer = Color(0xFF12323B),
    onSecondaryContainer = Color(0xFFD4F8FF),
    tertiary = Emerald,
    onTertiary = Ink,
    background = Ink,
    onBackground = TextPrimary,
    surface = Surface1,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextSecondary,
    outline = Stroke,
    error = Danger,
    onError = Ink,
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(9.dp),
    small = RoundedCornerShape(13.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

/**
 * Vazirmatn (SIL OFL 1.1) as a single variable file: one 240 KB asset covers every weight,
 * where four static cuts would cost twice that. Android's default Persian face is a naskh
 * design meant for running text; a UI-proportioned face is what makes Persian labels read
 * as deliberately set rather than defaulted.
 */
@OptIn(ExperimentalTextApi::class)
private fun vazirmatn(weight: FontWeight) = Font(
    R.font.vazirmatn_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

private val Vazirmatn = FontFamily(
    vazirmatn(FontWeight.Normal),
    vazirmatn(FontWeight.Medium),
    vazirmatn(FontWeight.SemiBold),
    vazirmatn(FontWeight.Bold),
)

/**
 * Persian is a connected script: tracking pulls joined letterforms apart and distorts the
 * measured width of a word, so every style keeps letterSpacing at zero. Line height is
 * trimmed to the text box rather than the font's Latin-first ascent, which otherwise leaves
 * Persian looking top-heavy inside its row.
 */
private val PersianLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun persian(
    size: Int,
    line: Int,
    weight: FontWeight = FontWeight.Normal,
    color: Color = Color.Unspecified,
) = TextStyle(
    fontFamily = Vazirmatn,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = 0.sp,
    color = color,
    lineHeightStyle = PersianLineHeight,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val AppTypography = Typography(
    headlineMedium = persian(29, 42, FontWeight.Bold),
    titleLarge = persian(20, 31, FontWeight.SemiBold),
    titleMedium = persian(16, 26, FontWeight.Medium),
    bodyLarge = persian(16, 27),
    bodyMedium = persian(14, 24),
    labelLarge = persian(14, 21, FontWeight.Medium),
    labelSmall = persian(12, 19, color = TextSecondary),
)

val MonoStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)

@Composable
fun TerminalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Scheme,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}

data class TerminalPalette(
    val id: String,
    val label: String,
    val background: Color,
    val foreground: Color,
    val accent: Color,
)

val TerminalPalettes = listOf(
    TerminalPalette("persian_neon", "نئون ایرانی", Color(0xFF05080B), Color(0xFFF0F6F8), Turquoise),
    TerminalPalette("oled", "مشکی OLED", Color(0xFF000000), Color(0xFFD6DEE6), Cyan),
    TerminalPalette("midnight", "نیمه‌شب", Color(0xFF09121E), Color(0xFFDCE8F2), Color(0xFF79A7FF)),
    TerminalPalette("solarized", "سولاریزد", Color(0xFF002B36), Color(0xFF93A1A1), Color(0xFFB58900)),
    TerminalPalette("classic", "سبز کلاسیک", Color(0xFF031008), Color(0xFF58F58A), Color(0xFF58F58A)),
    TerminalPalette("amber", "کهربایی", Color(0xFF100B04), Color(0xFFFFC66D), Amber),
)
