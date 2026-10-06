package com.marbledo.core.data.repository

import com.marbledo.core.data.db.TaskDao
import com.marbledo.core.data.db.TaskEntity
import com.marbledo.domain.model.ChecklistItem
import com.marbledo.domain.model.RecurrenceRule
import com.marbledo.domain.model.Task
import com.marbledo.domain.model.TaskPriority
import com.marbledo.domain.repository.TaskRepository
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomTaskRepository(
    private val dao: TaskDao,
) : TaskRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override fun observeTasks(): Flow<List<Task>> = dao.observeAll().map { rows -> rows.map(::toDomain) }

    override suspend fun getTask(id: Long): Task? = dao.getById(id)?.let(::toDomain)

    override suspend fun save(task: Task): Long {
        val now = System.currentTimeMillis()
        return dao.insert(task.toEntity(updatedAt = now))
    }

    override suspend fun setCompleted(id: Long, completed: Boolean) {
        dao.setCompleted(id, completed, System.currentTimeMillis())
    }

    override suspend fun setArchived(id: Long, archived: Boolean) {
        dao.setArchived(id, archived, System.currentTimeMillis())
    }

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun replaceAll(tasks: List<Task>) {
        dao.replaceAll(tasks.map { it.toEntity(updatedAt = it.updatedAtEpochMillis) })
    }

    suspend fun snapshot(): List<Task> = dao.snapshot().map(::toDomain)

    private fun Task.toEntity(updatedAt: Long) = TaskEntity(
        id = id,
        title = title.trim(),
        description = description,
        dueAtEpochMillis = dueAtEpochMillis,
        isAllDay = isAllDay,
        priority = priority.name,
        colorArgb = colorArgb,
        tagsJson = json.encodeToString(ListSerializer(String.serializer()), tags),
        project = project,
        category = category,
        isPinned = isPinned,
        checklistJson = json.encodeToString(ListSerializer(ChecklistItem.serializer()), checklist),
        link = link,
        attachmentUri = attachmentUri,
        recurrenceJson = recurrence?.let { json.encodeToString(RecurrenceRule.serializer(), it) },
        countdownTheme = countdownTheme,
        isCompleted = isCompleted,
        isArchived = isArchived,
        focusMinutes = focusMinutes,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAt,
    )

    private fun toDomain(entity: TaskEntity): Task = Task(
        id = entity.id,
        title = entity.title,
        description = entity.description,
        dueAtEpochMillis = entity.dueAtEpochMillis,
        isAllDay = entity.isAllDay,
        priority = runCatching { TaskPriority.valueOf(entity.priority) }.getOrDefault(TaskPriority.NORMAL),
        colorArgb = entity.colorArgb,
        tags = decodeList(entity.tagsJson, String.serializer()),
        project = entity.project,
        category = entity.category,
        isPinned = entity.isPinned,
        checklist = decodeList(entity.checklistJson, ChecklistItem.serializer()),
        link = entity.link,
        attachmentUri = entity.attachmentUri,
        recurrence = entity.recurrenceJson?.let {
            runCatching { json.decodeFromString(RecurrenceRule.serializer(), it) }.getOrNull()
        },
        countdownTheme = entity.countdownTheme,
        isCompleted = entity.isCompleted,
        isArchived = entity.isArchived,
        focusMinutes = entity.focusMinutes,
        createdAtEpochMillis = entity.createdAtEpochMillis,
        updatedAtEpochMillis = entity.updatedAtEpochMillis,
    )

    private fun <T> decodeList(raw: String, serializer: kotlinx.serialization.KSerializer<T>): List<T> =
        runCatching { json.decodeFromString(ListSerializer(serializer), raw) }.getOrDefault(emptyList())
}
