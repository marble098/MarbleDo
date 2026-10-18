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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marbledo.core.designsystem.LocalNumeralMode
import com.marbledo.core.designsystem.MarbleTextStyles
import com.marbledo.domain.util.TextNormalizer

/**
 * Occasion Bloom — a playful, dense visual summary of the month's occasions.
 * Each occasion is a small "petal" colored by its category; holidays get a golden core.
 * Groups by week, with a horizontal bloom strip that is fully RTL-aware.
 *
 * This is the 3rd big new visual feature: it replaces the plain month grid's tiny 4dp dots
 * with an expressive, glanceable blossom field while retaining the precise grid for interaction.
 */
@Composable
fun OccasionBloomStrip(
    monthOccasions: List<Pair<Int, Occasion>>,
    languageTag: String,
    onOccasionClick: (Occasion) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (monthOccasions.isEmpty()) return
    val numeralMode = LocalNumeralMode.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            TextNormalizer.formatDigits("شکوفه‌های این ماه · ${monthOccasions.size} مناسبت", numeralMode),
            style = MarbleTextStyles.metaLabel,
            color = MaterialTheme.colorScheme.primary,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            reverseLayout = isRtl,
        ) {
            items(monthOccasions.take(24), key = { it.second.id }) { (day, occasion) ->
                BloomPetal(
                    day = day,
                    occasion = occasion,
                    languageTag = languageTag,
                    numeralMode = numeralMode,
                    onClick = { onOccasionClick(occasion) },
                )
            }
            if (monthOccasions.size > 24) {
                item(key = "bloom-more") {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(56.dp),
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
    numeralMode: com.marbledo.domain.model.NumeralMode,
    onClick: () -> Unit,
) {
    val bg = categoryBloomColor(occasion.category)
    val isHoliday = occasion.isHoliday
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bg.copy(alpha = 0.18f)),
        modifier = Modifier.size(width = 86.dp, height = 64.dp).clickable(onClick = onClick),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Box(
                Modifier.size(22.dp).clip(CircleShape).background(if (isHoliday) Color(0xFFFFCA28) else bg),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    TextNormalizer.formatDigits(day.toString(), numeralMode),
                    style = MarbleTextStyles.metaLabel.copy(fontSize = 11.sp, lineHeight = 11.sp),
                    color = if (isHoliday) Color(0xFF5D4037) else Color.White,
                    textAlign = TextAlign.Center,
                )
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

private fun categoryBloomColor(category: OccasionCategory): Color = when (category) {
    OccasionCategory.OFFICIAL -> Color(0xFF5B6ABF)
    OccasionCategory.NATIONAL -> Color(0xFF2E9E6B)
    OccasionCategory.RELIGIOUS -> Color(0xFF8E5A9E)
    OccasionCategory.PERSONAL -> Color(0xFFE07A3F)
    OccasionCategory.INTERNATIONAL -> Color(0xFF4BA3C7)
    OccasionCategory.EDUCATIONAL -> Color(0xFFD4A017)
}

@Composable
fun EnhancedDayBloomDot(
    occasions: List<Occasion>,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    if (occasions.isEmpty()) return
    val isHoliday = occasions.any(Occasion::isHoliday)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        occasions.take(4).forEach { occ ->
            val color = categoryBloomColor(occ.category)
            Box(
                Modifier
                    .size(if (occ.isHoliday) 6.dp else 5.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
        if (occasions.size > 4) {
            Text(
                "+${occasions.size - 4}",
                style = MarbleTextStyles.metaLabel.copy(fontSize = 8.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isHoliday) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFCA28).copy(alpha = 0.9f)),
            )
        }
    }
}
