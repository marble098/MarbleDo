package com.marble098.marbledo

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.marbledo.core.data.backup.BackupCodec
import com.marbledo.core.data.backup.BackupEnvelope
import com.marbledo.core.data.backup.LocalBackupWorker
import com.marbledo.core.data.settings.SettingsRepository
import com.marbledo.domain.model.AppSettings
import com.marbledo.domain.model.Task
import com.marbledo.domain.util.TextNormalizer
import com.marbledo.feature.calendar.CalendarScreen
import com.marbledo.feature.countdown.CountdownFocusDialog
import com.marbledo.feature.countdown.CountdownScreen
import com.marbledo.feature.tasks.TasksScreen
import com.marbledo.feature.tasks.TasksViewModel
import kotlinx.serialization.Serializable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Serializable private data object TasksDestination : NavKey
@Serializable private data object CalendarDestination : NavKey
@Serializable private data object CountdownDestination : NavKey
@Serializable private data object SettingsDestination : NavKey

private data class Destination(
    val route: String,
    val key: NavKey,
    val label: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

@Composable
fun MarbleDoApp(
    initialShareText: String?,
    initialOpenAdd: Boolean,
    initialDestination: String?,
    initialTaskId: Long?,
    onIntentConsumed: () -> Unit,
) {
    val context = LocalContext.current
    val taskViewModel: TasksViewModel = koinViewModel()
    val taskState by taskViewModel.state.collectAsStateWithLifecycle()
    val settingsRepository: SettingsRepository = koinInject()
    val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val backStack = rememberNavBackStack(TasksDestination)
    val coroutineScope = rememberCoroutineScope()
    var focusTask by remember { mutableStateOf<Task?>(null) }
    var restoreEnvelope by remember { mutableStateOf<BackupEnvelope?>(null) }
    var showRestoreChoice by remember { mutableStateOf(false) }
    var showExportPasswordDialog by remember { mutableStateOf(false) }
    var exportPassphrase by remember { mutableStateOf("") }
    var pendingExportPassphrase by remember { mutableStateOf<CharArray?>(null) }
    var pendingRestoreBytes by remember { mutableStateOf<ByteArray?>(null) }
    var showRestorePasswordDialog by remember { mutableStateOf(false) }
    var restorePassphrase by remember { mutableStateOf("") }
    val configuration = LocalConfiguration.current
    val isLarge = configuration.screenWidthDp >= 600

    val destinations = listOf(
        Destination("tasks", TasksDestination, R.string.nav_home, Icons.Outlined.CheckCircle),
        Destination("calendar", CalendarDestination, R.string.nav_calendar, Icons.Outlined.CalendarMonth),
        Destination("countdown", CountdownDestination, R.string.nav_countdown, Icons.Outlined.Timer),
        Destination("settings", SettingsDestination, R.string.nav_settings, Icons.Outlined.Settings),
    )

    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                runCatching {
                    val passphrase = pendingExportPassphrase
                    pendingExportPassphrase = null
                    val bytes = withContext(Dispatchers.Default) {
                        try { BackupCodec.encode(taskState.allTasks, settings, passphrase) }
                        finally { passphrase?.fill(0.toChar()) }
                    }
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                            ?: error("Could not open selected backup destination")
                    }
                }.onSuccess {
                    Toast.makeText(context, R.string.settings_backup_exported, Toast.LENGTH_SHORT).show()
                }.onFailure {
                    pendingExportPassphrase?.fill(0.toChar())
                    pendingExportPassphrase = null
                    Toast.makeText(context, R.string.settings_backup_failed, Toast.LENGTH_LONG).show()
                }
            }
        } else {
            pendingExportPassphrase?.fill(0.toChar())
            pendingExportPassphrase = null
        }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                runCatching {
                    val bytes = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { stream -> stream.readBounded(MAX_BACKUP_FILE_BYTES) }
                            ?: error("Could not read selected backup")
                    }
                    if (BackupCodec.isEncrypted(bytes)) {
                        pendingRestoreBytes = bytes
                        showRestorePasswordDialog = true
                        null
                    } else {
                        withContext(Dispatchers.Default) { BackupCodec.decode(bytes) }
                    }
                }.onSuccess { envelope ->
                    if (envelope != null) {
                        restoreEnvelope = envelope
                        showRestoreChoice = true
                    }
                }.onFailure {
                    Toast.makeText(context, R.string.settings_backup_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(context, R.string.settings_notifications_denied, Toast.LENGTH_LONG).show()
    }

    fun navigateTo(key: NavKey) {
        val existingIndex = backStack.indexOf(key)
        if (existingIndex < 0) {
            backStack.add(key)
        } else {
            while (backStack.lastOrNull() != key && backStack.isNotEmpty()) backStack.removeAt(backStack.lastIndex)
        }
    }

    val selectedDestination = destinations.firstOrNull { it.key == backStack.lastOrNull() } ?: destinations.first()

    SideEffect {
        val currentTags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        if (currentTags.substringBefore(',') != settings.languageTag) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(settings.languageTag))
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalLayoutDirection provides if (settings.languageTag == "fa") LayoutDirection.Rtl else LayoutDirection.Ltr,
    ) {
        com.marbledo.core.designsystem.MarbleTheme(
            mode = settings.themeMode.name,
            reduceMotion = settings.reduceMotion,
            fontScale = settings.fontScale,
            numeralMode = settings.numeralMode,
        ) {
            Surface(Modifier.fillMaxSize()) {
                val content: @Composable (Modifier) -> Unit = { contentModifier ->
                    NavDisplay(
                        backStack = backStack,
                        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
                        modifier = contentModifier,
                        transitionSpec = {
                            if (settings.reduceMotion) EnterTransition.None togetherWith ExitTransition.None
                            else (fadeIn(tween(230)) + slideInHorizontally(animationSpec = tween(230)) { if (settings.languageTag == "fa") -it / 14 else it / 14 }) togetherWith fadeOut(tween(150))
                        },
                        popTransitionSpec = {
                            if (settings.reduceMotion) EnterTransition.None togetherWith ExitTransition.None
                            else fadeIn(tween(180)) togetherWith fadeOut(tween(150))
                        },
                        predictivePopTransitionSpec = {
                            if (settings.reduceMotion) EnterTransition.None togetherWith ExitTransition.None
                            else fadeIn(tween(180)) togetherWith fadeOut(tween(150))
                        },
                        entryProvider = entryProvider {
                            entry<TasksDestination> {
                                TasksScreen(
                                    viewModel = taskViewModel,
                                    onOpenCountdown = { task -> focusTask = task; navigateTo(CountdownDestination) },
                                    initialShareText = initialShareText,
                                    initialOpenAdd = initialOpenAdd,
                                    onInitialIntentConsumed = onIntentConsumed,
                                )
                            }
                            entry<CalendarDestination> {
                                val categories = buildSet {
                                    if (settings.officialEventsEnabled) add("official")
                                    if (settings.nationalEventsEnabled) add("national")
                                    if (settings.religiousEventsEnabled) add("religious")
                                    if (settings.personalEventsEnabled) add("personal")
                                }
                                CalendarScreen(
                                    tasks = taskState.allTasks,
                                    languageTag = settings.languageTag,
                                    weekStartsSaturday = settings.weekStartsSaturday,
                                    lunarOffsetDays = settings.lunarOffsetDays,
                                    enabledEventCategories = categories,
                                )
                            }
                            entry<CountdownDestination> {
                                CountdownScreen(
                                    tasks = taskState.allTasks,
                                    selectedThemeId = settings.countdownTheme,
                                    onThemeSelected = { id -> coroutineScope.launch { settingsRepository.update { it.copy(countdownTheme = id) } } },
                                    onOpenFocus = { focusTask = it },
                                )
                            }
                            entry<SettingsDestination> {
                                SettingsScreen(
                                    settings = settings,
                                    onUpdate = { updated -> coroutineScope.launch { settingsRepository.update { updated } } },
                                    onExport = { showExportPasswordDialog = true },
                                    onImport = { openBackup.launch(arrayOf("application/octet-stream", "application/json", "*/*")) },
                                    onRestoreAutomatic = {
                                        coroutineScope.launch {
                                            runCatching {
                                                val latest = withContext(Dispatchers.IO) {
                                                    File(context.filesDir, LocalBackupWorker.BACKUP_DIRECTORY)
                                                        .listFiles { file -> file.name.endsWith(".mdo") }
                                                        ?.maxByOrNull(File::lastModified)
                                                        ?.inputStream()?.use { it.readBounded(MAX_BACKUP_FILE_BYTES) }
                                                        ?: error(context.getString(R.string.settings_no_automatic_backup))
                                                }
                                                withContext(Dispatchers.Default) { BackupCodec.decode(latest) }
                                            }.onSuccess { envelope ->
                                                restoreEnvelope = envelope
                                                showRestoreChoice = true
                                            }.onFailure {
                                                Toast.makeText(context, R.string.settings_no_automatic_backup, Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    },
                                    onRequestNotifications = {
                                        if (Build.VERSION.SDK_INT >= 33) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        else requestNotificationPermission(context)
                                    },
                                    onRequestExactAlarm = { requestExactAlarmSettings(context) },
                                    onCheckUpdates = { openReleasePage(context) },
                                )
                            }
                        },
                    )
                }

                if (isLarge) {
                    Row(Modifier.fillMaxSize()) {
                        NavigationRail {
                            destinations.forEach { destination ->
                                val selected = selectedDestination.key == destination.key
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateTo(destination.key) },
                                    icon = { Icon(destination.icon, contentDescription = stringResource(destination.label)) },
                                    label = { Text(stringResource(destination.label)) },
                                )
                            }
                        }
                        content(Modifier.weight(1f))
                    }
                } else {
                    Scaffold(
                        bottomBar = {
                            NavigationBar {
                                destinations.forEach { destination ->
                                    val selected = selectedDestination.key == destination.key
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { navigateTo(destination.key) },
                                        icon = { Icon(destination.icon, contentDescription = stringResource(destination.label)) },
                                        label = { Text(stringResource(destination.label)) },
                                    )
                                }
                            }
                        },
                    ) { innerPadding -> content(Modifier.padding(innerPadding)) }
                }
            }
        }
    }

    LaunchedEffect(initialDestination) {
        initialDestination?.let { route ->
            destinations.firstOrNull { it.route == route }?.let { navigateTo(it.key) }
        }
    }

    LaunchedEffect(initialShareText, initialOpenAdd) {
        if (initialOpenAdd || !initialShareText.isNullOrBlank()) navigateTo(TasksDestination)
    }

    LaunchedEffect(initialTaskId, taskState.allTasks) {
        initialTaskId?.let { id ->
            taskState.allTasks.firstOrNull { it.id == id }?.let { task ->
                focusTask = task
                navigateTo(CountdownDestination)
                onIntentConsumed()
            }
        }
    }

    focusTask?.let { task ->
        CountdownFocusDialog(task = task, themeId = settings.countdownTheme, onDismiss = { focusTask = null })
    }

    if (showRestoreChoice && restoreEnvelope != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showRestoreChoice = false },
            title = { Text(stringResource(R.string.settings_import)) },
            text = { Text(TextNormalizer.formatDigits(stringResource(R.string.settings_import_confirmation, restoreEnvelope!!.tasks.size), settings.numeralMode)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val backup = restoreEnvelope
                    if (backup != null) {
                        taskViewModel.restoreTasks(backup.tasks, merge = true)
                        coroutineScope.launch { settingsRepository.update { backup.settings } }
                    }
                    restoreEnvelope = null
                    showRestoreChoice = false
                    onIntentConsumed()
                    Toast.makeText(context, R.string.settings_backup_imported, Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(R.string.settings_import)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { restoreEnvelope = null; showRestoreChoice = false }) {
                    Text(stringResource(R.string.settings_close))
                }
            },
        )
    }

    if (showExportPasswordDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showExportPasswordDialog = false; exportPassphrase = "" },
            title = { Text(stringResource(R.string.settings_backup_passphrase_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.settings_backup_passphrase_info))
                    OutlinedTextField(
                        value = exportPassphrase,
                        onValueChange = { exportPassphrase = it },
                        label = { Text(stringResource(R.string.settings_backup_passphrase_hint)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
            },
            confirmButton = {
                Column {
                    androidx.compose.material3.TextButton(onClick = {
                        pendingExportPassphrase = null
                        exportPassphrase = ""
                        showExportPasswordDialog = false
                        createBackup.launch("MarbleDo-backup.mdo")
                    }) { Text(stringResource(R.string.settings_backup_export_plain)) }
                    androidx.compose.material3.TextButton(onClick = {
                        if (exportPassphrase.length < 8) {
                            Toast.makeText(context, R.string.settings_backup_password_too_short, Toast.LENGTH_SHORT).show()
                        } else {
                            pendingExportPassphrase = exportPassphrase.toCharArray()
                            exportPassphrase = ""
                            showExportPasswordDialog = false
                            createBackup.launch("MarbleDo-encrypted-backup.mdo")
                        }
                    }) { Text(stringResource(R.string.settings_backup_export_encrypted)) }
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showExportPasswordDialog = false; exportPassphrase = "" }) {
                    Text(stringResource(R.string.settings_close))
                }
            },
        )
    }

    if (showRestorePasswordDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                showRestorePasswordDialog = false
                pendingRestoreBytes = null
                restorePassphrase = ""
            },
            title = { Text(stringResource(R.string.settings_backup_passphrase_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.settings_backup_enter_passphrase))
                    OutlinedTextField(
                        value = restorePassphrase,
                        onValueChange = { restorePassphrase = it },
                        label = { Text(stringResource(R.string.settings_backup_passphrase_hint)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val bytes = pendingRestoreBytes
                    val passphrase = restorePassphrase.toCharArray()
                    showRestorePasswordDialog = false
                    restorePassphrase = ""
                    if (bytes != null) {
                        coroutineScope.launch {
                            runCatching {
                                withContext(Dispatchers.Default) {
                                    try { BackupCodec.decode(bytes, passphrase) }
                                    finally { passphrase.fill(0.toChar()) }
                                }
                            }.onSuccess { envelope ->
                                restoreEnvelope = envelope
                                showRestoreChoice = true
                                pendingRestoreBytes = null
                            }.onFailure {
                                showRestorePasswordDialog = true
                                Toast.makeText(context, R.string.settings_backup_decryption_failed, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }) { Text(stringResource(R.string.settings_import)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showRestorePasswordDialog = false
                    pendingRestoreBytes = null
                    restorePassphrase = ""
                }) { Text(stringResource(R.string.settings_close)) }
            },
        )
    }
}

private fun requestNotificationPermission(context: android.content.Context) {
    if (Build.VERSION.SDK_INT >= 33) {
        val intent = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    } else {
        runCatching {
            context.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

private fun requestExactAlarmSettings(context: android.content.Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        if (alarmManager.canScheduleExactAlarms()) return
        val intent = Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).setData(Uri.parse("package:${context.packageName}"))
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

private fun openReleasePage(context: android.content.Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/marble098/MarbleDo/releases"))
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private const val MAX_BACKUP_FILE_BYTES = 64 * 1024 * 1024 + 4096

private fun InputStream.readBounded(maxBytes: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        total += count
        require(total <= maxBytes) { "Backup file exceeds the safety limit" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
