package com.marbledo.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marbledo.core.designsystem.LocalNumeralMode
import com.marbledo.core.designsystem.MarbleTextStyles
import com.marbledo.domain.model.NumeralMode
import com.marbledo.domain.util.TextNormalizer

/**
 * Occasion Bloom — a compact, scannable horizontal ribbon of the month's occasions.
 * Each occasion is a sleek pill ("petal") coloured by its category; holidays get a golden badge.
 * Tapping a petal selects that day (and opens its plan sheet), so the strip is an interactive
 * index of the month rather than a tall block pushing the calendar grid down.
 *
 * The strip is direction-neutral: a LazyRow already lays out right-to-left in RTL, and the caller's
 * chronological order is preserved.
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
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
            Text(
                TextNormalizer.formatDigits(
                    stringResource(R.string.calendar_bloom_title, monthOccasions.size),
                    numeralMode,
                ),
                style = MarbleTextStyles.metaLabel,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (holidayCount > 0) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                ) {
                    Text(
                        TextNormalizer.formatDigits("$holidayCount ${stringResource(R.string.calendar_weekend_marker)}", numeralMode),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.height(38.dp),
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

@Composable
private fun BloomPetal(
    day: Int,
    occasion: Occasion,
    languageTag: String,
    numeralMode: NumeralMode,
    onClick: () -> Unit,
) {
    val accent = categoryColor(occasion.category)
    val isHoliday = occasion.isHoliday
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .height(38.dp)
            .widthIn(min = 84.dp, max = 168.dp)
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(accent.copy(alpha = 0.20f), accent.copy(alpha = 0.08f)),
                ),
            )
            .border(
                width = 1.dp,
                color = if (isHoliday) MaterialTheme.colorScheme.error.copy(alpha = 0.32f) else accent.copy(alpha = 0.22f),
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (isHoliday) MaterialTheme.colorScheme.error else accent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                TextNormalizer.formatDigits(day.toString(), numeralMode),
                style = MarbleTextStyles.metaLabel.copy(fontSize = 10.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold),
                color = Color.White,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            occasion.title(languageTag),
            style = MarbleTextStyles.metaLabel.copy(fontSize = 11.sp, lineHeight = 14.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * The compact dot strip under a day number. At most three category dots plus one holiday star, so
 * the strip can never grow wider than a 7-column calendar cell (the old `+N` label did).
 */
@Composable
fun EnhancedDayBloomDot(
    occasions: List<Occasion>,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    if (occasions.isEmpty()) return
    val isHoliday = occasions.any(Occasion::isHoliday)
    val dotColor = if (selected) MaterialTheme.colorScheme.onPrimary else null
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        occasions.take(if (isHoliday) 2 else 3).forEach { occ ->
            val color = dotColor ?: categoryColor(occ.category)
            Box(
                Modifier
                    .size(if (occ.isHoliday) 5.dp else 4.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
        if (isHoliday) {
            Box(
                Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(if (selected) MaterialTheme.colorScheme.onPrimary else Color(0xFFFFC245)),
            )
        }
    }
}
