package com.marble098.marbledo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
    val colors = MaterialTheme.colorScheme
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(shape = CircleShape, color = colors.primaryContainer) {
                    Icon(
                        Icons.Outlined.DateRange,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.padding(8.dp).size(18.dp),
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.weekly_title), style = MarbleTextStyles.cardTitle)
                    Text(
                        TextNormalizer.formatDigits(
                            stringResource(R.string.weekly_summary, review.scheduledOpen, review.overdue, review.unscheduled),
                            numeralMode,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                if (review.overdue + review.unscheduled > 0) {
                    Surface(
                        shape = CircleShape,
                        color = if (review.overdue > 0) colors.errorContainer else colors.secondaryContainer,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { showBacklog = true; selectedDateIso = null },
                    ) {
                        Text(
                            stringResource(R.string.weekly_backlog),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (review.overdue > 0) colors.onErrorContainer else colors.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
                items(review.days, key = { it.date.toString() }) { day ->
                    val isToday = day.date == today
                    val epoch = remember(day.date, zone) { day.date.atStartOfDay(zone).toInstant().toEpochMilli() }
                    val persian = remember(epoch, zone) { PersianDateUtils.of(epoch, zone) }
                    val total = day.open + day.completed
                    val ratio = if (total == 0) 0f else day.completed.toFloat() / total.toFloat()
                    val tileShape = MaterialTheme.shapes.large
                    Surface(
                        shape = tileShape,
                        color = if (isToday) colors.primaryContainer.copy(alpha = 0.72f)
                        else colors.surfaceVariant.copy(alpha = 0.50f),
                        modifier = Modifier
                            .widthIn(min = 104.dp)
                            .then(
                                if (isToday) Modifier.border(1.5.dp, colors.primary.copy(alpha = 0.65f), tileShape)
                                else Modifier,
                            )
                            .clip(tileShape)
                            .clickable { selectedDateIso = day.date.toString(); showBacklog = false },
                    ) {
                        Column(
                            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    PersianDateUtils.weekdayName(persian.weekdayIndex, languageTag),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isToday) colors.primary else colors.onSurfaceVariant,
                                )
                                if (isToday) {
                                    Box(Modifier.size(6.dp).clip(CircleShape).background(colors.primary))
                                }
                            }
                            Text(
                                TextNormalizer.formatDigits("${persian.day} ${PersianDateUtils.monthName(persian.month, languageTag)}", numeralMode),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                TextNormalizer.formatDigits(stringResource(R.string.weekly_day_open, day.open), numeralMode),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (day.open > 0) colors.primary else colors.onSurface,
                            )
                            Text(
                                TextNormalizer.formatDigits(stringResource(R.string.weekly_day_done, day.completed), numeralMode),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant,
                            )
                            LinearProgressIndicator(
                                progress = { ratio },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                            )
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.weekly_hint),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
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
