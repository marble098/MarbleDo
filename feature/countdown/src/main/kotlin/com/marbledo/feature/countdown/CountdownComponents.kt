package com.marbledo.feature.countdown

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.marbledo.core.designsystem.LocalNumeralMode
import com.marbledo.domain.model.CountdownTheme
import com.marbledo.domain.model.CalendarDisplayMode
import com.marbledo.domain.model.Task
import com.marbledo.domain.model.TaskPriority
import com.marbledo.domain.util.TextNormalizer
import java.time.Duration
import java.time.ZonedDateTime

/** Immersive full-screen countdown with a theme picker that switches the look in place. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountdownFocusDialog(
    task: Task,
    themeId: String,
    calendarDisplay: CalendarDisplayMode = CalendarDisplayMode.PERSIAN,
    onThemeSelected: (String) -> Unit = {},
    onDismiss: () -> Unit,
) {
    val theme = CountdownTheme.from(themeId)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(18.dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.countdown_close))
                }
                Column(
                    modifier = Modifier.fillMaxWidth().align(Alignment.Center).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(task.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    task.dueAtEpochMillis?.let { due ->
                        CountdownFace(task.title, due, theme, Modifier.fillMaxWidth(), compact = false)
                        Text(
                            stringResource(R.string.countdown_target_date, countdownDateLabel(due, calendarDisplay)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Text(stringResource(R.string.countdown_quick_theme), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    ThemeSwatchRow(selected = theme, onSelect = { onThemeSelected(it.id) })
                    Text(stringResource(R.string.countdown_focus_mode), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Horizontal strip of theme swatches used by the hero card and the focus view. */
@Composable
fun ThemeSwatchRow(
    selected: CountdownTheme,
    onSelect: (CountdownTheme) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(CountdownTheme.entries, key = { it.id }) { theme ->
            CountdownThemeSwatch(theme = theme, selected = theme == selected, onClick = { onSelect(theme) })
        }
    }
}

/** Bottom sheet gallery with a live preview of every redesigned theme. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountdownThemeGallerySheet(
    selectedThemeId: String,
    previewTitle: String,
    previewDueAtEpochMillis: Long,
    onThemeSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selected = CountdownTheme.from(selectedThemeId)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 22.dp)) {
            Text(stringResource(R.string.countdown_gallery_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.countdown_gallery_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(CountdownTheme.entries, key = { it.id }) { theme ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.large)
                            .clickable { onThemeSelected(theme.id) },
                    ) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                countdownThemeLabel(theme),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (theme == selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (theme == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.weight(1f))
                            if (theme == selected) {
                                Text(stringResource(R.string.countdown_theme_active), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        CountdownFace(previewTitle, previewDueAtEpochMillis, theme, Modifier.fillMaxWidth(), compact = true)
                        Text(
                            countdownThemeCaption(theme),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Small card for the dashboard's "upcoming moments" strip. */
@Composable
fun CountdownMiniCard(
    title: String,
    dueAtEpochMillis: Long,
    themeId: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = CountdownTheme.from(themeId)
    val units = rememberCountdownUnits(dueAtEpochMillis)
    val numeralMode = LocalNumeralMode.current
    Surface(
        modifier = modifier.width(184.dp).clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(countdownThemeBrush(theme)))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                TextNormalizer.formatDigits(remainingShort(units), numeralMode),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                countdownThemeLabel(theme),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Improved countdown creation sheet: presets first, validated date/time second. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountdownCreateSheet(
    calendarDisplay: CalendarDisplayMode,
    onCalendarDisplaySelected: (CalendarDisplayMode) -> Unit,
    onDismiss: () -> Unit,
    onCreate: (Task) -> Unit,
) {
    val initialTarget = remember { ZonedDateTime.now().plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0).toInstant().toEpochMilli() }
    var title by rememberSaveable { mutableStateOf("") }
    var selectedCalendar by rememberSaveable { mutableStateOf(calendarDisplay) }
    var dateText by rememberSaveable { mutableStateOf(CountdownDateUtils.dateInput(initialTarget, calendarDisplay)) }
    var timeText by rememberSaveable { mutableStateOf(CountdownDateUtils.timeInput(initialTarget)) }
    val parsedDate = remember(dateText, selectedCalendar) { CountdownDateUtils.parseDateInput(dateText, selectedCalendar) }
    val parsedTime = remember(timeText) { CountdownDateUtils.parseTimeInput(timeText) }
    val targetMillis = remember(dateText, timeText, selectedCalendar) { CountdownDateUtils.parseDateTime(dateText, timeText, selectedCalendar) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun applyPreset(target: ZonedDateTime) {
        val epoch = target.withSecond(0).withNano(0).toInstant().toEpochMilli()
        dateText = CountdownDateUtils.dateInput(epoch, selectedCalendar)
        timeText = CountdownDateUtils.timeInput(epoch)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.countdown_create_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.countdown_create_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.countdown_title_field)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { FilterChip(selected = false, onClick = { applyPreset(ZonedDateTime.now().plusHours(1)) }, label = { Text(stringResource(R.string.countdown_preset_hour)) }) }
                item { FilterChip(selected = false, onClick = { applyPreset(ZonedDateTime.now().plusDays(1)) }, label = { Text(stringResource(R.string.countdown_preset_tomorrow)) }) }
                item { FilterChip(selected = false, onClick = { applyPreset(ZonedDateTime.now().plusWeeks(1)) }, label = { Text(stringResource(R.string.countdown_preset_week)) }) }
                item { FilterChip(selected = false, onClick = { applyPreset(ZonedDateTime.now().plusMonths(1)) }, label = { Text(stringResource(R.string.countdown_preset_month)) }) }
                item { FilterChip(selected = false, onClick = { applyPreset(ZonedDateTime.now().plusYears(1)) }, label = { Text(stringResource(R.string.countdown_preset_year)) }) }
            }
            Text(stringResource(R.string.countdown_calendar_display), style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(CalendarDisplayMode.entries) { mode ->
                    FilterChip(
                        selected = selectedCalendar == mode,
                        onClick = {
                            val previousTarget = targetMillis
                            selectedCalendar = mode
                            if (previousTarget != null) dateText = CountdownDateUtils.dateInput(previousTarget, mode)
                            onCalendarDisplaySelected(mode)
                        },
                        label = { Text(countdownCalendarLabel(mode), maxLines = 1) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    label = { Text(stringResource(R.string.countdown_date_field)) },
                    supportingText = {
                        when {
                            parsedDate == null -> Text(stringResource(R.string.countdown_date_hint))
                            targetMillis != null -> Text(stringResource(R.string.countdown_date_preview, countdownDateLabel(targetMillis, selectedCalendar)))
                        }
                    },
                    isError = dateText.isNotBlank() && parsedDate == null,
                    singleLine = true,
                    modifier = Modifier.weight(1.3f),
                )
                OutlinedTextField(
                    value = timeText,
                    onValueChange = { timeText = it },
                    label = { Text(stringResource(R.string.countdown_time_field)) },
                    supportingText = { if (parsedTime == null) Text(stringResource(R.string.countdown_time_hint)) },
                    isError = timeText.isNotBlank() && parsedTime == null,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        val dueAt = targetMillis ?: return@Button
                        onCreate(Task(title = title.trim(), dueAtEpochMillis = dueAt, priority = TaskPriority.NORMAL))
                    },
                    enabled = title.isNotBlank() && targetMillis != null,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.countdown_create_action)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.countdown_cancel)) }
            }
        }
    }
}

/** Delete affordance kept next to the countdown list in the dashboard. */
@Composable
fun CountdownRemoveAction(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.countdown_disable))
    }
}

@Composable
internal fun countdownDateLabel(epochMillis: Long, mode: CalendarDisplayMode): String {
    val locale = LocalConfiguration.current.locales[0]
    val numeralMode = LocalNumeralMode.current
    return remember(epochMillis, mode, locale, numeralMode) {
        CountdownDateUtils.format(epochMillis, mode, locale, numeralMode)
    }
}

@Composable
internal fun countdownCalendarLabel(mode: CalendarDisplayMode): String = when (mode) {
    CalendarDisplayMode.PERSIAN -> stringResource(R.string.countdown_calendar_persian)
    CalendarDisplayMode.GREGORIAN -> stringResource(R.string.countdown_calendar_gregorian)
    CalendarDisplayMode.ISLAMIC_CIVIL -> stringResource(R.string.countdown_calendar_lunar)
}

internal fun remainingShort(units: CountdownUnits): String = when {
    units.isFinished -> "۰"
    units.days > 0 -> "${units.days} · ${units.hours.toString().padStart(2, '0')}:${units.minutes.toString().padStart(2, '0')}"
    else -> "${units.hours.toString().padStart(2, '0')}:${units.minutes.toString().padStart(2, '0')}:${units.seconds.toString().padStart(2, '0')}"
}

internal fun remainingLabel(dueAtEpochMillis: Long): String {
    val duration = Duration.ofMillis((dueAtEpochMillis - System.currentTimeMillis()).coerceAtLeast(0))
    val totalSeconds = duration.seconds
    val days = totalSeconds / 86_400
    val hours = ((totalSeconds / 3_600) % 24).toInt()
    val minutes = ((totalSeconds / 60) % 60).toInt()
    return "$days · ${hours.toString().padStart(2, '0')} · ${minutes.toString().padStart(2, '0')}"
}
