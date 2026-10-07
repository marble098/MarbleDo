package com.marbledo.feature.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.marbledo.domain.model.RepeatCalendar
import com.marbledo.domain.model.RepeatFrequency
import com.marbledo.domain.model.RecurrenceRule
import com.marbledo.domain.model.Task
import com.marbledo.domain.model.TaskPriority
import com.marbledo.domain.util.TextNormalizer
import java.time.Instant
import java.time.ZoneId

/** Full editor for an existing task, including the per-task countdown switch and theme picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorSheet(
    task: Task,
    categories: List<String>,
    onAddCategory: (String) -> Unit,
    onSave: (Task) -> Unit,
    onDelete: () -> Unit,
    onArchive: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    countdownThemePicker: @Composable (selectedThemeId: String, onSelect: (String) -> Unit) -> Unit = { _, _ -> },
    countdownThemeLabel: @Composable (themeId: String) -> String = { it },
) {
    val zone = remember { ZoneId.systemDefault() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by rememberSaveable(task.id) { mutableStateOf(task.title) }
    var dueText by rememberSaveable(task.id) { mutableStateOf("") }
    var description by rememberSaveable(task.id) { mutableStateOf(task.description) }
    var tags by rememberSaveable(task.id) { mutableStateOf(task.tags.joinToString(", ")) }
    var project by rememberSaveable(task.id) { mutableStateOf(task.project) }
    var category by rememberSaveable(task.id) { mutableStateOf(task.category) }
    var priority by rememberSaveable(task.id) { mutableStateOf(task.priority) }
    var frequency by rememberSaveable(task.id) { mutableStateOf(task.recurrence?.frequency ?: RepeatFrequency.NONE) }
    var persianRepeat by rememberSaveable(task.id) { mutableStateOf(task.recurrence?.calendar == RepeatCalendar.PERSIAN) }
    var countdownEnabled by rememberSaveable(task.id) { mutableStateOf(task.countdownEnabled) }
    var clearDue by rememberSaveable(task.id) { mutableStateOf(false) }
    var themeId by rememberSaveable(task.id) { mutableStateOf(task.countdownTheme) }
    var showCategoryDialog by rememberSaveable { mutableStateOf(false) }
    var categoryDraft by rememberSaveable { mutableStateOf("") }
    val parsedDue = remember(dueText) { SmartTaskParser.parse(dueText)?.takeIf { it.recognizedDate || it.recognizedTime } }
    val availableCategories = remember(categories) { categories.map { it.trim() }.filter { it.isNotBlank() }.distinct() }
    val effectiveDue = when {
        clearDue -> null
        parsedDue?.task?.dueAtEpochMillis != null -> parsedDue.task.dueAtEpochMillis
        else -> task.dueAtEpochMillis
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, modifier = modifier.imePadding()) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.tasks_edit_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.tasks_task_title_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = dueText,
                onValueChange = { dueText = it },
                label = { Text(stringResource(R.string.tasks_due_hint)) },
                placeholder = { Text(stringResource(R.string.tasks_quick_add_hint)) },
                supportingText = {
                    val preview = effectiveDue
                    when {
                        dueText.isNotBlank() && parsedDue == null -> Text(stringResource(R.string.tasks_date_unrecognized), color = MaterialTheme.colorScheme.error)
                        preview != null -> Text(stringResource(R.string.tasks_due_at, localizedTaskDate(preview)))
                        else -> Text(stringResource(R.string.tasks_no_due_date))
                    }
                },
                isError = dueText.isNotBlank() && parsedDue?.task?.dueAtEpochMillis == null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            val todayLabel = stringResource(R.string.tasks_today)
            val tomorrowLabel = stringResource(R.string.tasks_tomorrow)
            val nextWeekLabel = stringResource(R.string.tasks_date_next_week)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(horizontal = 1.dp)) {
                item {
                    FilterChip(
                        selected = effectiveDue == null,
                        onClick = { clearDue = true; dueText = "" },
                        label = { Text(stringResource(R.string.tasks_date_none)) },
                    )
                }
                item { FilterChip(selected = false, onClick = { clearDue = false; dueText = todayLabel }, label = { Text(todayLabel) }) }
                item { FilterChip(selected = false, onClick = { clearDue = false; dueText = tomorrowLabel }, label = { Text(tomorrowLabel) }) }
                item { FilterChip(selected = false, onClick = { clearDue = false; dueText = nextWeekLabel }, label = { Text(nextWeekLabel) }) }
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.tasks_notes_hint)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text(stringResource(R.string.tasks_tag_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = project,
                    onValueChange = { project = it },
                    label = { Text(stringResource(R.string.tasks_project_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.tasks_priority), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TaskPriority.entries.forEach { option ->
                    FilterChip(selected = priority == option, onClick = { priority = option }, label = { Text(priorityLabel(option)) })
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.tasks_category), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { categoryDraft = ""; showCategoryDialog = true }) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.tasks_create_category))
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(horizontal = 1.dp)) {
                item {
                    FilterChip(selected = category.isBlank(), onClick = { category = "" }, label = { Text(stringResource(R.string.tasks_category_none)) })
                }
                items(availableCategories) { name ->
                    FilterChip(selected = category == name, onClick = { category = name }, label = { Text(name, maxLines = 1) })
                }
            }

            Text(stringResource(R.string.tasks_repeat), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(horizontal = 1.dp)) {
                items(RepeatFrequency.entries) { option ->
                    FilterChip(selected = frequency == option, onClick = { frequency = option }, label = { Text(repeatLabel(option)) })
                }
            }
            if (frequency == RepeatFrequency.MONTHLY || frequency == RepeatFrequency.YEARLY) {
                FilterChip(
                    selected = persianRepeat,
                    onClick = { persianRepeat = !persianRepeat },
                    label = { Text(stringResource(R.string.tasks_repeat_persian_monthly)) },
                )
            }

            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text(stringResource(R.string.tasks_countdown_toggle_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (effectiveDue == null) stringResource(R.string.tasks_countdown_needs_date) else stringResource(R.string.tasks_countdown_toggle_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = countdownEnabled && effectiveDue != null, enabled = effectiveDue != null, onCheckedChange = { countdownEnabled = it })
                    }
                    if (countdownEnabled && effectiveDue != null) {
                        Text(
                            stringResource(R.string.tasks_countdown_theme, countdownThemeLabel(themeId)),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        countdownThemePicker(themeId) { selected -> themeId = selected }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        onSave(
                            buildEditedTask(
                                original = task,
                                title = title,
                                description = description,
                                tags = tags,
                                project = project,
                                category = category,
                                priority = priority,
                                frequency = frequency,
                                persianRepeat = persianRepeat,
                                dueAtEpochMillis = effectiveDue,
                                countdownEnabled = countdownEnabled,
                                themeId = themeId,
                                zone = zone,
                            ),
                        )
                    },
                    enabled = title.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.tasks_save)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.tasks_cancel)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onArchive) {
                    Icon(Icons.Outlined.Archive, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(stringResource(R.string.tasks_archive))
                }
                TextButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(stringResource(R.string.tasks_delete))
                }
            }
        }
    }

    if (showCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showCategoryDialog = false },
            title = { Text(stringResource(R.string.tasks_create_category)) },
            text = {
                OutlinedTextField(
                    value = categoryDraft,
                    onValueChange = { categoryDraft = it },
                    label = { Text(stringResource(R.string.tasks_category_name)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = categoryDraft.trim()
                    if (name.isNotBlank()) {
                        category = name
                        if (availableCategories.none { TextNormalizer.searchKey(it) == TextNormalizer.searchKey(name) }) onAddCategory(name)
                    }
                    showCategoryDialog = false
                }) { Text(stringResource(R.string.tasks_save)) }
            },
            dismissButton = { TextButton(onClick = { showCategoryDialog = false }) { Text(stringResource(R.string.tasks_cancel)) } },
        )
    }
}

private fun buildEditedTask(
    original: Task,
    title: String,
    description: String,
    tags: String,
    project: String,
    category: String,
    priority: TaskPriority,
    frequency: RepeatFrequency,
    persianRepeat: Boolean,
    dueAtEpochMillis: Long?,
    countdownEnabled: Boolean,
    themeId: String,
    zone: ZoneId,
): Task {
    val rule = frequency.takeIf { it != RepeatFrequency.NONE }?.let {
        RecurrenceRule(
            frequency = it,
            interval = 1,
            monthDay = dueAtEpochMillis?.let { millis ->
                if (persianRepeat) {
                    persianCalendar(android.icu.util.TimeZone.getDefault()).apply { timeInMillis = millis }
                        .get(android.icu.util.Calendar.DAY_OF_MONTH)
                } else {
                    Instant.ofEpochMilli(millis).atZone(zone).dayOfMonth
                }
            },
            calendar = if (persianRepeat) RepeatCalendar.PERSIAN else RepeatCalendar.GREGORIAN,
        )
    }
    return original.copy(
        title = title.trim(),
        description = description.trim(),
        dueAtEpochMillis = dueAtEpochMillis,
        priority = priority,
        tags = tags.split(',').map(String::trim).filter(String::isNotBlank),
        project = project.trim(),
        category = category.trim(),
        recurrence = rule,
        countdownEnabled = countdownEnabled,
        countdownTheme = themeId,
        updatedAtEpochMillis = System.currentTimeMillis(),
    )
}
