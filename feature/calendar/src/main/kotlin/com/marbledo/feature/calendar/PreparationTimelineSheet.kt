package com.marbledo.feature.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.marbledo.core.designsystem.LocalNumeralMode
import com.marbledo.core.designsystem.MarbleTextStyles
import com.marbledo.domain.model.CalendarDisplayMode
import com.marbledo.domain.model.Task
import com.marbledo.domain.util.TextNormalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PreparationTimelineSheet(
    occasion: Occasion,
    epochMillis: Long,
    languageTag: String,
    tasks: List<Task>,
    holidays: Set<LocalDate>,
    thursdayClosed: Boolean,
    onCreate: (List<Task>) -> Unit,
    onDismiss: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val eventDate = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    val numeral = LocalNumeralMode.current
    var avoidClosed by rememberSaveable { mutableStateOf(true) }
    val suggested = remember(occasion, eventDate, today, tasks, avoidClosed, holidays, thursdayClosed, languageTag) {
        PreparationTimeline.suggest(occasion, eventDate, today, tasks, languageTag, holidays, avoidClosed, thursdayClosed)
    }
    var selected by rememberSaveable(occasion.id, eventDate.toString()) {
        mutableStateOf(suggested.filter { !it.isPast && !it.alreadyExists && it.workingDateFound }.map { it.stage.key })
    }
    var titles by rememberSaveable(occasion.id, eventDate.toString()) { mutableStateOf(suggested.map { it.title }) }
    var dateOverrides by rememberSaveable(occasion.id, eventDate.toString()) { mutableStateOf(List(4) { "" }) }
    var editingStage by rememberSaveable { mutableStateOf<Int?>(null) }
    var pickerCalendar by rememberSaveable { mutableStateOf(CalendarDisplayMode.PERSIAN) }
    val steps = suggested.mapIndexed { i, step ->
        val date = dateOverrides[i].takeIf { it.isNotBlank() }?.let(LocalDate::parse) ?: step.date
        step.copy(title = titles[i], date = date, isPast = date < today, workingDateFound = step.workingDateFound || dateOverrides[i].isNotBlank())
    }
    val chosen = steps.filter { it.stage.key in selected && !it.isPast && !it.alreadyExists && it.workingDateFound }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.timeline_title), style = MarbleTextStyles.sectionTitle)
            Text(occasion.title(languageTag), style = MarbleTextStyles.itemTitle)
            Text(stringResource(R.string.timeline_hint), style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.timeline_avoid_closed), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(avoidClosed, onCheckedChange = { avoidClosed = it })
            }
            steps.forEachIndexed { i, step ->
                val enabled = !step.alreadyExists && !step.isPast && step.workingDateFound
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(step.stage.key in selected && enabled, enabled = enabled, onCheckedChange = {
                                selected = if (it) selected + step.stage.key else selected - step.stage.key
                            })
                            Text(stringResource(stageLabel(step.stage)), style = MaterialTheme.typography.titleSmall)
                        }
                        OutlinedTextField(titles[i], onValueChange = { value -> titles = titles.toMutableList().also { it[i] = value } },
                            modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.timeline_task_title)) },
                            isError = titles[i].isBlank(), enabled = !step.alreadyExists, maxLines = 3)
                        TextButton(onClick = { editingStage = i }, enabled = !step.alreadyExists) {
                            Text(PersianDateUtils.fullDate(step.date.atStartOfDay(zone).toInstant().toEpochMilli(), languageTag, numeral, zone))
                        }
                        if (step.date != step.originalDate && dateOverrides[i].isBlank()) {
                            Text(stringResource(R.string.timeline_shifted), style = MaterialTheme.typography.labelSmall)
                        }
                        if (!step.workingDateFound) Text(stringResource(R.string.timeline_no_workday),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        if (step.alreadyExists || step.isPast) {
                            Text(stringResource(if (step.alreadyExists) R.string.timeline_duplicate else R.string.timeline_past),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            Button(onClick = {
                onCreate(chosen.map { PreparationTimeline.draft(occasion, eventDate, it, languageTag, zone) })
                onDismiss()
            }, enabled = chosen.isNotEmpty() && chosen.all { it.title.isNotBlank() }, modifier = Modifier.fillMaxWidth()) {
                Text(TextNormalizer.formatDigits(stringResource(R.string.timeline_create, chosen.size), numeral))
            }
        }
    }
    editingStage?.let { i ->
        SchedulePickerDialog(stringResource(R.string.timeline_change_date), SchedulePickerMode.DATE_ONLY, steps[i].date, null,
            languageTag, numeral, true, pickerCalendar, { pickerCalendar = it },
            onConfirm = { date, _ -> dateOverrides = dateOverrides.toMutableList().also { it[i] = date.toString() }; editingStage = null },
            onDismiss = { editingStage = null }, allowAnyTime = false)
    }
}

private fun stageLabel(stage: PreparationStage): Int = when (stage) {
    PreparationStage.PLAN -> R.string.timeline_stage_plan
    PreparationStage.COORDINATE -> R.string.timeline_stage_coordinate
    PreparationStage.PREPARE -> R.string.timeline_stage_prepare
    PreparationStage.DAY -> R.string.timeline_stage_day
}
