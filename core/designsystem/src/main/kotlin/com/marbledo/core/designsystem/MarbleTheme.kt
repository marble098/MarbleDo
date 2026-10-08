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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            typography = marbleTypography(languageTag),
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

/**
 * The nine registered Vazirmatn weights, each reserved for a specific role in the app:
 * Thin/ExtraLight/Light carry oversized display numerals where the large size keeps them
 * legible, Normal carries prose, Medium/SemiBold carry UI chrome, and Bold/ExtraBold/Black
 * carry titles from card level up to screen heroes.
 */
object MarbleTextStyles {
    /** Giant live-countdown digits — Vazirmatn Thin (wght 100). */
    val heroDigits: TextStyle = TextStyle(
        fontFamily = MarbleFontFamily,
        fontWeight = FontWeight.Thin,
        fontSize = 56.sp,
        lineHeight = 64.sp,
    )

    /** Calendar year numerals and month navigators — Vazirmatn ExtraLight (wght 200). */
    val yearDigits: TextStyle = TextStyle(
        fontFamily = MarbleFontFamily,
        fontWeight = FontWeight.ExtraLight,
        fontSize = 30.sp,
        lineHeight = 36.sp,
    )

    /** Large day-of-month numerals and date subtitles — Vazirmatn Light (wght 300). */
    val dateDigits: TextStyle = TextStyle(
        fontFamily = MarbleFontFamily,
        fontWeight = FontWeight.Light,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    )

    /** Long-form body copy — Vazirmatn Normal (wght 400). */
    val bodyPrimary: TextStyle = TextStyle(
        fontFamily = MarbleFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 24.sp,
    )

    /** Chips, metadata and captions — Vazirmatn Medium (wght 500). */
    val metaLabel: TextStyle = TextStyle(
        fontFamily = MarbleFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    )

    /** List and row titles — Vazirmatn SemiBold (wght 600). */
    val itemTitle: TextStyle = TextStyle(
        fontFamily = MarbleFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    )

    /** Card titles and emphasized counters — Vazirmatn Bold (wght 700). */
    val cardTitle: TextStyle = TextStyle(
        fontFamily = MarbleFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 26.sp,
    )

    /** Section headers across the app — Vazirmatn ExtraBold (wght 800). */
    val sectionTitle: TextStyle = TextStyle(
        fontFamily = MarbleFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    )

    /** Screen-level hero titles and greetings — Vazirmatn Black (wght 900). */
    val screenTitle: TextStyle = TextStyle(
        fontFamily = MarbleFontFamily,
        fontWeight = FontWeight.Black,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    )
}

private val defaultTypography = Typography()

/** Full M3 scale in which every one of the nine Vazirmatn weights has a dedicated slot. */
val PersianTypography: Typography = defaultTypography.copy(
    // Oversized display numerals run lightest so big digits stay elegant instead of heavy.
    displayLarge = defaultTypography.displayLarge.copy(fontFamily = MarbleFontFamily, fontWeight = FontWeight.Thin),
    displayMedium = defaultTypography.displayMedium.copy(fontFamily = MarbleFontFamily, fontWeight = FontWeight.ExtraLight),
    displaySmall = defaultTypography.displaySmall.copy(fontFamily = MarbleFontFamily, fontWeight = FontWeight.Light),
    // Titles climb the weight ladder toward the screen hero role.
    headlineLarge = defaultTypography.headlineLarge.copy(fontFamily = MarbleFontFamily, fontWeight = FontWeight.Black),
    headlineMedium = defaultTypography.headlineMedium.copy(fontFamily = MarbleFontFamily, fontWeight = FontWeight.ExtraBold),
    headlineSmall = defaultTypography.headlineSmall.copy(fontFamily = MarbleFontFamily, fontWeight = FontWeight.Bold),
    titleLarge = defaultTypography.titleLarge.copy(fontFamily = MarbleFontFamily, fontWeight = FontWeight.Bold),
    titleMedium = defaultTypography.titleMedium.copy(fontFamily = MarbleFontFamily, fontWeight = FontWeight.SemiBold),
    titleSmall = defaultTypography.titleSmall.copy(fontFamily = MarbleFontFamily, fontWeight = FontWeight.Medium),
    // Prose stays calm at Normal.
    bodyLarge = defaultTypography.bodyLarge.copy(fontFamily = MarbleFontFamily, fontWeight = FontWeight.Normal),
    bodyMedium = defaultTypography.bodyMedium.copy(fontFamily = MarbleFontFamily, fontWeight = FontWeight.Normal),
    bodySmall = defaultTypography.bodySmall.copy(fontFamily = MarbleFontFamily, fontWeight = FontWeight.Normal),
    // Chrome and labels carry Medium-to-SemiBold for small-size legibility.
    labelLarge = defaultTypography.labelLarge.copy(fontFamily = MarbleFontFamily, fontWeight = FontWeight.SemiBold),
    labelMedium = defaultTypography.labelMedium.copy(fontFamily = MarbleFontFamily, fontWeight = FontWeight.Medium),
    labelSmall = defaultTypography.labelSmall.copy(fontFamily = MarbleFontFamily, fontWeight = FontWeight.Medium),
)

/** Vazirmatn backs the whole Persian scale; English keeps the platform family at the same weights. */
fun marbleTypography(languageTag: String): Typography =
    if (languageTag == "fa") PersianTypography else defaultTypography
