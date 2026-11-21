package com.marble098.marbledo

import android.app.Application
import android.os.StrictMode
import com.marble098.marbledo.app.sync.OccasionSyncWorker
import com.marbledo.core.data.backup.BackupWorkInitializer
import com.marbledo.core.data.backup.WorkManagerBackupScheduler
import com.marbledo.core.data.db.MarbleDatabase
import com.marbledo.core.data.repository.RoomFinanceRepository
import com.marbledo.core.data.repository.RoomHabitRepository
import com.marbledo.core.data.repository.RoomTaskRepository
import com.marbledo.core.data.settings.SettingsRepository
import com.marbledo.feature.calendar.OccasionRepository
import com.marbledo.domain.repository.TaskBackupScheduler
import com.marbledo.domain.repository.TaskReminderScheduler
import com.marbledo.domain.repository.FinanceRepository
import com.marbledo.domain.repository.HabitRepository
import com.marbledo.domain.repository.TaskRepository
import com.marbledo.feature.habits.HabitsViewModel
import com.marbledo.feature.tasks.TasksViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.dsl.module

class MarbleDoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        enableDebugStrictMode()
        startKoin {
            androidContext(this@MarbleDoApplication)
            modules(marbleDoModule)
        }
        BackupWorkInitializer.ensureDailyBackup(this)
        OccasionSyncWorker.ensureDaily(this)
    }

    private fun enableDebugStrictMode() {
        if (!BuildConfig.DEBUG) return
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectActivityLeaks()
                .penaltyLog()
                .build(),
        )
    }
}

private val marbleDoModule = module {
    single { MarbleDatabase.create(androidContext()) }
    single<TaskRepository> { RoomTaskRepository(get<MarbleDatabase>().taskDao()) }
    single<HabitRepository> { RoomHabitRepository(get<MarbleDatabase>().habitDao()) }
    single<FinanceRepository> { RoomFinanceRepository(get<MarbleDatabase>()) }
    single { SettingsRepository(androidContext()) }
    single { OccasionRepository(androidContext()) }
    single { WorkManagerBackupScheduler(androidContext()) }
    single<TaskBackupScheduler> { get<WorkManagerBackupScheduler>() }
    viewModel { TasksViewModel(get(), get()) }
    // The habits screen writes reminders and refreshes widgets, so it needs the app context.
    viewModel { HabitsViewModel(get(), androidContext()) }
}
