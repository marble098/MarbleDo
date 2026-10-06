package com.marbledo.feature.tasks

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.marbledo.core.designsystem.LocalNumeralMode
import com.marbledo.domain.model.RepeatCalendar
import com.marbledo.domain.model.RepeatFrequency
import com.marbledo.domain.model.RecurrenceRule
import com.marbledo.domain.model.Task
import com.marbledo.domain.model.TaskPriority
import com.marbledo.domain.util.TextNormalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    viewModel: TasksViewModel,
    onOpenCountdown: (Task) -> Unit,
    initialShareText: String? = null,
    initialOpenAdd: Boolean = false,
    onInitialIntentConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<Task?>(null) }
    var quickInput by rememberSaveable { mutableStateOf(initialShareText.orEmpty()) }
    var showUndo by remember { mutableStateOf(false) }

    LaunchedEffect(initialShareText, initialOpenAdd) {
        if (initialOpenAdd) showEditor = true
        if (!initialShareText.isNullOrBlank()) quickInput = initialShareText
        if (initialOpenAdd || !initialShareText.isNullOrBlank()) onInitialIntentConsumed()
    }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) quickInput = spoken
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchSpeech(context as? Activity, speechLauncher::launch)
        else Toast.makeText(context, R.string.tasks_speech_unavailable, Toast.LENGTH_SHORT).show()
    }

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(stringResource(R.string.tasks_title), fontWeight = FontWeight.Bold)
                    Text(
                        TextNormalizer.formatDigits(stringResource(R.string.tasks_left_count, state.activeCount), LocalNumeralMode.current),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            actions = {
                if (state.selectedIds.isNotEmpty()) {
                    TextButton(onClick = viewModel::completeSelected) {
                        Text(stringResource(R.string.tasks_bulk_complete))
                    }
                    TextButton(onClick = viewModel::clearSelection) {
                        Text(stringResource(R.string.tasks_cancel))
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        )

        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(TextNormalizer.formatDigits(stringResource(R.string.tasks_done_count, state.completedCount), LocalNumeralMode.current), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.tasks_parse_help), style = MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }

        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text(stringResource(R.string.tasks_search)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(TaskFilter.entries) { item ->
                FilterChip(
                    selected = state.filter == item,
                    onClick = { viewModel.setFilter(item) },
                    label = { Text(filterLabel(item)) },
                )
            }
        }

        OutlinedTextField(
            value = quickInput,
            onValueChange = { quickInput = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            placeholder = { Text(stringResource(R.string.tasks_quick_add_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = {
                IconButton(onClick = {
                    if (SpeechRecognizer.isRecognitionAvailable(context)) {
                        microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        Toast.makeText(context, R.string.tasks_speech_unavailable, Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Icon(Icons.Outlined.Mic, contentDescription = stringResource(R.string.tasks_voice_input))
                }
            },
            trailingIcon = {
                TextButton(onClick = {
                    val parsed = SmartTaskParser.parse(quickInput)
                    if (parsed == null) {
                        Toast.makeText(context, R.string.tasks_task_title_hint, Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.addTask(parsed.task)
                        quickInput = ""
                    }
                }) { Text(stringResource(R.string.tasks_add)) }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            ),
        )

        AnimatedVisibility(visible = showUndo) {
            TextButton(
                onClick = {
                    viewModel.undoLastChange()
                    showUndo = false
                },
                modifier = Modifier.align(Alignment.End).padding(end = 16.dp),
            ) { Text(stringResource(R.string.tasks_undo)) }
        }

        if (state.visibleTasks.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.tasks_empty_title), style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.tasks_empty_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.visibleTasks, key = { it.id }) { task ->
                    TaskRow(
                        task = task,
                        selected = task.id in state.selectedIds,
                        onToggle = { viewModel.toggleCompleted(task) },
                        onCountdown = { onOpenCountdown(task) },
                        onEdit = { editingTask = task },
                        onArchive = { viewModel.archive(task) },
                        onDelete = { viewModel.delete(task); showUndo = true },
                        onLongPress = { viewModel.toggleSelection(task.id) },
                    )
                }
            }
        }
    }

    if (showEditor || editingTask != null) {
        TaskEditorDialog(
            task = editingTask,
            onDismiss = { showEditor = false; editingTask = null },
            onSave = { task ->
                if (editingTask == null) viewModel.addTask(task) else viewModel.editTask(task)
                showEditor = false
                editingTask = null
            },
        )
    }
}

@Composable
private fun filterLabel(filter: TaskFilter): String = when (filter) {
    TaskFilter.TODAY -> stringResource(R.string.tasks_today)
    TaskFilter.TOMORROW -> stringResource(R.string.tasks_tomorrow)
    TaskFilter.UPCOMING -> stringResource(R.string.tasks_upcoming)
    TaskFilter.ALL -> stringResource(R.string.tasks_all)
    TaskFilter.COMPLETED -> stringResource(R.string.tasks_completed)
    TaskFilter.ARCHIVED -> stringResource(R.string.tasks_archived)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskRow(
    task: Task,
    selected: Boolean,
    onToggle: () -> Unit,
    onCountdown: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onLongPress: () -> Unit,
) {
    var menuExpanded by rememberSaveable(task.id) { mutableStateOf(false) }
    val priorityColor = when (task.priority) {
        TaskPriority.LOW -> MaterialTheme.colorScheme.tertiary
        TaskPriority.NORMAL -> MaterialTheme.colorScheme.primary
        TaskPriority.HIGH -> Color(0xFFDB8C27)
        TaskPriority.URGENT -> MaterialTheme.colorScheme.error
    }
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = onEdit,
            onLongClick = onLongPress,
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = { onToggle() },
                modifier = Modifier.semantics { contentDescription = task.title },
            )
            Column(modifier = Modifier.weight(1f).padding(start = 6.dp, end = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(priorityColor))
                    Spacer(Modifier.width(7.dp))
                    Text(
                        task.title,
                        style = MaterialTheme.typography.titleMedium,
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                        color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val due = task.dueAtEpochMillis
                Text(
                    text = if (due == null) stringResource(R.string.tasks_no_due_date)
                    else stringResource(R.string.tasks_due_at, localizedTaskDate(due)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (task.tags.isNotEmpty()) {
                    Text(task.tags.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                }
            }
            IconButton(onClick = onCountdown, enabled = task.dueAtEpochMillis != null) {
                Icon(Icons.Outlined.Timer, contentDescription = stringResource(R.string.tasks_open_countdown), tint = MaterialTheme.colorScheme.primary)
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.tasks_more))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tasks_edit_title)) },
                        onClick = { menuExpanded = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tasks_archive)) },
                        leadingIcon = { Icon(Icons.Outlined.Archive, contentDescription = null) },
                        onClick = { menuExpanded = false; onArchive() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tasks_delete)) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                        onClick = { menuExpanded = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskEditorDialog(
    task: Task?,
    onDismiss: () -> Unit,
    onSave: (Task) -> Unit,
) {
    var title by rememberSaveable(task?.id) { mutableStateOf(task?.title.orEmpty()) }
    var dueText by rememberSaveable(task?.id) { mutableStateOf("") }
    var description by rememberSaveable(task?.id) { mutableStateOf(task?.description.orEmpty()) }
    var tags by rememberSaveable(task?.id) { mutableStateOf(task?.tags?.joinToString(", ").orEmpty()) }
    var project by rememberSaveable(task?.id) { mutableStateOf(task?.project.orEmpty()) }
    var priority by rememberSaveable(task?.id) { mutableStateOf(task?.priority ?: TaskPriority.NORMAL) }
    var frequency by rememberSaveable(task?.id) { mutableStateOf(task?.recurrence?.frequency ?: RepeatFrequency.NONE) }
    var persianRepeat by rememberSaveable(task?.id) { mutableStateOf(task?.recurrence?.calendar == RepeatCalendar.PERSIAN) }
    var priorityMenu by remember { mutableStateOf(false) }
    var repeatMenu by remember { mutableStateOf(false) }
    val parsedDue = remember(dueText) { SmartTaskParser.parse(dueText)?.task?.dueAtEpochMillis }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (task == null) R.string.tasks_add else R.string.tasks_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.tasks_task_title_hint)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = dueText,
                    onValueChange = { dueText = it },
                    label = { Text(stringResource(R.string.tasks_due_hint)) },
                    placeholder = { Text(stringResource(R.string.tasks_quick_add_hint)) },
                    supportingText = {
                        if (parsedDue != null) Text(localizedTaskDate(parsedDue))
                        else if (task?.dueAtEpochMillis != null && dueText.isBlank()) {
                            Text(stringResource(R.string.tasks_due_at, localizedTaskDate(task.dueAtEpochMillis)))
                        }
                    },
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.tasks_notes_hint)) },
                    minLines = 2,
                )
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text(stringResource(R.string.tasks_tag_hint)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = project,
                    onValueChange = { project = it },
                    label = { Text(stringResource(R.string.tasks_project_hint)) },
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                        FilterChip(selected = false, onClick = { priorityMenu = true }, label = { Text(priorityLabel(priority)) })
                        DropdownMenu(expanded = priorityMenu, onDismissRequest = { priorityMenu = false }) {
                            TaskPriority.entries.forEach { item ->
                                DropdownMenuItem(text = { Text(priorityLabel(item)) }, onClick = { priority = item; priorityMenu = false })
                            }
                        }
                    }
                    Box {
                        FilterChip(selected = false, onClick = { repeatMenu = true }, label = { Text(repeatLabel(frequency)) })
                        DropdownMenu(expanded = repeatMenu, onDismissRequest = { repeatMenu = false }) {
                            RepeatFrequency.entries.forEach { item ->
                                DropdownMenuItem(text = { Text(repeatLabel(item)) }, onClick = {
                                    frequency = item
                                    persianRepeat = item == RepeatFrequency.MONTHLY && persianRepeat
                                    repeatMenu = false
                                })
                            }
                        }
                    }
                }
                if (frequency == RepeatFrequency.MONTHLY || frequency == RepeatFrequency.YEARLY) {
                    FilterChip(
                        selected = persianRepeat,
                        onClick = { persianRepeat = !persianRepeat },
                        label = { Text(stringResource(R.string.tasks_repeat_persian_monthly)) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val trimmed = title.trim()
                if (trimmed.isNotEmpty()) {
                    val parsed = SmartTaskParser.parse(dueText)
                    val rule = frequency.takeIf { it != RepeatFrequency.NONE }?.let {
                        RecurrenceRule(
                            frequency = it,
                            interval = 1,
                            monthDay = task?.dueAtEpochMillis?.let { millis ->
                                if (persianRepeat) {
                                    android.icu.util.PersianCalendar().apply { timeInMillis = millis }
                                        .get(android.icu.util.Calendar.DAY_OF_MONTH)
                                } else {
                                    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).dayOfMonth
                                }
                            },
                            calendar = if (persianRepeat) RepeatCalendar.PERSIAN else RepeatCalendar.GREGORIAN,
                        )
                    }
                    onSave(
                        Task(
                            id = task?.id ?: 0,
                            title = trimmed,
                            description = description.trim(),
                            dueAtEpochMillis = when {
                                dueText.isNotBlank() -> parsed?.task?.dueAtEpochMillis
                                else -> task?.dueAtEpochMillis
                            },
                            isAllDay = parsed?.task?.isAllDay ?: task?.isAllDay ?: false,
                            priority = priority,
                            colorArgb = task?.colorArgb ?: 0xFF6E61D8,
                            tags = tags.split(',').map(String::trim).filter(String::isNotBlank),
                            project = project.trim(),
                            checklist = task?.checklist.orEmpty(),
                            link = task?.link,
                            attachmentUri = task?.attachmentUri,
                            recurrence = rule,
                            countdownTheme = task?.countdownTheme ?: "MARBLE",
                            isCompleted = task?.isCompleted ?: false,
                            isArchived = task?.isArchived ?: false,
                            focusMinutes = task?.focusMinutes ?: 0,
                            createdAtEpochMillis = task?.createdAtEpochMillis ?: System.currentTimeMillis(),
                            updatedAtEpochMillis = System.currentTimeMillis(),
                        ),
                    )
                }
            }) { Text(stringResource(R.string.tasks_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.tasks_cancel)) } },
    )
}

@Composable
private fun priorityLabel(priority: TaskPriority): String = when (priority) {
    TaskPriority.LOW -> stringResource(R.string.tasks_priority_low)
    TaskPriority.NORMAL -> stringResource(R.string.tasks_priority_normal)
    TaskPriority.HIGH -> stringResource(R.string.tasks_priority_high)
    TaskPriority.URGENT -> stringResource(R.string.tasks_priority_urgent)
}

@Composable
private fun repeatLabel(frequency: RepeatFrequency): String = when (frequency) {
    RepeatFrequency.NONE -> stringResource(R.string.tasks_repeat_none)
    RepeatFrequency.DAILY -> stringResource(R.string.tasks_repeat_daily)
    RepeatFrequency.WEEKLY -> stringResource(R.string.tasks_repeat_weekly)
    RepeatFrequency.MONTHLY -> stringResource(R.string.tasks_repeat_monthly)
    RepeatFrequency.YEARLY -> stringResource(R.string.tasks_repeat_yearly)
    RepeatFrequency.CUSTOM -> stringResource(R.string.tasks_repeat_weekly)
}

private fun launchSpeech(activity: Activity?, launch: (Intent) -> Unit) {
    if (activity == null) return
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, activity.resources.configuration.locales[0].toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_PROMPT, activity.getString(R.string.tasks_voice_input))
    }
    try {
        launch(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(activity, R.string.tasks_speech_unavailable, Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun localizedTaskDate(epochMillis: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    val numeralMode = LocalNumeralMode.current
    return remember(epochMillis, locale, numeralMode) { TaskDateLabels.tripleDate(epochMillis, locale, numeralMode) }
}
