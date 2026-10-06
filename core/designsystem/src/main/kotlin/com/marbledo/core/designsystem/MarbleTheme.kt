package com.marbledo.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.marbledo.domain.model.NumeralMode
import com.marble098.marbledo.core.designsystem.R

val LocalReduceMotion = staticCompositionLocalOf { false }
val LocalAppFontScale = staticCompositionLocalOf { 1f }
val LocalNumeralMode = staticCompositionLocalOf { NumeralMode.PERSIAN }

private val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF6258C8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7E3FF),
    onPrimaryContainer = Color(0xFF1C145C),
    secondary = Color(0xFF4E667A),
    onSecondary = Color.White,
    tertiary = Color(0xFF9A5265),
    background = Color(0xFFF7F5FB),
    surface = Color(0xFFFFFBFF),
    surfaceVariant = Color(0xFFECE8F2),
    outline = Color(0xFF817C8C),
    error = Color(0xFFBA1A1A),
)

private val GlassColors = LightColors.copy(
    background = Color(0xFFF2F3FA),
    surface = Color(0xF7FFFFFF),
    surfaceVariant = Color(0xDDE8E8F4),
    primaryContainer = Color(0xFFE2DEFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC7BFFF),
    onPrimary = Color(0xFF302782),
    primaryContainer = Color(0xFF49409B),
    onPrimaryContainer = Color(0xFFE5DFFF),
    secondary = Color(0xFFB7C9DE),
    tertiary = Color(0xFFFFB2C1),
    background = Color(0xFF141319),
    surface = Color(0xFF1C1B22),
    surfaceVariant = Color(0xFF34323A),
    outline = Color(0xFF938F9A),
)

private val AmoledColors = DarkColors.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF111116),
    surfaceContainer = Color(0xFF09090D),
    surfaceContainerLow = Color.Black,
    surfaceContainerHigh = Color(0xFF17161C),
)

@Composable
fun MarbleTheme(
    mode: String,
    reduceMotion: Boolean = false,
    fontScale: Float = 1f,
    numeralMode: NumeralMode = NumeralMode.PERSIAN,
    languageTag: String = "fa",
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val baseDensity = LocalDensity.current
    val effectiveFontScale = baseDensity.fontScale * fontScale.coerceIn(0.8f, 1.5f)
    val systemDark = isSystemInDarkTheme()
    val dynamic = mode == "DYNAMIC" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val dark = when (mode) {
        "DARK", "AMOLED" -> true
        "GLASS_LIGHT" -> false
        else -> systemDark
    }
    val scheme = when {
        dynamic && dark -> dynamicDarkColorScheme(context)
        dynamic && !dark -> dynamicLightColorScheme(context)
        mode == "GLASS_LIGHT" -> GlassColors
        mode == "AMOLED" -> AmoledColors
        dark -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = baseDensity.density,
            fontScale = effectiveFontScale,
        ),
        LocalReduceMotion provides reduceMotion,
        LocalAppFontScale provides fontScale.coerceIn(0.8f, 1.5f),
        LocalNumeralMode provides numeralMode,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = if (languageTag == "fa") PersianTypography else Typography(),
            shapes = Shapes(
                extraSmall = RoundedCornerShape(12.dp),
                small = RoundedCornerShape(16.dp),
                medium = RoundedCornerShape(22.dp),
                large = RoundedCornerShape(30.dp),
                extraLarge = RoundedCornerShape(36.dp),
            ),
            content = content,
        )
    }
}

/**
 * Vazirmatn's single bundled variable face is registered at every common `wght` axis value.
 * Compose can then pick a real variable weight instead of synthesizing bold text.
 */
@OptIn(ExperimentalTextApi::class)
val MarbleFontFamily: FontFamily = FontFamily(
    Font(R.font.vazirmatn_variable, weight = FontWeight.Thin, variationSettings = FontVariation.Settings(FontVariation.weight(100))),
    Font(R.font.vazirmatn_variable, weight = FontWeight.ExtraLight, variationSettings = FontVariation.Settings(FontVariation.weight(200))),
    Font(R.font.vazirmatn_variable, weight = FontWeight.Light, variationSettings = FontVariation.Settings(FontVariation.weight(300))),
    Font(R.font.vazirmatn_variable, weight = FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.vazirmatn_variable, weight = FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.vazirmatn_variable, weight = FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.vazirmatn_variable, weight = FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.vazirmatn_variable, weight = FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
    Font(R.font.vazirmatn_variable, weight = FontWeight.Black, variationSettings = FontVariation.Settings(FontVariation.weight(900))),
)

private val defaultTypography = Typography()
private val PersianTypography = defaultTypography.copy(
    displayLarge = defaultTypography.displayLarge.copy(fontFamily = MarbleFontFamily, fontWeight = FontWeight.Light),
    displayMedium = defaultTypography.displayMedium.copy(fontFamily = MarbleFontFamily, fontWeight = FontWeight.Normal),
    displaySmall = defaultTypography.displaySmall.copy(fontFamily = MarbleFontFamily, fontWeight = FontWeight.Normal),
    headlineLarge = defaultTypography.headlineLarge.copy(fontFamily = MarbleFontFamily, fontWeight = FontWeight.SemiBold),
    headlineMedium = defaultTypography.headlineMedium.copy(fontFamily = MarbleFontFamily, fontWeight = FontWeight.SemiBold),
    headlineSmall = defaultTypography.headlineSmall.copy(fontFamily = MarbleFontFamily, fontWeight = FontWeight.SemiBold),
    titleLarge = defaultTypography.titleLarge.copy(fontFamily = MarbleFontFamily, fontWeight = FontWeight.SemiBold),
    titleMedium = defaultTypography.titleMedium.copy(fontFamily = MarbleFontFamily, fontWeight = FontWeight.Medium),
    titleSmall = defaultTypography.titleSmall.copy(fontFamily = MarbleFontFamily, fontWeight = FontWeight.Medium),
    bodyLarge = defaultTypography.bodyLarge.copy(fontFamily = MarbleFontFamily, fontWeight = FontWeight.Normal),
    bodyMedium = defaultTypography.bodyMedium.copy(fontFamily = MarbleFontFamily, fontWeight = FontWeight.Normal),
    bodySmall = defaultTypography.bodySmall.copy(fontFamily = MarbleFontFamily, fontWeight = FontWeight.Normal),
    labelLarge = defaultTypography.labelLarge.copy(fontFamily = MarbleFontFamily, fontWeight = FontWeight.Medium),
    labelMedium = defaultTypography.labelMedium.copy(fontFamily = MarbleFontFamily, fontWeight = FontWeight.Medium),
    labelSmall = defaultTypography.labelSmall.copy(fontFamily = MarbleFontFamily, fontWeight = FontWeight.Medium),
)
