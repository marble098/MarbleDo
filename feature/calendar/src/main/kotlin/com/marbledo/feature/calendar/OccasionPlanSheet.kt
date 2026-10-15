package com.marbledo.feature.calendar

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.marbledo.domain.util.TextNormalizer
import java.time.LocalDate
import com.marbledo.domain.model.Task
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AddTask
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marbledo.core.designsystem.LocalNumeralMode
import com.marbledo.core.designsystem.MarbleTextStyles

/**
 * Turns an occasion into something actionable in one tap: a preparation task, a task on the day
 * itself, a live countdown, or a favourite that later drives the pre-occasion reminder.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OccasionPlanSheet(
    occasion: Occasion,
    epochMillis: Long,
    languageTag: String,
    isFavorite: Boolean,
    prepDays: Int,
    onPlan: (OccasionPlanRequest) -> Unit,
    onToggleFavorite: () -> Unit,
    onDismiss: () -> Unit,
    references: List<CatalogReference> = emptyList(),
    educationModeEnabled: Boolean = false,
    tasks: List<Task> = emptyList(),
    holidays: Set<LocalDate> = emptySet(),
    thursdayClosed: Boolean = false,
    onCreatePreparation: (List<Task>) -> Unit = {},
) {
    var days by rememberSaveable(occasion.id) { mutableIntStateOf(prepDays.coerceIn(0, 30)) }
    var template by rememberSaveable(occasion.id) { mutableStateOf(PreparationTemplate.SIMPLE) }
    var showTimeline by rememberSaveable(occasion.id) { mutableStateOf(false) }
    if (showTimeline) {
        PreparationTimelineSheet(occasion, epochMillis, languageTag, tasks, holidays, thursdayClosed,
            onCreate = onCreatePreparation, onDismiss = { showTimeline = false; onDismiss() })
        return
    }
    val numeralMode = LocalNumeralMode.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(categoryColor(occasion.category)),
                )
                Text(
                    occasion.title(languageTag),
                    style = MarbleTextStyles.cardTitle,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(stringResource(evidenceLabelRes(occasion.evidence)), style = MaterialTheme.typography.labelMedium)
            Text(PersianDateUtils.fullDate(epochMillis, languageTag, numeralMode), style = MaterialTheme.typography.bodyMedium)
            if (occasion.note(languageTag).isNotBlank()) Text(occasion.note(languageTag), style = MaterialTheme.typography.bodySmall)
            if (occasion.calendar == OccasionCalendar.LUNAR) {
                Text(stringResource(R.string.catalog_lunar_caveat), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (occasion.durationDays > 1) {
                Text(TextNormalizer.formatDigits(stringResource(R.string.calendar_window_days, occasion.durationDays), numeralMode),
                    style = MaterialTheme.typography.bodySmall)
            }
            val cultureNames = occasion.cultures.map { stringResource(cultureLabelRes(it)) }
            Text(cultureNames.joinToString(" · "), style = MaterialTheme.typography.labelSmall)
            if (occasion.dateRule != OccasionDateRule.FIXED) {
                Text(stringResource(R.string.occasion_calculated_date), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary)
            }
            val eventDate = java.time.Instant.ofEpochMilli(epochMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            if (OccasionIntelligence.isPlanned(occasion, eventDate, tasks, java.time.ZoneId.systemDefault())) {
                Text(stringResource(R.string.radar_planned), style = MaterialTheme.typography.bodySmall)
            }
            PlanAction(icon = Icons.Outlined.Checklist, title = stringResource(R.string.timeline_title),
                subtitle = stringResource(R.string.timeline_short_hint), onClick = { showTimeline = true })
            Text(
                stringResource(R.string.calendar_plan_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PlanAction(
                icon = Icons.Outlined.EventAvailable,
                title = stringResource(R.string.calendar_plan_task_on_day),
                subtitle = PersianDateUtils.fullDate(epochMillis, languageTag, numeralMode),
                onClick = { onPlan(OccasionPlanner.planRequest(occasion, epochMillis, languageTag)) },
            )
            Text(stringResource(R.string.calendar_prepare_lead), style = MaterialTheme.typography.titleSmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
                items(listOf(0, 1, 3, 7, 14, 30)) { lead ->
                    FilterChip(selected = days == lead, onClick = { days = lead }, label = {
                        Text(TextNormalizer.formatDigits(stringResource(R.string.calendar_prepare_days, lead), numeralMode))
                    })
                }
            }
            val prepRequest = OccasionPlanner.planRequest(occasion, epochMillis, languageTag, daysBefore = days)
            if (prepRequest.dateIso?.let { LocalDate.parse(it) < LocalDate.now() } == true) {
                Text(stringResource(R.string.calendar_plan_past_warning), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
            PlanAction(
                icon = Icons.Outlined.Alarm,
                title = TextNormalizer.formatDigits(stringResource(R.string.calendar_plan_prepare, days), numeralMode),
                subtitle = stringResource(R.string.calendar_plan_prepare_hint),
                onClick = {
                    onPlan(OccasionPlanner.planRequest(occasion, epochMillis, languageTag, daysBefore = days))
                },
            )
            PlanAction(
                icon = Icons.Outlined.AddTask,
                title = stringResource(R.string.calendar_plan_countdown),
                subtitle = stringResource(R.string.calendar_plan_countdown_hint),
                onClick = {
                    onPlan(OccasionPlanner.planRequest(occasion, epochMillis, languageTag, asCountdown = true))
                },
            )
            HorizontalDivider()
            Text(stringResource(R.string.calendar_template_title), style = MaterialTheme.typography.titleSmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PreparationTemplate.entries.filter { educationModeEnabled || it != PreparationTemplate.SCHOOL }) { option ->
                    FilterChip(selected = template == option, onClick = { template = option },
                        label = { Text(stringResource(templateLabelRes(option))) })
                }
            }
            OccasionTemplates.checklist(template, languageTag).forEach { step ->
                Text("• $step", style = MaterialTheme.typography.bodySmall)
            }
            PlanAction(
                icon = Icons.Outlined.Checklist,
                title = stringResource(R.string.calendar_template_use),
                subtitle = stringResource(R.string.calendar_template_hint),
                onClick = {
                    onPlan(prepRequest.copy(
                        checklist = OccasionTemplates.checklist(template, languageTag),
                        description = occasion.note(languageTag),
                    ))
                },
            )
            PlanAction(
                icon = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                title = stringResource(if (isFavorite) R.string.calendar_favorite_remove else R.string.calendar_favorite_add),
                subtitle = stringResource(R.string.calendar_favorite_hint),
                onClick = onToggleFavorite,
            )
            if (references.isNotEmpty()) {
                Text(stringResource(R.string.catalog_references), style = MaterialTheme.typography.titleSmall)
                references.forEach { ReferenceLink(it, languageTag) }
            }
        }
    }
}

@Composable
private fun PlanAction(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MarbleTextStyles.itemTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun templateLabelRes(template: PreparationTemplate): Int = when (template) {
    PreparationTemplate.SIMPLE -> R.string.calendar_template_simple
    PreparationTemplate.FAMILY -> R.string.calendar_template_family
    PreparationTemplate.CULTURAL -> R.string.calendar_template_cultural
    PreparationTemplate.SCHOOL -> R.string.calendar_template_school
}
