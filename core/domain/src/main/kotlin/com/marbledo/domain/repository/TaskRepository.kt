package com.marbledo.domain.repository

import com.marbledo.domain.model.Task
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    fun observeTasks(): Flow<List<Task>>
    suspend fun getTask(id: Long): Task?
    suspend fun save(task: Task): Long
    suspend fun setCompleted(id: Long, completed: Boolean)
    suspend fun setArchived(id: Long, archived: Boolean)
    suspend fun delete(id: Long)
    suspend fun replaceAll(tasks: List<Task>)
}

interface TaskBackupScheduler {
    fun scheduleDebounced()
}
