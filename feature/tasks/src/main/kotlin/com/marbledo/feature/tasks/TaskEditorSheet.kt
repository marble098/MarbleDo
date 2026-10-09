package com.marbledo.feature.tasks

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marbledo.core.designsystem.LocalNumeralMode
import com.marbledo.domain.model.CalendarDisplayMode
import com.marbledo.domain.model.ChecklistItem
import com.marbledo.domain.model.RecurrenceRule
import com.marbledo.domain.model.RepeatCalendar
import com.marbledo.domain.model.RepeatFrequency
import com.marbledo.domain.model.Task
import com.marbledo.domain.model.TaskPriority
import com.marbledo.domain.util.TextNormalizer
import com.marbledo.feature.calendar.SchedulePickerDialog
import com.marbledo.feature.calendar.SchedulePickerField
import com.marbledo.feature.calendar.SchedulePickerMode
import com.marbledo.feature.calendar.formatScheduleSummary
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

/** Colours a task can be marked with. The first entry is the default. */
private val taskColorOptions: List<Long> = listOf(
    0xFF6E61D8L,
    0xFF2E9E7AL,
    0xFF3F88C5L,
    0xFFDB8C27L,
    0xFFD9534FL,
    0xFF8E6CC9L,
    0xFF5A6B7AL,
)

/**
 * Full editor for an existing task. Schedule is chosen with the shared picker and one-tap chips, and the sheet
 * also covers the checklist, link, attachment, focus time, colour, repeat rule and countdown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorSheet(
    task: Task,
    categories: List<String>,
    languageTag: String,
    weekStartsSaturday: Boolean,
    pickerCalendar: CalendarDisplayMode,
    onPickerCalendarChange: (CalendarDisplayMode) -> Unit,
    onAddCategory: (String) -> Unit,
    onSave: (Task) -> Unit,
    onDelete: () -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    countdownThemePicker: @Composable (selectedThemeId: String, onSelect: (String) -> Unit) -> Unit = { _, _ -> },
    countdownThemeLabel: @Composable (themeId: String) -> String = { it },
) {
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    val numeralMode = LocalNumeralMode.current
    val uriHandler = LocalUriHandler.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val initialSchedule = remember(task.id) { scheduleOf(task.dueAtEpochMillis, task.isAllDay, zone) }

    var title by rememberSaveable(task.id) { mutableStateOf(task.title) }
    var dateIso by rememberSaveable(task.id) { mutableStateOf(initialSchedule.date?.toString()) }
    var timeIso by rememberSaveable(task.id) { mutableStateOf(initialSchedule.time?.toString()) }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var description by rememberSaveable(task.id) { mutableStateOf(task.description) }
    var tags by rememberSaveable(task.id) { mutableStateOf(task.tags.joinToString(", ")) }
    var project by rememberSaveable(task.id) { mutableStateOf(task.project) }
    var category by rememberSaveable(task.id) { mutableStateOf(task.category) }
    var priority by rememberSaveable(task.id) { mutableStateOf(task.priority) }
    var frequency by rememberSaveable(task.id) { mutableStateOf(task.recurrence?.frequency ?: RepeatFrequency.NONE) }
    var persianRepeat by rememberSaveable(task.id) { mutableStateOf(task.recurrence?.calendar == RepeatCalendar.PERSIAN) }
    var countdownEnabled by rememberSaveable(task.id) { mutableStateOf(task.countdownEnabled) }
    var themeId by rememberSaveable(task.id) { mutableStateOf(task.countdownTheme) }
    var link by rememberSaveable(task.id) { mutableStateOf(task.link.orEmpty()) }
    var attachment by rememberSaveable(task.id) { mutableStateOf(task.attachmentUri) }
    var focusText by rememberSaveable(task.id) { mutableStateOf(if (task.focusMinutes > 0) task.focusMinutes.toString() else "") }
    var colorArgb by rememberSaveable(task.id) { mutableStateOf(task.colorArgb) }
    val checklist = rememberSaveable(task.id, saver = checklistSaver) { task.checklist.toMutableStateList() }
    var showCategoryDialog by rememberSaveable { mutableStateOf(false) }
    var categoryDraft by rememberSaveable { mutableStateOf("") }

    val availableCategories = remember(categories) { categories.map { it.trim() }.filter { it.isNotBlank() }.distinct() }
    val schedule = TaskSchedule(dateIso?.let(::parseIsoDate), timeIso?.let(::parseIsoTime))
    val dueMillis = schedule.toDueMillis(zone, LocalDate.now(zone))
    val pickedDate = schedule.date ?: LocalDate.now(zone)
    val scheduleText = if (schedule.isEmpty) {
        stringResource(R.string.tasks_date_none)
    } else {
        formatScheduleSummary(pickedDate, schedule.time, pickerCalendar, languageTag, numeralMode, zone)
    }
    val linkValid = link.isBlank() || isWebLink(link)
    val focusMinutes = focusText.toIntOrNull()?.coerceIn(0, 1440) ?: 0
    val canSave = title.isNotBlank() && linkValid

    fun pickDate(date: LocalDate) {
        dateIso = date.toString()
    }

    fun pickTime(time: LocalTime?) {
        timeIso = time?.toString()
        if (dateIso == null) dateIso = defaultDateFor(time, ZonedDateTime.now(zone)).toString()
    }

    fun clearSchedule() {
        dateIso = null
        timeIso = null
    }

    val attachmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            // Keep read access across restarts when the provider allows it; the file still opens without it.
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            attachment = uri.toString()
        }
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
                isError = title.isBlank(),
                modifier = Modifier.fillMaxWidth(),
            )

            Text(stringResource(R.string.tasks_when), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SchedulePickerField(
                label = stringResource(R.string.tasks_schedule_field),
                value = scheduleText,
                onClick = { showPicker = true },
            )
            ScheduleQuickChips(
                schedule = schedule,
                zone = zone,
                numeralMode = numeralMode,
                onPickDate = { pickDate(it) },
                onPickTime = { pickTime(it) },
                onClear = { clearSchedule() },
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.tasks_notes_hint)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(stringResource(R.string.tasks_checklist), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            checklist.forEachIndexed { index, item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = item.isDone,
                        onCheckedChange = { checked -> checklist[index] = item.copy(isDone = checked) },
                    )
                    OutlinedTextField(
                        value = item.text,
                        onValueChange = { text -> checklist[index] = item.copy(text = text) },
                        placeholder = { Text(stringResource(R.string.tasks_checklist_item_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { checklist.removeAt(index) }) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.tasks_checklist_remove))
                    }
                }
            }
            TextButton(onClick = { checklist.add(ChecklistItem(id = UUID.randomUUID().toString(), text = "")) }) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.tasks_checklist_add))
            }

            OutlinedTextField(
                value = link,
                onValueChange = { link = it },
                label = { Text(stringResource(R.string.tasks_link_hint)) },
                placeholder = { Text("https://") },
                singleLine = true,
                isError = !linkValid,
                supportingText = {
                    Text(
                        stringResource(if (linkValid) R.string.tasks_link_help else R.string.tasks_link_invalid),
                        color = if (linkValid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    )
                },
                trailingIcon = {
                    if (linkValid && link.isNotBlank()) {
                        IconButton(onClick = { runCatching { uriHandler.openUri(link.trim()) } }) {
                            Icon(Icons.Outlined.OpenInNew, contentDescription = stringResource(R.string.tasks_link_open))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            attachment?.let { uriString ->
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AttachFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Text(
                            attachmentName(uriString, stringResource(R.string.tasks_attachment_default)),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        )
                        IconButton(onClick = { openAttachment(context, uriString) }) {
                            Icon(Icons.Outlined.OpenInNew, contentDescription = stringResource(R.string.tasks_attachment_open))
                        }
                        IconButton(onClick = { attachment = null }) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.tasks_attachment_remove))
                        }
                    }
                }
            }
            TextButton(onClick = { attachmentPicker.launch(arrayOf("*/*")) }) {
                Icon(Icons.Outlined.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(if (attachment == null) R.string.tasks_attach_file else R.string.tasks_attach_replace))
            }

            OutlinedTextField(
                value = focusText,
                onValueChange = { raw -> focusText = TextNormalizer.digitsToLatin(raw).filter(Char::isDigit).take(4) },
                label = { Text(stringResource(R.string.tasks_focus_minutes)) },
                supportingText = { Text(stringResource(R.string.tasks_focus_help)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(stringResource(R.string.tasks_priority), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                items(RepeatFrequency.entries.filter { it != RepeatFrequency.CUSTOM }) { option ->
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
            if (frequency != RepeatFrequency.NONE && schedule.isEmpty) {
                Text(stringResource(R.string.tasks_repeat_needs_date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            Text(stringResource(R.string.tasks_color), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(horizontal = 1.dp)) {
                items(taskColorOptions) { option ->
                    val selectedSwatch = colorArgb == option
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(option.toInt()))
                            .border(
                                width = if (selectedSwatch) 3.dp else 0.dp,
                                color = MaterialTheme.colorScheme.onSurface,
                                shape = CircleShape,
                            )
                            .clickable { colorArgb = option },
                    )
                }
            }

            OutlinedTextField(
                value = tags,
                onValueChange = { tags = it },
                label = { Text(stringResource(R.string.tasks_tag_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = project,
                onValueChange = { project = it },
                label = { Text(stringResource(R.string.tasks_project_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text(stringResource(R.string.tasks_countdown_toggle_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (dueMillis == null) stringResource(R.string.tasks_countdown_needs_date) else stringResource(R.string.tasks_countdown_toggle_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = countdownEnabled && dueMillis != null,
                            enabled = dueMillis != null,
                            onCheckedChange = { countdownEnabled = it },
                        )
                    }
                    if (countdownEnabled && dueMillis != null) {
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
                                dueAtEpochMillis = dueMillis,
                                isAllDay = schedule.time == null,
                                countdownEnabled = countdownEnabled,
                                themeId = themeId,
                                link = link,
                                attachmentUri = attachment,
                                checklist = checklist.map { it.copy(text = it.text.trim()) }.filter { it.text.isNotBlank() },
                                colorArgb = colorArgb,
                                focusMinutes = focusMinutes,
                                zone = zone,
                            ),
                        )
                    },
                    enabled = canSave,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.tasks_save)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.tasks_cancel)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                if (task.isArchived) {
                    TextButton(onClick = onUnarchive) {
                        Icon(Icons.Outlined.Unarchive, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(stringResource(R.string.tasks_unarchive))
                    }
                } else {
                    TextButton(onClick = onArchive) {
                        Icon(Icons.Outlined.Archive, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(stringResource(R.string.tasks_archive))
                    }
                }
                TextButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(stringResource(R.string.tasks_delete))
                }
            }
        }
    }

    if (showPicker) {
        SchedulePickerDialog(
            title = stringResource(R.string.tasks_schedule_title),
            mode = SchedulePickerMode.DATE_AND_TIME,
            initialDate = schedule.date,
            initialTime = schedule.time,
            languageTag = languageTag,
            numeralMode = numeralMode,
            weekStartsSaturday = weekStartsSaturday,
            calendarMode = pickerCalendar,
            onCalendarModeChange = onPickerCalendarChange,
            onConfirm = { date, time ->
                dateIso = date.toString()
                timeIso = time?.toString()
                showPicker = false
            },
            onDismiss = { showPicker = false },
            onClear = {
                clearSchedule()
                showPicker = false
            },
        )
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

private val checklistSaver: Saver<SnapshotStateList<ChecklistItem>, Any> = listSaver(
    save = { items -> items.map(::encodeChecklistItem) },
    restore = { encoded -> encoded.map(::decodeChecklistItem).toMutableStateList() },
)

private fun encodeChecklistItem(item: ChecklistItem): String =
    listOf(item.id, if (item.isDone) "1" else "0", item.text).joinToString("|")

private fun decodeChecklistItem(encoded: String): ChecklistItem {
    val parts = encoded.split('|', limit = 3)
    return ChecklistItem(
        id = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
        text = parts.getOrNull(2).orEmpty(),
        isDone = parts.getOrNull(1) == "1",
    )
}

private fun parseIsoDate(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()

private fun parseIsoTime(value: String): LocalTime? = runCatching { LocalTime.parse(value) }.getOrNull()

private fun isWebLink(value: String): Boolean {
    val trimmed = value.trim()
    return (trimmed.startsWith("https://") || trimmed.startsWith("http://")) &&
        trimmed.length > "https://".length &&
        trimmed.none(Char::isWhitespace)
}

private fun attachmentName(uriString: String, fallback: String): String =
    runCatching { Uri.parse(uriString).lastPathSegment }.getOrNull()
        ?.substringAfterLast(':')
        ?.substringAfterLast('/')
        ?.takeIf { it.isNotBlank() }
        ?: fallback

private fun openAttachment(context: Context, uriString: String) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setData(Uri.parse(uriString))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure { Toast.makeText(context, R.string.tasks_attachment_unavailable, Toast.LENGTH_SHORT).show() }
}

/**
 * Repeat rule for the chosen frequency. A Persian-calendar rule is only kept for monthly and yearly repeats,
 * because the recurrence calculator supports the Persian calendar for those two frequencies alone.
 */
private fun buildRecurrence(
    frequency: RepeatFrequency,
    persianRepeat: Boolean,
    dueAtEpochMillis: Long?,
    zone: ZoneId,
): RecurrenceRule? {
    if (frequency == RepeatFrequency.NONE || dueAtEpochMillis == null) return null
    val usePersian = persianRepeat && (frequency == RepeatFrequency.MONTHLY || frequency == RepeatFrequency.YEARLY)
    val monthDay = if (usePersian) {
        persianCalendar(android.icu.util.TimeZone.getTimeZone(zone.id))
            .apply { timeInMillis = dueAtEpochMillis }
            .get(android.icu.util.Calendar.DAY_OF_MONTH)
    } else {
        Instant.ofEpochMilli(dueAtEpochMillis).atZone(zone).dayOfMonth
    }
    return RecurrenceRule(
        frequency = frequency,
        interval = 1,
        monthDay = monthDay,
        calendar = if (usePersian) RepeatCalendar.PERSIAN else RepeatCalendar.GREGORIAN,
    )
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
    isAllDay: Boolean,
    countdownEnabled: Boolean,
    themeId: String,
    link: String,
    attachmentUri: String?,
    checklist: List<ChecklistItem>,
    colorArgb: Long,
    focusMinutes: Int,
    zone: ZoneId,
): Task = original.copy(
    title = title.trim(),
    description = description.trim(),
    dueAtEpochMillis = dueAtEpochMillis,
    isAllDay = isAllDay,
    priority = priority,
    tags = tags.split(',').map(String::trim).filter(String::isNotBlank),
    project = project.trim(),
    category = category.trim(),
    checklist = checklist,
    link = link.trim().takeIf { it.isNotBlank() },
    attachmentUri = attachmentUri,
    recurrence = buildRecurrence(frequency, persianRepeat, dueAtEpochMillis, zone),
    countdownEnabled = countdownEnabled,
    countdownTheme = themeId,
    colorArgb = colorArgb,
    focusMinutes = focusMinutes,
    updatedAtEpochMillis = System.currentTimeMillis(),
)
