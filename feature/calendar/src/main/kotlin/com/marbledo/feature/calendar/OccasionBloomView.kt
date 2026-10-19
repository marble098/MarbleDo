package com.marbledo.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marbledo.core.designsystem.LocalNumeralMode
import com.marbledo.core.designsystem.MarbleTextStyles
import com.marbledo.domain.model.NumeralMode
import com.marbledo.domain.util.TextNormalizer

/**
 * Occasion Bloom — a playful, dense visual summary of the month's occasions.
 * Each occasion is a small "petal" coloured by its category; holidays get a golden core.
 * Tapping a petal selects that day (and opens its plan sheet), so the strip is decoration with a
 * purpose instead of a dead surface.
 *
 * The strip is direction-neutral: a LazyRow already lays out right-to-left in RTL, and the caller's
 * chronological order is preserved. (Forcing `reverseLayout` flipped the days backwards.)
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
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(monthOccasions.take(24), key = { it.second.id }) { (day, occasion) ->
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
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(width = 76.dp, height = 58.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
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
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier
            .size(width = 76.dp, height = 58.dp)
            .clickable(onClick = onClick),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(accent.copy(alpha = 0.24f), accent.copy(alpha = 0.08f)),
                    ),
                )
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    Modifier
                        .size(19.dp)
                        .clip(CircleShape)
                        .background(if (isHoliday) Color(0xFFFFC245) else accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        TextNormalizer.formatDigits(day.toString(), numeralMode),
                        style = MarbleTextStyles.metaLabel.copy(fontSize = 10.sp, lineHeight = 10.sp),
                        color = if (isHoliday) Color(0xFF4A3410) else Color.White,
                        textAlign = TextAlign.Center,
                    )
                }
                if (isHoliday) {
                    Text(
                        "★",
                        style = MarbleTextStyles.metaLabel.copy(fontSize = 9.sp, lineHeight = 9.sp),
                        color = Color(0xFFB57A00),
                    )
                }
            }
            Text(
                occasion.title(languageTag),
                style = MarbleTextStyles.metaLabel.copy(fontSize = 10.sp, lineHeight = 12.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )
        }
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
