package com.marbledo.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marbledo.domain.model.Task
import com.marbledo.domain.repository.TaskBackupScheduler
import com.marbledo.domain.repository.TaskReminderScheduler
import com.marbledo.domain.repository.TaskRepository
import com.marbledo.domain.util.TextNormalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

fun interface TaskActionSink {
    fun onTaskChanged(task: Task)
}

enum class TaskFilter { TODAY, TOMORROW, UPCOMING, ALL, COMPLETED, ARCHIVED }
enum class TaskSort { DUE_DATE, PRIORITY, TITLE, RECENT }

private data class TaskOrganization(
    val sortBy: TaskSort = TaskSort.DUE_DATE,
    val category: String? = null,
)

private sealed interface TaskUndoAction {
    data class Remove(val taskId: Long) : TaskUndoAction
    data class Restore(val task: Task, val removeTaskIds: List<Long> = emptyList()) : TaskUndoAction
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
    private var lastUndoAction: TaskUndoAction? = null

    val state = combine(repository.observeTasks(), filter, query, selected, organization) { tasks, currentFilter, search, ids, org ->
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val filtered = tasks.filter { task ->
            val date = task.dueAtEpochMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            val matchesFilter = when (currentFilter) {
                TaskFilter.TODAY -> !task.isArchived && !task.isCompleted && (date == null || date == today)
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
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksUiState())

    fun setFilter(value: TaskFilter) { filter.value = value }
    fun setQuery(value: String) { query.value = value }
    fun setSort(value: TaskSort) { organization.value = organization.value.copy(sortBy = value) }
    fun setCategory(value: String?) { organization.value = organization.value.copy(category = value?.takeIf { it.isNotBlank() }) }

    fun addTask(task: Task) {
        viewModelScope.launch {
            val id = repository.save(task)
            val saved = task.copy(id = id)
            lastUndoAction = TaskUndoAction.Remove(id)
            backupScheduler.scheduleDebounced()
            reminderScheduler.schedule(saved)
        }
    }

    fun editTask(task: Task) {
        viewModelScope.launch {
            val previous = repository.getTask(task.id)
            repository.save(task.copy(updatedAtEpochMillis = System.currentTimeMillis()))
            lastUndoAction = previous?.let { TaskUndoAction.Restore(it) }
            backupScheduler.scheduleDebounced()
            reminderScheduler.schedule(task)
        }
    }

    fun toggleCompleted(task: Task) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            var spawnedTaskId: Long? = null
            if (task.isCompleted) {
                repository.setCompleted(task.id, false)
                val reopened = task.copy(isCompleted = false, updatedAtEpochMillis = now)
                reminderScheduler.schedule(reopened)
            } else {
                repository.setCompleted(task.id, true)
                reminderScheduler.cancel(task.id)
                val repeat = task.recurrence
                val previousDue = task.dueAtEpochMillis
                if (repeat != null && previousDue != null) {
                    val nextDue = AndroidRecurrenceCalculator.nextAfter(previousDue, repeat)
                    val nextTask = task.copy(
                        id = 0,
                        dueAtEpochMillis = nextDue,
                        isCompleted = false,
                        isArchived = false,
                        createdAtEpochMillis = now,
                        updatedAtEpochMillis = now,
                    )
                    val nextId = repository.save(nextTask)
                    spawnedTaskId = nextId
                    reminderScheduler.schedule(nextTask.copy(id = nextId))
                }
            }
            lastUndoAction = TaskUndoAction.Restore(task, spawnedTaskId?.let { listOf(it) }.orEmpty())
            backupScheduler.scheduleDebounced()
        }
    }

    fun archive(task: Task) {
        viewModelScope.launch {
            repository.setArchived(task.id, true)
            lastUndoAction = TaskUndoAction.Restore(task)
            reminderScheduler.cancel(task.id)
            backupScheduler.scheduleDebounced()
        }
    }

    fun delete(task: Task) {
        viewModelScope.launch {
            repository.delete(task.id)
            lastUndoAction = TaskUndoAction.Restore(task)
            reminderScheduler.cancel(task.id)
            backupScheduler.scheduleDebounced()
        }
    }

    fun toggleSelection(taskId: Long) {
        selected.value = selected.value.toMutableSet().apply {
            if (!add(taskId)) remove(taskId)
        }
    }

    fun clearSelection() { selected.value = emptySet() }

    fun completeSelected() {
        val tasks = state.value.allTasks.filter { it.id in selected.value }
        tasks.forEach(::toggleCompleted)
        clearSelection()
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
            repository.observeTasks().first().forEach(reminderScheduler::schedule)
            backupScheduler.scheduleDebounced()
        }
    }

    fun undoLastChange() {
        val action = lastUndoAction ?: return
        lastUndoAction = null
        viewModelScope.launch {
            when (action) {
                is TaskUndoAction.Remove -> {
                    repository.delete(action.taskId)
                    reminderScheduler.cancel(action.taskId)
                }
                is TaskUndoAction.Restore -> {
                    action.removeTaskIds.forEach { taskId ->
                        repository.delete(taskId)
                        reminderScheduler.cancel(taskId)
                    }
                    val restored = action.task.copy(updatedAtEpochMillis = System.currentTimeMillis())
                    repository.save(restored)
                    reminderScheduler.schedule(restored)
                }
            }
            backupScheduler.scheduleDebounced()
        }
    }

    private fun priorityRank(task: Task): Int = when (task.priority) {
        com.marbledo.domain.model.TaskPriority.LOW -> 0
        com.marbledo.domain.model.TaskPriority.NORMAL -> 1
        com.marbledo.domain.model.TaskPriority.HIGH -> 2
        com.marbledo.domain.model.TaskPriority.URGENT -> 3
    }
}
