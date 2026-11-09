@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.marbledo.core.designsystem

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
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

/**
 * The single spacing scale for the whole app (docs/UI_COUNCIL_REVIEW.md, DS-2).
 *
 * Before this existed the three main screens used 29 distinct dp values, many off the 4dp grid
 * (9, 11, 13, 17, 19, 3.5…), which is why paddings and gaps never lined up. New layout code must
 * take its gaps and paddings from here instead of inventing literals.
 */
object MarbleSpacing {
    /** Hairline gaps inside a single visual unit (dot rows, stacked micro-labels). */
    val xxs = 2.dp

    /** Inner gaps between an icon and its label, or stacked lines of one row. */
    val xs = 4.dp

    /** Default gap between rows, chips and inline controls. */
    val sm = 8.dp

    /** Gap between blocks inside one card, and the vertical rhythm of lists. */
    val md = 12.dp

    /** Card content padding and the screen's horizontal margin. */
    val lg = 16.dp

    /** Section breaks and empty-state padding. */
    val xl = 24.dp

    /** Minimum interactive size for any touch target (Material / WCAG). */
    val touchTarget = 48.dp
}

val LocalReduceMotion = staticCompositionLocalOf { false }
val LocalAppFontScale = staticCompositionLocalOf { 1f }
val LocalNumeralMode = staticCompositionLocalOf { NumeralMode.PERSIAN }

internal val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF6258C8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7E3FF),
    onPrimaryContainer = Color(0xFF1C145C),
    secondary = Color(0xFF4E667A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE5F0),
    onSecondaryContainer = Color(0xFF1A2C3B),
    tertiary = Color(0xFF9A5265),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD9E1),
    onTertiaryContainer = Color(0xFF3E101F),
    background = Color(0xFFF7F5FB),
    surface = Color(0xFFFFFBFF),
    surfaceVariant = Color(0xFFECE8F2),
    surfaceContainerLow = Color(0xFFF9F6FD),
    surfaceContainer = Color(0xFFF2ECF5),
    surfaceContainerHigh = Color(0xFFEEE8F1),
    outline = Color(0xFF817C8C),
    outlineVariant = Color(0xFFC9C5D0),
    scrim = Color(0xFF1B1B1F).copy(alpha = 0.45f),
    error = Color(0xFFBA1A1A),
)

internal val GlassColors = LightColors.copy(
    background = Color(0xFFF2F3FA),
    surface = Color(0xF7FFFFFF),
    surfaceVariant = Color(0xDDE8E8F4),
    surfaceContainerLow = Color(0xFFF6F4FC),
    surfaceContainer = Color(0xFFF0EDF8),
    surfaceContainerHigh = Color(0xFFEDE8F5),
    primaryContainer = Color(0xFFE2DEFF),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFFC7BFFF),
    onPrimary = Color(0xFF302782),
    primaryContainer = Color(0xFF49409B),
    onPrimaryContainer = Color(0xFFE5DFFF),
    secondary = Color(0xFFB7C9DE),
    onSecondary = Color(0xFF2D3B4E),
    secondaryContainer = Color(0xFF35485C),
    onSecondaryContainer = Color(0xFFD6E4F7),
    tertiary = Color(0xFFFFB2C1),
    onTertiary = Color(0xFF5E172B),
    tertiaryContainer = Color(0xFF7B2E42),
    onTertiaryContainer = Color(0xFFFFD9E1),
    background = Color(0xFF141319),
    surface = Color(0xFF1C1B22),
    surfaceVariant = Color(0xFF34323A),
    surfaceContainerLow = Color(0xFF191820),
    surfaceContainer = Color(0xFF25242D),
    surfaceContainerHigh = Color(0xFF2E2C36),
    outline = Color(0xFF938F9A),
    outlineVariant = Color(0xFF4A474E),
    scrim = Color.Black.copy(alpha = 0.55f),
)

internal val AmoledColors = DarkColors.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF111116),
    surfaceContainer = Color(0xFF09090D),
    surfaceContainerLow = Color.Black,
    surfaceContainerHigh = Color(0xFF17161C),
    outline = Color(0xFF3A3840),
    outlineVariant = Color(0xFF25232A),
)

// Calm, low-glare palettes. Each named palette follows system light/dark mode while keeping
// the user's chosen hue; unlike DYNAMIC, these do not depend on Android 12+ wallpaper colors.
internal val OceanLight = lightColorScheme(
    primary = Color(0xFF006874), onPrimary = Color.White,
    primaryContainer = Color(0xFFA2EEFA), onPrimaryContainer = Color(0xFF001F24),
    secondary = Color(0xFF4A6268), onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDE7EC), onSecondaryContainer = Color(0xFF051F24),
    tertiary = Color(0xFF52607D), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD9E2FF), onTertiaryContainer = Color(0xFF0E1B36),
    background = Color(0xFFF3F8F9), surface = Color(0xFFFAFDFE),
    surfaceVariant = Color(0xFFDCE4E6), surfaceContainerLow = Color(0xFFF0F6F7),
    surfaceContainer = Color(0xFFEAF1F2), surfaceContainerHigh = Color(0xFFE4ECEE),
    outline = Color(0xFF6F797B), outlineVariant = Color(0xFFBEC8CA),
)
internal val OceanDark = darkColorScheme(
    primary = Color(0xFF6DD9E8), onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF004F58), onPrimaryContainer = Color(0xFFA2EEFA),
    secondary = Color(0xFFB1CBD0), onSecondary = Color(0xFF1C3439),
    secondaryContainer = Color(0xFF334B50), onSecondaryContainer = Color(0xFFCDE7EC),
    tertiary = Color(0xFFBAC6EA), onTertiary = Color(0xFF24304D),
    tertiaryContainer = Color(0xFF3A4664), onTertiaryContainer = Color(0xFFD9E2FF),
    background = Color(0xFF10191B), surface = Color(0xFF171F21),
    surfaceVariant = Color(0xFF3F484A), surfaceContainerLow = Color(0xFF151D1F),
    surfaceContainer = Color(0xFF20292B), surfaceContainerHigh = Color(0xFF2A3335),
    outline = Color(0xFF899395), outlineVariant = Color(0xFF3F484A),
)
internal val ForestLight = lightColorScheme(
    primary = Color(0xFF35693D), onPrimary = Color.White,
    primaryContainer = Color(0xFFB7F0B5), onPrimaryContainer = Color(0xFF002108),
    secondary = Color(0xFF52634E), onSecondary = Color.White,
    secondaryContainer = Color(0xFFD5E8CE), onSecondaryContainer = Color(0xFF101F0F),
    tertiary = Color(0xFF756038), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF2E1B9), onTertiaryContainer = Color(0xFF261A00),
    background = Color(0xFFF5F8F1), surface = Color(0xFFFCFDF7),
    surfaceVariant = Color(0xFFE0E5D9), surfaceContainerLow = Color(0xFFF1F5EC),
    surfaceContainer = Color(0xFFEBF0E5), surfaceContainerHigh = Color(0xFFE5EBDD),
    outline = Color(0xFF73796D), outlineVariant = Color(0xFFC4C9BC),
)
internal val ForestDark = darkColorScheme(
    primary = Color(0xFF9BD69A), onPrimary = Color(0xFF073911),
    primaryContainer = Color(0xFF1E5128), onPrimaryContainer = Color(0xFFB7F0B5),
    secondary = Color(0xFFB9CCB2), onSecondary = Color(0xFF253422),
    secondaryContainer = Color(0xFF3B4B37), onSecondaryContainer = Color(0xFFD5E8CE),
    tertiary = Color(0xFFD7C49A), onTertiary = Color(0xFF3F2E0B),
    tertiaryContainer = Color(0xFF59451F), onTertiaryContainer = Color(0xFFF2E1B9),
    background = Color(0xFF151A14), surface = Color(0xFF1C211A),
    surfaceVariant = Color(0xFF41483E), surfaceContainerLow = Color(0xFF191E17),
    surfaceContainer = Color(0xFF252B23), surfaceContainerHigh = Color(0xFF2F352D),
    outline = Color(0xFF90968A), outlineVariant = Color(0xFF41483E),
)
internal val SunsetLight = lightColorScheme(
    primary = Color(0xFF98440D), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBC7), onPrimaryContainer = Color(0xFF321000),
    secondary = Color(0xFF745944), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDCC0), onSecondaryContainer = Color(0xFF291807),
    tertiary = Color(0xFF6C5B7E), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF2DAFF), onTertiaryContainer = Color(0xFF251432),
    background = Color(0xFFFFF7F2), surface = Color(0xFFFFFBF8),
    surfaceVariant = Color(0xFFF0DED4), surfaceContainerLow = Color(0xFFFFF4EC),
    surfaceContainer = Color(0xFFF8ECE4), surfaceContainerHigh = Color(0xFFF2E5DD),
    outline = Color(0xFF81756E), outlineVariant = Color(0xFFD5C3B9),
)
internal val SunsetDark = darkColorScheme(
    primary = Color(0xFFFFB68C), onPrimary = Color(0xFF542000),
    primaryContainer = Color(0xFF76300A), onPrimaryContainer = Color(0xFFFFDBC7),
    secondary = Color(0xFFE5C1A6), onSecondary = Color(0xFF422C1A),
    secondaryContainer = Color(0xFF5B4230), onSecondaryContainer = Color(0xFFFFDCC0),
    tertiary = Color(0xFFD6BFE7), onTertiary = Color(0xFF3B2949),
    tertiaryContainer = Color(0xFF534060), onTertiaryContainer = Color(0xFFF2DAFF),
    background = Color(0xFF1D1714), surface = Color(0xFF251E1A),
    surfaceVariant = Color(0xFF51443D), surfaceContainerLow = Color(0xFF211A17),
    surfaceContainer = Color(0xFF2D2520), surfaceContainerHigh = Color(0xFF372E29),
    outline = Color(0xFF9A8D85), outlineVariant = Color(0xFF51443D),
)
internal val RoseLight = lightColorScheme(
    primary = Color(0xFF9B3F63), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E3), onPrimaryContainer = Color(0xFF3E001D),
    secondary = Color(0xFF75565D), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9E0), onSecondaryContainer = Color(0xFF2B151A),
    tertiary = Color(0xFF795A35), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDB8), onTertiaryContainer = Color(0xFF291800),
    background = Color(0xFFFFF7F8), surface = Color(0xFFFFFBFC),
    surfaceVariant = Color(0xFFF1DEE2), surfaceContainerLow = Color(0xFFFFF1F4),
    surfaceContainer = Color(0xFFF8E9ED), surfaceContainerHigh = Color(0xFFF2E2E6),
    outline = Color(0xFF827277), outlineVariant = Color(0xFFD6C2C7),
)
internal val RoseDark = darkColorScheme(
    primary = Color(0xFFFFB1C8), onPrimary = Color(0xFF5F1133),
    primaryContainer = Color(0xFF7E294A), onPrimaryContainer = Color(0xFFFFD9E3),
    secondary = Color(0xFFE5BDC5), onSecondary = Color(0xFF432930),
    secondaryContainer = Color(0xFF5C3F46), onSecondaryContainer = Color(0xFFFFD9E0),
    tertiary = Color(0xFFE9C08E), onTertiary = Color(0xFF452B08),
    tertiaryContainer = Color(0xFF60411D), onTertiaryContainer = Color(0xFFFFDDB8),
    background = Color(0xFF1E171A), surface = Color(0xFF261E21),
    surfaceVariant = Color(0xFF514348), surfaceContainerLow = Color(0xFF221A1D),
    surfaceContainer = Color(0xFF2E2528), surfaceContainerHigh = Color(0xFF382E32),
    outline = Color(0xFF9A8B90), outlineVariant = Color(0xFF514348),
)

/**
 * Resolves a theme name to its Material colour scheme.
 *
 * This is the **only** place the mapping from an app theme name to a colour scheme lives:
 * [MarbleTheme] uses it for the Compose tree, and [MarblePalettes] uses it for everything that
 * cannot read Compose colours — the Glance widgets and the RemoteViews notification — so one
 * theme change repaints all three instead of only the app.
 */
internal fun marbleColorScheme(mode: String, dark: Boolean, context: Context?): ColorScheme {
    val dynamic = mode == "DYNAMIC" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && context != null
    return when (mode) {
        "OCEAN" -> if (dark) OceanDark else OceanLight
        "FOREST" -> if (dark) ForestDark else ForestLight
        "SUNSET" -> if (dark) SunsetDark else SunsetLight
        "ROSE" -> if (dark) RoseDark else RoseLight
        "GLASS_LIGHT" -> GlassColors
        "AMOLED" -> AmoledColors
        "DARK" -> DarkColors
        "DYNAMIC" -> if (dynamic && dark) dynamicDarkColorScheme(context!!)
            else if (dynamic) dynamicLightColorScheme(context!!)
            else if (dark) DarkColors else LightColors
        else -> if (dynamic && dark) dynamicDarkColorScheme(context!!)
            else if (dynamic) dynamicLightColorScheme(context!!)
            else if (dark) DarkColors else LightColors
    }
}

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
    val dark = MarblePalettes.isDark(mode, systemDark)
    val scheme = marbleColorScheme(mode, dark, context)
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = baseDensity.density,
            fontScale = effectiveFontScale,
        ),
        LocalReduceMotion provides reduceMotion,
        LocalAppFontScale provides fontScale.coerceIn(0.8f, 1.5f),
        LocalNumeralMode provides numeralMode,
    ) {
        // Material 3 Expressive (material3 1.5.0-beta01): the same colour and type identity, but
        // every stock component now animates on the scheme's springs instead of fixed easing
        // curves — chips, switches, FAB, sheets, the navigation-bar indicator, ripples and state
        // layers all pick it up without touching their call sites. Reduced-motion users get the
        // utilitarian standard scheme so nothing bounces at them.
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = if (reduceMotion) MotionScheme.standard() else MotionScheme.expressive(),
            typography = marbleTypography(languageTag),
            // Council review (docs/UI_COUNCIL_REVIEW.md, DS-1): the old 12/16/22/30/36 scale was
            // roughly double the Material 3 scale and made every card and field look inflated.
            // 8/12/16/24/28 keeps the soft identity without the bloat.
            shapes = Shapes(
                extraSmall = RoundedCornerShape(8.dp),
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(16.dp),
                large = RoundedCornerShape(24.dp),
                extraLarge = RoundedCornerShape(28.dp),
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

    // Roles the screens used to improvise with `.copy(fontSize = …)` on the styles above
    // (docs/UI_COUNCIL_REVIEW.md, DS-3). They reuse existing weights, so the nine-weight
    // one-to-one mapping of the core roles is unchanged.

    /** Full-date line under the dashboard greeting — Vazirmatn Light (wght 300). */
    val dateCaption: TextStyle = TextStyle(
        fontFamily = MarbleFontFamily,
        fontWeight = FontWeight.Light,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    )

    /** Day-of-month numeral inside calendar cells — Vazirmatn SemiBold (wght 600), Light at rest. */
    val cellNumber: TextStyle = TextStyle(
        fontFamily = MarbleFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 18.sp,
    )

    /** Day numeral in agenda and occasion date chips — Vazirmatn Bold (wght 700). */
    val dayNumber: TextStyle = TextStyle(
        fontFamily = MarbleFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
    )

    /** Dense meta lines: triple-date captions and orbit fractions — Vazirmatn Medium (wght 500). */
    val microLabel: TextStyle = TextStyle(
        fontFamily = MarbleFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
    )
}

private val defaultTypography = Typography()

/**
 * The nine Vazirmatn weights in ascending order, used to step a style one weight up.
 *
 * `FontWeight`'s constructor is not public, so the ladder is spelled out with the named constants
 * rather than computed from `weight + 100`.
 */
private val weightLadder = listOf(
    FontWeight.Thin,
    FontWeight.ExtraLight,
    FontWeight.Light,
    FontWeight.Normal,
    FontWeight.Medium,
    FontWeight.SemiBold,
    FontWeight.Bold,
    FontWeight.ExtraBold,
    FontWeight.Black,
)

private fun TextStyle.oneWeightUp(): TextStyle {
    val current = weightLadder.indexOf(fontWeight ?: FontWeight.Normal).coerceAtLeast(0)
    return copy(fontWeight = weightLadder[(current + 1).coerceAtMost(weightLadder.lastIndex)])
}

/**
 * Fills the emphasized half of the Material 3 Expressive type scale.
 *
 * Expressive components ask for the `…Emphasized` slot when they want more weight; left at the
 * library default that slot resolves to the platform's Roboto emphasized token, which would break
 * the nine-weight Vazirmatn ladder mid-screen. Material's own rule is applied instead: same face
 * and same size, one weight step up.
 */
private fun Typography.emphasized(): Typography = copy(
    displayLargeEmphasized = displayLarge.oneWeightUp(),
    displayMediumEmphasized = displayMedium.oneWeightUp(),
    displaySmallEmphasized = displaySmall.oneWeightUp(),
    headlineLargeEmphasized = headlineLarge.oneWeightUp(),
    headlineMediumEmphasized = headlineMedium.oneWeightUp(),
    headlineSmallEmphasized = headlineSmall.oneWeightUp(),
    titleLargeEmphasized = titleLarge.oneWeightUp(),
    titleMediumEmphasized = titleMedium.oneWeightUp(),
    titleSmallEmphasized = titleSmall.oneWeightUp(),
    bodyLargeEmphasized = bodyLarge.oneWeightUp(),
    bodyMediumEmphasized = bodyMedium.oneWeightUp(),
    bodySmallEmphasized = bodySmall.oneWeightUp(),
    labelLargeEmphasized = labelLarge.oneWeightUp(),
    labelMediumEmphasized = labelMedium.oneWeightUp(),
    labelSmallEmphasized = labelSmall.oneWeightUp(),
)

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
).emphasized()

/** Vazirmatn backs the whole Persian scale; English keeps the platform family at the same weights. */
fun marbleTypography(languageTag: String): Typography =
    if (languageTag == "fa") PersianTypography else defaultTypography.emphasized()
