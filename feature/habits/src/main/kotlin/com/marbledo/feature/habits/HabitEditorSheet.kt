package com.marbledo.feature.habits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.marbledo.core.designsystem.LocalNumeralMode
import com.marbledo.domain.model.Habit
import com.marbledo.domain.model.HabitDifficulty
import com.marbledo.domain.model.HabitKind
import com.marbledo.domain.model.HabitSchedule
import com.marbledo.domain.model.HabitScheduleType
import com.marbledo.domain.model.HabitTheme
import com.marbledo.domain.model.HabitTracking
import com.marbledo.domain.model.NumeralMode
import com.marbledo.domain.util.TextNormalizer
import java.time.LocalDate
import java.time.LocalTime

/** Reminder presets offered in the editor, in minutes of day. */
private val REMINDER_PRESETS = listOf(Habit.NO_REMINDER, 7 * 60, 9 * 60, 13 * 60, 18 * 60, 21 * 60)

/**
 * Creates or edits a habit. Beyond the name and schedule it asks for the three things that make a
 * habit stick: a cue to attach it to, the identity it serves, and a reason to come back to.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HabitEditorSheet(
    habit: Habit?,
    kind: HabitKind,
    palette: HabitPalette,
    dark: Boolean,
    onSave: (Habit) -> Unit,
    onDelete: (Habit) -> Unit,
    onDismiss: () -> Unit,
) {
    val numeralMode = LocalNumeralMode.current
    val languageTag = if (LocalConfiguration.current.locales[0].language == "fa") "fa" else "en"
    val existing = habit != null
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by rememberSaveable { mutableStateOf(habit?.name.orEmpty()) }
    var icon by rememberSaveable { mutableStateOf(habit?.icon ?: Habit.DEFAULT_ICON) }
    var habitKind by rememberSaveable { mutableStateOf(habit?.kind ?: kind) }
    var tracking by rememberSaveable { mutableStateOf(habit?.tracking ?: HabitTracking.CHECK) }
    var scheduleType by rememberSaveable { mutableStateOf(habit?.schedule?.type ?: HabitScheduleType.EVERY_DAY) }
    var weekdays by rememberSaveable { mutableStateOf(habit?.schedule?.weekdays ?: setOf(1, 2, 3, 4, 5)) }
    var timesPerWeek by rememberSaveable { mutableIntStateOf(habit?.schedule?.timesPerWeek ?: 3) }
    var targetText by rememberSaveable { mutableStateOf((habit?.dailyTarget ?: 1).toString()) }
    var unit by rememberSaveable { mutableStateOf(habit?.unitLabel.orEmpty()) }
    var difficulty by rememberSaveable { mutableStateOf(habit?.difficulty ?: HabitDifficulty.MEDIUM) }
    var reminder by rememberSaveable { mutableIntStateOf(habit?.reminderMinuteOfDay ?: Habit.NO_REMINDER) }
    var cue by rememberSaveable { mutableStateOf(habit?.cue.orEmpty()) }
    var identity by rememberSaveable { mutableStateOf(habit?.identity.orEmpty()) }
    var why by rememberSaveable { mutableStateOf(habit?.why.orEmpty()) }
    var themeId by rememberSaveable { mutableStateOf(habit?.themeId ?: HabitTheme.DEFAULT.id) }
    var nameError by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
                .padding(bottom = 20.dp)
                .heightIn(max = 620.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                stringResource(if (existing) R.string.habit_editor_edit else R.string.habit_editor_new),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it; nameError = false },
                label = { Text(stringResource(R.string.habit_field_name)) },
                isError = nameError,
                supportingText = if (nameError) ({ Text(stringResource(R.string.habit_name_required)) }) else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))

            EditorLabel(stringResource(R.string.habit_field_kind))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = habitKind == HabitKind.BUILD,
                    onClick = { habitKind = HabitKind.BUILD },
                    label = { Text(stringResource(R.string.habit_kind_build)) },
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = habitKind == HabitKind.QUIT,
                    onClick = { habitKind = HabitKind.QUIT },
                    label = { Text(stringResource(R.string.habit_kind_quit)) },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(14.dp))

            EditorLabel(stringResource(R.string.habit_field_icon))
            HabitIconPicker(selected = icon, onSelect = { icon = it }, palette = palette)
            Spacer(Modifier.height(14.dp))

            if (habitKind == HabitKind.BUILD) {
                EditorLabel(stringResource(R.string.habit_field_tracking))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = tracking == HabitTracking.CHECK,
                        onClick = { tracking = HabitTracking.CHECK },
                        label = { Text(stringResource(R.string.habit_tracking_check)) },
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = tracking == HabitTracking.COUNT,
                        onClick = { tracking = HabitTracking.COUNT },
                        label = { Text(stringResource(R.string.habit_tracking_count)) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (tracking == HabitTracking.COUNT) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = targetText,
                            onValueChange = { targetText = it.filter(Char::isDigit).take(2) },
                            label = { Text(stringResource(R.string.habit_field_target)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = unit,
                            onValueChange = { unit = it },
                            label = { Text(stringResource(R.string.habit_field_unit)) },
                            placeholder = { Text(stringResource(R.string.habit_unit_hint)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            EditorLabel(stringResource(R.string.habit_field_schedule))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = scheduleType == HabitScheduleType.EVERY_DAY,
                    onClick = { scheduleType = HabitScheduleType.EVERY_DAY },
                    label = { Text(stringResource(R.string.habit_schedule_every_day)) },
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = scheduleType == HabitScheduleType.SPECIFIC_DAYS,
                    onClick = { scheduleType = HabitScheduleType.SPECIFIC_DAYS },
                    label = { Text(stringResource(R.string.habit_schedule_specific)) },
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = scheduleType == HabitScheduleType.TIMES_PER_WEEK,
                    onClick = { scheduleType = HabitScheduleType.TIMES_PER_WEEK },
                    label = { Text(stringResource(R.string.habit_schedule_times)) },
                    modifier = Modifier.weight(1f),
                )
            }
            when (scheduleType) {
                HabitScheduleType.SPECIFIC_DAYS -> {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..7).forEach { day ->
                            val selected = day in weekdays
                            FilterChip(
                                selected = selected,
                                onClick = { weekdays = if (selected) weekdays - day else weekdays + day },
                                label = { Text(weekdayName(day, languageTag, short = true)) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                HabitScheduleType.TIMES_PER_WEEK -> {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..7).forEach { times ->
                            FilterChip(
                                selected = timesPerWeek == times,
                                onClick = { timesPerWeek = times },
                                label = { Text(TextNormalizer.formatDigits(times.toString(), numeralMode)) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.habit_times_per_week, TextNormalizer.formatDigits(timesPerWeek.toString(), numeralMode)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HabitScheduleType.EVERY_DAY -> Unit
            }
            Spacer(Modifier.height(14.dp))

            EditorLabel(stringResource(R.string.habit_field_difficulty))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HabitDifficulty.entries.forEach { level ->
                    FilterChip(
                        selected = difficulty == level,
                        onClick = { difficulty = level },
                        label = { Text(difficultyLabel(level)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))

            EditorLabel(stringResource(R.string.habit_field_reminder))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                REMINDER_PRESETS.forEach { minute ->
                    FilterChip(
                        selected = reminder == minute,
                        onClick = { reminder = minute },
                        label = { Text(reminderLabel(minute, numeralMode)) },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = cue,
                onValueChange = { cue = it },
                label = { Text(stringResource(R.string.habit_field_cue)) },
                placeholder = { Text(stringResource(R.string.habit_cue_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = identity,
                onValueChange = { identity = it },
                label = { Text(stringResource(R.string.habit_field_identity)) },
                placeholder = { Text(stringResource(R.string.habit_identity_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = why,
                onValueChange = { why = it },
                label = { Text(stringResource(R.string.habit_field_why)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))

            EditorLabel(stringResource(R.string.habit_field_theme))
            HabitThemeSwatchRow(
                selected = HabitTheme.from(themeId),
                onSelect = { themeId = it.id },
                dark = dark,
            )
            Spacer(Modifier.height(18.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (existing) {
                    TextButton(onClick = { habit?.let(onDelete) }) { Text(stringResource(R.string.habit_delete)) }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.habit_cancel)) }
                Button(onClick = {
                    if (name.isBlank()) {
                        nameError = true
                        return@Button
                    }
                    val schedule = when (scheduleType) {
                        HabitScheduleType.EVERY_DAY -> HabitSchedule(HabitScheduleType.EVERY_DAY)
                        HabitScheduleType.SPECIFIC_DAYS -> HabitSchedule(
                            type = HabitScheduleType.SPECIFIC_DAYS,
                            weekdays = weekdays.ifEmpty { setOf(1, 2, 3, 4, 5) },
                        )
                        HabitScheduleType.TIMES_PER_WEEK -> HabitSchedule(
                            type = HabitScheduleType.TIMES_PER_WEEK,
                            timesPerWeek = timesPerWeek,
                        )
                    }
                    val target = targetText.toIntOrNull()?.coerceIn(1, 99) ?: 1
                    onSave(
                        (habit ?: Habit(
                            name = name.trim(),
                            startDate = LocalDate.now().toString(),
                            createdAtEpochMillis = System.currentTimeMillis(),
                        )).copy(
                            name = name.trim(),
                            kind = habitKind,
                            tracking = if (habitKind == HabitKind.QUIT) HabitTracking.CHECK else tracking,
                            schedule = schedule,
                            dailyTarget = if (habitKind == HabitKind.QUIT) 1 else target,
                            unitLabel = unit.trim(),
                            icon = icon,
                            themeId = themeId,
                            cue = cue.trim(),
                            identity = identity.trim(),
                            why = why.trim(),
                            difficulty = difficulty,
                            reminderMinuteOfDay = reminder,
                        ),
                    )
                }) { Text(stringResource(R.string.habit_save)) }
            }
        }
    }
}

@Composable
private fun EditorLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
fun difficultyLabel(level: HabitDifficulty): String = stringResource(
    when (level) {
        HabitDifficulty.EASY -> R.string.habit_difficulty_easy
        HabitDifficulty.MEDIUM -> R.string.habit_difficulty_medium
        HabitDifficulty.HARD -> R.string.habit_difficulty_hard
    },
)

@Composable
fun reminderLabel(minuteOfDay: Int, numeralMode: NumeralMode): String {
    if (minuteOfDay < 0) return stringResource(R.string.habit_reminder_off)
    val time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
    val text = "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
    return stringResource(R.string.habit_reminder_at, TextNormalizer.formatDigits(text, numeralMode))
}
