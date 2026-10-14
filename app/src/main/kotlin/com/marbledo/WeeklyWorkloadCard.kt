package com.marble098.marbledo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.marbledo.core.designsystem.LocalNumeralMode
import com.marbledo.core.designsystem.MarbleTextStyles
import com.marbledo.domain.model.Task
import com.marbledo.domain.util.PlanningInsights
import com.marbledo.domain.util.TextNormalizer
import com.marbledo.feature.calendar.PersianDateUtils
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
internal fun WeeklyWorkloadCard(
    tasks: List<Task>,
    now: Instant,
    zone: ZoneId,
    languageTag: String,
    onOpenTask: (Task) -> Unit,
    onAdd: (LocalDate) -> Unit,
) {
    val numeralMode = LocalNumeralMode.current
    val today = remember(now, zone) { now.atZone(zone).toLocalDate() }
    val review = remember(tasks, today, zone) {
        PlanningInsights.week(tasks, today.atStartOfDay(zone).toInstant(), zone)
    }
    var selectedDateIso by rememberSaveable { mutableStateOf<String?>(null) }
    var showBacklog by rememberSaveable { mutableStateOf(false) }
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.weekly_title), style = MarbleTextStyles.cardTitle)
            Text(
                TextNormalizer.formatDigits(stringResource(R.string.weekly_summary, review.scheduledOpen, review.overdue, review.unscheduled), numeralMode),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                items(review.days, key = { it.date.toString() }) { day ->
                    val isToday = day.date == today
                    val epoch = remember(day.date, zone) { day.date.atStartOfDay(zone).toInstant().toEpochMilli() }
                    val persian = remember(epoch, zone) { PersianDateUtils.of(epoch, zone) }
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = if (isToday) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.clickable { selectedDateIso = day.date.toString(); showBacklog = false },
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                PersianDateUtils.weekdayName(persian.weekdayIndex, languageTag),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                TextNormalizer.formatDigits("${persian.day} ${PersianDateUtils.monthName(persian.month, languageTag)}", numeralMode),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                TextNormalizer.formatDigits(stringResource(R.string.weekly_day_open, day.open), numeralMode),
                                style = MaterialTheme.typography.titleSmall,
                                color = if (day.open > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                TextNormalizer.formatDigits(stringResource(R.string.weekly_day_done, day.completed), numeralMode),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Text(stringResource(R.string.weekly_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (review.overdue + review.unscheduled > 0) {
                TextButton(onClick = { showBacklog = true; selectedDateIso = null }) { Text(stringResource(R.string.weekly_backlog)) }
            }
        }
    }
    val selectedDate = selectedDateIso?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    if (selectedDate != null || showBacklog) {
        val rows = tasks.filter { task ->
            if (task.isArchived) false else {
                val dueMillis = task.dueAtEpochMillis
                val date = dueMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
                if (showBacklog) !task.isCompleted && (date == null ||
                    if (task.isAllDay) date < today else (dueMillis ?: Long.MAX_VALUE) < now.toEpochMilli())
                else date == selectedDate
            }
        }.sortedWith(compareBy({ it.isCompleted }, { it.dueAtEpochMillis ?: Long.MAX_VALUE }, { it.id }))
        fun close() { selectedDateIso = null; showBacklog = false }
        AlertDialog(
            onDismissRequest = ::close,
            title = { Text(if (showBacklog || selectedDate == null) stringResource(R.string.weekly_backlog) else
                PersianDateUtils.fullDate(selectedDate.atStartOfDay(zone).toInstant().toEpochMilli(), languageTag, numeralMode, zone)) },
            text = {
                if (rows.isEmpty()) Text(stringResource(R.string.weekly_empty)) else LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(rows, key = { it.id }) { task ->
                        TextButton(onClick = { close(); onOpenTask(task) }) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(task.title)
                                if (task.isCompleted) Text(stringResource(R.string.weekly_task_done), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (selectedDate != null) TextButton(onClick = { close(); onAdd(selectedDate) }) { Text(stringResource(R.string.weekly_add)) }
            },
            dismissButton = { TextButton(onClick = ::close) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}
