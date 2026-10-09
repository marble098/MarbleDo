package com.marbledo.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marbledo.domain.model.Task
import com.marbledo.domain.model.TaskPriority
import com.marbledo.domain.repository.TaskBackupScheduler
import com.marbledo.domain.repository.TaskReminderScheduler
import com.marbledo.domain.repository.TaskRepository
import com.marbledo.domain.util.TextNormalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

fun interface TaskActionSink {
    fun onTaskChanged(task: Task)
}

enum class TaskFilter { TODAY, TOMORROW, UPCOMING, ALL, COMPLETED, ARCHIVED }
enum class TaskSort { DUE_DATE, PRIORITY, TITLE, RECENT }

/** What just happened, so the screen can confirm it with a short message and an undo action. */
enum class TaskChangeKind { ADDED, EDITED, COMPLETED, REOPENED, BULK_COMPLETED, ARCHIVED, UNARCHIVED, DELETED }

data class TaskChange(val kind: TaskChangeKind, val count: Int = 1)

private data class TaskOrganization(
    val sortBy: TaskSort = TaskSort.DUE_DATE,
    val category: String? = null,
)

private sealed interface TaskUndoAction {
    data class Remove(val taskIds: List<Long>) : TaskUndoAction
    data class Restore(val tasks: List<Task>, val removeTaskIds: List<Long> = emptyList()) : TaskUndoAction
}

data class TasksUiState(
    val allTasks: List<Task> = emptyList(),
    val visibleTasks: List<Task> = emptyList(),
    val filter: TaskFilter = TaskFilter.TODAY,
    val query: String = "",
    val sortBy: TaskSort = TaskSort.DUE_DATE,
    val selectedCategory: String? = null,
    val selectedIds: Set<Long> = emptySet(),
    val completedCount: Int = 0,
    val activeCount: Int = 0,
    val overdueCount: Int = 0,
)

class TasksViewModel(
    private val repository: TaskRepository,
    private val backupScheduler: TaskBackupScheduler,
    private val reminderScheduler: TaskReminderScheduler,
) : ViewModel() {
    private val filter = MutableStateFlow(TaskFilter.TODAY)
    private val query = MutableStateFlow("")
    private val selected = MutableStateFlow<Set<Long>>(emptySet())
    private val organization = MutableStateFlow(TaskOrganization())
    private val changeEvents = MutableSharedFlow<TaskChange>(extraBufferCapacity = 16)
    private var lastUndoAction: TaskUndoAction? = null

    /** Emits after every change the user made, so the screen can show a confirmation with undo. */
    val changes: SharedFlow<TaskChange> = changeEvents.asSharedFlow()

    val state = combine(repository.observeTasks(), filter, query, selected, organization) { tasks, currentFilter, search, ids, org ->
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val today = LocalDate.now(zone)
        val filtered = tasks.filter { task ->
            val date = task.dueAtEpochMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            val matchesFilter = when (currentFilter) {
                // Overdue open tasks stay in Today so they can be finished or rescheduled.
                TaskFilter.TODAY -> !task.isArchived && !task.isCompleted && (date == null || !date.isAfter(today))
                TaskFilter.TOMORROW -> !task.isArchived && !task.isCompleted && date == today.plusDays(1)
                TaskFilter.UPCOMING -> !task.isArchived && !task.isCompleted && date?.isAfter(today.plusDays(1)) == true
                TaskFilter.ALL -> !task.isArchived && !task.isCompleted
                TaskFilter.COMPLETED -> !task.isArchived && task.isCompleted
                TaskFilter.ARCHIVED -> task.isArchived
            }
            val needle = TextNormalizer.searchKey(search)
            val haystack = TextNormalizer.searchKey(
                listOf(task.title, task.description, task.project, task.category, task.tags.joinToString(" ")).joinToString(" "),
            )
            val matchesCategory = org.category == null || task.category == org.category
            matchesFilter && matchesCategory && (needle.isBlank() || haystack.contains(needle))
        }
        val sorted = filtered.sortedWith(Comparator { left, right ->
            if (left.isPinned != right.isPinned) return@Comparator right.isPinned.compareTo(left.isPinned)
            val primary = when (org.sortBy) {
                TaskSort.DUE_DATE -> compareValues(left.dueAtEpochMillis ?: Long.MAX_VALUE, right.dueAtEpochMillis ?: Long.MAX_VALUE)
                TaskSort.PRIORITY -> priorityRank(right).compareTo(priorityRank(left))
                TaskSort.TITLE -> TextNormalizer.searchKey(left.title).compareTo(TextNormalizer.searchKey(right.title))
                TaskSort.RECENT -> right.updatedAtEpochMillis.compareTo(left.updatedAtEpochMillis)
            }
            if (primary != 0) primary else left.id.compareTo(right.id)
        })
        TasksUiState(
            allTasks = tasks,
            visibleTasks = sorted,
            filter = currentFilter,
            query = search,
            sortBy = org.sortBy,
            selectedCategory = org.category,
            selectedIds = ids,
            completedCount = tasks.count { it.isCompleted && !it.isArchived },
            activeCount = tasks.count { !it.isCompleted && !it.isArchived },
            overdueCount = tasks.count { taskIsOverdue(it.isCompleted, it.isArchived, it.dueAtEpochMillis, it.isAllDay, now, zone) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksUiState())

    fun setFilter(value: TaskFilter) { filter.value = value }
    fun setQuery(value: String) { query.value = value }
    fun setSort(value: TaskSort) { organization.value = organization.value.copy(sortBy = value) }
    fun setCategory(value: String?) { organization.value = organization.value.copy(category = value?.takeIf { it.isNotBlank() }) }

    fun addTask(task: Task) {
        viewModelScope.launch {
            val id = repository.save(task)
            scheduleOrCancel(task.copy(id = id))
            lastUndoAction = TaskUndoAction.Remove(listOf(id))
            backupScheduler.scheduleDebounced()
            changeEvents.emit(TaskChange(TaskChangeKind.ADDED))
        }
    }

    fun editTask(task: Task) {
        viewModelScope.launch {
            val previous = repository.getTask(task.id)
            val saved = task.copy(updatedAtEpochMillis = System.currentTimeMillis())
            repository.save(saved)
            lastUndoAction = previous?.let { TaskUndoAction.Restore(listOf(it)) }
            scheduleOrCancel(saved)
            backupScheduler.scheduleDebounced()
            changeEvents.emit(TaskChange(TaskChangeKind.EDITED))
        }
    }

    fun toggleCompleted(task: Task) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (task.isCompleted) {
                repository.setCompleted(task.id, false)
                scheduleOrCancel(task.copy(isCompleted = false, updatedAtEpochMillis = now))
                lastUndoAction = TaskUndoAction.Restore(listOf(task))
                changeEvents.emit(TaskChange(TaskChangeKind.REOPENED))
            } else {
                val spawnedId = completeTask(task, now)
                lastUndoAction = TaskUndoAction.Restore(listOf(task), listOfNotNull(spawnedId))
                changeEvents.emit(TaskChange(TaskChangeKind.COMPLETED))
            }
            backupScheduler.scheduleDebounced()
        }
    }

    fun archive(task: Task) {
        viewModelScope.launch {
            repository.setArchived(task.id, true)
            reminderScheduler.cancel(task.id)
            lastUndoAction = TaskUndoAction.Restore(listOf(task))
            backupScheduler.scheduleDebounced()
            changeEvents.emit(TaskChange(TaskChangeKind.ARCHIVED))
        }
    }

    /** Moves an archived task back to the active lists and schedules its reminder again. */
    fun unarchive(task: Task) {
        viewModelScope.launch {
            repository.setArchived(task.id, false)
            val restored = task.copy(isArchived = false, updatedAtEpochMillis = System.currentTimeMillis())
            scheduleOrCancel(restored)
            lastUndoAction = TaskUndoAction.Restore(listOf(task))
            backupScheduler.scheduleDebounced()
            changeEvents.emit(TaskChange(TaskChangeKind.UNARCHIVED))
        }
    }

    fun delete(task: Task) {
        viewModelScope.launch {
            repository.delete(task.id)
            reminderScheduler.cancel(task.id)
            lastUndoAction = TaskUndoAction.Restore(listOf(task))
            backupScheduler.scheduleDebounced()
            changeEvents.emit(TaskChange(TaskChangeKind.DELETED))
        }
    }

    fun toggleSelection(taskId: Long) {
        selected.value = selected.value.toMutableSet().apply {
            if (!add(taskId)) remove(taskId)
        }
    }

    fun clearSelection() { selected.value = emptySet() }

    /** Completes every selected open task. Tasks that are already done are left alone, never reopened. */
    fun completeSelected() {
        val tasks = state.value.allTasks.filter { it.id in selected.value && !it.isCompleted && !it.isArchived }
        clearSelection()
        if (tasks.isEmpty()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val spawnedIds = tasks.mapNotNull { completeTask(it, now) }
            lastUndoAction = TaskUndoAction.Restore(tasks, spawnedIds)
            backupScheduler.scheduleDebounced()
            changeEvents.emit(TaskChange(TaskChangeKind.BULK_COMPLETED, tasks.size))
        }
    }

    fun restoreTasks(incoming: List<Task>, merge: Boolean) {
        viewModelScope.launch {
            if (!merge) {
                repository.replaceAll(incoming)
            } else {
                val existing = repository.observeTasks().first().associateBy(Task::id)
                incoming.forEach { candidate ->
                    val current = existing[candidate.id]
                    when {
                        candidate.id == 0L -> repository.save(candidate.copy(id = 0))
                        current == null -> repository.save(candidate)
                        candidate.updatedAtEpochMillis > current.updatedAtEpochMillis -> repository.save(candidate)
                    }
                }
            }
            repository.observeTasks().first().forEach(::scheduleOrCancel)
            backupScheduler.scheduleDebounced()
        }
    }

    fun undoLastChange() {
        val action = lastUndoAction ?: return
        lastUndoAction = null
        viewModelScope.launch {
            when (action) {
                is TaskUndoAction.Remove -> action.taskIds.forEach { taskId ->
                    repository.delete(taskId)
                    reminderScheduler.cancel(taskId)
                }
                is TaskUndoAction.Restore -> {
                    action.removeTaskIds.forEach { taskId ->
                        repository.delete(taskId)
                        reminderScheduler.cancel(taskId)
                    }
                    val now = System.currentTimeMillis()
                    action.tasks.forEach { task ->
                        val restored = task.copy(updatedAtEpochMillis = now)
                        repository.save(restored)
                        scheduleOrCancel(restored)
                    }
                }
            }
            backupScheduler.scheduleDebounced()
        }
    }

    /**
     * Marks [task] complete, cancels its reminder, and for repeating tasks creates the next occurrence.
     * Returns the id of that next occurrence, if one was created.
     */
    private suspend fun completeTask(task: Task, now: Long): Long? {
        repository.setCompleted(task.id, true)
        reminderScheduler.cancel(task.id)
        val repeat = task.recurrence ?: return null
        val previousDue = task.dueAtEpochMillis ?: return null
        val nextTask = task.copy(
            id = 0,
            dueAtEpochMillis = AndroidRecurrenceCalculator.nextAfter(previousDue, repeat),
            isCompleted = false,
            isArchived = false,
            checklist = task.checklist.map { it.copy(isDone = false) },
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
        val nextId = repository.save(nextTask)
        scheduleOrCancel(nextTask.copy(id = nextId))
        return nextId
    }

    /** Reminders only exist for open, active tasks with a due time. */
    private fun scheduleOrCancel(task: Task) {
        if (task.isArchived || task.isCompleted) reminderScheduler.cancel(task.id) else reminderScheduler.schedule(task)
    }

    private fun priorityRank(task: Task): Int = when (task.priority) {
        TaskPriority.LOW -> 0
        TaskPriority.NORMAL -> 1
        TaskPriority.HIGH -> 2
        TaskPriority.URGENT -> 3
    }
}
