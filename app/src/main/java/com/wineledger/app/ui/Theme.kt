package com.wineledger.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.wineledger.app.R

/** Extra colours that Material 3 has no slot for. */
data class WlColors(
    val paperWarm: Color,
    val paperDeep: Color,
    val gold: Color,
    val burgundy: Color,
    val green: Color,
    val muted: Color,
    val rule: Color,
    val red: Color,
    val white: Color,
    val rose: Color,
    val sparkling: Color,
    val dessert: Color,
)

val LocalWl = staticCompositionLocalOf<WlColors> { error("WlColors not provided") }

private val Paper = Color(0xFFF5F1E8)
private val Ink = Color(0xFF14110D)
private val Green = Color(0xFF1F5C3A)
private val Burgundy = Color(0xFF8E1B3E)
private val Gold = Color(0xFFB8935A)

private val LightScheme = lightColorScheme(
    primary = Green,
    onPrimary = Color(0xFFF5F1E8),
    primaryContainer = Color(0xFFD7E6DB),
    onPrimaryContainer = Color(0xFF0E2E1D),
    secondary = Burgundy,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1D9E0),
    onSecondaryContainer = Color(0xFF4A0D20),
    tertiary = Gold,
    onTertiary = Ink,
    tertiaryContainer = Color(0xFFF1E4CC),
    onTertiaryContainer = Color(0xFF3C2A0C),
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEBE4D3),
    onSurfaceVariant = Color(0xFF55503F),
    surfaceContainerLowest = Color(0xFFFBF8F1),
    surfaceContainerLow = Color(0xFFF8F4EA),
    surfaceContainer = Color(0xFFF1EBDC),
    surfaceContainerHigh = Color(0xFFEBE4D3),
    surfaceContainerHighest = Color(0xFFE5DDC9),
    outline = Color(0xFF8A8478),
    outlineVariant = Color(0xFFD9D1BD),
    error = Color(0xFFA11D2B),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF8CC7A1),
    onPrimary = Color(0xFF0B2A19),
    primaryContainer = Color(0xFF1F5C3A),
    onPrimaryContainer = Color(0xFFD7E6DB),
    secondary = Color(0xFFE59AAF),
    onSecondary = Color(0xFF47091C),
    secondaryContainer = Color(0xFF6B1432),
    onSecondaryContainer = Color(0xFFF7DCE4),
    tertiary = Color(0xFFD9B77C),
    onTertiary = Color(0xFF2E1F06),
    tertiaryContainer = Color(0xFF5A4318),
    onTertiaryContainer = Color(0xFFF1E4CC),
    background = Color(0xFF14110D),
    onBackground = Color(0xFFEFE8D8),
    surface = Color(0xFF14110D),
    onSurface = Color(0xFFEFE8D8),
    surfaceVariant = Color(0xFF2A251D),
    onSurfaceVariant = Color(0xFFC9C1AC),
    surfaceContainerLowest = Color(0xFF0F0D0A),
    surfaceContainerLow = Color(0xFF1A1712),
    surfaceContainer = Color(0xFF1F1B15),
    surfaceContainerHigh = Color(0xFF29241C),
    surfaceContainerHighest = Color(0xFF332D23),
    outline = Color(0xFF9A927F),
    outlineVariant = Color(0xFF454033),
    error = Color(0xFFFFB3B8),
)

private val LightWl = WlColors(
    paperWarm = Color(0xFFEBE4D3),
    paperDeep = Color(0xFFDDD3BD),
    gold = Gold,
    burgundy = Burgundy,
    green = Green,
    muted = Color(0xFF6E6858),
    rule = Color(0x1F14110D),
    red = Color(0xFF990044),
    white = Color(0xFFB8935A),
    rose = Color(0xFFD98090),
    sparkling = Color(0xFF076967),
    dessert = Color(0xFFC77B3A),
)

private val DarkWl = WlColors(
    paperWarm = Color(0xFF29241C),
    paperDeep = Color(0xFF332D23),
    gold = Color(0xFFD9B77C),
    burgundy = Color(0xFFE59AAF),
    green = Color(0xFF8CC7A1),
    muted = Color(0xFFA79F8B),
    rule = Color(0x33EFE8D8),
    red = Color(0xFFD9477A),
    white = Color(0xFFD9B77C),
    rose = Color(0xFFE5A0AE),
    sparkling = Color(0xFF3AA6A3),
    dessert = Color(0xFFE19A5C),
)

@OptIn(ExperimentalTextApi::class)
private fun frauncesWeight(w: Int) = Font(
    R.font.fraunces,
    weight = FontWeight(w),
    variationSettings = FontVariation.Settings(FontVariation.weight(w)),
)

@OptIn(ExperimentalTextApi::class)
private fun interWeight(w: Int) = Font(
    R.font.inter,
    weight = FontWeight(w),
    variationSettings = FontVariation.Settings(FontVariation.weight(w)),
)

val Fraunces = FontFamily(frauncesWeight(400), frauncesWeight(600), frauncesWeight(700))
val Inter = FontFamily(interWeight(400), interWeight(500), interWeight(600))

private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 40.sp),
    headlineLarge = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp),
)

@Composable
fun WineLedgerTheme(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalWl provides (if (dark) DarkWl else LightWl)) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = AppTypography,
            content = content,
        )
    }
}

val wl: WlColors
    @Composable get() = LocalWl.current

/** Colour of a wine style dot. */
@Composable
fun styleColor(style: String): Color = when (style) {
    "Red" -> wl.red
    "White" -> wl.white
    "Rosé" -> wl.rose
    "Sparkling" -> wl.sparkling
    "Dessert" -> wl.dessert
    else -> wl.muted
}
