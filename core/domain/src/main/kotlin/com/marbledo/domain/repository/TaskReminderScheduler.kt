package com.marbledo.domain.repository

import com.marbledo.domain.model.Task

interface TaskReminderScheduler {
    fun schedule(task: Task)
    fun cancel(taskId: Long)
}
