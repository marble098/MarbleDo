package com.marble098.marbledo

import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marble098.marbledo.notifications.NotificationArtwork
import com.marble098.marbledo.notifications.NotificationMood
import com.marble098.marbledo.notifications.PersistentCalendarNotification
import com.marbledo.core.data.backup.AutomaticBackupStatus
import com.marbledo.core.designsystem.MarbleTextStyles
import com.marbledo.domain.model.AppSettings
import com.marbledo.domain.model.AppThemeMode
import com.marbledo.domain.model.CalendarDisplayMode
import com.marbledo.domain.model.CountdownTheme
import com.marbledo.domain.model.NumeralMode
import com.marbledo.domain.model.StatusBarIconStyle
import com.marbledo.domain.model.Task
import com.marbledo.domain.util.HiddenTapSequence
import com.marbledo.domain.util.TextNormalizer
import com.marbledo.feature.calendar.OccasionCategory
import com.marbledo.feature.calendar.OccasionPlanner
import com.marbledo.feature.calendar.OccasionState
import com.marbledo.feature.calendar.PersianDateUtils
import com.marbledo.feature.calendar.SchedulePickerDialog
import com.marbledo.feature.calendar.SchedulePickerMode
import com.marbledo.feature.calendar.formatClock
import com.marbledo.feature.countdown.CountdownThemeGallerySheet
import com.marbledo.feature.countdown.countdownThemeLabel
import com.marbledo.feature.tasks.localizedTaskDateTime
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    settings: AppSettings,
    notificationsGranted: Boolean,
    exactAlarmGranted: Boolean,
    occasionState: OccasionState,
    onUpdate: (AppSettings) -> Unit,
    onAddWidget: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onRestoreAutomatic: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestExactAlarm: () -> Unit,
    onRefreshOccasions: () -> Unit,
    onCheckUpdates: () -> Unit,
    modifier: Modifier = Modifier,
    tasks: List<Task> = emptyList(),
) {
    var themeMenu by remember { mutableStateOf(false) }
    var languageMenu by remember { mutableStateOf(false) }
    var numeralMenu by remember { mutableStateOf(false) }
    var countdownCalendarMenu by remember { mutableStateOf(false) }
    var statusCalendarMenu by remember { mutableStateOf(false) }
    var leadMenu by remember { mutableStateOf(false) }
    var quietEdge by remember { mutableStateOf<QuietEdge?>(null) }
    val context = LocalContext.current
    val lastBackup by produceState<LastBackup?>(initialValue = null) {
        value = LastBackup(withContext(Dispatchers.IO) { AutomaticBackupStatus.latestBackupMillis(context) })
    }
    var showCountdownThemeGallery by rememberSaveable { mutableStateOf(false) }
    var showBatteryHelp by remember { mutableStateOf(false) }
    var showEducationControls by rememberSaveable { mutableStateOf(false) }
    val educationGesture = remember { HiddenTapSequence() }
    val listedCatalog = remember(occasionState.catalog, settings.educationModeEnabled) {
        if (settings.educationModeEnabled) occasionState.catalog else occasionState.catalog.copy(
            occasions = occasionState.catalog.occasions.filter { it.category != OccasionCategory.EDUCATIONAL },
        )
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            stringResource(R.string.settings_title),
            style = MarbleTextStyles.screenTitle,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )

        SettingsSection(title = stringResource(R.string.settings_appearance), icon = Icons.Outlined.Palette) {
            SettingMenuRow(
                label = stringResource(R.string.settings_theme),
                value = themeLabel(settings.themeMode),
                icon = Icons.Outlined.Palette,
                expanded = themeMenu,
                onExpandedChange = { themeMenu = it },
            ) {
                AppThemeMode.entries.filter { it != AppThemeMode.DYNAMIC || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(themeLabel(item)) },
                        onClick = { onUpdate(settings.copy(themeMode = item)); themeMenu = false },
                    )
                }
            }
            SettingsDivider()
            SettingMenuRow(
                label = stringResource(R.string.settings_language),
                value = if (settings.languageTag == "fa") stringResource(R.string.language_persian) else stringResource(R.string.language_english),
                icon = Icons.Outlined.Language,
                expanded = languageMenu,
                onExpandedChange = { languageMenu = it },
            ) {
                DropdownMenuItem(text = { Text(stringResource(R.string.language_persian)) }, onClick = { onUpdate(settings.copy(languageTag = "fa")); languageMenu = false })
                DropdownMenuItem(text = { Text(stringResource(R.string.language_english)) }, onClick = { onUpdate(settings.copy(languageTag = "en")); languageMenu = false })
            }
            SettingsDivider()
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.settings_countdown_theme), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        stringResource(R.string.settings_countdown_theme_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.clickable { showCountdownThemeGallery = true },
                ) {
                    Text(
                        countdownThemeLabel(CountdownTheme.from(settings.countdownTheme)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
            SettingsDivider()
            SettingMenuRow(
                label = stringResource(R.string.settings_numerals),
                value = numeralLabel(settings.numeralMode),
                icon = Icons.Outlined.Language,
                expanded = numeralMenu,
                onExpandedChange = { numeralMenu = it },
            ) {
                NumeralMode.entries.forEach { item ->
                    DropdownMenuItem(text = { Text(numeralLabel(item)) }, onClick = { onUpdate(settings.copy(numeralMode = item)); numeralMenu = false })
                }
            }
            SettingsDivider()
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    TextNormalizer.formatDigits(stringResource(R.string.settings_font_scale, (settings.fontScale * 100).toInt()), settings.numeralMode),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onUpdate(settings.copy(fontScale = (settings.fontScale - 0.1f).coerceAtLeast(0.8f))) },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.settings_font_smaller), modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = { onUpdate(settings.copy(fontScale = (settings.fontScale + 0.1f).coerceAtMost(1.5f))) },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.settings_font_larger), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            SettingsDivider()
            SettingSwitchRow(
                text = stringResource(R.string.settings_reduce_motion),
                checked = settings.reduceMotion,
                onCheckedChange = { onUpdate(settings.copy(reduceMotion = it)) },
            )
        }

        SettingsSection(title = stringResource(R.string.settings_home_widget), icon = Icons.Outlined.Home) {
            Text(
                stringResource(R.string.settings_home_widget_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onAddWidget, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_add_widget))
            }
        }

        SettingsSection(title = stringResource(R.string.settings_calendar), icon = Icons.Outlined.CalendarMonth) {
            SettingMenuRow(
                label = stringResource(R.string.settings_countdown_calendar),
                value = countdownCalendarLabel(settings.countdownCalendar),
                icon = Icons.Outlined.CalendarMonth,
                expanded = countdownCalendarMenu,
                onExpandedChange = { countdownCalendarMenu = it },
            ) {
                CalendarDisplayMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(countdownCalendarLabel(mode)) },
                        onClick = { onUpdate(settings.copy(countdownCalendar = mode)); countdownCalendarMenu = false },
                    )
                }
            }
            SettingsDivider()
            SettingSwitchRow(
                text = stringResource(R.string.settings_saturday_start),
                checked = settings.weekStartsSaturday,
                onCheckedChange = { onUpdate(settings.copy(weekStartsSaturday = it)) },
            )
            SettingsDivider()
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    TextNormalizer.formatDigits(stringResource(R.string.settings_lunar_offset, settings.lunarOffsetDays), settings.numeralMode),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onUpdate(settings.copy(lunarOffsetDays = (settings.lunarOffsetDays - 1).coerceAtLeast(-2))) },
                            modifier = Modifier.size(36.dp),
                        ) { Text("−", fontWeight = FontWeight.Bold) }
                        IconButton(
                            onClick = { onUpdate(settings.copy(lunarOffsetDays = (settings.lunarOffsetDays + 1).coerceAtMost(2))) },
                            modifier = Modifier.size(36.dp),
                        ) { Text("+", fontWeight = FontWeight.Bold) }
                    }
                }
            }
            SettingsDivider()
            SettingSwitchRow(
                text = stringResource(R.string.settings_calendar_notifications),
                checked = settings.calendarNotificationsEnabled,
                onCheckedChange = { onUpdate(settings.copy(calendarNotificationsEnabled = it)) },
            )
            SettingSwitchRow(stringResource(R.string.settings_official_events), settings.officialEventsEnabled) { onUpdate(settings.copy(officialEventsEnabled = it)) }
            SettingSwitchRow(stringResource(R.string.settings_national_events), settings.nationalEventsEnabled) { onUpdate(settings.copy(nationalEventsEnabled = it)) }
            SettingSwitchRow(stringResource(R.string.settings_religious_events), settings.religiousEventsEnabled) { onUpdate(settings.copy(religiousEventsEnabled = it)) }
            SettingSwitchRow(stringResource(R.string.settings_personal_events), settings.personalEventsEnabled) { onUpdate(settings.copy(personalEventsEnabled = it)) }
            SettingSwitchRow(stringResource(R.string.settings_international_events), settings.internationalEventsEnabled) { onUpdate(settings.copy(internationalEventsEnabled = it)) }
            SettingSwitchRow(stringResource(R.string.settings_thursday_weekend), settings.thursdayWeekendEnabled) { onUpdate(settings.copy(thursdayWeekendEnabled = it)) }
            if (settings.educationModeEnabled) {
                SettingsDivider()
                SettingSwitchRow(
                    text = stringResource(R.string.settings_education_mode),
                    subtitle = stringResource(R.string.settings_education_hint),
                    checked = true,
                ) { onUpdate(settings.copy(educationModeEnabled = it)) }
            }
        }

        SettingsSection(title = stringResource(R.string.settings_occasions), icon = Icons.Outlined.Refresh) {
            SettingSwitchRow(
                text = stringResource(R.string.settings_occasions_auto_update),
                checked = settings.occasionAutoUpdateEnabled,
                onCheckedChange = { onUpdate(settings.copy(occasionAutoUpdateEnabled = it)) },
            )
            Spacer(Modifier.height(6.dp))
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            TextNormalizer.formatDigits(stringResource(R.string.settings_occasions_count, listedCatalog.occasions.size), settings.numeralMode),
                            style = MarbleTextStyles.itemTitle,
                        )
                        Text(
                            occasionStatusText(occasionState, settings.numeralMode),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (occasionState.lastError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilledTonalActionPill(
                        text = stringResource(R.string.settings_occasions_update_now),
                        enabled = !occasionState.isRefreshing,
                        onClick = onRefreshOccasions,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            SettingsDivider()
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.settings_occasion_reminder),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        stringResource(R.string.settings_occasion_reminder_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(
                        TextNormalizer.formatDigits(
                            stringResource(R.string.settings_occasion_favorites, settings.favoriteOccasionIds.size),
                            settings.numeralMode,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OCCASION_REMINDER_OPTIONS.forEach { days ->
                    FilterChip(
                        selected = settings.occasionReminderLeadDays == days,
                        onClick = { onUpdate(settings.copy(occasionReminderLeadDays = days)) },
                        label = {
                            Text(
                                if (days == 0) stringResource(R.string.settings_reminder_lead_off)
                                else TextNormalizer.formatDigits(
                                    stringResource(R.string.settings_occasion_reminder_value, days),
                                    settings.numeralMode,
                                ),
                            )
                        },
                    )
                }
            }
            val coverage = remember(listedCatalog) { OccasionPlanner.coverage(listedCatalog) }
            if (coverage.total > 0) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                stringResource(R.string.settings_coverage_title),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                TextNormalizer.formatDigits(
                                    stringResource(R.string.settings_coverage_summary, coverage.total, coverage.coveredMonths),
                                    settings.numeralMode,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            TextNormalizer.formatDigits(
                                if (coverage.isFullYear) stringResource(R.string.settings_coverage_complete)
                                else stringResource(R.string.settings_coverage_missing, coverage.missingMonths.size),
                                settings.numeralMode,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (coverage.isFullYear) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        )
                        Text(
                            TextNormalizer.formatDigits(
                                stringResource(R.string.settings_coverage_categories, coverage.categoryCount),
                                settings.numeralMode,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Redesigned minimal Notification Box: structured sub-cards, status pill, live preview,
        // side-by-side quiet hours tiles, and zero wall-of-text stacking.
        SettingsSection(title = stringResource(R.string.settings_notifications), icon = Icons.Outlined.NotificationsActive) {
            val statusState = remember(settings.persistentDateNotificationEnabled, notificationsGranted) {
                PersistentCalendarNotification.statusIconState(context, settings.persistentDateNotificationEnabled)
            }
            val statusError = statusState == PersistentCalendarNotification.StatusIconState.PERMISSION_MISSING ||
                statusState == PersistentCalendarNotification.StatusIconState.BLOCKED

            // 1. Toggle + integrated subtitle + clean minimal status badge pill
            SettingSwitchRow(
                text = stringResource(R.string.settings_persistent_date_notification),
                subtitle = stringResource(R.string.settings_persistent_date_notification_hint),
                checked = settings.persistentDateNotificationEnabled,
                onCheckedChange = { enabled ->
                    onUpdate(settings.copy(persistentDateNotificationEnabled = enabled))
                    if (enabled && !notificationsGranted) onRequestNotifications()
                },
            )
            Spacer(Modifier.height(6.dp))
            NotificationStatusBadge(
                text = stringResource(statusStateTextRes(statusState)),
                state = statusState,
                isError = statusError,
            )

            // 2. Minimal Live Notification Preview Card so the user sees the clean notification layout
            if (settings.persistentDateNotificationEnabled) {
                Spacer(Modifier.height(10.dp))
                MinimalNotificationPreviewBox(
                    context = context,
                    settings = settings,
                    tasks = tasks,
                    catalog = listedCatalog,
                )
            }

            Spacer(Modifier.height(10.dp))
            SettingsDivider()
            Spacer(Modifier.height(4.dp))

            // 3. Status bar calendar + icon style picker
            SettingMenuRow(
                label = stringResource(R.string.settings_status_calendar),
                value = countdownCalendarLabel(settings.statusBarCalendar),
                icon = Icons.Outlined.CalendarMonth,
                expanded = statusCalendarMenu,
                onExpandedChange = { statusCalendarMenu = it },
            ) {
                CalendarDisplayMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(countdownCalendarLabel(mode)) },
                        onClick = { onUpdate(settings.copy(statusBarCalendar = mode)); statusCalendarMenu = false },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.settings_status_icon_style),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    stringResource(R.string.settings_status_icon_style_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            StatusIconStylePicker(
                selected = settings.statusBarIconStyle,
                numeralMode = settings.numeralMode,
                onSelect = { onUpdate(settings.copy(statusBarIconStyle = it)) },
            )

            Spacer(Modifier.height(12.dp))
            SettingsDivider()
            Spacer(Modifier.height(4.dp))

            // 4. Reminder lead & Quiet hours with side-by-side time tiles
            SettingMenuRow(
                label = stringResource(R.string.settings_reminder_lead),
                value = leadLabel(settings.reminderLeadMinutes, settings.numeralMode),
                icon = Icons.Outlined.Alarm,
                expanded = leadMenu,
                onExpandedChange = { leadMenu = it },
            ) {
                REMINDER_LEAD_OPTIONS.forEach { minutes ->
                    DropdownMenuItem(
                        text = { Text(leadLabel(minutes, settings.numeralMode)) },
                        onClick = { onUpdate(settings.copy(reminderLeadMinutes = minutes)); leadMenu = false },
                    )
                }
            }
            SettingsDivider()
            SettingSwitchRow(
                text = stringResource(R.string.settings_quiet_hours),
                subtitle = stringResource(R.string.settings_quiet_hint),
                checked = settings.quietHoursEnabled,
                onCheckedChange = { onUpdate(settings.copy(quietHoursEnabled = it)) },
            )
            if (settings.quietHoursEnabled) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val startFormatted = formatMinute(settings.quietStartMinute, settings.numeralMode)
                    val endFormatted = formatMinute(settings.quietEndMinute, settings.numeralMode)
                    QuietTimeTile(
                        label = stringResource(R.string.settings_quiet_start_title),
                        summary = stringResource(R.string.settings_quiet_start, startFormatted),
                        value = startFormatted,
                        onClick = { quietEdge = QuietEdge.START },
                        modifier = Modifier.weight(1f),
                    )
                    QuietTimeTile(
                        label = stringResource(R.string.settings_quiet_end_title),
                        summary = stringResource(R.string.settings_quiet_end, endFormatted),
                        value = endFormatted,
                        onClick = { quietEdge = QuietEdge.END },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            quietEdge?.let { edge ->
                val currentMinute = if (edge == QuietEdge.START) settings.quietStartMinute else settings.quietEndMinute
                SchedulePickerDialog(
                    title = stringResource(
                        if (edge == QuietEdge.START) R.string.settings_quiet_start_title else R.string.settings_quiet_end_title,
                    ),
                    mode = SchedulePickerMode.TIME_ONLY,
                    initialDate = null,
                    initialTime = LocalTime.of(currentMinute.coerceIn(0, 1439) / 60, currentMinute.coerceIn(0, 1439) % 60),
                    languageTag = settings.languageTag,
                    numeralMode = settings.numeralMode,
                    weekStartsSaturday = settings.weekStartsSaturday,
                    calendarMode = settings.countdownCalendar,
                    onCalendarModeChange = {},
                    onConfirm = { _, time ->
                        val picked = time?.let { it.hour * 60 + it.minute } ?: currentMinute
                        onUpdate(
                            if (edge == QuietEdge.START) settings.copy(quietStartMinute = picked) else settings.copy(quietEndMinute = picked),
                        )
                        quietEdge = null
                    },
                    onDismiss = { quietEdge = null },
                    allowAnyTime = false,
                )
            }
            if (!notificationsGranted || !exactAlarmGranted) {
                Spacer(Modifier.height(10.dp))
                SettingsDivider()
                Spacer(Modifier.height(6.dp))
                if (!notificationsGranted) {
                    SettingsActionRow(stringResource(R.string.settings_request_notifications), onRequestNotifications)
                }
                if (!exactAlarmGranted) {
                    SettingsActionRow(stringResource(R.string.settings_request_exact_alarm), onRequestExactAlarm)
                }
            }
        }

        SettingsSection(title = stringResource(R.string.settings_backup), icon = Icons.Outlined.Backup) {
            Text(
                stringResource(R.string.settings_local_only),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            lastBackup?.let { loaded ->
                Spacer(Modifier.height(4.dp))
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ) {
                    Text(
                        loaded.millis?.let { stringResource(R.string.settings_last_backup, localizedTaskDateTime(it, allDay = false)) }
                            ?: stringResource(R.string.settings_last_backup_none),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_export)) }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_import)) }
            TextButton(onClick = onRestoreAutomatic, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_restore_automatic)) }
            Text(
                stringResource(R.string.settings_drive_optional),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsSection(title = stringResource(R.string.settings_privacy), icon = Icons.Outlined.PrivacyTip) {
            Text(
                stringResource(R.string.settings_privacy_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            SettingsDivider()
            SettingsActionRow(stringResource(R.string.settings_battery_guide_title), onClick = { showBatteryHelp = true })
            SettingsActionRow(stringResource(R.string.settings_update), onCheckUpdates)
        }

        Text(
            stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clip(CircleShape)
                .clickable {
                    if (educationGesture.tap(SystemClock.elapsedRealtime())) showEducationControls = true
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showEducationControls) {
        AlertDialog(
            onDismissRequest = { showEducationControls = false },
            title = { Text(stringResource(R.string.settings_education_mode)) },
            text = { Text(stringResource(R.string.settings_education_hint)) },
            confirmButton = {
                TextButton(onClick = {
                    onUpdate(settings.copy(educationModeEnabled = !settings.educationModeEnabled))
                    showEducationControls = false
                }) { Text(stringResource(if (settings.educationModeEnabled) R.string.settings_education_disable else R.string.settings_education_enable)) }
            },
            dismissButton = { TextButton(onClick = { showEducationControls = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
    if (showCountdownThemeGallery) {
        CountdownThemeGallerySheet(
            selectedThemeId = settings.countdownTheme,
            previewTitle = stringResource(R.string.app_name),
            previewDueAtEpochMillis = remember { System.currentTimeMillis() + 6L * 24L * 60L * 60L * 1000L },
            onThemeSelected = { themeId -> onUpdate(settings.copy(countdownTheme = themeId)) },
            onDismiss = { showCountdownThemeGallery = false },
        )
    }

    if (showBatteryHelp) {
        AlertDialog(
            onDismissRequest = { showBatteryHelp = false },
            title = { Text(stringResource(R.string.settings_battery_guide_title)) },
            text = { Text(stringResource(R.string.settings_battery_guide)) },
            confirmButton = { TextButton(onClick = { showBatteryHelp = false }) { Text(stringResource(R.string.settings_close)) } },
        )
    }
}

/**
 * Clean, minimal live preview of today's persistent notification card inside the Notification Box.
 * Shows the date badge, primary date title, secondary calendar dates, and separated pill rows for
 * today's occasions and next task instead of a continuous wall of text.
 */
@Composable
private fun MinimalNotificationPreviewBox(
    context: Context,
    settings: AppSettings,
    tasks: List<Task>,
    catalog: com.marbledo.feature.calendar.OccasionCatalog,
) {
    val content = remember(
        settings.languageTag,
        settings.numeralMode,
        settings.lunarOffsetDays,
        settings.statusBarCalendar,
        tasks,
        catalog,
    ) {
        PersistentCalendarNotification.buildContent(context, settings, tasks, catalog)
    }
    val badgeBitmap = remember(context, content.dayDigits, content.monthLabel, content.mood) {
        NotificationArtwork.dateBadge(context, content.dayDigits, content.monthLabel, content.mood)
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f),
                shape = MaterialTheme.shapes.large,
            ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (badgeBitmap != null) {
                    Image(
                        bitmap = badgeBitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)),
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            content.title,
                            style = MarbleTextStyles.itemTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                content.moodSubtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Text(
                        content.secondaryDatesLine,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val occasionPreview = if (content.occasionLines.isEmpty()) {
                    content.fallbackOccasionText
                } else {
                    content.occasionLines.joinToString("  ·  ")
                }
                MinimalPreviewRow(
                    symbol = "✦",
                    text = occasionPreview,
                    emphasized = content.occasionLines.isNotEmpty(),
                )
                MinimalPreviewRow(
                    symbol = "⏳",
                    text = content.nextTaskSummary ?: content.fallbackTaskText,
                    emphasized = content.nextTaskSummary != null,
                )
            }
        }
    }
}

@Composable
private fun MinimalPreviewRow(
    symbol: String,
    text: String,
    emphasized: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (emphasized) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                symbol,
                style = MaterialTheme.typography.labelMedium,
                color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (emphasized) FontWeight.Medium else FontWeight.Normal,
                color = if (emphasized) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun NotificationStatusBadge(
    text: String,
    state: PersistentCalendarNotification.StatusIconState,
    isError: Boolean,
) {
    val dotColor = when {
        isError -> MaterialTheme.colorScheme.error
        state == PersistentCalendarNotification.StatusIconState.ACTIVE -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    val containerColor = when {
        isError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
        state == PersistentCalendarNotification.StatusIconState.ACTIVE -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    }
    val contentColor = when {
        isError -> MaterialTheme.colorScheme.onErrorContainer
        state == PersistentCalendarNotification.StatusIconState.ACTIVE -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun QuietTimeTile(
    label: String,
    summary: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    summary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                value,
                style = MarbleTextStyles.itemTitle,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun FilledTonalActionPill(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .clip(CircleShape)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 2.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
    )
}

/** Reminder lead times offered for starred occasions; 0 keeps the reminder off. */
private val OCCASION_REMINDER_OPTIONS = listOf(0, 1, 2, 3, 7)

@Composable
private fun occasionStatusText(state: OccasionState, numeralMode: NumeralMode): String {
    val locale = LocalConfiguration.current.locales[0]
    val stamp = state.lastUpdatedEpochMillis
    return when {
        state.isRefreshing -> stringResource(R.string.settings_occasions_updating)
        state.lastRefreshWasOffline -> stringResource(R.string.settings_occasions_offline)
        state.lastError -> stringResource(R.string.settings_occasions_failed)
        stamp != null -> stringResource(R.string.settings_occasions_updated_at, PersianDateUtils.gregorianDate(stamp, locale))
        else -> stringResource(R.string.settings_occasions_updated_never)
    }.let { TextNormalizer.formatDigits(it, numeralMode) }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(7.dp).size(18.dp),
                    )
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun SettingSwitchRow(
    text: String,
    checked: Boolean,
    subtitle: String? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsActionRow(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onClick, enabled = enabled) { Text(stringResource(R.string.settings_action)) }
    }
}

@Composable
private fun SettingMenuRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    menuItems: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable { onExpandedChange(true) }
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
        Box {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.clip(CircleShape).clickable { onExpandedChange(true) },
            ) {
                Text(
                    value,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) { menuItems() }
        }
    }
}

@Composable
private fun themeLabel(mode: AppThemeMode): String = when (mode) {
    AppThemeMode.AUTO -> stringResource(R.string.theme_auto)
    AppThemeMode.GLASS_LIGHT -> stringResource(R.string.theme_glass)
    AppThemeMode.DARK -> stringResource(R.string.theme_dark)
    AppThemeMode.AMOLED -> stringResource(R.string.theme_amoled)
    AppThemeMode.DYNAMIC -> stringResource(R.string.theme_dynamic)
}

@Composable
private fun numeralLabel(mode: NumeralMode): String = when (mode) {
    NumeralMode.PERSIAN -> stringResource(R.string.numerals_persian)
    NumeralMode.LATIN -> stringResource(R.string.numerals_latin)
    NumeralMode.ARABIC -> stringResource(R.string.numerals_arabic)
}

/**
 * Every status-bar icon style, drawn from the same generated bitmap the notification uses and
 * shown twice: at the size the status bar actually renders it, and enlarged so the digits can be
 * judged. The strip is dark because that is where the icon lives on most devices. Six looks do not
 * fit on one line, so they are laid out three per row.
 */
@Composable
private fun StatusIconStylePicker(
    selected: StatusBarIconStyle,
    numeralMode: NumeralMode,
    onSelect: (StatusBarIconStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val digits = TextNormalizer.formatDigits("28", numeralMode)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StatusBarIconStyle.entries.chunked(STYLES_PER_ROW).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { style ->
                    StatusIconStyleCard(
                        style = style,
                        digits = digits,
                        context = context,
                        isSelected = style == selected,
                        onSelect = onSelect,
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(STYLES_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** One status-bar look: the icon at its real size in a fake strip, then the same bitmap enlarged. */
@Composable
private fun StatusIconStyleCard(
    style: StatusBarIconStyle,
    digits: String,
    context: Context,
    isSelected: Boolean,
    onSelect: (StatusBarIconStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stripBitmap = remember(context, style, digits) {
        NotificationArtwork.statusBitmap(context, digits, NotificationMood.OCCASION, style, NotificationArtwork.STATUS_ICON_DP)
    }
    val bitmap = remember(context, style, digits) {
        NotificationArtwork.statusBitmap(context, digits, NotificationMood.OCCASION, style, NotificationArtwork.PREVIEW_ICON_DP)
    }
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = modifier
            .clip(shape)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.14f),
                shape = shape,
            )
            .clickable { onSelect(style) }
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = RoundedCornerShape(7.dp), color = Color(0xFF101014)) {
            Row(
                Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StatusIconPreview(stripBitmap, Modifier.size(17.dp), Color.White)
                Box(
                    Modifier
                        .size(width = 20.dp, height = 6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.35f)),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        StatusIconPreview(
            bitmap = bitmap,
            modifier = Modifier.size(44.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            statusIconStyleLabel(style),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A generated style bitmap, or the vector fallback for the styles that have no bitmap. */
@Composable
private fun StatusIconPreview(
    bitmap: android.graphics.Bitmap?,
    modifier: Modifier,
    tint: Color,
) {
    if (bitmap != null) {
        Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, modifier = modifier)
    } else {
        Icon(
            painter = painterResource(NotificationArtwork.vectorIconRes(NotificationMood.OCCASION)),
            contentDescription = null,
            tint = tint,
            modifier = modifier,
        )
    }
}

@Composable
private fun statusIconStyleLabel(style: StatusBarIconStyle): String = when (style) {
    StatusBarIconStyle.NUMERAL -> stringResource(R.string.settings_status_icon_style_numeral)
    StatusBarIconStyle.FRAME -> stringResource(R.string.settings_status_icon_style_frame)
    StatusBarIconStyle.TILE -> stringResource(R.string.settings_status_icon_style_tile)
    StatusBarIconStyle.DISC -> stringResource(R.string.settings_status_icon_style_disc)
    StatusBarIconStyle.PAGE -> stringResource(R.string.settings_status_icon_style_page)
    StatusBarIconStyle.ICON_ONLY -> stringResource(R.string.settings_status_icon_style_icon)
}

/** Looks shown per row of the status-bar icon picker. */
private const val STYLES_PER_ROW = 3

private enum class QuietEdge { START, END }

/** Loaded backup status. Null millis means no automatic backup exists yet. */
private class LastBackup(val millis: Long?)

private val REMINDER_LEAD_OPTIONS = listOf(0, 5, 10, 15, 30, 60)

@Composable
private fun leadLabel(minutes: Int, numeralMode: NumeralMode): String =
    if (minutes == 0) {
        stringResource(R.string.settings_reminder_lead_off)
    } else {
        TextNormalizer.formatDigits(stringResource(R.string.settings_reminder_lead_value, minutes), numeralMode)
    }

private fun formatMinute(minuteOfDay: Int, numeralMode: NumeralMode): String {
    val clamped = minuteOfDay.coerceIn(0, 1439)
    return TextNormalizer.formatDigits(formatClock(LocalTime.of(clamped / 60, clamped % 60)), numeralMode)
}

private fun statusStateTextRes(state: PersistentCalendarNotification.StatusIconState): Int = when (state) {
    PersistentCalendarNotification.StatusIconState.ACTIVE -> R.string.settings_status_icon_state_active
    PersistentCalendarNotification.StatusIconState.OFF -> R.string.settings_status_icon_state_off
    PersistentCalendarNotification.StatusIconState.PERMISSION_MISSING -> R.string.settings_status_icon_state_permission
    PersistentCalendarNotification.StatusIconState.BLOCKED -> R.string.settings_status_icon_state_blocked
}

@Composable
private fun countdownCalendarLabel(mode: CalendarDisplayMode): String = when (mode) {
    CalendarDisplayMode.PERSIAN -> stringResource(R.string.calendar_display_persian)
    CalendarDisplayMode.GREGORIAN -> stringResource(R.string.calendar_display_gregorian)
    CalendarDisplayMode.ISLAMIC_CIVIL -> stringResource(R.string.calendar_display_lunar)
}
