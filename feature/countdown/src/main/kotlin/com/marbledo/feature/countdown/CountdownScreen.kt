package com.marbledo.feature.countdown

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.marbledo.core.designsystem.LocalReduceMotion
import com.marbledo.core.designsystem.LocalNumeralMode
import com.marbledo.domain.model.CalendarDisplayMode
import com.marbledo.domain.model.Task
import com.marbledo.domain.model.TaskPriority
import com.marbledo.domain.util.TextNormalizer
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.ZonedDateTime

private enum class CountdownTheme(val id: String, val stringId: Int) {
    MINIMAL("MINIMAL", R.string.countdown_theme_minimal),
    GLASS("GLASS", R.string.countdown_theme_glass),
    NEON("NEON", R.string.countdown_theme_neon),
    FLIP_CLOCK("FLIP_CLOCK", R.string.countdown_theme_flip),
    CIRCULAR("CIRCULAR", R.string.countdown_theme_circular),
    LIQUID("LIQUID", R.string.countdown_theme_liquid),
    RING("RING", R.string.countdown_theme_ring),
    ANALOG("ANALOG", R.string.countdown_theme_analog),
    TERMINAL("TERMINAL", R.string.countdown_theme_terminal),
    MARBLE("MARBLE", R.string.countdown_theme_marble),
    AURORA("AURORA", R.string.countdown_theme_aurora),
    RETRO("RETRO", R.string.countdown_theme_retro),
    ;

    companion object {
        fun from(id: String): CountdownTheme = entries.firstOrNull { it.id == id } ?: MARBLE
    }
}

@Composable
fun CountdownScreen(
    tasks: List<Task>,
    selectedThemeId: String,
    calendarDisplay: CalendarDisplayMode,
    onThemeSelected: (String) -> Unit,
    onCalendarDisplaySelected: (CalendarDisplayMode) -> Unit,
    onAddCountdown: (Task) -> Unit,
    onOpenFocus: (Task) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    val active = remember(tasks) {
        tasks.filter { !it.isCompleted && !it.isArchived && it.dueAtEpochMillis != null }
            .sortedBy { it.dueAtEpochMillis }
    }
    val theme = CountdownTheme.from(selectedThemeId)
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.countdown_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.countdown_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = { showCreateDialog = true }) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(5.dp))
                Text(stringResource(R.string.countdown_add))
            }
        }
        Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Text(
                stringResource(R.string.countdown_calendar_display),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 18.dp, bottom = 4.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(CalendarDisplayMode.entries) { mode ->
                    FilterChip(
                        selected = calendarDisplay == mode,
                        onClick = { onCalendarDisplaySelected(mode) },
                        label = { Text(calendarDisplayLabel(mode)) },
                    )
                }
            }
        }
        if (active.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.countdown_empty), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { showCreateDialog = true }) { Text(stringResource(R.string.countdown_add_first)) }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    CountdownFace(
                        title = active.first().title,
                        dueAtEpochMillis = active.first().dueAtEpochMillis!!,
                        theme = theme,
                        modifier = Modifier.fillMaxWidth().clickable { onOpenFocus(active.first()) },
                    )
                    Text(
                        stringResource(R.string.countdown_target_date, countdownDateLabel(active.first().dueAtEpochMillis!!, calendarDisplay)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp),
                        textAlign = TextAlign.Center,
                    )
                }
                item {
                    Text(stringResource(R.string.countdown_themes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxWidth().height(210.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        userScrollEnabled = true,
                    ) {
                        items(CountdownTheme.entries) { item ->
                            FilterChip(
                                selected = item == theme,
                                onClick = { onThemeSelected(item.id) },
                                label = { Text(stringResource(item.stringId), maxLines = 1) },
                            )
                        }
                    }
                }
                item {
                    Text(stringResource(R.string.countdown_upcoming), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                items(active.drop(1), key = { it.id }) { task ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenFocus(task) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(task.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                Text(stringResource(R.string.countdown_target_date, countdownDateLabel(task.dueAtEpochMillis!!, calendarDisplay)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(stringResource(R.string.countdown_remaining, TextNormalizer.formatDigits(remainingLabel(task.dueAtEpochMillis!!), LocalNumeralMode.current)), style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { onOpenFocus(task) }) { Text(stringResource(R.string.countdown_focus)) }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CountdownCreateDialog(
            calendarDisplay = calendarDisplay,
            onCalendarDisplaySelected = onCalendarDisplaySelected,
            onDismiss = { showCreateDialog = false },
            onCreate = { task -> onAddCountdown(task); showCreateDialog = false },
        )
    }
}

@Composable
private fun CountdownCreateDialog(
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
    val targetMillis = remember(dateText, timeText, selectedCalendar) {
        CountdownDateUtils.parseDateTime(dateText, timeText, selectedCalendar)
    }

    fun applyPreset(target: ZonedDateTime) {
        val epoch = target.withSecond(0).withNano(0).toInstant().toEpochMilli()
        dateText = CountdownDateUtils.dateInput(epoch, selectedCalendar)
        timeText = CountdownDateUtils.timeInput(epoch)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.countdown_create_title)) },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.countdown_create_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.countdown_title_field)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
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
                            label = { Text(calendarDisplayLabel(mode), maxLines = 1) },
                        )
                    }
                }
                Text(stringResource(R.string.countdown_quick_presets), style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { FilterChip(selected = false, onClick = { applyPreset(ZonedDateTime.now().plusHours(1)) }, label = { Text(stringResource(R.string.countdown_preset_hour)) }) }
                    item { FilterChip(selected = false, onClick = { applyPreset(ZonedDateTime.now().plusDays(1)) }, label = { Text(stringResource(R.string.countdown_preset_tomorrow)) }) }
                    item { FilterChip(selected = false, onClick = { applyPreset(ZonedDateTime.now().plusWeeks(1)) }, label = { Text(stringResource(R.string.countdown_preset_week)) }) }
                    item { FilterChip(selected = false, onClick = { applyPreset(ZonedDateTime.now().plusMonths(1)) }, label = { Text(stringResource(R.string.countdown_preset_month)) }) }
                    item { FilterChip(selected = false, onClick = { applyPreset(ZonedDateTime.now().plusYears(1)) }, label = { Text(stringResource(R.string.countdown_preset_year)) }) }
                }
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    label = { Text(stringResource(R.string.countdown_date_field)) },
                    placeholder = { Text("YYYY/MM/DD") },
                    supportingText = {
                        when {
                            parsedDate == null -> Text(stringResource(R.string.countdown_date_hint))
                            targetMillis != null -> Text(stringResource(R.string.countdown_date_preview, countdownDateLabel(targetMillis, selectedCalendar)))
                        }
                    },
                    isError = dateText.isNotBlank() && parsedDate == null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = timeText,
                    onValueChange = { timeText = it },
                    label = { Text(stringResource(R.string.countdown_time_field)) },
                    placeholder = { Text("14:30") },
                    supportingText = {
                        if (parsedTime == null) Text(stringResource(R.string.countdown_time_hint))
                    },
                    isError = timeText.isNotBlank() && parsedTime == null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && targetMillis != null,
                onClick = {
                    val dueAt = targetMillis ?: return@TextButton
                    onCreate(Task(title = title.trim(), dueAtEpochMillis = dueAt, priority = TaskPriority.NORMAL))
                },
            ) { Text(stringResource(R.string.countdown_create_action)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.countdown_cancel)) } },
    )
}

@Composable
private fun calendarDisplayLabel(mode: CalendarDisplayMode): String = when (mode) {
    CalendarDisplayMode.PERSIAN -> stringResource(R.string.countdown_calendar_persian)
    CalendarDisplayMode.GREGORIAN -> stringResource(R.string.countdown_calendar_gregorian)
    CalendarDisplayMode.ISLAMIC_CIVIL -> stringResource(R.string.countdown_calendar_lunar)
}

@Composable
private fun countdownDateLabel(epochMillis: Long, mode: CalendarDisplayMode): String {
    val locale = LocalConfiguration.current.locales[0]
    val numeralMode = LocalNumeralMode.current
    return remember(epochMillis, mode, locale, numeralMode) {
        CountdownDateUtils.format(epochMillis, mode, locale, numeralMode)
    }
}

@Composable
fun CountdownFocusDialog(
    task: Task,
    themeId: String,
    calendarDisplay: CalendarDisplayMode = CalendarDisplayMode.PERSIAN,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(Modifier.fillMaxSize()) {
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(18.dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.countdown_close))
                }
                Column(
                    modifier = Modifier.fillMaxWidth().align(Alignment.Center).padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Text(task.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    task.dueAtEpochMillis?.let {
                        CountdownFace(task.title, it, CountdownTheme.from(themeId), Modifier.fillMaxWidth())
                        Text(stringResource(R.string.countdown_target_date, countdownDateLabel(it, calendarDisplay)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(stringResource(R.string.countdown_focus_mode), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
@Composable
private fun CountdownFace(
    title: String,
    dueAtEpochMillis: Long,
    theme: CountdownTheme,
    modifier: Modifier = Modifier,
) {
    var nowMillis by rememberSaveable(dueAtEpochMillis) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(dueAtEpochMillis) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val remaining = remember(dueAtEpochMillis, nowMillis) {
        Duration.ofMillis((dueAtEpochMillis - nowMillis).coerceAtLeast(0))
    }
    val totalSeconds = remaining.seconds
    val days = totalSeconds / 86_400
    val hours = ((totalSeconds / 3_600) % 24).toInt()
    val minutes = ((totalSeconds / 60) % 60).toInt()
    val seconds = (totalSeconds % 60).toInt()
    val numeralMode = LocalNumeralMode.current
    val time = listOf(days, hours, minutes, seconds).joinToString(" : ") {
        TextNormalizer.formatDigits(it.toString().padStart(2, '0'), numeralMode)
    }
    val accent = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val reduceMotion = LocalReduceMotion.current

    when (theme) {
        CountdownTheme.MINIMAL -> Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 2)
                Spacer(Modifier.height(12.dp))
                Text(time, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light), color = accent)
                Text(stringResource(R.string.countdown_days_hours_minutes_seconds), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        CountdownTheme.GLASS -> Card(
            modifier,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
        ) {
            CountdownTexts(title, time, stringResource(R.string.countdown_glass), accent, Modifier.fillMaxWidth().padding(26.dp))
        }
        CountdownTheme.NEON -> Card(
            modifier,
            colors = CardDefaults.cardColors(containerColor = Color(0xFF10101D)),
            border = BorderStroke(1.dp, Brush.linearGradient(listOf(Color(0xFF00F5D4), Color(0xFFB44AFF), Color(0xFFFF4FA3)))),
        ) {
            CountdownTexts(title, time, stringResource(R.string.countdown_neon), Color(0xFF00F5D4), Modifier.fillMaxWidth().padding(24.dp), mono = true)
        }
        CountdownTheme.FLIP_CLOCK -> Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, maxLines = 2)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf(days, hours, minutes, seconds).forEach { value ->
                        FlipUnit(TextNormalizer.formatDigits(value.toString().padStart(2, '0'), numeralMode))
                    }
                }
                Text(stringResource(R.string.countdown_flip_units), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
            }
        }
        CountdownTheme.CIRCULAR -> Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                CircularCountdown(time, Brush.linearGradient(listOf(accent, onSurface)), days, Modifier.size(180.dp))
            }
        }
        CountdownTheme.LIQUID -> {
            val transition = rememberInfiniteTransition(label = "liquid-wave")
            val phase by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = if (reduceMotion) infiniteRepeatable(tween(4_000), RepeatMode.Restart) else infiniteRepeatable(tween(2_600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "liquid-phase",
            )
            Card(modifier, colors = CardDefaults.cardColors(containerColor = Color(0xFFE7F6F5))) {
                Box(Modifier.fillMaxWidth().height(220.dp)) {
                    Canvas(Modifier.fillMaxSize()) {
                        val waveY = size.height * (0.78f - phase * 0.12f)
                        drawRect(Brush.verticalGradient(listOf(Color(0xFF70D6C1), Color(0xFF2D87AA))), topLeft = Offset(0f, waveY), size = Size(size.width, size.height - waveY))
                    }
                    Column(Modifier.align(Alignment.Center).padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(title, color = Color(0xFF143F52), style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
                        Text(time, color = Color(0xFF143F52), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.countdown_liquid), color = Color(0xFF143F52), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        CountdownTheme.RING -> Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                CircularCountdown(time, Brush.sweepGradient(listOf(Color(0xFF6A5AE0), Color(0xFF00C8A8), Color(0xFFFFB25E), Color(0xFF6A5AE0))), days, Modifier.size(190.dp), track = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
        CountdownTheme.ANALOG -> Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                AnalogClock(remaining, accent, Modifier.size(190.dp))
                Text(stringResource(R.string.countdown_remaining, TextNormalizer.formatDigits(remainingLabel(dueAtEpochMillis), numeralMode)), style = MaterialTheme.typography.labelMedium)
            }
        }
        CountdownTheme.TERMINAL -> Card(modifier, colors = CardDefaults.cardColors(containerColor = Color(0xFF07130E))) {
            Column(Modifier.fillMaxWidth().padding(22.dp)) {
                Text(stringResource(R.string.countdown_terminal_status), color = Color(0xFF68FF9A), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
                Text(time, color = Color(0xFF68FF9A), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(title, color = Color(0xFFB4FFC7), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
        }
        CountdownTheme.MARBLE -> Card(
            modifier,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                Box(
                    Modifier.size(112.dp).shadow(12.dp, CircleShape).clip(CircleShape).background(
                        Brush.radialGradient(listOf(Color.White, Color(0xFFC9C0FF), Color(0xFF7665D6), Color(0xFF352D77))),
                    ),
                ) {
                    Box(Modifier.size(34.dp).align(Alignment.TopStart).padding(12.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.7f)))
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                    Spacer(Modifier.height(8.dp))
                    Text(time, style = MaterialTheme.typography.titleLarge, color = accent, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.countdown_marble), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        CountdownTheme.AURORA -> {
            val transition = rememberInfiniteTransition(label = "aurora")
            val phase by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = if (reduceMotion) infiniteRepeatable(tween(5_000), RepeatMode.Reverse) else infiniteRepeatable(tween(3_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "aurora-phase",
            )
            val brush = Brush.linearGradient(listOf(Color(0xFF102D50), Color(0xFF167C80).copy(alpha = 0.8f + 0.2f * phase), Color(0xFF3E2A72)))
            Card(modifier, colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
                Box(Modifier.fillMaxWidth().background(brush, RoundedCornerShape(26.dp)).padding(26.dp)) {
                    CountdownTexts(title, time, stringResource(R.string.countdown_aurora), Color.White, Modifier.fillMaxWidth())
                }
            }
        }
        CountdownTheme.RETRO -> Card(modifier, colors = CardDefaults.cardColors(containerColor = Color(0xFF18130A))) {
            Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.countdown_retro), color = Color(0xFFFFBF52), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
                Text(time, color = Color(0xFFFFBF52), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.headlineMedium, letterSpacing = 1.sp)
                Text(title, color = Color(0xFFEED9A8), style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
        }
    }
}

@Composable
private fun CountdownTexts(title: String, time: String, caption: String, color: Color, modifier: Modifier, mono: Boolean = false) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = color, textAlign = TextAlign.Center, maxLines = 2)
        Text(time, style = MaterialTheme.typography.headlineMedium, fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default, color = color, fontWeight = FontWeight.Bold)
        Text(caption, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.75f))
    }
}

@Composable
private fun FlipUnit(value: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(10.dp)) {
        Text(value, modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp), fontFamily = FontFamily.Monospace, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CircularCountdown(
    time: String,
    brush: Brush,
    days: Long,
    modifier: Modifier,
    track: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(12.dp)) {
            val strokeWidth = 12.dp.toPx()
            drawArc(track, -90f, 360f, false, style = Stroke(strokeWidth, cap = StrokeCap.Round))
            val fraction = (days.toFloat() / 365f).coerceIn(0.025f, 1f)
            drawArc(brush, -90f, 360f * fraction, false, style = Stroke(strokeWidth, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(TextNormalizer.formatDigits(days.toString(), LocalNumeralMode.current), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.countdown_days), style = MaterialTheme.typography.labelSmall)
            Text(time.substringAfter(" : "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AnalogClock(remaining: Duration, accent: Color, modifier: Modifier) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val outlineColor = MaterialTheme.colorScheme.onSurfaceVariant
    val handColor = MaterialTheme.colorScheme.onSurface
    Canvas(modifier.padding(10.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * 0.44f
        drawCircle(color = surfaceColor, radius = radius, center = center)
        drawCircle(color = accent.copy(alpha = 0.55f), radius = radius, center = center, style = Stroke(3.dp.toPx()))
        val totalSeconds = remaining.seconds
        val hours = ((totalSeconds / 3_600) % 12).toFloat()
        val minutes = ((totalSeconds / 60) % 60).toFloat()
        val seconds = (totalSeconds % 60).toFloat()
        fun hand(angle: Float, length: Float, width: Float, color: Color) {
            val radians = Math.toRadians((angle - 90f).toDouble())
            val end = Offset(center.x + kotlin.math.cos(radians).toFloat() * length, center.y + kotlin.math.sin(radians).toFloat() * length)
            drawLine(color, center, end, width, cap = StrokeCap.Round)
        }
        repeat(12) { index ->
            val angle = Math.toRadians((index * 30 - 90).toDouble())
            val inner = Offset(center.x + kotlin.math.cos(angle).toFloat() * radius * 0.84f, center.y + kotlin.math.sin(angle).toFloat() * radius * 0.84f)
            val outer = Offset(center.x + kotlin.math.cos(angle).toFloat() * radius * 0.94f, center.y + kotlin.math.sin(angle).toFloat() * radius * 0.94f)
            drawLine(outlineColor, inner, outer, 2.dp.toPx(), cap = StrokeCap.Round)
        }
        hand(hours * 30f + minutes * 0.5f, radius * 0.48f, 5.dp.toPx(), accent)
        hand(minutes * 6f + seconds * 0.1f, radius * 0.68f, 3.dp.toPx(), handColor)
        drawCircle(accent, radius = 5.dp.toPx(), center = center)
    }
}

private fun remainingLabel(dueAtEpochMillis: Long): String {
    val duration = Duration.ofMillis((dueAtEpochMillis - System.currentTimeMillis()).coerceAtLeast(0))
    val days = duration.toDays()
    val totalSeconds = duration.seconds
    val hours = ((totalSeconds / 3_600) % 24).toInt()
    val minutes = ((totalSeconds / 60) % 60).toInt()
    return "$days · ${hours.toString().padStart(2, '0')} · ${minutes.toString().padStart(2, '0')}"
}
