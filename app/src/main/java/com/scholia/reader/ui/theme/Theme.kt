package com.scholia.reader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scholia.reader.R

// Palette from the Stitch design system (Stitch project "Android E-Book Reader").
private val LightColors = lightColorScheme(
    primary = Color(0xFF94451F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBCD),
    onPrimaryContainer = Color(0xFF79310C),
    secondary = Color(0xFF655D54),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE9DED2),
    onSecondaryContainer = Color(0xFF4C463D),
    tertiary = Color(0xFF6C5D33),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF6E1AC),
    onTertiaryContainer = Color(0xFF53451E),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFFDF9F1),
    onBackground = Color(0xFF1C1C17),
    surface = Color(0xFFFDF9F1),
    onSurface = Color(0xFF1C1C17),
    surfaceVariant = Color(0xFFE6E2DA),
    onSurfaceVariant = Color(0xFF55433C),
    surfaceTint = Color(0xFF974722),
    inverseSurface = Color(0xFF31302B),
    inverseOnSurface = Color(0xFFF4F0E8),
    inversePrimary = Color(0xFFFFB597),
    outline = Color(0xFF88736A),
    outlineVariant = Color(0xFFDBC1B8),
    surfaceBright = Color(0xFFFDF9F1),
    surfaceDim = Color(0xFFDDDAD2),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F3EB),
    surfaceContainer = Color(0xFFF1EDE6),
    surfaceContainerHigh = Color(0xFFECE8E0),
    surfaceContainerHighest = Color(0xFFE6E2DA),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB68A),
    onPrimary = Color(0xFF522300),
    primaryContainer = Color(0xFFE0884E),
    onPrimaryContainer = Color(0xFF2E1200),
    secondary = Color(0xFFFFB77D),
    onSecondary = Color(0xFF4D2600),
    secondaryContainer = Color(0xFF783F01),
    onSecondaryContainer = Color(0xFFFEAD6A),
    tertiary = Color(0xFF9FD1BB),
    onTertiary = Color(0xFF013828),
    tertiaryContainer = Color(0xFF77A893),
    onTertiaryContainer = Color(0xFF073D2D),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF141312),
    onBackground = Color(0xFFE6E1DF),
    surface = Color(0xFF141312),
    onSurface = Color(0xFFE6E1DF),
    surfaceVariant = Color(0xFF363433),
    onSurfaceVariant = Color(0xFFDAC2B5),
    surfaceTint = Color(0xFFFFB68A),
    inverseSurface = Color(0xFFE6E1DF),
    inverseOnSurface = Color(0xFF32302F),
    inversePrimary = Color(0xFF934B15),
    outline = Color(0xFFA28C81),
    outlineVariant = Color(0xFF54433A),
    surfaceBright = Color(0xFF3B3937),
    surfaceDim = Color(0xFF141312),
    surfaceContainerLowest = Color(0xFF0F0E0D),
    surfaceContainerLow = Color(0xFF1C1B1A),
    surfaceContainer = Color(0xFF211F1E),
    surfaceContainerHigh = Color(0xFF2B2A28),
    surfaceContainerHighest = Color(0xFF363433),
)

private fun merriweather(weight: Int, style: FontStyle = FontStyle.Normal) = Font(
    if (style == FontStyle.Italic) R.font.merriweather_italic else R.font.merriweather,
    weight = FontWeight(weight),
    style = style,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private fun jakarta(weight: Int) = Font(
    R.font.plus_jakarta_sans,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Merriweather = FontFamily(
    merriweather(300), merriweather(400), merriweather(700),
    merriweather(300, FontStyle.Italic), merriweather(400, FontStyle.Italic),
)

val PlusJakartaSans = FontFamily(jakarta(400), jakarta(500), jakarta(600), jakarta(700))

private val Base = Typography()

private val ScholiaTypography = Typography(
    displayLarge = TextStyle(fontFamily = Merriweather, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 40.sp),
    headlineLarge = TextStyle(fontFamily = Merriweather, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
    headlineMedium = TextStyle(fontFamily = Merriweather, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontFamily = Merriweather, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontFamily = Merriweather, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = Merriweather, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = Base.titleSmall.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.6.sp),
)

/** Quoted passages: italic Merriweather, as in the notes hub and note sheet. */
val QuoteStyle = TextStyle(
    fontFamily = Merriweather,
    fontStyle = FontStyle.Italic,
    fontWeight = FontWeight.Light,
    fontSize = 17.sp,
    lineHeight = 26.sp,
)

private val ScholiaShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun ScholiaTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = ScholiaTypography,
        shapes = ScholiaShapes,
        content = content,
    )
}
