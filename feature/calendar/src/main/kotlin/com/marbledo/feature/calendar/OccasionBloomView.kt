package com.marbledo.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.marbledo.core.designsystem.LocalNumeralMode
import com.marbledo.core.designsystem.MarbleSpacing
import com.marbledo.core.designsystem.MarbleTextStyles
import com.marbledo.domain.model.NumeralMode
import com.marbledo.domain.util.TextNormalizer

/**
 * Occasion Bloom — a compact, scannable horizontal ribbon of the month's occasions.
 * Each occasion is a pill ("petal") coloured by its category; holidays are outlined in the error
 * colour. Tapping a petal selects that day and opens its plan sheet, so the strip is an
 * interactive index of the month rather than a tall block pushing the calendar grid down.
 *
 * The strip is direction-neutral: a LazyRow already lays out right-to-left in RTL, and the caller's
 * chronological order is preserved. It ends with real trailing padding so the last petal is never
 * sliced in half at the screen edge (docs/UI_REFRESH.md, V-7).
 */
@Composable
fun OccasionBloomStrip(
    monthOccasions: List<Pair<Int, Occasion>>,
    languageTag: String,
    onOccasionClick: (Int, Occasion) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    if (monthOccasions.isEmpty()) return
    val numeralMode = LocalNumeralMode.current
    val holidayCount = monthOccasions.count { it.second.isHoliday }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MarbleSpacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MarbleSpacing.sm),
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
            Text(
                TextNormalizer.formatDigits(
                    stringResource(R.string.calendar_bloom_title, monthOccasions.size),
                    numeralMode,
                ),
                style = MarbleTextStyles.metaLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (holidayCount > 0) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                ) {
                    Text(
                        TextNormalizer.formatDigits("$holidayCount ${stringResource(R.string.calendar_weekend_marker)}", numeralMode),
                        style = MarbleTextStyles.microLabel,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MarbleSpacing.sm),
            contentPadding = PaddingValues(start = MarbleSpacing.xxs, end = MarbleSpacing.sm),
        ) {
            items(monthOccasions.take(24), key = { "${it.first}-${it.second.id}" }) { (day, occasion) ->
                BloomPetal(
                    day = day,
                    occasion = occasion,
                    languageTag = languageTag,
                    numeralMode = numeralMode,
                    onClick = { onOccasionClick(day, occasion) },
                )
            }
            if (monthOccasions.size > 24) {
                item(key = "bloom-more") {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.height(40.dp),
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                TextNormalizer.formatDigits("+${monthOccasions.size - 24}", numeralMode),
                                style = MarbleTextStyles.metaLabel,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One occasion pill: the day numeral in a category disc and the title. The title is allowed two
 * lines inside a wider pill, so ordinary occasion names no longer end in "…".
 */
@Composable
private fun BloomPetal(
    day: Int,
    occasion: Occasion,
    languageTag: String,
    numeralMode: NumeralMode,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val accent = categoryColor(occasion.category)
    val isHoliday = occasion.isHoliday
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .widthIn(min = 104.dp, max = 208.dp)
            .clip(shape)
            .background(accent.copy(alpha = 0.10f))
            .border(
                width = 1.dp,
                color = if (isHoliday) colors.error.copy(alpha = 0.30f) else accent.copy(alpha = 0.20f),
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = MarbleSpacing.sm, vertical = MarbleSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MarbleSpacing.sm),
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (isHoliday) colors.error else accent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                TextNormalizer.formatDigits(day.toString(), numeralMode),
                style = MarbleTextStyles.microLabel,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
        Text(
            occasion.title(languageTag),
            style = MarbleTextStyles.metaLabel,
            maxLines = 2,
            color = colors.onSurface,
            modifier = Modifier.widthIn(max = 152.dp),
        )
    }
}

/**
 * The compact dot strip under a day number. One dot per category, at most three in total, and a
 * filled dot on the leading edge when the day has tasks. The previous version drew up to four
 * dots in two sizes, which read as a row of grey dashes — "…" — under almost every day
 * (docs/UI_REFRESH.md, V-6).
 */
@Composable
fun EnhancedDayBloomDot(
    occasions: List<Occasion>,
    selected: Boolean,
    modifier: Modifier = Modifier,
    taskCount: Int = 0,
    maxDots: Int = 3,
) {
    if (occasions.isEmpty() && taskCount <= 0) return
    val colors = MaterialTheme.colorScheme
    val slots = maxDots.coerceAtLeast(1)
    // Distinct category colours only: two occasions of the same kind are one dot, not two, and a
    // day with tasks gives the first slot to the task marker.
    val accents = occasions
        .map { if (it.isHoliday) colors.error else categoryColor(it.category) }
        .distinct()
        .take(if (taskCount > 0) (slots - 1).coerceAtLeast(0) else slots)
    Row(
        modifier.height(8.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (taskCount > 0) {
            Box(
                Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(if (selected) colors.onPrimary else colors.primary),
            )
        }
        accents.forEach { color ->
            Box(
                Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(if (selected) colors.onPrimary else color),
            )
        }
    }
}
