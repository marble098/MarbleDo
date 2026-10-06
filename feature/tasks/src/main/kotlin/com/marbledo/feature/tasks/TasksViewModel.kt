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

data class TasksUiState(
    val allTasks: List<Task> = emptyList(),
    val visibleTasks: List<Task> = emptyList(),
    val filter: TaskFilter = TaskFilter.TODAY,
    val query: String = "",
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
    private var lastChanged: Task? = null

    val state = combine(repository.observeTasks(), filter, query, selected) { tasks, currentFilter, search, ids ->
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
                listOf(task.title, task.description, task.project, task.tags.joinToString(" ")).joinToString(" "),
            )
            matchesFilter && (needle.isBlank() || haystack.contains(needle))
        }
        TasksUiState(
            allTasks = tasks,
            visibleTasks = filtered,
            filter = currentFilter,
            query = search,
            selectedIds = ids,
            completedCount = tasks.count { it.isCompleted && !it.isArchived },
            activeCount = tasks.count { !it.isCompleted && !it.isArchived },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksUiState())

    fun setFilter(value: TaskFilter) { filter.value = value }
    fun setQuery(value: String) { query.value = value }

    fun addTask(task: Task) {
        viewModelScope.launch {
            val id = repository.save(task)
            val saved = task.copy(id = id)
            lastChanged = saved
            backupScheduler.scheduleDebounced()
            reminderScheduler.schedule(saved)
        }
    }

    fun editTask(task: Task) {
        viewModelScope.launch {
            repository.save(task.copy(updatedAtEpochMillis = System.currentTimeMillis()))
            lastChanged = task
            backupScheduler.scheduleDebounced()
            reminderScheduler.schedule(task)
        }
    }

    fun toggleCompleted(task: Task) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (task.isCompleted) {
                repository.setCompleted(task.id, false)
                val reopened = task.copy(isCompleted = false, updatedAtEpochMillis = now)
                lastChanged = reopened
                reminderScheduler.schedule(reopened)
            } else {
                repository.setCompleted(task.id, true)
                lastChanged = task.copy(isCompleted = true, updatedAtEpochMillis = now)
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
                    reminderScheduler.schedule(nextTask.copy(id = nextId))
                }
            }
            backupScheduler.scheduleDebounced()
        }
    }

    fun archive(task: Task) {
        viewModelScope.launch {
            repository.setArchived(task.id, true)
            reminderScheduler.cancel(task.id)
            backupScheduler.scheduleDebounced()
        }
    }

    fun delete(task: Task) {
        viewModelScope.launch {
            repository.delete(task.id)
            reminderScheduler.cancel(task.id)
            lastChanged = task
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
        val task = lastChanged ?: return
        lastChanged = null
        editTask(task)
    }
}
