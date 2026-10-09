package com.marble098.marbledo

import com.marbledo.core.data.backup.AutomaticBackupStatus
import com.marbledo.feature.tasks.localizedTaskDateTime
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.os.Build
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.ui.platform.LocalContext
import com.marble098.marbledo.notifications.PersistentCalendarNotification
import com.marbledo.feature.calendar.SchedulePickerDialog
import com.marbledo.feature.calendar.SchedulePickerMode
import com.marbledo.feature.calendar.formatClock
import java.time.LocalTime
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.marbledo.domain.model.AppSettings
import com.marbledo.domain.model.AppThemeMode
import com.marbledo.domain.model.CalendarDisplayMode
import com.marbledo.domain.model.CountdownTheme
import com.marbledo.domain.model.NumeralMode
import com.marbledo.domain.util.TextNormalizer
import com.marbledo.feature.countdown.CountdownThemeGallerySheet
import com.marbledo.feature.countdown.countdownThemeLabel
import com.marbledo.feature.calendar.OccasionState
import com.marbledo.feature.calendar.PersianDateUtils

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

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))

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
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(stringResource(R.string.settings_countdown_theme), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        countdownThemeLabel(CountdownTheme.from(settings.countdownTheme)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.settings_countdown_theme_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { showCountdownThemeGallery = true }) {
                    Text(stringResource(R.string.settings_browse_themes))
                }
            }
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
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    TextNormalizer.formatDigits(stringResource(R.string.settings_font_scale, (settings.fontScale * 100).toInt()), settings.numeralMode),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onUpdate(settings.copy(fontScale = (settings.fontScale - 0.1f).coerceAtLeast(0.8f))) }) {
                    Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.settings_font_smaller))
                }
                IconButton(onClick = { onUpdate(settings.copy(fontScale = (settings.fontScale + 0.1f).coerceAtMost(1.5f))) }) {
                    Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.settings_font_larger))
                }
            }
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
            SettingSwitchRow(
                text = stringResource(R.string.settings_saturday_start),
                checked = settings.weekStartsSaturday,
                onCheckedChange = { onUpdate(settings.copy(weekStartsSaturday = it)) },
            )
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    TextNormalizer.formatDigits(stringResource(R.string.settings_lunar_offset, settings.lunarOffsetDays), settings.numeralMode),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onUpdate(settings.copy(lunarOffsetDays = (settings.lunarOffsetDays - 1).coerceAtLeast(-2))) }) { Text("−") }
                IconButton(onClick = { onUpdate(settings.copy(lunarOffsetDays = (settings.lunarOffsetDays + 1).coerceAtMost(2))) }) { Text("+") }
            }
            SettingSwitchRow(
                text = stringResource(R.string.settings_calendar_notifications),
                checked = settings.calendarNotificationsEnabled,
                onCheckedChange = { onUpdate(settings.copy(calendarNotificationsEnabled = it)) },
            )
            SettingSwitchRow(stringResource(R.string.settings_official_events), settings.officialEventsEnabled) { onUpdate(settings.copy(officialEventsEnabled = it)) }
            SettingSwitchRow(stringResource(R.string.settings_national_events), settings.nationalEventsEnabled) { onUpdate(settings.copy(nationalEventsEnabled = it)) }
            SettingSwitchRow(stringResource(R.string.settings_religious_events), settings.religiousEventsEnabled) { onUpdate(settings.copy(religiousEventsEnabled = it)) }
            SettingSwitchRow(stringResource(R.string.settings_personal_events), settings.personalEventsEnabled) { onUpdate(settings.copy(personalEventsEnabled = it)) }
        }

        SettingsSection(title = stringResource(R.string.settings_occasions), icon = Icons.Outlined.Refresh) {
            SettingSwitchRow(
                text = stringResource(R.string.settings_occasions_auto_update),
                checked = settings.occasionAutoUpdateEnabled,
                onCheckedChange = { onUpdate(settings.copy(occasionAutoUpdateEnabled = it)) },
            )
            Text(
                TextNormalizer.formatDigits(stringResource(R.string.settings_occasions_count, occasionState.occasionCount), settings.numeralMode),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                occasionStatusText(occasionState, settings.numeralMode),
                style = MaterialTheme.typography.bodySmall,
                color = if (occasionState.lastError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SettingsActionRow(stringResource(R.string.settings_occasions_update_now), onRefreshOccasions, enabled = !occasionState.isRefreshing)
        }

        SettingsSection(title = stringResource(R.string.settings_notifications), icon = Icons.Outlined.NotificationsActive) {
            SettingSwitchRow(
                text = stringResource(R.string.settings_persistent_date_notification),
                checked = settings.persistentDateNotificationEnabled,
                onCheckedChange = { enabled ->
                    onUpdate(settings.copy(persistentDateNotificationEnabled = enabled))
                    if (enabled && !notificationsGranted) onRequestNotifications()
                },
            )
            Text(
                stringResource(R.string.settings_persistent_date_notification_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val statusState = remember(settings.persistentDateNotificationEnabled, notificationsGranted) {
                PersistentCalendarNotification.statusIconState(context, settings.persistentDateNotificationEnabled)
            }
            Text(
                stringResource(statusStateTextRes(statusState)),
                style = MaterialTheme.typography.bodySmall,
                color = if (statusState == PersistentCalendarNotification.StatusIconState.PERMISSION_MISSING ||
                    statusState == PersistentCalendarNotification.StatusIconState.BLOCKED
                ) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
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
            SettingSwitchRow(
                text = stringResource(R.string.settings_quiet_hours),
                checked = settings.quietHoursEnabled,
                onCheckedChange = { onUpdate(settings.copy(quietHoursEnabled = it)) },
            )
            if (settings.quietHoursEnabled) {
                SettingsActionRow(
                    text = stringResource(R.string.settings_quiet_start, formatMinute(settings.quietStartMinute, settings.numeralMode)),
                    onClick = { quietEdge = QuietEdge.START },
                )
                SettingsActionRow(
                    text = stringResource(R.string.settings_quiet_end, formatMinute(settings.quietEndMinute, settings.numeralMode)),
                    onClick = { quietEdge = QuietEdge.END },
                )
            }
            Text(
                stringResource(R.string.settings_quiet_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
            if (!notificationsGranted) {
                SettingsActionRow(stringResource(R.string.settings_request_notifications), onRequestNotifications)
            }
            if (!exactAlarmGranted) {
                SettingsActionRow(stringResource(R.string.settings_request_exact_alarm), onRequestExactAlarm)
            }
        }

        SettingsSection(title = stringResource(R.string.settings_backup), icon = Icons.Outlined.Backup) {
            Text(stringResource(R.string.settings_local_only), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            lastBackup?.let { loaded ->
                Text(
                    loaded.millis?.let { stringResource(R.string.settings_last_backup, localizedTaskDateTime(it, allDay = false)) }
                        ?: stringResource(R.string.settings_last_backup_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_export)) }
            OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_import)) }
            TextButton(onClick = onRestoreAutomatic, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_restore_automatic)) }
            Text(stringResource(R.string.settings_drive_optional), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SettingsSection(title = stringResource(R.string.settings_privacy), icon = Icons.Outlined.PrivacyTip) {
            Text(stringResource(R.string.settings_privacy_body), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = { showBatteryHelp = true }) { Text(stringResource(R.string.settings_battery_guide_title)) }
            SettingsActionRow(stringResource(R.string.settings_update), onCheckUpdates)
        }

        Text(
            stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun SettingsSection(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun SettingSwitchRow(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsActionRow(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, modifier = Modifier.weight(1f))
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
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            TextButton(onClick = { onExpandedChange(true) }) { Text(stringResource(R.string.settings_change)) }
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

@Composable
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

private fun countdownCalendarLabel(mode: CalendarDisplayMode): String = when (mode) {
    CalendarDisplayMode.PERSIAN -> stringResource(R.string.calendar_display_persian)
    CalendarDisplayMode.GREGORIAN -> stringResource(R.string.calendar_display_gregorian)
    CalendarDisplayMode.ISLAMIC_CIVIL -> stringResource(R.string.calendar_display_lunar)
}
