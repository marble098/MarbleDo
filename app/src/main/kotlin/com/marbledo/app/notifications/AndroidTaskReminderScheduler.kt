package com.marble098.marbledo.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.marbledo.domain.model.Task
import com.marbledo.domain.repository.TaskReminderScheduler

class AndroidTaskReminderScheduler(context: Context) : TaskReminderScheduler {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    override fun schedule(task: Task) {
        val dueAt = task.dueAtEpochMillis
        if (dueAt == null || task.isCompleted || task.isArchived || dueAt <= System.currentTimeMillis()) {
            cancel(task.id)
            return
        }
        cancel(task.id)
        scheduleAt(task.id, dueAt)
        val lead = ReminderPreferences.leadMillis(appContext)
        val earlyAt = dueAt - lead
        if (lead > 0 && earlyAt > System.currentTimeMillis()) scheduleAt(task.id, earlyAt, requestCode(task.id, early = true))
    }

    override fun cancel(taskId: Long) {
        alarmManager.cancel(pendingIntent(taskId, requestCode(taskId, early = false)))
        alarmManager.cancel(pendingIntent(taskId, requestCode(taskId, early = true)))
        alarmManager.cancel(pendingIntent(taskId, deferredRequestCode(taskId)))
    }

    /**
     * Fires the reminder again at [atMillis], for snooze and for reminders that fall inside quiet hours.
     * It uses its own request code, so the due-time and early alarms stay scheduled.
     */
    fun remindAgainAt(taskId: Long, atMillis: Long) {
        scheduleAt(taskId, atMillis, requestCode = deferredRequestCode(taskId))
    }

    private fun scheduleAt(
        taskId: Long,
        atMillis: Long,
        requestCode: Int = requestCode(taskId, early = false),
        action: String = ReminderReceiver.ACTION_REMIND,
    ) {
        val pending = pendingIntent(taskId, requestCode, action)
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        }
    }

    private fun pendingIntent(taskId: Long, requestCode: Int, action: String = ReminderReceiver.ACTION_REMIND): PendingIntent {
        val intent = Intent(appContext, ReminderReceiver::class.java).apply {
            this.action = action
            data = Uri.parse("marbledo://reminder/$taskId/$requestCode")
            putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getBroadcast(
            appContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun requestCode(id: Long, early: Boolean): Int =
        (id xor (id ushr 32)).toInt() * 2 + if (early) 1 else 0

    private fun deferredRequestCode(id: Long): Int = requestCode(id, early = false) xor DEFERRED_REQUEST_FLAG

    private companion object {
        const val DEFERRED_REQUEST_FLAG = 0x4000_0000
    }
}
