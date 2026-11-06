package com.marbledo.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.marbledo.core.designsystem.LocalNumeralMode
import com.marbledo.core.designsystem.MarbleSpacing
import com.marbledo.core.designsystem.MarbleTextStyles
import com.marbledo.domain.model.NumeralMode
import com.marbledo.domain.model.Task
import com.marbledo.domain.util.TextNormalizer
import java.time.ZoneId

/**
 * Offline suggestions for occasions that are close enough to plan for. The rows are plain list
 * items separated by hairlines instead of tinted cards inside a card, so the card breathes and
 * a two-line occasion title no longer has to fit next to a day badge and an action icon
 * (docs/UI_REFRESH.md, V-8).
 */
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
    // Suggestions are a secondary block: on Home they start folded to a single line and only
    // unfold their filters and rows on request, so the page keeps a calm rhythm (V-3).
    var expanded by rememberSaveable { mutableStateOf(false) }
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
        Column(Modifier.padding(MarbleSpacing.lg), verticalArrangement = Arrangement.spacedBy(MarbleSpacing.md)) {
            val stateLabel = stringResource(if (expanded) R.string.calendar_state_expanded else R.string.calendar_state_collapsed)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(role = androidx.compose.ui.semantics.Role.Button) { expanded = !expanded }
                    .semantics { stateDescription = stateLabel },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MarbleSpacing.sm),
            ) {
                Surface(shape = CircleShape, color = colors.primaryContainer) {
                    Icon(
                        Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.padding(8.dp).size(18.dp),
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MarbleSpacing.xxs)) {
                    Text(stringResource(R.string.radar_title), style = MarbleTextStyles.cardTitle)
                    Text(
                        if (expanded) stringResource(R.string.radar_hint)
                        else TextNormalizer.formatDigits(stringResource(R.string.radar_collapsed_summary, recommendations.size), numeral),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                Surface(shape = CircleShape, color = colors.surfaceVariant.copy(alpha = 0.6f)) {
                    Icon(
                        if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.padding(6.dp).size(18.dp),
                    )
                }
            }
            if (expanded) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MarbleSpacing.xs),
                contentPadding = PaddingValues(start = MarbleSpacing.xxs, end = MarbleSpacing.md),
            ) {
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
            recommendations.forEachIndexed { position, recommendation ->
                if (position > 0) {
                    HorizontalDivider(color = colors.outline.copy(alpha = 0.12f))
                }
                RadarRow(
                    recommendation = recommendation,
                    languageTag = languageTag,
                    numeral = numeral,
                    onClick = { onOpen(recommendation) },
                )
            }
            }
        }
    }
}

@Composable
private fun RadarRow(
    recommendation: OccasionRecommendation,
    languageTag: String,
    numeral: NumeralMode,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val upcoming = recommendation.upcoming
    val accent = categoryColor(upcoming.occasion.category)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = MarbleSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MarbleSpacing.md),
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MarbleSpacing.xxs)) {
            Text(
                upcoming.occasion.title(languageTag),
                style = MarbleTextStyles.itemTitle,
                maxLines = 2,
            )
            Text(
                stringResource(reasonLabel(recommendation.reason)),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 2,
            )
            Text(
                if (recommendation.alreadyPlanned) stringResource(R.string.radar_planned)
                else TextNormalizer.formatDigits(
                    stringResource(R.string.radar_prepare, recommendation.preparationDays),
                    numeral,
                ),
                style = MarbleTextStyles.microLabel,
                color = if (recommendation.alreadyPlanned) colors.tertiary else colors.primary,
                maxLines = 1,
            )
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(MarbleSpacing.xs),
        ) {
            Surface(shape = CircleShape, color = colors.primaryContainer) {
                Text(
                    TextNormalizer.formatDigits(
                        stringResource(R.string.radar_days_short, upcoming.daysUntil.toInt()),
                        numeral,
                    ),
                    style = MarbleTextStyles.microLabel,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
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
