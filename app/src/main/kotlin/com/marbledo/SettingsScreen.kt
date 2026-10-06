package com.marble098.marbledo

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PrivacyTip
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.marbledo.domain.model.AppSettings
import com.marbledo.domain.model.AppThemeMode
import com.marbledo.domain.model.NumeralMode
import com.marbledo.domain.util.TextNormalizer

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onUpdate: (AppSettings) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onRestoreAutomatic: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestExactAlarm: () -> Unit,
    onCheckUpdates: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var themeMenu by remember { mutableStateOf(false) }
    var languageMenu by remember { mutableStateOf(false) }
    var numeralMenu by remember { mutableStateOf(false) }
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
                AppThemeMode.entries.forEach { item ->
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
                Text(TextNormalizer.formatDigits(stringResource(R.string.settings_font_scale, (settings.fontScale * 100).toInt()), settings.numeralMode), modifier = Modifier.weight(1f))
                IconButton(onClick = { onUpdate(settings.copy(fontScale = (settings.fontScale - 0.1f).coerceAtLeast(0.8f))) }) { Text("−") }
                IconButton(onClick = { onUpdate(settings.copy(fontScale = (settings.fontScale + 0.1f).coerceAtMost(1.5f))) }) { Text("+") }
            }
            SettingSwitchRow(
                text = stringResource(R.string.settings_reduce_motion),
                checked = settings.reduceMotion,
                onCheckedChange = { onUpdate(settings.copy(reduceMotion = it)) },
            )
        }

        SettingsSection(title = stringResource(R.string.settings_calendar), icon = Icons.Outlined.CalendarMonth) {
            SettingSwitchRow(
                text = stringResource(R.string.settings_saturday_start),
                checked = settings.weekStartsSaturday,
                onCheckedChange = { onUpdate(settings.copy(weekStartsSaturday = it)) },
            )
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(TextNormalizer.formatDigits(stringResource(R.string.settings_lunar_offset, settings.lunarOffsetDays), settings.numeralMode), modifier = Modifier.weight(1f))
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

        SettingsSection(title = stringResource(R.string.settings_notifications), icon = Icons.Outlined.NotificationsActive) {
            SettingsActionRow(stringResource(R.string.settings_request_notifications), onRequestNotifications)
            SettingsActionRow(stringResource(R.string.settings_request_exact_alarm), onRequestExactAlarm)
            TextButton(onClick = { showBatteryHelp = true }) { Text(stringResource(R.string.settings_battery_guide_title)) }
        }

        SettingsSection(title = stringResource(R.string.settings_backup), icon = Icons.Outlined.Backup) {
            Text(stringResource(R.string.settings_local_only), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_export)) }
            OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_import)) }
            TextButton(onClick = onRestoreAutomatic, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_restore_automatic)) }
            Text(stringResource(R.string.settings_drive_optional), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SettingsSection(title = stringResource(R.string.settings_privacy), icon = Icons.Outlined.PrivacyTip) {
            Text(stringResource(R.string.settings_local_only), style = MaterialTheme.typography.bodyMedium)
            SettingsActionRow(stringResource(R.string.settings_update), onCheckUpdates)
        }
        Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun SettingsActionRow(text: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, modifier = Modifier.weight(1f))
        TextButton(onClick = onClick) { Text(stringResource(R.string.settings_action)) }
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
        BoxMenu(expanded, onExpandedChange, menuItems)
    }
}

@Composable
private fun BoxMenu(expanded: Boolean, onExpandedChange: (Boolean) -> Unit, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box {
        TextButton(onClick = { onExpandedChange(true) }) { Text(stringResource(R.string.settings_change)) }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) { content() }
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
