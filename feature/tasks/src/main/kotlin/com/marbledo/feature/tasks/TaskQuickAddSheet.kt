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
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marbledo.core.designsystem.LocalNumeralMode
import com.marbledo.domain.model.CalendarDisplayMode
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

private enum class VoiceStep { CATEGORY, TITLE, DATE, TIME, PRIORITY }

private class SpeechLauncherHolder(var launch: (Intent) -> Unit = {})

/** How the due date is chosen: follow the typed text, clear it, or use the picked values. */
private const val SCHEDULE_AUTO = "auto"
private const val SCHEDULE_NONE = "none"
private const val SCHEDULE_SET = "set"

/**
 * The creation flow: one free-text box with live offline parsing, a full date and time picker, one-tap date
 * and time presets, priority and category chips, a voice assistant and an explicit countdown switch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskQuickAddSheet(
    categories: List<String>,
    languageTag: String,
    countdownThemeId: String,
    onAddCategory: (String) -> Unit,
    onSubmit: (Task) -> Unit,
    onDismiss: () -> Unit,
    initialText: String = "",
    modifier: Modifier = Modifier,
    /** ISO date (yyyy-MM-dd) to preselect, for example when the user taps a day in the calendar. */
    initialDateIso: String? = null,
    weekStartsSaturday: Boolean,
    pickerCalendar: CalendarDisplayMode,
    onPickerCalendarChange: (CalendarDisplayMode) -> Unit,
    /** Slots supplied by the app module so this feature does not depend on the countdown feature. */
    countdownThemePicker: @Composable (selectedThemeId: String, onSelect: (String) -> Unit) -> Unit = { _, _ -> },
    countdownThemeLabel: @Composable (themeId: String) -> String = { it },
) {
    val context = LocalContext.current
    val noCategoryLabel = stringResource(R.string.tasks_category_none)
    val voicePrompts = mapOf(
        VoiceStep.CATEGORY to stringResource(R.string.tasks_voice_prompt_category),
        VoiceStep.TITLE to stringResource(R.string.tasks_voice_prompt_title),
        VoiceStep.DATE to stringResource(R.string.tasks_voice_prompt_date),
        VoiceStep.TIME to stringResource(R.string.tasks_voice_prompt_time),
        VoiceStep.PRIORITY to stringResource(R.string.tasks_voice_prompt_priority),
    )
    val zone = remember { ZoneId.systemDefault() }
    val numeralMode = LocalNumeralMode.current
    var input by rememberSaveable { mutableStateOf(initialText) }
    var scheduleMode by rememberSaveable { mutableStateOf(if (initialDateIso != null) SCHEDULE_SET else SCHEDULE_AUTO) }
    var pickedDateIso by rememberSaveable { mutableStateOf(initialDateIso) }
    var pickedTimeIso by rememberSaveable { mutableStateOf<String?>(null) }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var priority by rememberSaveable { mutableStateOf(TaskPriority.NORMAL) }
    var category by rememberSaveable { mutableStateOf("") }
    var countdownEnabled by rememberSaveable { mutableStateOf(true) }
    var themeId by rememberSaveable { mutableStateOf(countdownThemeId) }
    var showCategoryDialog by rememberSaveable { mutableStateOf(false) }
    var categoryDraft by rememberSaveable { mutableStateOf("") }
    var showVoiceGuide by rememberSaveable { mutableStateOf(false) }
    var voiceStep by rememberSaveable { mutableStateOf<VoiceStep?>(null) }
    var voiceCategory by rememberSaveable { mutableStateOf("") }
    var voiceTitle by rememberSaveable { mutableStateOf("") }
    var voiceDate by rememberSaveable { mutableStateOf("") }
    var voiceTime by rememberSaveable { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusRequester = remember { FocusRequester() }

    // Date and time recognized in the typed text. The title is whatever text remains once the schedule is removed.
    val titleDraft = remember(input) { SmartTaskParser.parseSchedule(input)?.title.orEmpty() }
    val textSchedule = remember(input) {
        val parsed = SmartTaskParser.parseSchedule(input)
        val millis = parsed?.dueAtEpochMillis
        if (parsed == null || millis == null) {
            TaskSchedule(null, null)
        } else {
            val zoned = Instant.ofEpochMilli(millis).atZone(zone)
            TaskSchedule(
                date = if (parsed.recognizedDate || parsed.recognizedTime) zoned.toLocalDate() else null,
                time = if (parsed.recognizedTime) zoned.toLocalTime() else null,
            )
        }
    }
    val pickedDate = remember(pickedDateIso) { pickedDateIso?.let { runCatching { LocalDate.parse(it) }.getOrNull() } }
    val pickedTime = remember(pickedTimeIso) { pickedTimeIso?.let { runCatching { LocalTime.parse(it) }.getOrNull() } }
    val schedule: TaskSchedule = when (scheduleMode) {
        SCHEDULE_NONE -> TaskSchedule(null, null)
        SCHEDULE_SET -> TaskSchedule(pickedDate, pickedTime)
        else -> textSchedule
    }
    val resolvedDue = remember(schedule, zone) { schedule.toDueMillis(zone, LocalDate.now(zone)) }
    val resolvedAllDay = resolvedDue != null && schedule.time == null
    val availableCategories = remember(categories) { categories.map { it.trim() }.filter { it.isNotBlank() }.distinct() }

    fun applySchedule(date: LocalDate, time: LocalTime?) {
        scheduleMode = SCHEDULE_SET
        pickedDateIso = date.toString()
        pickedTimeIso = time?.toString()
    }

    fun pickDate(date: LocalDate) = applySchedule(date, schedule.time)

    fun pickTime(time: LocalTime?) =
        applySchedule(schedule.date ?: defaultDateFor(time, ZonedDateTime.now(zone)), time)

    fun clearSchedule() {
        scheduleMode = SCHEDULE_NONE
        pickedDateIso = null
        pickedTimeIso = null
    }

    fun submit(categoryOverride: String? = null) {
        val title = titleDraft.trim()
        if (title.isBlank()) {
            Toast.makeText(context, R.string.tasks_task_title_hint, Toast.LENGTH_SHORT).show()
            return
        }
        val chosenCategory = (categoryOverride ?: category).trim()
        if (chosenCategory.isNotBlank() && availableCategories.none { TextNormalizer.searchKey(it) == TextNormalizer.searchKey(chosenCategory) }) {
            onAddCategory(chosenCategory)
        }
        onSubmit(
            Task(
                title = title,
                dueAtEpochMillis = resolvedDue,
                isAllDay = resolvedAllDay,
                priority = priority,
                category = chosenCategory,
                countdownEnabled = countdownEnabled,
                countdownTheme = themeId,
            ),
        )
    }

    // ---- voice assistant -------------------------------------------------
    val speechLauncherHolder = remember { SpeechLauncherHolder() }

    fun resetVoiceDraft() {
        voiceCategory = ""
        voiceTitle = ""
        voiceDate = ""
        voiceTime = ""
        voiceStep = null
    }

    fun promptFor(step: VoiceStep): String = voicePrompts.getValue(step)

    fun startVoiceRecognition(step: VoiceStep) {
        voiceStep = step
        val activity = context as? Activity ?: return
        val recognizerLanguage = if (languageTag == "fa") "fa-IR" else "en-US"
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, recognizerLanguage)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, recognizerLanguage)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PROMPT, promptFor(step))
        }
        try {
            speechLauncherHolder.launch(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(activity, R.string.tasks_speech_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    fun finishVoiceTask(priorityPhrase: String?) {
        val combinedInput = listOf(voiceTitle, voiceDate, voiceTime).filter { it.isNotBlank() }.joinToString(" ")
        val voiceParsed = SmartTaskParser.parse(combinedInput)
        if (voiceParsed == null) {
            Toast.makeText(context, R.string.tasks_voice_title_required, Toast.LENGTH_SHORT).show()
            resetVoiceDraft()
            return
        }
        if (voiceDate.isNotBlank() && !voiceParsed.recognizedDate) {
            voiceStep = VoiceStep.DATE
            Toast.makeText(context, R.string.tasks_voice_date_retry, Toast.LENGTH_SHORT).show()
            return
        }
        if (voiceTime.isNotBlank() && !voiceParsed.recognizedTime) {
            voiceStep = VoiceStep.TIME
            Toast.makeText(context, R.string.tasks_voice_time_retry, Toast.LENGTH_SHORT).show()
            return
        }
        val voiceCategoryName = voiceCategory.trim().takeUnless(::isVoiceSkipPhrase).orEmpty()
        onSubmit(
            voiceParsed.task.copy(
                category = voiceCategoryName,
                priority = priorityPhrase?.let(::priorityFromVoice) ?: TaskPriority.NORMAL,
            ),
        )
        resetVoiceDraft()
        Toast.makeText(context, R.string.tasks_voice_task_created, Toast.LENGTH_SHORT).show()
    }

    fun acceptVoiceAnswer(step: VoiceStep, answer: String) {
        if (answer.isBlank()) {
            Toast.makeText(context, R.string.tasks_voice_try_again, Toast.LENGTH_SHORT).show()
            return
        }
        when (step) {
            VoiceStep.CATEGORY -> {
                voiceCategory = answer.trim().takeUnless(::isVoiceSkipPhrase).orEmpty()
                startVoiceRecognition(VoiceStep.TITLE)
            }
            VoiceStep.TITLE -> {
                if (isVoiceSkipPhrase(answer)) {
                    Toast.makeText(context, R.string.tasks_voice_title_required, Toast.LENGTH_SHORT).show()
                    resetVoiceDraft()
                } else {
                    voiceTitle = answer.trim()
                    startVoiceRecognition(VoiceStep.DATE)
                }
            }
            VoiceStep.DATE -> {
                voiceDate = answer.trim().takeUnless(::isVoiceSkipPhrase).orEmpty()
                startVoiceRecognition(VoiceStep.TIME)
            }
            VoiceStep.TIME -> {
                voiceTime = answer.trim().takeUnless(::isVoiceSkipPhrase).orEmpty()
                startVoiceRecognition(VoiceStep.PRIORITY)
            }
            VoiceStep.PRIORITY -> finishVoiceTask(answer.trim())
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val step = voiceStep
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (step != null && !spoken.isNullOrBlank()) acceptVoiceAnswer(step, spoken)
        else if (step != null) Toast.makeText(context, R.string.tasks_voice_try_again, Toast.LENGTH_SHORT).show()
    }
    SideEffect { speechLauncherHolder.launch = { intent -> speechLauncher.launch(intent) } }

    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoiceRecognition(VoiceStep.CATEGORY)
        else Toast.makeText(context, R.string.tasks_speech_unavailable, Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    val scheduleText = if (resolvedDue == null) {
        stringResource(R.string.tasks_date_none)
    } else {
        formatScheduleSummary(schedule.date ?: LocalDate.now(zone), schedule.time, pickerCalendar, languageTag, numeralMode, zone)
    }
    val hasTextSchedule = textSchedule.date != null || textSchedule.time != null

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, modifier = modifier.imePadding()) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.tasks_quick_add_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.tasks_quick_add_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { showVoiceGuide = true }) {
                    Icon(Icons.Outlined.Mic, contentDescription = stringResource(R.string.tasks_voice_input), tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.tasks_cancel))
                }
            }

            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                placeholder = { Text(stringResource(R.string.tasks_quick_add_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                trailingIcon = {
                    if (input.isNotBlank()) {
                        IconButton(onClick = { input = "" }) { Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.tasks_clear_input)) }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
            )

            if (hasTextSchedule && scheduleMode == SCHEDULE_AUTO) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Text(
                        TextNormalizer.formatDigits(stringResource(R.string.tasks_parsed_hint), numeralMode),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

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

            Text(stringResource(R.string.tasks_priority), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TaskPriority.entries.forEach { option ->
                    FilterChip(
                        selected = priority == option,
                        onClick = { priority = option },
                        label = { Text(priorityLabel(option)) },
                    )
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
                    FilterChip(
                        selected = category.isBlank(),
                        onClick = { category = "" },
                        label = { Text(stringResource(R.string.tasks_category_none)) },
                    )
                }
                items(availableCategories) { name ->
                    FilterChip(selected = category == name, onClick = { category = name }, label = { Text(name, maxLines = 1) })
                }
            }

            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text(stringResource(R.string.tasks_countdown_toggle_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (resolvedDue == null) stringResource(R.string.tasks_countdown_needs_date) else stringResource(R.string.tasks_countdown_toggle_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = countdownEnabled && resolvedDue != null,
                            enabled = resolvedDue != null,
                            onCheckedChange = { countdownEnabled = it },
                        )
                    }
                    if (countdownEnabled && resolvedDue != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                            Text(stringResource(R.string.tasks_countdown_theme, countdownThemeLabel(themeId)), style = MaterialTheme.typography.labelMedium)
                        }
                        countdownThemePicker(themeId) { selected -> themeId = selected }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { submit() }, modifier = Modifier.weight(1f), enabled = titleDraft.isNotBlank()) {
                    Text(stringResource(R.string.tasks_add))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.tasks_cancel)) }
            }
        }
    }

    if (showPicker) {
        SchedulePickerDialog(
            title = stringResource(R.string.tasks_schedule_title),
            mode = SchedulePickerMode.DATE_AND_TIME,
            initialDate = schedule.date ?: LocalDate.now(zone),
            initialTime = schedule.time,
            languageTag = languageTag,
            numeralMode = numeralMode,
            weekStartsSaturday = weekStartsSaturday,
            calendarMode = pickerCalendar,
            onCalendarModeChange = onPickerCalendarChange,
            onConfirm = { date, time ->
                applySchedule(date, time)
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

    if (showVoiceGuide) {
        AlertDialog(
            onDismissRequest = { showVoiceGuide = false },
            title = { Text(stringResource(R.string.tasks_voice_guide_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.tasks_voice_guide_body))
                    Text(stringResource(R.string.tasks_voice_guide_example), color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.tasks_voice_guide_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showVoiceGuide = false
                    if (SpeechRecognizer.isRecognitionAvailable(context)) microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                    else Toast.makeText(context, R.string.tasks_speech_unavailable, Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(R.string.tasks_voice_start)) }
            },
            dismissButton = { TextButton(onClick = { showVoiceGuide = false }) { Text(stringResource(R.string.tasks_cancel)) } },
        )
    }

    voiceStep?.let { step ->
        val stepNumber = VoiceStep.entries.indexOf(step) + 1
        AlertDialog(
            onDismissRequest = { resetVoiceDraft() },
            title = { Text(stringResource(R.string.tasks_voice_progress_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.tasks_voice_step_count, stepNumber, VoiceStep.entries.size))
                    Text(promptFor(step), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.tasks_voice_current_category, voiceCategory.ifBlank { noCategoryLabel }),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (voiceTitle.isNotBlank()) Text(stringResource(R.string.tasks_voice_current_title, voiceTitle), style = MaterialTheme.typography.bodySmall)
                    if (voiceDate.isNotBlank()) Text(stringResource(R.string.tasks_voice_current_date, voiceDate), style = MaterialTheme.typography.bodySmall)
                    if (voiceTime.isNotBlank()) Text(stringResource(R.string.tasks_voice_current_time, voiceTime), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { startVoiceRecognition(step) }) { Text(stringResource(R.string.tasks_voice_repeat)) } },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        when (step) {
                            VoiceStep.CATEGORY -> { voiceCategory = ""; startVoiceRecognition(VoiceStep.TITLE) }
                            VoiceStep.TITLE -> {
                                Toast.makeText(context, R.string.tasks_voice_title_required, Toast.LENGTH_SHORT).show()
                                resetVoiceDraft()
                            }
                            VoiceStep.DATE -> { voiceDate = ""; startVoiceRecognition(VoiceStep.TIME) }
                            VoiceStep.TIME -> { voiceTime = ""; startVoiceRecognition(VoiceStep.PRIORITY) }
                            VoiceStep.PRIORITY -> finishVoiceTask(null)
                        }
                    }) { Text(stringResource(R.string.tasks_voice_skip)) }
                    TextButton(onClick = { resetVoiceDraft() }) { Text(stringResource(R.string.tasks_cancel)) }
                }
            },
        )
    }
}

private fun isVoiceSkipPhrase(value: String): Boolean {
    val compact = TextNormalizer.searchKey(value).replace(" ", "")
    return compact in setOf(
        "skip", "none", "no", "nocategory", "nodate", "notime", "ندارد", "هیچ", "هیچکدام", "ردکن", "بعدی", "نه", "خیر",
        "بدوندسته", "بدونتاریخ", "بدونساعت", "ندارم",
    )
}

private fun priorityFromVoice(value: String): TaskPriority {
    val phrase = TextNormalizer.searchKey(value)
    val compact = phrase.replace(" ", "")
    return when {
        listOf("فوری", "اضطراری", "خیلی مهم", "urgent", "asap").any { phrase.contains(it) } -> TaskPriority.URGENT
        listOf("زیاد", "بالا", "مهم", "high").any { phrase.contains(it) } -> TaskPriority.HIGH
        listOf("کم", "پایین", "low").any { phrase.contains(it) } -> TaskPriority.LOW
        compact in setOf("معمولی", "عادی", "normal", "medium") -> TaskPriority.NORMAL
        else -> TaskPriority.NORMAL
    }
}
