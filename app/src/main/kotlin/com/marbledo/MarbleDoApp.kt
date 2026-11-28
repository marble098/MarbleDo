package com.marble098.marbledo

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.runtime.saveable.rememberSaveable
import com.marbledo.domain.model.CalendarDisplayMode
import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import android.view.accessibility.AccessibilityManager
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.WindowCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import com.marbledo.core.designsystem.FloatingGlassTabBar
import com.marbledo.core.designsystem.GlassTabItem
import com.marbledo.core.designsystem.LocalGlassNavInset
import com.marbledo.core.designsystem.MarbleGlass
import com.marbledo.core.designsystem.MarbleSpacing
import com.marbledo.core.designsystem.TabBarAutoHide
import com.marbledo.core.designsystem.glassBackdropSource
import com.marbledo.core.designsystem.glassTintOnBackground
import com.marbledo.core.designsystem.MarblePalettes
import com.marbledo.core.data.backup.BackupCodec
import com.marbledo.core.data.backup.BackupEnvelope
import com.marbledo.core.data.backup.LocalBackupWorker
import com.marbledo.core.data.settings.SettingsRepository
import com.marbledo.domain.model.AppSettings
import com.marbledo.domain.repository.FinanceRepository
import com.marbledo.domain.repository.FinanceSnapshot
import com.marbledo.domain.repository.HabitRepository
import com.marbledo.domain.model.Task
import com.marbledo.domain.model.enabledOccasionCultures
import com.marbledo.domain.util.TextNormalizer
import com.marbledo.feature.calendar.CalendarScreen
import com.marbledo.feature.calendar.enabledOccasionCategories
import com.marbledo.feature.calendar.OccasionCategory
import com.marbledo.feature.calendar.OccasionPlanRequest
import com.marbledo.feature.calendar.OccasionRefreshResult
import com.marbledo.feature.calendar.OccasionRepository
import com.marbledo.feature.countdown.CountdownFocusDialog
import com.marbledo.feature.habits.HabitsScreen
import com.marbledo.feature.habits.HabitsViewModel
import com.marbledo.feature.habits.HabitWidgetPinResult
import com.marbledo.feature.habits.requestHabitWidgetPin
import com.marbledo.feature.habits.updateMarbleHabitWidgets
import com.marbledo.feature.countdown.CountdownWidgetPinResult
import com.marbledo.feature.countdown.requestCountdownWidgetPin
import com.marbledo.feature.countdown.updateMarbleCountdownWidgets
import com.marbledo.feature.tasks.TasksViewModel
import com.marble098.marbledo.notifications.NotificationChannels
import com.marble098.marbledo.notifications.CalendarOccurrenceWorker
import com.marble098.marbledo.notifications.PersistentCalendarNotification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private const val UI_PREFS = "marbledo_ui"
private const val KEY_NOTIFICATION_PROMPT_SHOWN = "notification_prompt_shown"

@Serializable private data object HomeDestination : NavKey
@Serializable private data object HabitsDestination : NavKey
@Serializable private data object CalendarDestination : NavKey
@Serializable private data object SettingsDestination : NavKey

private data class Destination(
    val route: String,
    val key: NavKey,
    val label: Int,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
)

/** Deep-link aliases kept for shortcuts, the quick-settings tile and calendar notifications. */
private fun destinationForRoute(route: String): String = when (route) {
    "tasks", "countdown", "home" -> "home"
    "habits" -> "habits"
    "calendar" -> "calendar"
    "settings" -> "settings"
    else -> "home"
}

// Material 3 Expressive: MarbleDoApp reads MaterialTheme.motionScheme for the screen
// transitions below, so it opts into the expressive API set once, at the root composable.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MarbleDoApp(
    initialShareText: String?,
    initialOpenAdd: Boolean,
    initialDestination: String?,
    initialTaskId: Long?,
    onIntentConsumed: () -> Unit,
) {
    val context = LocalContext.current
    val noAutomaticBackupMessage = stringResource(R.string.settings_no_automatic_backup)
    val refreshFailedMessage = stringResource(R.string.settings_occasions_failed)
    val refreshOfflineMessage = stringResource(R.string.settings_occasions_offline)
    val taskViewModel: TasksViewModel = koinViewModel()
    val taskState by taskViewModel.state.collectAsStateWithLifecycle()
    val habitsViewModel: HabitsViewModel = koinViewModel()
    val habitState by habitsViewModel.state.collectAsStateWithLifecycle()
    val habitRepository: HabitRepository = koinInject()
    val financeRepository: FinanceRepository = koinInject()
    val settingsRepository: SettingsRepository = koinInject()
    val occasionRepository: OccasionRepository = koinInject()
    // Keep the not-yet-loaded state distinct from the default profile. Applying the default
    // language during this gap can race the persisted locale during Activity recreation and
    // repeatedly flip locales (and recreate the Activity) when the user chose English.
    val settingsFlow = remember(settingsRepository) {
        settingsRepository.settings.map<AppSettings, AppSettings?> { it }
    }
    val persistedSettings by settingsFlow.collectAsStateWithLifecycle(initialValue = null)
    val activeAppLanguage = AppCompatDelegate.getApplicationLocales()
        .toLanguageTags()
        .substringBefore(',')
        .substringBefore('-')
        .takeIf { it == "fa" || it == "en" }
    val settings = persistedSettings ?: AppSettings(languageTag = activeAppLanguage ?: "fa")
    val occasionState by occasionRepository.state.collectAsStateWithLifecycle()
    val backStack = rememberNavBackStack(HomeDestination)
    // Hand-offs from the calendar to the dashboard: a day to add a task on, or a task to open.
    var pendingAddDateIso by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingEditTaskId by rememberSaveable { mutableStateOf<Long?>(null) }
    // Hand-off from the calendar: an occasion the user wants as a task, prep step or countdown.
    var pendingOccasionPlan by remember { mutableStateOf<OccasionPlanRequest?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val updateCountdownCalendar: (CalendarDisplayMode) -> Unit = { mode ->
        coroutineScope.launch { settingsRepository.update { current -> current.copy(countdownCalendar = mode) } }
    }
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
    // System bars: edge-to-edge draws the app under the status and navigation bars, and the icon
    // colour there has to follow *the app theme*, not the device's night mode. `enableEdgeToEdge()`
    // in the Activity can only infer it from the system setting, so a user on a pinned dark theme
    // with a light device got dark icons on the dark app — invisible — and vice versa. The mode is
    // reapplied whenever the resolved palette's lightness changes.
    SystemBarIcons(dark = MarblePalettes.isDark(settings.themeMode.name, isSystemDark(context)))
    val notificationsGranted = rememberNotificationsGranted()
    val exactAlarmGranted = rememberExactAlarmGranted()

    val destinations = listOf(
        Destination("home", HomeDestination, R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
        Destination("habits", HabitsDestination, R.string.nav_habits, Icons.Filled.TrendingUp, Icons.Outlined.TrendingUp),
        Destination("calendar", CalendarDestination, R.string.nav_calendar, Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
        Destination("settings", SettingsDestination, R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings),
    )

    LaunchedEffect(Unit) { occasionRepository.load() }
    LaunchedEffect(occasionState.isLoaded) {
        if (occasionState.isLoaded) occasionRepository.refreshIfStale()
    }

    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                runCatching {
                    val passphrase = pendingExportPassphrase
                    pendingExportPassphrase = null
                    val habits = withContext(Dispatchers.IO) { habitRepository.snapshot() }
                    val finance = withContext(Dispatchers.IO) { financeRepository.snapshot() }
                    val bytes = withContext(Dispatchers.Default) {
                        try {
                            BackupCodec.encode(
                                tasks = taskState.allTasks,
                                settings = settings,
                                passphrase = passphrase,
                                habits = habits.habits,
                                habitEntries = habits.entries,
                                debts = finance.debts,
                                checks = finance.checks,
                            )
                        } finally { passphrase?.fill(0.toChar()) }
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
    val calendarExporter = rememberCalendarExporter()
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

    // Ask once, on first launch, so the status-bar date icon and reminders can work without a trip to Settings.
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            val prefs = context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted && !prefs.getBoolean(KEY_NOTIFICATION_PROMPT_SHOWN, false)) {
                prefs.edit().putBoolean(KEY_NOTIFICATION_PROMPT_SHOWN, true).apply()
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
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

    LaunchedEffect(persistedSettings?.languageTag) {
        val languageTag = applicationLanguageToApply(
            persistedLanguageTag = persistedSettings?.languageTag,
            currentLocaleTags = AppCompatDelegate.getApplicationLocales().toLanguageTags(),
        ) ?: return@LaunchedEffect
        // Locale changes recreate AppCompat activities. This effect is keyed to the persisted
        // language and waits for DataStore, so a temporary default can never cause a second
        // recreation in the opposite direction.
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
    }

    LaunchedEffect(persistedSettings?.languageTag) {
        if (persistedSettings != null) NotificationChannels.create(context)
    }

    LaunchedEffect(persistedSettings?.educationModeEnabled) {
        if (persistedSettings?.educationModeEnabled == false) {
            CalendarOccurrenceWorker.clearEducationalNotifications(context.applicationContext)
        }
    }

    LaunchedEffect(persistedSettings?.occasionCultureIds) {
        persistedSettings?.let { CalendarOccurrenceWorker.clearHiddenCultureNotifications(context.applicationContext, it.enabledOccasionCultures()) }
    }

    LaunchedEffect(
        persistedSettings,
        settings,
        taskState.allTasks,
        occasionState.catalog,
        occasionState.isLoaded,
        notificationsGranted,
    ) {
        if (persistedSettings != null && occasionState.isLoaded) {
            withContext(Dispatchers.Default) {
                PersistentCalendarNotification.update(
                    context = context.applicationContext,
                    settings = settings,
                    tasks = taskState.allTasks,
                    catalog = occasionState.catalog,
                )
            }
        }
    }

    LaunchedEffect(
        habitState.habits,
        habitState.entries,
        habitState.today,
        settings.languageTag,
        settings.numeralMode,
        settings.habitTheme,
        // The habit widget now paints itself from the app theme, so a theme switch has to redraw it.
        settings.themeMode,
    ) {
        updateMarbleHabitWidgets(context)
    }

    LaunchedEffect(
        taskState.allTasks,
        settings.languageTag,
        settings.themeMode,
        settings.numeralMode,
        settings.countdownCalendar,
        settings.countdownTheme,
        settings.enabledOccasionCategories(),
        settings.occasionCultureIds,
        settings.lunarOffsetDays,
        occasionState.catalog,
    ) {
        updateMarbleCountdownWidgets(context)
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalLayoutDirection provides if (settings.languageTag == "fa") LayoutDirection.Rtl else LayoutDirection.Ltr,
    ) {
        com.marbledo.core.designsystem.MarbleTheme(
            mode = settings.themeMode.name,
            reduceMotion = settings.reduceMotion,
            fontScale = settings.fontScale,
            numeralMode = settings.numeralMode,
            languageTag = settings.languageTag,
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                val enabledOccasionCategories = settings.enabledOccasionCategories()
                // Material 3 Expressive motion: the screen transition rides the theme's springs
                // instead of a hard-coded tween, so it matches every component's timing.
                val motion = MaterialTheme.motionScheme
                val content: @Composable (Modifier) -> Unit = { contentModifier ->
                    NavDisplay(
                        backStack = backStack,
                        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
                        modifier = contentModifier,
                        transitionSpec = {
                            if (settings.reduceMotion) EnterTransition.None togetherWith ExitTransition.None
                            else (fadeIn(motion.defaultEffectsSpec()) + slideInHorizontally(animationSpec = motion.defaultSpatialSpec()) { if (settings.languageTag == "fa") -it / 14 else it / 14 }) togetherWith fadeOut(motion.fastEffectsSpec())
                        },
                        popTransitionSpec = {
                            if (settings.reduceMotion) EnterTransition.None togetherWith ExitTransition.None
                            else fadeIn(motion.fastEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec())
                        },
                        predictivePopTransitionSpec = {
                            if (settings.reduceMotion) EnterTransition.None togetherWith ExitTransition.None
                            else fadeIn(motion.fastEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec())
                        },
                        entryProvider = entryProvider {
                            entry<HomeDestination> {
                                DashboardScreen(
                                    viewModel = taskViewModel,
                                    financeRepository = financeRepository,
                                    settings = settings,
                                    occasionCatalog = occasionState.catalog,
                                    enabledOccasionCategories = enabledOccasionCategories,
                                    onOpenFocus = { task -> focusTask = task },
                                    onOpenCalendar = { navigateTo(CalendarDestination) },
                                    onViewModeChange = { mode ->
                                        coroutineScope.launch {
                                            settingsRepository.update { current -> current.copy(dashboardViewMode = mode) }
                                        }
                                    },
                                    onAddCategory = { name ->
                                        val key = TextNormalizer.searchKey(name)
                                        coroutineScope.launch {
                                            settingsRepository.update { current ->
                                                if (current.taskCategories.any { TextNormalizer.searchKey(it) == key }) current
                                                else current.copy(taskCategories = current.taskCategories + name.trim())
                                            }
                                        }
                                    },
                                    initialShareText = initialShareText,
                                    initialOpenAdd = initialOpenAdd,
                                    onInitialIntentConsumed = onIntentConsumed,
                                    initialAddDateIso = pendingAddDateIso,
                                    onInitialAddDateConsumed = { pendingAddDateIso = null },
                                    pendingEditTaskId = pendingEditTaskId,
                                    onPendingEditTaskConsumed = { pendingEditTaskId = null },
                                    onCalendarDisplaySelected = updateCountdownCalendar,
                                    favoriteOccasionIds = occasionState.catalog.canonicalFavoriteIds(settings.favoriteOccasionIds),
                                    onToggleFavoriteOccasion = { occasionId ->
                                        coroutineScope.launch {
                                            settingsRepository.update { current ->
                                                val ids = occasionState.catalog.canonicalFavoriteIds(current.favoriteOccasionIds)
                                                current.copy(favoriteOccasionIds =
                                                    (if (occasionId in ids) ids - occasionId else ids + occasionId).toList())
                                            }
                                        }
                                    },
                                    initialPlanRequest = pendingOccasionPlan,
                                    onInitialPlanConsumed = { pendingOccasionPlan = null },
                                )
                            }
                            entry<CalendarDestination> {
                                CalendarScreen(
                                    tasks = taskState.allTasks,
                                    catalog = occasionState.catalog,
                                    occasionState = occasionState,
                                    enabledCategories = enabledOccasionCategories,
                                    enabledCultures = settings.enabledOccasionCultures(),
                                    onCreatePreparation = { tasks -> taskViewModel.addTasks(tasks); navigateTo(HomeDestination) },
                                    languageTag = settings.languageTag,
                                    weekStartsSaturday = settings.weekStartsSaturday,
                                    lunarOffsetDays = settings.lunarOffsetDays,
                                    pickerCalendar = settings.countdownCalendar,
                                    onPickerCalendarChange = updateCountdownCalendar,
                                    thursdayWeekendEnabled = settings.thursdayWeekendEnabled,
                                    exportInProgress = calendarExporter.busy,
                                    onExport = calendarExporter.export,
                                    onAddTask = { date ->
                                        pendingAddDateIso = date.toString()
                                        navigateTo(HomeDestination)
                                    },
                                    onOpenTask = { task ->
                                        pendingEditTaskId = task.id
                                        navigateTo(HomeDestination)
                                    },
                                    favoriteOccasionIds = occasionState.catalog.canonicalFavoriteIds(settings.favoriteOccasionIds),
                                    occasionReminderLeadDays = settings.occasionReminderLeadDays,
                                    onToggleFavorite = { occasionId ->
                                        coroutineScope.launch {
                                            settingsRepository.update { current ->
                                                val ids = occasionState.catalog.canonicalFavoriteIds(current.favoriteOccasionIds)
                                                current.copy(favoriteOccasionIds =
                                                    (if (occasionId in ids) ids - occasionId else ids + occasionId).toList())
                                            }
                                        }
                                    },
                                    onPlanOccasion = { request ->
                                        pendingOccasionPlan = request
                                        navigateTo(HomeDestination)
                                    },
                                )
                            }
                            entry<HabitsDestination> {
                                HabitsScreen(
                                    viewModel = habitsViewModel,
                                    settings = settings,
                                    onThemeChange = { theme ->
                                        coroutineScope.launch {
                                            settingsRepository.update { current -> current.copy(habitTheme = theme.id) }
                                        }
                                    },
                                    onAddWidget = {
                                        val message = when (requestHabitWidgetPin(context)) {
                                            HabitWidgetPinResult.REQUESTED -> R.string.settings_widget_pin_requested
                                            HabitWidgetPinResult.UNSUPPORTED -> R.string.settings_widget_pin_unsupported
                                            HabitWidgetPinResult.FAILED -> R.string.settings_widget_pin_failed
                                        }
                                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                    },
                                )
                            }
                            entry<SettingsDestination> {
                                SettingsScreen(
                                    settings = settings,
                                    notificationsGranted = notificationsGranted,
                                    exactAlarmGranted = exactAlarmGranted,
                                    occasionState = occasionState,
                                    onUpdate = { updated -> coroutineScope.launch { settingsRepository.update { updated } } },
                                    onAddWidget = {
                                        val message = when (requestCountdownWidgetPin(context)) {
                                            CountdownWidgetPinResult.REQUESTED -> R.string.settings_widget_pin_requested
                                            CountdownWidgetPinResult.UNSUPPORTED -> R.string.settings_widget_pin_unsupported
                                            CountdownWidgetPinResult.FAILED -> R.string.settings_widget_pin_failed
                                        }
                                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                    },
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
                                                        ?: error(noAutomaticBackupMessage)
                                                }
                                                withContext(Dispatchers.Default) { BackupCodec.decode(latest) }
                                            }.onSuccess { envelope ->
                                                restoreEnvelope = envelope
                                                showRestoreChoice = true
                                            }.onFailure {
                                                Toast.makeText(context, noAutomaticBackupMessage, Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    },
                                    onRequestNotifications = {
                                        if (Build.VERSION.SDK_INT >= 33) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        else requestNotificationPermission(context)
                                    },
                                    onRequestExactAlarm = { requestExactAlarmSettings(context) },
                                    onCheckUpdates = { openReleasePage(context) },
                                    tasks = taskState.allTasks,
                                )
                            }
                        },
                    )
                }

                if (isLarge) {
                    val colors = MaterialTheme.colorScheme
                    val density = LocalDensity.current
                    val navBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    val imeBottom = with(density) { WindowInsets.ime.getBottom(this).toDp() }
                    val contentInset = maxOf(navBarsBottom + MarbleSpacing.xl, imeBottom)
                    // Large screens get a floating glass rail instead of an edge-anchored bar:
                    // the same material language as the compact tab bar, composited over the flat
                    // page since nothing scrolls under a side rail (docs/UI_OVERHAUL_2025.md).
                    Row(
                        Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
                    ) {
                        Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(MarbleGlass.corner),
                            color = glassTintOnBackground(),
                            modifier = Modifier.padding(
                                start = MarbleSpacing.sm,
                                top = MarbleSpacing.sm,
                                bottom = MarbleSpacing.sm,
                            ),
                        ) {
                            NavigationRail(
                                containerColor = Color.Transparent,
                                windowInsets = WindowInsets(0.dp),
                            ) {
                            // Give the rail a clear home and stronger hierarchy on tablets/foldables.
                            // The compact mark stays separate from destination icons so it is never
                            // mistaken for another selectable route.
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp, bottom = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                            ) {
                                Surface(
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                                    color = colors.primaryContainer,
                                    contentColor = colors.onPrimaryContainer,
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.GridView,
                                        contentDescription = null,
                                        modifier = Modifier.padding(10.dp),
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                            destinations.forEach { destination ->
                                val selected = selectedDestination.key == destination.key
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateTo(destination.key) },
                                    icon = {
                                        Icon(
                                            if (selected) destination.selectedIcon else destination.unselectedIcon,
                                            contentDescription = stringResource(destination.label),
                                        )
                                    },
                                    label = {
                                        Text(
                                            stringResource(destination.label),
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        )
                                    },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = colors.primary,
                                        selectedTextColor = colors.primary,
                                        indicatorColor = colors.primaryContainer,
                                        unselectedIconColor = colors.onSurfaceVariant,
                                        unselectedTextColor = colors.onSurfaceVariant,
                                    ),
                                )
                            }
                            }
                        }
                        Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                            CompositionLocalProvider(LocalGlassNavInset provides contentInset) {
                                content(Modifier.widthIn(max = 880.dp).fillMaxSize())
                            }
                        }
                    }
                } else {
                    // The compact shell: one floating liquid-glass tab bar over full-bleed content,
                    // with a real capture of what scrolls underneath it (docs/UI_OVERHAUL_2025.md).
                    val backdrop = rememberGraphicsLayer()
                    val backdropOrigin = remember { mutableStateOf(Offset.Zero) }
                    val autoHide = remember { mutableStateOf(TabBarAutoHide()) }
                    val density = LocalDensity.current
                    val thresholdPx = with(density) { MarbleGlass.scrollThreshold.toPx() }
                    val navBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    val imeBottom = with(density) { WindowInsets.ime.getBottom(this).toDp() }
                    val contentInset = maxOf(MarbleGlass.clearance(navBarsBottom), imeBottom)
                    // The bar follows sustained scroll direction. Reduced-motion users and
                    // touch-exploration (TalkBack) users get a bar that never moves.
                    val touchExplorationEnabled = rememberTouchExplorationEnabled()
                    val autoHideEnabled = !settings.reduceMotion && !touchExplorationEnabled
                    LaunchedEffect(autoHideEnabled, imeBottom > 0.dp) {
                        if (!autoHideEnabled || imeBottom > 0.dp) autoHide.value = autoHide.value.reset()
                    }
                    val scrollConnection = remember(autoHideEnabled, thresholdPx) {
                        object : NestedScrollConnection {
                            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                                if (autoHideEnabled && source == NestedScrollSource.UserInput) {
                                    autoHide.value = autoHide.value.scrolled(available.y, thresholdPx)
                                }
                                return Offset.Zero
                            }
                        }
                    }
                    Box(Modifier.fillMaxSize().nestedScroll(scrollConnection)) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                                .onGloballyPositioned { backdropOrigin.value = it.positionInWindow() }
                                .glassBackdropSource(backdrop),
                        ) {
                            CompositionLocalProvider(LocalGlassNavInset provides contentInset) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                                    content(Modifier.widthIn(max = 880.dp).fillMaxSize())
                                }
                            }
                            FloatingGlassTabBar(
                                items = destinations.map { destination ->
                                    GlassTabItem(
                                        label = stringResource(destination.label),
                                        selectedIcon = destination.selectedIcon,
                                        unselectedIcon = destination.unselectedIcon,
                                    )
                                },
                                selectedIndex = destinations.indexOfFirst { it.key == selectedDestination.key },
                                onSelect = { index ->
                                    navigateTo(destinations[index].key)
                                    autoHide.value = autoHide.value.reset()
                                },
                                backdrop = backdrop,
                                backdropOrigin = backdropOrigin.value,
                                visible = autoHide.value.shouldShow(autoHideEnabled, imeBottom > 0.dp),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .navigationBarsPadding()
                                    .padding(
                                        bottom = MarbleGlass.floatGap,
                                        start = MarbleGlass.floatMargin,
                                        end = MarbleGlass.floatMargin,
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(initialDestination) {
        initialDestination?.let { route ->
            val target = destinations.firstOrNull { it.route == destinationForRoute(route) }
            if (target != null) navigateTo(target.key)
        }
    }

    LaunchedEffect(initialShareText, initialOpenAdd) {
        if (initialOpenAdd || !initialShareText.isNullOrBlank()) navigateTo(HomeDestination)
    }

    LaunchedEffect(initialTaskId, taskState.allTasks) {
        initialTaskId?.let { id ->
            taskState.allTasks.firstOrNull { it.id == id }?.let { task ->
                focusTask = task
                navigateTo(HomeDestination)
                onIntentConsumed()
            }
        }
    }

    focusTask?.let { task ->
        CountdownFocusDialog(
            task = task,
            themeId = task.countdownTheme.takeIf { com.marbledo.domain.model.CountdownTheme.isKnown(it) } ?: settings.countdownTheme,
            calendarDisplay = settings.countdownCalendar,
            onDismiss = { focusTask = null },
        )
    }

    val pendingRestore = if (showRestoreChoice) restoreEnvelope else null
    if (pendingRestore != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showRestoreChoice = false },
            title = { Text(stringResource(R.string.settings_import)) },
            text = { Text(TextNormalizer.formatDigits(stringResource(R.string.settings_import_confirmation, pendingRestore.tasks.size), settings.numeralMode)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val backup = restoreEnvelope
                    if (backup != null) {
                        taskViewModel.restoreTasks(backup.tasks, merge = true)
                        coroutineScope.launch {
                            // Persist data before settings: applying a restored locale can recreate
                            // the Activity and cancel this composition's scope.
                            habitRepository.replaceAll(backup.habits, backup.habitEntries)
                            financeRepository.replaceAll(FinanceSnapshot(backup.debts, backup.checks))
                            settingsRepository.update { backup.settings }
                        }
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

/** Re-reads the notification grant on every resume so the settings screen can hide itself again. */
@Composable
private fun rememberNotificationsGranted(): Boolean {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return granted
}

/** Exact-alarm access is always considered granted below Android 12. */
@Composable
private fun rememberExactAlarmGranted(): Boolean {
    val context = LocalContext.current
    fun read(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return false
        return alarmManager.canScheduleExactAlarms()
    }
    var granted by remember { mutableStateOf(read()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = read()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return granted
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

/**
 * Tints the status- and navigation-bar icons for the app theme's lightness.
 *
 * The window is edge-to-edge, so these icons sit on top of the app's own background. Android only
 * offers "light icons" / "dark icons" as a flag, which `enableEdgeToEdge()` derives from the
 * *device* night mode; when the app theme is pinned the other way round the icons end up
 * dark-on-dark or white-on-white. This side effect re-applies the flag from the resolved palette.
 */
@Composable
private fun SystemBarIcons(dark: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    // LocalView's context is a ContextThemeWrapper, not the Activity itself.
    val window = view.context.findActivity()?.window ?: return
    DisposableEffect(dark) {
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
        onDispose { }
    }
}

/** Walks the ContextWrapper chain the way every Android library has to. */
private fun Context.findActivity(): android.app.Activity? {
    var context: Context? = this
    while (context is android.content.ContextWrapper) {
        if (context is android.app.Activity) return context
        context = context.baseContext
    }
    return context as? android.app.Activity
}

/** Mirrors `MarblePalettes.isDark` for the system half of an "AUTO" theme. */
private fun isSystemDark(context: Context): Boolean =
    context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES

/**
 * Whether touch exploration (TalkBack and friends) is active. Observe the system setting instead
 * of sampling it during composition: the bar must reappear immediately if accessibility is turned
 * on while it is hidden (docs/UI_OVERHAUL_2025.md, "Accessibility").
 */
@Composable
private fun rememberTouchExplorationEnabled(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(AccessibilityManager::class.java) }
    var enabled by remember(manager) { mutableStateOf(manager?.isTouchExplorationEnabled == true) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled = it }
        manager?.addTouchExplorationStateChangeListener(listener)
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }
    return enabled
}
