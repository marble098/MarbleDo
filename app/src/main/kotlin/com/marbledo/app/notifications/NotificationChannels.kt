package com.marble098.marbledo.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.marble098.marbledo.R

object NotificationChannels {
    const val TASKS = "marbledo_tasks"
    const val COUNTDOWN = "marbledo_countdown"
    const val CALENDAR = "marbledo_calendar"
    const val BACKUP = "marbledo_backup"
    const val DAILY = "marbledo_daily_summary"
    const val PERSISTENT_CALENDAR = "marbledo_persistent_calendar"

    fun create(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channels = listOf(
            NotificationChannel(TASKS, context.getString(R.string.channel_tasks), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_tasks_description)
                enableVibration(true)
            },
            NotificationChannel(COUNTDOWN, context.getString(R.string.channel_countdown), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.channel_countdown_description)
                setShowBadge(false)
            },
            NotificationChannel(CALENDAR, context.getString(R.string.channel_calendar), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.channel_calendar_description)
            },
            NotificationChannel(BACKUP, context.getString(R.string.channel_backup), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.channel_backup_description)
            },
            NotificationChannel(DAILY, context.getString(R.string.channel_daily), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.channel_daily_description)
            },
            NotificationChannel(PERSISTENT_CALENDAR, context.getString(R.string.channel_persistent_calendar), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.channel_persistent_calendar_description)
                setShowBadge(false)
            },
        )
        manager.createNotificationChannels(channels)
    }
}
