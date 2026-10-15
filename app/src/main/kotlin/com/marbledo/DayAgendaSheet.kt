package com.marble098.marbledo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.marbledo.core.designsystem.MarbleTextStyles
import com.marbledo.domain.model.AppSettings
import com.marbledo.domain.model.Task
import com.marbledo.domain.util.DayAgendaPlanner
import com.marbledo.domain.util.TextNormalizer
import com.marbledo.feature.calendar.PersianDateUtils
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Suggestions remain drafts until the user confirms them in the normal task editor. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DayAgendaSheet(
    tasks: List<Task>,
    now: Instant,
    settings: AppSettings,
    onDraft: (Task) -> Unit,
    onOpenTask: (Task) -> Unit,
    onDismiss: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    var dayOffset by rememberSaveable { mutableIntStateOf(0) }
    var duration by rememberSaveable { mutableIntStateOf(25) }
    var startHour by rememberSaveable { mutableIntStateOf(9) }
    var endHour by rememberSaveable { mutableIntStateOf(18) }
    val date = now.atZone(zone).toLocalDate().plusDays(dayOffset.toLong())
    val plan = remember(tasks, date, now, zone, duration, startHour, endHour) {
        DayAgendaPlanner.plan(tasks, date, now, zone, duration, LocalTime.of(startHour, 0), LocalTime.of(endHour, 0))
    }
    fun digits(text: String) = TextNormalizer.formatDigits(text, settings.numeralMode)
    fun time(instant: Instant) = digits(instant.atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm")))
    val newTaskTitle = stringResource(R.string.agenda_new_task)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.agenda_title), style = MarbleTextStyles.sectionTitle)
            Text(stringResource(R.string.agenda_assumptions), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (0..6).forEach { offset ->
                    item {
                        val day = now.atZone(zone).toLocalDate().plusDays(offset.toLong())
                        val label = PersianDateUtils.fullDate(day.atStartOfDay(zone).toInstant().toEpochMilli(), settings.languageTag, settings.numeralMode, zone)
                        FilterChip(dayOffset == offset, { dayOffset = offset }, label = { Text(label) })
                    }
                }
            }
            Text(stringResource(R.string.agenda_duration), style = MaterialTheme.typography.titleSmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 25, 45, 60, 90).forEach { minutes -> item {
                    FilterChip(duration == minutes, { duration = minutes }, label = { Text(digits(stringResource(R.string.agenda_minutes, minutes))) })
                } }
            }
            Text(stringResource(R.string.agenda_work_window), style = MaterialTheme.typography.titleSmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(8 to 17, 9 to 18, 10 to 20).forEach { (start, end) -> item {
                    FilterChip(startHour == start && endHour == end, { startHour = start; endHour = end }, label = { Text(digits("$start:00 – $end:00")) })
                } }
            }
            Text(digits(stringResource(R.string.agenda_summary, plan.estimatedMinutes, plan.allDayTasks.size, plan.conflicts.size)),
                style = MaterialTheme.typography.bodyMedium)
            if (plan.conflicts.isNotEmpty()) {
                Text(stringResource(R.string.agenda_conflicts), style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error)
                plan.conflicts.take(5).forEach { conflict ->
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth().clickable { onOpenTask(conflict.first); onDismiss() }) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${conflict.first.title} / ${conflict.second.title}", style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(R.string.agenda_edit_conflict), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            Text(stringResource(R.string.agenda_free_slots), style = MaterialTheme.typography.titleSmall)
            if (plan.freeSlots.isEmpty()) Text(stringResource(R.string.agenda_no_slots), style = MaterialTheme.typography.bodyMedium)
            plan.freeSlots.forEach { slot ->
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${time(slot.start)} – ${time(slot.end)}", style = MarbleTextStyles.itemTitle.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr))
                        TextButton(onClick = {
                            onDraft(Task(title = newTaskTitle, dueAtEpochMillis = slot.end.toEpochMilli(), focusMinutes = duration,
                                countdownEnabled = false, countdownTheme = settings.countdownTheme))
                            onDismiss()
                        }) { Text(stringResource(R.string.agenda_use_slot)) }
                    }
                }
            }
            if (plan.allDayTasks.isNotEmpty()) {
                Text(stringResource(R.string.agenda_all_day), style = MaterialTheme.typography.titleSmall)
                plan.allDayTasks.take(8).forEach { task ->
                    TextButton(onClick = { onOpenTask(task); onDismiss() }) { Text(task.title) }
                }
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.agenda_close)) }
        }
    }
}
