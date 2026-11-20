package com.marble098.marbledo

import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Tune
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
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.marble098.marbledo.notifications.NotificationArtwork
import com.marble098.marbledo.notifications.NotificationChipFills
import com.marble098.marbledo.notifications.NotificationLook
import com.marble098.marbledo.notifications.NotificationMood
import com.marble098.marbledo.notifications.PersistentCalendarNotification
import com.marble098.marbledo.notifications.iconRes
import com.marble098.marbledo.notifications.notificationPalette
import com.marble098.marbledo.notifications.stateHue
import com.marbledo.core.data.backup.AutomaticBackupStatus
import com.marbledo.core.designsystem.MarbleBoxBorder
import com.marbledo.core.designsystem.MarbleContrast
import com.marbledo.core.designsystem.MarblePalettes
import com.marbledo.core.designsystem.MarbleSpacing
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
import com.marbledo.feature.calendar.OccasionCultureSelector
import com.marbledo.feature.calendar.OccasionSourceStatus
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
    onRefreshOccasions: () -> Unit = {},
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
    var occasionHourMenu by remember { mutableStateOf(false) }
    var quietEdge by remember { mutableStateOf<QuietEdge?>(null) }
    val context = LocalContext.current
    val lastBackup by produceState<LastBackup?>(initialValue = null) {
        value = LastBackup(withContext(Dispatchers.IO) { AutomaticBackupStatus.latestBackupMillis(context) })
    }
    var showCountdownThemeGallery by rememberSaveable { mutableStateOf(false) }
    var showBatteryHelp by remember { mutableStateOf(false) }
    var showEducationControls by rememberSaveable { mutableStateOf(false) }
    var selectedCategory by rememberSaveable { mutableStateOf(SettingsCategoryFilter.ALL) }
    val educationGesture = remember { HiddenTapSequence() }
    val listedCatalog = remember(occasionState.catalog, settings.educationModeEnabled) {
        if (settings.educationModeEnabled) occasionState.catalog else occasionState.catalog.copy(
            occasions = occasionState.catalog.occasions.filter { it.category != OccasionCategory.EDUCATIONAL },
        )
    }

    // Lazy so off-screen sections (the notification preview builds bitmaps, the icon picker
    // builds twelve) are only composed when scrolled to (docs/UI_COUNCIL_REVIEW.md, S-1).
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = MarbleSpacing.lg, vertical = MarbleSpacing.md),
        verticalArrangement = Arrangement.spacedBy(MarbleSpacing.md),
    ) {
        item {
            SettingsCommandCard(
                settings = settings,
                selectedCategory = selectedCategory,
                onSelectCategory = { selectedCategory = it },
            )
        }

        if (selectedCategory == SettingsCategoryFilter.ALL || selectedCategory == SettingsCategoryFilter.APPEARANCE) {
        item { Column(verticalArrangement = Arrangement.spacedBy(MarbleSpacing.md)) {
        SettingsSection(
            title = stringResource(R.string.settings_appearance),
            icon = Icons.Outlined.Palette,
            showHeader = selectedCategory == SettingsCategoryFilter.ALL,
        ) {
            SettingMenuRow(
                label = stringResource(R.string.settings_theme),
                value = themeLabel(settings.themeMode),
                icon = Icons.Outlined.Palette,
                expanded = themeMenu,
                onExpandedChange = { themeMenu = it },
            ) {
                AppThemeMode.entries.filter { it != AppThemeMode.DYNAMIC || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }.forEach { item ->
                    DropdownMenuItem(
                        text = { ThemeMenuLabel(item) },
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
                    modifier = Modifier.minimumInteractiveComponentSize().clip(CircleShape).clickable(role = Role.Button) { showCountdownThemeGallery = true },
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
                        ) {
                            Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.settings_font_smaller), modifier = Modifier.size(20.dp))
                        }
                        IconButton(
                            onClick = { onUpdate(settings.copy(fontScale = (settings.fontScale + 0.1f).coerceAtMost(1.5f))) },
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.settings_font_larger), modifier = Modifier.size(20.dp))
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
        } }
        }

        if (selectedCategory == SettingsCategoryFilter.ALL || selectedCategory == SettingsCategoryFilter.CALENDAR) {
        item { Column(verticalArrangement = Arrangement.spacedBy(MarbleSpacing.md)) {
        SettingsSection(
            title = stringResource(R.string.settings_calendar),
            icon = Icons.Outlined.CalendarMonth,
            showHeader = selectedCategory == SettingsCategoryFilter.ALL,
        ) {
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
                        ) {
                            Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.settings_decrease), modifier = Modifier.size(20.dp))
                        }
                        IconButton(
                            onClick = { onUpdate(settings.copy(lunarOffsetDays = (settings.lunarOffsetDays + 1).coerceAtMost(2))) },
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.settings_increase), modifier = Modifier.size(20.dp))
                        }
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
            SettingsDivider()
            OccasionCultureSelector(settings.occasionCultureIds) { onUpdate(settings.copy(occasionCultureIds = it)) }
            SettingsDivider()
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

        SettingsSection(title = stringResource(R.string.settings_occasion_sources), icon = Icons.Outlined.Language) {
            Text(
                occasionStatusText(occasionState, settings.numeralMode),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(
                    if (occasionState.hasOfficialLunarAnchors) R.string.settings_occasion_anchors_official
                    else R.string.settings_occasion_anchors_estimated,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.settings_occasion_sources_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (occasionState.crossCheck.feeds > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    TextNormalizer.formatDigits(
                        stringResource(
                            R.string.settings_occasion_crosscheck,
                            occasionState.crossCheck.confirmed.size,
                            occasionState.crossCheck.communityOnly.size,
                        ),
                        settings.numeralMode,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (occasionState.sourceStatus.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                occasionState.sourceStatus.forEach { source ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(source.id, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(
                            "${stringResource(tierLabelRes(source.tier))} · ${stringResource(sourceRoleLabelRes(source))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                !source.ok -> MaterialTheme.colorScheme.error
                                source.role == OccasionSourceStatus.ROLE_STANDBY -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onRefreshOccasions,
                modifier = Modifier.fillMaxWidth(),
                enabled = !occasionState.isRefreshing,
            ) {
                Text(stringResource(R.string.settings_occasions_update_now))
            }
        }

        SettingsSection(title = stringResource(R.string.settings_occasion_reminder), icon = Icons.Outlined.Refresh) {
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
        }
        } }
        }

        // Redesigned minimal Notification Box: structured sub-cards, status pill, live preview,
        // side-by-side quiet hours tiles, and zero wall-of-text stacking.
        if (selectedCategory == SettingsCategoryFilter.ALL || selectedCategory == SettingsCategoryFilter.NOTIFICATIONS) {
        item { Column(verticalArrangement = Arrangement.spacedBy(MarbleSpacing.md)) {
        SettingsSection(
            title = stringResource(R.string.settings_notifications),
            icon = Icons.Outlined.NotificationsActive,
            showHeader = selectedCategory == SettingsCategoryFilter.ALL,
        ) {
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
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.settings_notification_design),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            NotificationDesignSelector(
                selectedDesign = settings.notificationDesign,
                onSelectDesign = { design -> onUpdate(settings.copy(notificationDesign = design)) },
            )

            // 2. Minimal Live Notification Preview Card so the user sees the clean notification layout
            if (settings.persistentDateNotificationEnabled) {
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.settings_notification_preview),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                NotificationLivePreviewCard(
                    context = context,
                    settings = settings,
                    tasks = tasks,
                    catalog = listedCatalog,
                )
            }

            Spacer(Modifier.height(10.dp))
            SettingsDivider()
            Spacer(Modifier.height(4.dp))

            // 2b. One switch per channel. The three reminder kinds arrive with different volume
            // and different urgency, so switching one off must not silence the others — which is
            // exactly what the old single "task reminders" channel did.
            SettingSwitchRow(
                text = stringResource(R.string.settings_overdue_notifications),
                subtitle = stringResource(R.string.settings_overdue_notifications_hint),
                checked = settings.overdueNotificationsEnabled,
                onCheckedChange = { onUpdate(settings.copy(overdueNotificationsEnabled = it)) },
            )
            SettingSwitchRow(
                text = stringResource(R.string.settings_today_reminders),
                subtitle = stringResource(R.string.settings_today_reminders_hint),
                checked = settings.todayRemindersEnabled,
                onCheckedChange = { onUpdate(settings.copy(todayRemindersEnabled = it)) },
            )
            SettingSwitchRow(
                text = stringResource(R.string.settings_occasion_channel),
                subtitle = stringResource(R.string.settings_occasion_channel_hint),
                checked = settings.calendarNotificationsEnabled,
                onCheckedChange = { onUpdate(settings.copy(calendarNotificationsEnabled = it)) },
            )
            if (settings.calendarNotificationsEnabled) {
                // The briefing is once a day and the user chose the morning; a fixed hour would
                // make the switch a half-truth.
                SettingMenuRow(
                    label = stringResource(R.string.notification_occasion_hour_title),
                    value = TextNormalizer.formatDigits(
                        stringResource(R.string.notification_occasion_hour_value, settings.occasionNotificationHour.toString()),
                        settings.numeralMode,
                    ),
                    icon = Icons.Outlined.Alarm,
                    expanded = occasionHourMenu,
                    onExpandedChange = { occasionHourMenu = it },
                ) {
                    OCCASION_HOUR_OPTIONS.forEach { hour ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    TextNormalizer.formatDigits(
                                        stringResource(R.string.notification_occasion_hour_value, hour.toString()),
                                        settings.numeralMode,
                                    ),
                                )
                            },
                            onClick = { onUpdate(settings.copy(occasionNotificationHour = hour)); occasionHourMenu = false },
                        )
                    }
                }
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
        } }
        }

        if (selectedCategory == SettingsCategoryFilter.ALL || selectedCategory == SettingsCategoryFilter.BACKUP) {
        item { Column(verticalArrangement = Arrangement.spacedBy(MarbleSpacing.md)) {
        SettingsSection(
            title = stringResource(R.string.settings_backup),
            icon = Icons.Outlined.Backup,
            showHeader = selectedCategory == SettingsCategoryFilter.ALL,
        ) {
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
        } }
        }

        item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable {
                            if (educationGesture.tap(SystemClock.elapsedRealtime())) showEducationControls = true
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
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

private enum class SettingsCategoryFilter {
    ALL,
    APPEARANCE,
    NOTIFICATIONS,
    CALENDAR,
    BACKUP,
}

@Composable
private fun SettingsCommandCard(
    settings: AppSettings,
    selectedCategory: SettingsCategoryFilter,
    onSelectCategory: (SettingsCategoryFilter) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(shape = CircleShape, color = colors.primaryContainer) {
                    Icon(
                        Icons.Outlined.Tune,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.padding(8.dp).size(20.dp),
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.settings_title), style = MarbleTextStyles.screenTitle)
                    Text(
                        "${themeLabel(settings.themeMode)} • ${numeralLabel(settings.numeralMode)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                Surface(shape = CircleShape, color = colors.secondaryContainer) {
                    Text(
                        if (settings.languageTag == "fa") stringResource(R.string.language_persian)
                        else stringResource(R.string.language_english),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
            val tabs = listOf(
                SettingsCategoryFilter.ALL to stringResource(R.string.dash_filter_all),
                SettingsCategoryFilter.APPEARANCE to stringResource(R.string.settings_appearance),
                SettingsCategoryFilter.NOTIFICATIONS to stringResource(R.string.settings_notifications),
                SettingsCategoryFilter.CALENDAR to stringResource(R.string.settings_calendar),
                SettingsCategoryFilter.BACKUP to stringResource(R.string.settings_backup),
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tabs.forEach { (category, label) ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { onSelectCategory(category) },
                        label = { Text(label) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationDesignSelector(
    selectedDesign: String,
    onSelectDesign: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val options = listOf(
        "classic" to R.string.notification_design_classic,
        "minimal" to R.string.notification_design_minimal,
        "editorial" to R.string.notification_design_editorial,
        "banner" to R.string.notification_design_banner,
    )
    // The four looks read as one horizontal strip of tiles — the same shape the cards themselves
    // now have — and every tile keeps a full-size touch target.
    LazyRow(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(options) { (design, labelRes) ->
            val selected = selectedDesign == design
            val shape = MaterialTheme.shapes.medium
            Column(
                modifier = Modifier
                    .width(144.dp)
                    .clip(shape)
                    .background(
                        if (selected) colors.primaryContainer.copy(alpha = 0.65f)
                        else colors.surfaceVariant.copy(alpha = 0.45f),
                    )
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) colors.primary else colors.outline.copy(alpha = 0.16f),
                        shape = shape,
                    )
                    .selectable(selected = selected, role = Role.RadioButton) { onSelectDesign(design) }
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        stringResource(labelRes),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) colors.primary else colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                // Mini schematic of the look's anatomy, drawn from the same flags the layouts and
                // the live preview use.
                DesignSchematic(NotificationLook.from(design))
            }
        }
    }
}

@Composable
private fun DesignSchematic(look: NotificationLook) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    Surface(shape = shape, color = colors.surface.copy(alpha = 0.88f), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (look.band) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.primary.copy(alpha = 0.9f)),
                )
            }
            // The header row: badge/date on the start, the state on the end.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (look.badge) {
                    Box(
                        Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(colors.primary.copy(alpha = 0.85f)),
                    )
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(if (look == NotificationLook.EDITORIAL) 5.dp else 4.dp)
                        .clip(CircleShape)
                        .background(colors.onSurface.copy(alpha = 0.6f)),
                )
                if (look.chipPill) {
                    Box(
                        Modifier
                            .width(12.dp)
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(colors.primary.copy(alpha = 0.22f)),
                    )
                } else {
                    Box(
                        Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(colors.primary),
                    )
                }
            }
            if (look.headerDivider) {
                HorizontalDivider(color = colors.outline.copy(alpha = 0.3f))
            }
            // The task row: the title keeps the width, the meta sits at the end.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(colors.onSurface.copy(alpha = 0.45f)),
                )
                Box(
                    Modifier
                        .width(10.dp)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(colors.onSurfaceVariant.copy(alpha = 0.4f)),
                )
            }
            Box(
                Modifier
                    .fillMaxWidth(0.55f)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(colors.onSurfaceVariant.copy(alpha = 0.35f)),
            )
        }
    }
}

/**
 * The in-app twin of the delivered card.
 *
 * It reads the same [PersistentCalendarNotification.NotificationBoxContent], the same
 * [notificationPalette] and the same [NotificationLook] flags as the shade, so the preview cannot
 * describe a card the notification no longer draws. Each look is rendered with its own anatomy —
 * badge and pill and panel, dot-and-word, masthead and rule, or band — and every section is a
 * horizontal row, exactly as the generated layouts do, which is what makes picking a look in
 * Settings a real choice.
 */
@Composable
private fun NotificationLivePreviewCard(
    context: Context,
    settings: AppSettings,
    tasks: List<Task>,
    catalog: com.marbledo.feature.calendar.OccasionCatalog,
) {
    val configuration = LocalConfiguration.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val now by produceState(initialValue = System.currentTimeMillis(), key1 = lifecycle) {
        // Refresh immediately after returning to Settings; do no background preview work.
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val current = System.currentTimeMillis()
                value = current
                kotlinx.coroutines.delay(60_000L - current % 60_000L)
            }
        }
    }
    val content = remember(settings, tasks, catalog, now, configuration) {
        PersistentCalendarNotification.buildContent(context, settings, tasks, catalog, now)
    }
    val shade = remember(settings.themeMode, configuration) {
        notificationPalette(context, settings.themeMode)
    }
    val look = NotificationLook.from(settings.notificationDesign)
    val themeAccent = shade.accent.toInt()
    val badgeBitmap = remember(context, content.badgeDayDigits, content.monthLabel, content.mood, configuration, themeAccent, look) {
        if (look.badge) NotificationArtwork.dateBadge(context, content.badgeDayDigits, content.monthLabel, content.mood, themeAccent) else null
    }
    val cardPlate = Color(shade.background)
    val cardInk = Color(shade.text)
    val cardMuted = Color(shade.muted)
    val cardAccent = Color(shade.accent)
    val cardHairline = Color(shade.hairline)
    val onAccent = Color(shade.onAccent)
    val stateHue = Color(shade.stateHue(content.state))
    // The same three ink rules as the builder: on a pill, the hue re-checked against the pill's
    // fill; on the band, the band's ink; otherwise the hue as it reads on the plate.
    val chipFill = Color(NotificationChipFills.fill(content.state, shade.isDark))
    val stateInk = when {
        look.chipPill -> Color(NotificationChipFills.ink(shade, content.state))
        look.band -> onAccent
        else -> stateHue
    }
    val stateMarkColor = when {
        look.chipPill -> stateInk
        look.band -> Color(MarbleContrast.readable(shade.stateHue(content.state), shade.accent, MarbleContrast.UI_MINIMUM))
        else -> stateHue
    }
    val headerInk = if (look.band) onAccent else cardInk
    val previewDirection = if (settings.languageTag == "fa") LayoutDirection.Rtl else LayoutDirection.Ltr
    val outerShape = RoundedCornerShape(20.dp)
    // The date is the hero of the editorial look and a title everywhere else, so it takes the
    // roles the type scale already reserves instead of improvising a size.
    val dateStyle = when (look) {
        NotificationLook.EDITORIAL -> MarbleTextStyles.dateDigits
        NotificationLook.CLASSIC, NotificationLook.BANNER -> MarbleTextStyles.cardTitle
        NotificationLook.MINIMAL -> MarbleTextStyles.itemTitle
    }

    @Composable
    fun StateChip() {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (look.chipPill) 4.dp else 5.dp),
            modifier = if (look.chipPill) {
                Modifier.clip(RoundedCornerShape(12.dp)).background(chipFill).padding(horizontal = 8.dp, vertical = 3.dp)
            } else {
                Modifier
            },
        ) {
            if (look.chipPill) {
                Icon(painterResource(content.state.iconRes()), contentDescription = null, tint = stateMarkColor, modifier = Modifier.size(12.dp))
            } else {
                Box(Modifier.size(7.dp).clip(CircleShape).background(stateMarkColor))
            }
            Text(
                content.stateLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = stateInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 112.dp),
            )
        }
    }

    @Composable
    fun HeaderRow() {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (look.badge && badgeBitmap != null) {
                Image(
                    bitmap = badgeBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)),
                )
            }
            Text(
                content.title,
                style = dateStyle,
                fontWeight = FontWeight.Bold,
                color = headerInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            StateChip()
        }
    }

    /**
     * One task as one horizontal row: the title keeps the width, the time sits in a fixed column.
     *
     * The column is a width, not a weight, so both rows and every design line their times up under
     * each other — the mirror of the generated layouts' `TASK_META_WIDTH`.
     */
    @Composable
    fun TaskRow(
        title: String,
        meta: String?,
        titleStyle: TextStyle,
        maxLines: Int,
        topPadding: Dp = 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = topPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                title,
                style = titleStyle,
                color = cardInk,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(3f),
            )
            if (meta != null) {
                Text(
                    meta,
                    style = MarbleTextStyles.microLabel,
                    color = cardMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(min = 46.dp),
                )
            }
        }
    }

    @Composable
    fun TaskRows() {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (content.hasTask) {
                Text(
                    content.tasksLabel,
                    style = MarbleTextStyles.metaLabel,
                    fontWeight = FontWeight.Bold,
                    color = cardAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TaskRow(
                title = content.taskTitle ?: content.emptyTaskText,
                meta = content.taskMetaLine,
                titleStyle = MarbleTextStyles.itemTitle,
                maxLines = 2,
            )
            content.secondTaskTitle?.let { second ->
                TaskRow(
                    title = second,
                    meta = content.secondTaskMetaLine,
                    titleStyle = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    topPadding = 10.dp,
                )
            }
            content.moreTasksLine?.let { more ->
                Text(
                    more,
                    style = MarbleTextStyles.microLabel,
                    color = cardMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }

    @Composable
    fun Occasions() {
        if (!content.hasOccasions) return
        HorizontalDivider(color = cardHairline, modifier = Modifier.padding(top = 14.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        ) {
            Icon(
                painterResource(R.drawable.ic_notification_occasion_star),
                contentDescription = null,
                tint = Color(shade.occasion),
                modifier = Modifier.size(13.dp),
            )
            Text(
                content.occasionsLabel,
                style = MarbleTextStyles.metaLabel,
                fontWeight = FontWeight.Bold,
                color = Color(shade.occasion),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            content.occasionsLine,
            style = MaterialTheme.typography.bodySmall,
            color = cardMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }

    CompositionLocalProvider(LocalLayoutDirection provides previewDirection) {
        Surface(
            shape = outerShape,
            color = cardPlate,
            contentColor = cardInk,
            modifier = Modifier.fillMaxWidth().border(1.dp, cardHairline, outerShape)
                .clearAndSetSemantics { contentDescription = content.accessibilityText(expanded = true) },
        ) {
            Column(Modifier.fillMaxWidth()) {
                if (look.band) {
                    // The band bleeds to the card's edge and carries the header row; the rest of
                    // the card keeps the side padding.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                            .background(cardAccent)
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                    ) { HeaderRow() }
                }
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
                    if (!look.band) {
                        HeaderRow()
                        if (look.headerDivider) {
                            HorizontalDivider(color = cardHairline, modifier = Modifier.padding(top = 10.dp))
                        }
                    }
                    // One surface only. The classic look's tonal panel and the editorial look's
                    // vertical rule are gone: the panel read as a box stuck onto the card and the
                    // rule fell on the first letter of the title in RTL.
                    Box(Modifier.fillMaxWidth().padding(top = 6.dp)) { TaskRows() }
                    Occasions()
                }
            }
        }
    }
    Text(
        stringResource(R.string.notification_preview_hint),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
            .clickable(role = Role.Button, onClick = onClick),
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
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 2.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
    )
}

/** Reminder lead times offered for starred occasions; 0 keeps the reminder off. */
private val OCCASION_REMINDER_OPTIONS = listOf(0, 1, 2, 3, 7)

@Composable
private fun tierLabelRes(tier: String): Int = when (tier) {
    "official" -> R.string.settings_tier_official
    "unofficial" -> R.string.settings_tier_unofficial
    else -> R.string.settings_tier_open_source
}

/**
 * How one endpoint behaved on the last refresh. A catalog mirror that was never contacted is
 * standby, not a failure: the mirrors carry one document, so the first that answers wins.
 */
private fun sourceRoleLabelRes(source: OccasionSourceStatus): Int = when {
    !source.ok -> R.string.settings_source_failed
    source.role == OccasionSourceStatus.ROLE_STANDBY -> R.string.settings_source_standby
    source.isCatalog -> R.string.settings_source_delivered
    else -> R.string.settings_source_ok
}

@Composable
private fun occasionStatusText(state: OccasionState, numeralMode: NumeralMode): String {
    val locales = LocalConfiguration.current.locales
    val locale = if (!locales.isEmpty) locales[0] else java.util.Locale.forLanguageTag("fa")
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
    showHeader: Boolean = true,
    content: @Composable () -> Unit,
) {
    // Every settings zone lives in its own expressive box: a rounded, tonal card with a
    // hairline border, so nothing on the page floats loose on the background.
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MarbleBoxBorder(MaterialTheme.colorScheme.surfaceContainerLow),
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(MarbleSpacing.lg)) {
            // When the user already filtered the screen to this very tab ("Appearance", "Calendar"…)
            // repeating the same title inside the card was pure redundancy (docs/UI_REFRESH.md, V-10).
            if (showHeader) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MarbleSpacing.sm),
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
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
                        modifier = Modifier.semantics { heading() },
                    )
                }
                Spacer(Modifier.height(MarbleSpacing.md))
            }
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
    // One toggle target: the row carries the Switch role and the visual Switch is decorative.
    // A clickable row plus a separately toggleable Switch gave TalkBack two stops (S-2).
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 8.dp),
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
        Switch(checked = checked, onCheckedChange = null)
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
            .clickable(role = Role.Button) { onExpandedChange(true) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
        Box {
            // Display only: the row itself is the single click target that opens the menu (S-4).
            // A flat label plus a chevron reads as "current value"; the filled capsule it used to
            // be looked like a second, separate button (docs/UI_REFRESH.md, V-11).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MarbleSpacing.xs),
                modifier = Modifier.padding(start = MarbleSpacing.md, end = MarbleSpacing.xs),
            ) {
                Text(
                    value,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
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
    AppThemeMode.OCEAN -> stringResource(R.string.theme_ocean)
    AppThemeMode.FOREST -> stringResource(R.string.theme_forest)
    AppThemeMode.SUNSET -> stringResource(R.string.theme_sunset)
    AppThemeMode.ROSE -> stringResource(R.string.theme_rose)
}

/**
 * The dot next to a theme name, in the colour that theme actually paints.
 *
 * The old dot was a hand-written table copied out of an earlier palette, so several of them no
 * longer matched the theme they named — Rose's dot was a muted plum the current scheme does not
 * contain at all, and Dark and AMOLED shared one indigo. Resolving the real palette means the dot
 * cannot drift again.
 */
@Composable
private fun ThemeMenuLabel(mode: AppThemeMode) {
    val context = LocalContext.current
    val swatch = when (mode) {
        AppThemeMode.DYNAMIC -> MaterialTheme.colorScheme.primary
        else -> {
            // The dot is a *colour sample*, so it has to be painted on a plate it can be seen on.
            // A light theme's primary over a dark menu row is a dot that vanishes.
            val dark = MarblePalettes.isDark(mode.name, isSystemInDarkTheme())
            remember(mode, dark, context) { Color(MarblePalettes.resolve(mode, dark, context).primary) }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(18.dp).background(swatch, CircleShape))
        Text(themeLabel(mode))
    }
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
    Column(modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                // A themed tint, not a 55% wash of the surface variant: the wash disappeared on
                // the AMOLED and glass schemes, so the options stopped looking like options.
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                shape = shape,
            )
            .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(style) }
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The strip previews a real status bar, which is dark on most devices — but it needs an
        // edge in the AMOLED scheme, where a black strip on a black page is invisible. The strip
        // keeps its own dark ink and gains a hairline of the surrounding outline.
        val stripInk = MarbleContrast.INK_DARK
        Surface(
            shape = RoundedCornerShape(7.dp),
            color = Color(stripInk),
            border = BorderStroke(1.dp, MarbleBoxBorder(Color(stripInk))),
        ) {
            Row(
                Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StatusIconPreview(stripBitmap, Modifier.size(17.dp), Color(MarbleContrast.INK_LIGHT))
                Box(
                    Modifier
                        .size(width = 20.dp, height = 6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(MarbleContrast.INK_LIGHT).copy(alpha = 0.45f)),
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

/**
 * The hours the morning briefing can be pinned to. A full 24-hour list is a scroll wheel nobody
 * wants in a settings sheet; the redesign's promise is "one morning briefing", so the choices are
 * the morning hours people actually read a calendar in, plus noon and the evening.
 */
private val OCCASION_HOUR_OPTIONS = listOf(6, 7, 8, 9, 10, 12, 18, 20)

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
