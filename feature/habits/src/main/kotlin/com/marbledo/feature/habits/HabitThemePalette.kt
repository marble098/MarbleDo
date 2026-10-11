package com.marbledo.feature.habits

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.marbledo.domain.model.HabitTheme

/**
 * Colors for one habit look. Habit cards draw their own themed surface instead of borrowing the
 * app palette, so a habit's progress page can feel distinct from the rest of the app.
 *
 * [heat] holds the five heatmap shades, from "barely" to "fully", in that order.
 */
data class HabitPalette(
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val card: Color,
    val accent: Color,
    val accentSoft: Color,
    val track: Color,
    val onCard: Color,
    val muted: Color,
    val heat: List<Color>,
) {
    /** Heatmap shade for a 0..1 intensity, clamped so partial days can never ask for index 5. */
    fun heatColor(intensity: Float): Color {
        val index = (intensity.coerceIn(0f, 1f) * (heat.size - 1)).toInt()
        return heat[index.coerceIn(0, heat.size - 1)]
    }

    companion object {
        /** Neutral fallback so the widget can draw before any theme look-up happens. */
        val FALLBACK = HabitPalette(
            backgroundTop = Color(0xFF1A1730),
            backgroundBottom = Color(0xFF10222A),
            card = Color(0xFF221E3A),
            accent = Color(0xFFB7A8FF),
            accentSoft = Color(0xFF302A5C),
            track = Color(0xFF2C2748),
            onCard = Color(0xFFF4F1FF),
            muted = Color(0xFFA8A2C6),
            heat = listOf(
                Color(0xFF241F3E),
                Color(0xFF332B60),
                Color(0xFF4A3D96),
                Color(0xFF6C55CE),
                Color(0xFF9C85FF),
            ),
        )
    }
}

fun habitPalette(theme: HabitTheme, dark: Boolean): HabitPalette = when (theme) {
    HabitTheme.AURORA -> if (dark) {
        HabitPalette(
            backgroundTop = Color(0xFF1A1730),
            backgroundBottom = Color(0xFF10222A),
            card = Color(0xFF221E3A),
            accent = Color(0xFFB7A8FF),
            accentSoft = Color(0xFF302A5C),
            track = Color(0xFF2C2748),
            onCard = Color(0xFFF4F1FF),
            muted = Color(0xFFA8A2C6),
            heat = listOf(
                Color(0xFF241F3E),
                Color(0xFF332B60),
                Color(0xFF4A3D96),
                Color(0xFF6C55CE),
                Color(0xFF9C85FF),
            ),
        )
    } else {
        HabitPalette(
            backgroundTop = Color(0xFFF6F3FF),
            backgroundBottom = Color(0xFFE8F8F5),
            card = Color(0xFFFFFFFF),
            accent = Color(0xFF6A5AE0),
            accentSoft = Color(0xFFECE7FF),
            track = Color(0xFFE1DCF6),
            onCard = Color(0xFF1B1733),
            muted = Color(0xFF6E6890),
            heat = listOf(
                Color(0xFFEFECFB),
                Color(0xFFD2C8F8),
                Color(0xFFB0A0F1),
                Color(0xFF8671E4),
                Color(0xFF5A45C8),
            ),
        )
    }

    HabitTheme.EMBER -> if (dark) {
        HabitPalette(
            backgroundTop = Color(0xFF21120D),
            backgroundBottom = Color(0xFF2A1510),
            card = Color(0xFF2C1A14),
            accent = Color(0xFFFF9E76),
            accentSoft = Color(0xFF4A2418),
            track = Color(0xFF3A211A),
            onCard = Color(0xFFFFEDE4),
            muted = Color(0xFFC3A08E),
            heat = listOf(
                Color(0xFF2B1A15),
                Color(0xFF43251A),
                Color(0xFF6A3521),
                Color(0xFF9E4B27),
                Color(0xFFE07A46),
            ),
        )
    } else {
        HabitPalette(
            backgroundTop = Color(0xFFFFF5EE),
            backgroundBottom = Color(0xFFFFE9E3),
            card = Color(0xFFFFFFFF),
            accent = Color(0xFFD9542F),
            accentSoft = Color(0xFFFFE2D6),
            track = Color(0xFFF6DDD2),
            onCard = Color(0xFF33160C),
            muted = Color(0xFF8A6252),
            heat = listOf(
                Color(0xFFFDEDE5),
                Color(0xFFF8CDBB),
                Color(0xFFF2A183),
                Color(0xFFE4744A),
                Color(0xFFC24A22),
            ),
        )
    }

    HabitTheme.GARDEN -> if (dark) {
        HabitPalette(
            backgroundTop = Color(0xFF0F1F18),
            backgroundBottom = Color(0xFF10241C),
            card = Color(0xFF16291F),
            accent = Color(0xFF6FE0A6),
            accentSoft = Color(0xFF1E3A2B),
            track = Color(0xFF1F3428),
            onCard = Color(0xFFE8F7EE),
            muted = Color(0xFF93B7A4),
            heat = listOf(
                Color(0xFF16281F),
                Color(0xFF1E3A2B),
                Color(0xFF2C5B44),
                Color(0xFF3F8560),
                Color(0xFF62B98B),
            ),
        )
    } else {
        HabitPalette(
            backgroundTop = Color(0xFFF0FBF3),
            backgroundBottom = Color(0xFFE6F7EE),
            card = Color(0xFFFFFFFF),
            accent = Color(0xFF1F8A5B),
            accentSoft = Color(0xFFDCF5E6),
            track = Color(0xFFD5EDDF),
            onCard = Color(0xFF0F2A1E),
            muted = Color(0xFF5C7F6D),
            heat = listOf(
                Color(0xFFEAF7EF),
                Color(0xFFC6EBD5),
                Color(0xFF94D8B0),
                Color(0xFF58BE8A),
                Color(0xFF26996A),
            ),
        )
    }

    HabitTheme.MIDNIGHT -> if (dark) {
        HabitPalette(
            backgroundTop = Color(0xFF0E1526),
            backgroundBottom = Color(0xFF101B33),
            card = Color(0xFF16203A),
            accent = Color(0xFF93B4FF),
            accentSoft = Color(0xFF1E2C4E),
            track = Color(0xFF1E2A46),
            onCard = Color(0xFFEAF0FF),
            muted = Color(0xFF98A8C8),
            heat = listOf(
                Color(0xFF151E33),
                Color(0xFF1E2A47),
                Color(0xFF2C3D68),
                Color(0xFF3E5590),
                Color(0xFF6E8BD6),
            ),
        )
    } else {
        HabitPalette(
            backgroundTop = Color(0xFFF2F5FF),
            backgroundBottom = Color(0xFFE7EEFB),
            card = Color(0xFFFFFFFF),
            accent = Color(0xFF2F5BD0),
            accentSoft = Color(0xFFE2EAFF),
            track = Color(0xFFD8E2F8),
            onCard = Color(0xFF0F1B36),
            muted = Color(0xFF5E6E92),
            heat = listOf(
                Color(0xFFEEF2FD),
                Color(0xFFD3DEFA),
                Color(0xFFAEC1F2),
                Color(0xFF7B96E4),
                Color(0xFF4C6BCB),
            ),
        )
    }

    HabitTheme.BLOOM -> if (dark) {
        HabitPalette(
            backgroundTop = Color(0xFF20111A),
            backgroundBottom = Color(0xFF2A1522),
            card = Color(0xFF2C1926),
            accent = Color(0xFFFF9DC0),
            accentSoft = Color(0xFF472030),
            track = Color(0xFF3A1C29),
            onCard = Color(0xFFFFEDF3),
            muted = Color(0xFFC79FB0),
            heat = listOf(
                Color(0xFF2A1622),
                Color(0xFF40202F),
                Color(0xFF642F45),
                Color(0xFF94445F),
                Color(0xFFD1648B),
            ),
        )
    } else {
        HabitPalette(
            backgroundTop = Color(0xFFFFF3F8),
            backgroundBottom = Color(0xFFFDEAF2),
            card = Color(0xFFFFFFFF),
            accent = Color(0xFFC7487F),
            accentSoft = Color(0xFFFFE1EC),
            track = Color(0xFFF6DCE7),
            onCard = Color(0xFF331224),
            muted = Color(0xFF8C5D73),
            heat = listOf(
                Color(0xFFFDEDF4),
                Color(0xFFF9CADD),
                Color(0xFFF2A2C0),
                Color(0xFFE2739C),
                Color(0xFFC44A79),
            ),
        )
    }

    HabitTheme.GLASS -> if (dark) {
        HabitPalette(
            backgroundTop = Color(0xFF101518),
            backgroundBottom = Color(0xFF131C20),
            card = Color(0xFF182227),
            accent = Color(0xFF7FD7E4),
            accentSoft = Color(0xFF1E2F34),
            track = Color(0xFF213035),
            onCard = Color(0xFFE6F1F4),
            muted = Color(0xFF94A9AF),
            heat = listOf(
                Color(0xFF182329),
                Color(0xFF22333A),
                Color(0xFF2F4C56),
                Color(0xFF44707D),
                Color(0xFF6FA8B6),
            ),
        )
    } else {
        HabitPalette(
            backgroundTop = Color(0xFFF5F7F9),
            backgroundBottom = Color(0xFFEAEFF3),
            card = Color(0xFFFFFFFF),
            accent = Color(0xFF2C6E7A),
            accentSoft = Color(0xFFDDEEF1),
            track = Color(0xFFDCE4E9),
            onCard = Color(0xFF122024),
            muted = Color(0xFF5F7278),
            heat = listOf(
                Color(0xFFEEF2F4),
                Color(0xFFD8E2E6),
                Color(0xFFB4C7CD),
                Color(0xFF82A3AC),
                Color(0xFF4C7883),
            ),
        )
    }
}

@Composable
fun habitThemeLabel(theme: HabitTheme): String = stringResource(
    when (theme) {
        HabitTheme.AURORA -> R.string.habit_theme_aurora
        HabitTheme.EMBER -> R.string.habit_theme_ember
        HabitTheme.GARDEN -> R.string.habit_theme_garden
        HabitTheme.MIDNIGHT -> R.string.habit_theme_midnight
        HabitTheme.BLOOM -> R.string.habit_theme_bloom
        HabitTheme.GLASS -> R.string.habit_theme_glass
    },
)

@Composable
fun habitThemeCaption(theme: HabitTheme): String = stringResource(
    when (theme) {
        HabitTheme.AURORA -> R.string.habit_theme_caption_aurora
        HabitTheme.EMBER -> R.string.habit_theme_caption_ember
        HabitTheme.GARDEN -> R.string.habit_theme_caption_garden
        HabitTheme.MIDNIGHT -> R.string.habit_theme_caption_midnight
        HabitTheme.BLOOM -> R.string.habit_theme_caption_bloom
        HabitTheme.GLASS -> R.string.habit_theme_caption_glass
    },
)
