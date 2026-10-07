package com.marble098.marbledo.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.marbledo.domain.repository.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                CalendarNotificationWorkInitializer.ensureDaily(context)
                PersistentCalendarNotificationWorker.ensurePeriodic(context)
                PersistentCalendarNotificationWorker.refreshNow(context)
                val koin = GlobalContext.getOrNull() ?: return@launch
                val repository = koin.get<TaskRepository>()
                val scheduler = koin.get<AndroidTaskReminderScheduler>()
                repository.observeTasks().first().forEach(scheduler::schedule)
            } finally {
                pending.finish()
            }
        }
    }
}
