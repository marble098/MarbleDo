package com.marbledo.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddTask
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marbledo.core.designsystem.LocalNumeralMode
import com.marbledo.core.designsystem.MarbleTextStyles
import com.marbledo.domain.model.Task
import com.marbledo.domain.util.TextNormalizer
import java.time.ZoneId

@Composable
fun OccasionRadarCard(
    index: OccasionIndex,
    years: List<Int>,
    nowMillis: Long,
    tasks: List<Task>,
    favoriteIds: Set<String>,
    languageTag: String,
    onOpen: (OccasionRecommendation) -> Unit,
    modifier: Modifier = Modifier,
) {
    var horizon by rememberSaveable { mutableIntStateOf(30) }
    var includePlanned by rememberSaveable { mutableStateOf(false) }
    val numeral = LocalNumeralMode.current
    val zone = ZoneId.systemDefault()
    val colors = MaterialTheme.colorScheme
    val recommendations = remember(index, years, nowMillis, tasks, favoriteIds, horizon, includePlanned, zone) {
        val upcoming = OccasionPlanner.upcoming(index, years, nowMillis, favoriteIds, false, Int.MAX_VALUE, zone)
        OccasionIntelligence.recommendations(upcoming, tasks, zone, horizon, Int.MAX_VALUE)
            .filter { includePlanned || !it.alreadyPlanned }.take(4)
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(shape = CircleShape, color = colors.primaryContainer) {
                    Icon(
                        Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.padding(8.dp).size(18.dp),
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.radar_title), style = MarbleTextStyles.cardTitle)
                    Text(
                        stringResource(R.string.radar_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7, 30, 90).forEach { days ->
                    item {
                        FilterChip(
                            selected = horizon == days,
                            onClick = { horizon = days },
                            label = {
                                Text(TextNormalizer.formatDigits(stringResource(R.string.radar_horizon, days), numeral))
                            },
                        )
                    }
                }
                item {
                    FilterChip(
                        selected = includePlanned,
                        onClick = { includePlanned = !includePlanned },
                        label = { Text(stringResource(R.string.radar_include_planned)) },
                    )
                }
            }
            if (recommendations.isEmpty()) {
                Text(
                    stringResource(R.string.radar_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            recommendations.forEach { recommendation ->
                val upcoming = recommendation.upcoming
                val accent = categoryColor(upcoming.occasion.category)
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = colors.surfaceContainer.copy(alpha = 0.72f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .clickable { onOpen(recommendation) },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            Modifier
                                .width(4.dp)
                                .height(36.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(accent),
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                upcoming.occasion.title(languageTag),
                                style = MarbleTextStyles.itemTitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                stringResource(reasonLabel(recommendation.reason)),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                if (recommendation.alreadyPlanned) stringResource(R.string.radar_planned)
                                else TextNormalizer.formatDigits(
                                    stringResource(R.string.radar_prepare, recommendation.preparationDays),
                                    numeral,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (recommendation.alreadyPlanned) colors.tertiary else colors.primary,
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = colors.primaryContainer,
                        ) {
                            Text(
                                TextNormalizer.formatDigits(
                                    stringResource(R.string.radar_days, upcoming.daysUntil.toInt()),
                                    numeral,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            )
                        }
                        Icon(
                            Icons.Outlined.AddTask,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun reasonLabel(reason: OccasionRecommendationReason): Int = when (reason) {
    OccasionRecommendationReason.FAVORITE -> R.string.radar_reason_favorite
    OccasionRecommendationReason.TODAY -> R.string.radar_reason_today
    OccasionRecommendationReason.APPROACHING -> R.string.radar_reason_soon
    OccasionRecommendationReason.HOLIDAY -> R.string.radar_reason_holiday
    OccasionRecommendationReason.DISCOVER -> R.string.radar_reason_discover
}
