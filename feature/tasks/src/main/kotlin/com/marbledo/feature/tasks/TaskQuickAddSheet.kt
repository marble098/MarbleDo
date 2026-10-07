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
import com.marbledo.domain.model.Task
import com.marbledo.domain.model.TaskPriority
import com.marbledo.domain.util.TextNormalizer
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

private enum class VoiceStep { CATEGORY, TITLE, DATE, TIME, PRIORITY }

private class SpeechLauncherHolder(var launch: (Intent) -> Unit = {})

/**
 * The improved creation flow: one free-text box with live offline parsing, one-tap date/time
 * presets, priority and category chips, a voice assistant and an explicit countdown switch.
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
    /** Slots supplied by the app module so this feature does not depend on the countdown feature. */
    countdownThemePicker: @Composable (selectedThemeId: String, onSelect: (String) -> Unit) -> Unit = { _, _ -> },
    countdownThemeLabel: @Composable (themeId: String) -> String = { it },
) {
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    val numeralMode = LocalNumeralMode.current
    var input by rememberSaveable { mutableStateOf(initialText) }
    var presetDayIso by rememberSaveable { mutableStateOf<String?>(null) }
    var presetTimeIso by rememberSaveable { mutableStateOf<String?>(null) }
    var dateText by rememberSaveable { mutableStateOf("") }
    var timeText by rememberSaveable { mutableStateOf("") }
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

    val parsed = remember(input) { SmartTaskParser.parse(input) }
    val manualParse = remember(dateText, timeText) {
        if (dateText.isBlank() && timeText.isBlank()) null
        else SmartTaskParser.parse(listOf(dateText, timeText).filter { it.isNotBlank() }.joinToString(" "))
    }
    val presetDay = remember(presetDayIso) { presetDayIso?.let { runCatching { LocalDate.parse(it) }.getOrNull() } }
    val presetTime = remember(presetTimeIso) { presetTimeIso?.let { runCatching { LocalTime.parse(it) }.getOrNull() } }
    val resolvedDue = remember(parsed, manualParse, presetDay, presetTime, zone) {
        val parsedDate = parsed?.takeIf { it.recognizedDate }?.task?.dueAtEpochMillis
        val parsedTime = parsed?.takeIf { it.recognizedTime }?.task?.dueAtEpochMillis
        val manualDate = manualParse?.takeIf { it.recognizedDate }?.task?.dueAtEpochMillis
        val manualTime = manualParse?.takeIf { it.recognizedTime }?.task?.dueAtEpochMillis
        val day = presetDay
            ?: manualDate?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            ?: parsedDate?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        val time = manualTime?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() }
            ?: parsedTime?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() }
            ?: presetTime
        if (day == null && time == null) null
        else ZonedDateTime.of(day ?: LocalDate.now(zone), time ?: LocalTime.of(9, 0), zone).toInstant().toEpochMilli()
    }
    val hasExplicitTime = manualParse?.recognizedTime == true || parsed?.recognizedTime == true || presetTime != null
    val availableCategories = remember(categories) { categories.map { it.trim() }.filter { it.isNotBlank() }.distinct() }

    fun clearSchedule() {
        presetDayIso = null
        presetTimeIso = null
        dateText = ""
        timeText = ""
    }

    fun submit(titleOverride: String? = null, priorityOverride: TaskPriority? = null, categoryOverride: String? = null) {
        val title = (titleOverride ?: parsed?.task?.title ?: input).trim()
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
                isAllDay = resolvedDue != null && !hasExplicitTime,
                priority = priorityOverride ?: priority,
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

    fun promptFor(step: VoiceStep): String = context.getString(
        when (step) {
            VoiceStep.CATEGORY -> R.string.tasks_voice_prompt_category
            VoiceStep.TITLE -> R.string.tasks_voice_prompt_title
            VoiceStep.DATE -> R.string.tasks_voice_prompt_date
            VoiceStep.TIME -> R.string.tasks_voice_prompt_time
            VoiceStep.PRIORITY -> R.string.tasks_voice_prompt_priority
        },
    )

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

            if (parsed != null && (parsed.recognizedDate || parsed.recognizedTime)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Text(
                        TextNormalizer.formatDigits(stringResource(R.string.tasks_parsed_hint), numeralMode),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (resolvedDue != null) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(17.dp))
                        Text(
                            localizedTaskDate(resolvedDue),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(onClick = { clearSchedule() }) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.tasks_clear_due), tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Text(stringResource(R.string.tasks_when), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(horizontal = 1.dp)) {
                item {
                    FilterChip(
                        selected = presetDayIso == null && dateText.isBlank(),
                        onClick = { clearSchedule() },
                        label = { Text(stringResource(R.string.tasks_date_none)) },
                    )
                }
                items(datePresets) { (labelRes, dayOffset) ->
                    val target = LocalDate.now(zone).plusDays(dayOffset.toLong())
                    FilterChip(
                        selected = presetDayIso == target.toString(),
                        onClick = { presetDayIso = target.toString() },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(horizontal = 1.dp)) {
                items(timePresets) { preset ->
                    FilterChip(
                        selected = presetTimeIso == preset.toString(),
                        onClick = {
                            presetTimeIso = if (presetTimeIso == preset.toString()) null else preset.toString()
                            timeText = ""
                        },
                        label = { Text(TextNormalizer.formatDigits(preset.hour.toString().padStart(2, '0') + ":00", numeralMode)) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it; presetDayIso = null },
                    label = { Text(stringResource(R.string.tasks_date_field)) },
                    placeholder = { Text(stringResource(R.string.tasks_date_hint)) },
                    isError = dateText.isNotBlank() && manualParse?.recognizedDate != true,
                    singleLine = true,
                    modifier = Modifier.weight(1.4f),
                )
                OutlinedTextField(
                    value = timeText,
                    onValueChange = { timeText = it; presetTimeIso = null },
                    label = { Text(stringResource(R.string.tasks_time_field)) },
                    placeholder = { Text(stringResource(R.string.tasks_time_hint)) },
                    isError = timeText.isNotBlank() && manualParse?.recognizedTime != true,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

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
                Button(onClick = { submit() }, modifier = Modifier.weight(1f), enabled = (parsed?.task?.title ?: input).isNotBlank()) {
                    Text(stringResource(R.string.tasks_add))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.tasks_cancel)) }
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
                        stringResource(R.string.tasks_voice_current_category, voiceCategory.ifBlank { context.getString(R.string.tasks_category_none) }),
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

private val timePresets = listOf(LocalTime.of(8, 0), LocalTime.of(12, 0), LocalTime.of(18, 0), LocalTime.of(21, 0))

/** Day offsets stay relative so the chips never go stale while the sheet is open. */
private val datePresets = listOf(
    R.string.tasks_today to 0L,
    R.string.tasks_tomorrow to 1L,
    R.string.tasks_date_next_week to 7L,
    R.string.tasks_date_next_month to 30L,
)

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
