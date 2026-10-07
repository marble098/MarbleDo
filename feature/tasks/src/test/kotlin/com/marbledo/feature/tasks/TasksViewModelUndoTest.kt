package com.marbledo.feature.tasks

import com.marbledo.domain.model.Task
import com.marbledo.domain.repository.TaskBackupScheduler
import com.marbledo.domain.repository.TaskReminderScheduler
import com.marbledo.domain.repository.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TasksViewModelUndoTest {
    @Test
    fun `undoing a new task removes it instead of saving a duplicate`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = InMemoryTaskRepository()
            val reminders = RecordingReminderScheduler()
            val viewModel = TasksViewModel(repository, NoOpBackupScheduler, reminders)

            viewModel.addTask(Task(title = "New countdown"))
            advanceUntilIdle()
            assertEquals(1, repository.tasks.value.size)
            val addedId = repository.tasks.value.single().id

            viewModel.undoLastChange()
            advanceUntilIdle()

            assertTrue(repository.tasks.value.isEmpty())
            assertTrue(addedId in reminders.cancelled)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `undoing an edit restores the previous task contents`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = InMemoryTaskRepository()
            val original = Task(id = 8L, title = "Original")
            repository.save(original)
            val viewModel = TasksViewModel(repository, NoOpBackupScheduler, RecordingReminderScheduler())

            viewModel.editTask(original.copy(title = "Changed"))
            advanceUntilIdle()
            assertEquals("Changed", repository.tasks.value.single().title)

            viewModel.undoLastChange()
            advanceUntilIdle()

            assertEquals("Original", repository.tasks.value.single().title)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class InMemoryTaskRepository : TaskRepository {
        val tasks = MutableStateFlow<List<Task>>(emptyList())
        private var nextId = 1L

        override fun observeTasks(): Flow<List<Task>> = tasks

        override suspend fun getTask(id: Long): Task? = tasks.value.firstOrNull { it.id == id }

        override suspend fun save(task: Task): Long {
            val id = if (task.id == 0L) nextId++ else task.id
            tasks.value = tasks.value.filterNot { it.id == id } + task.copy(id = id)
            return id
        }

        override suspend fun setCompleted(id: Long, completed: Boolean) {
            tasks.value = tasks.value.map { if (it.id == id) it.copy(isCompleted = completed) else it }
        }

        override suspend fun setArchived(id: Long, archived: Boolean) {
            tasks.value = tasks.value.map { if (it.id == id) it.copy(isArchived = archived) else it }
        }

        override suspend fun delete(id: Long) {
            tasks.value = tasks.value.filterNot { it.id == id }
        }

        override suspend fun replaceAll(tasks: List<Task>) {
            this.tasks.value = tasks
        }
    }

    private class RecordingReminderScheduler : TaskReminderScheduler {
        val cancelled = mutableListOf<Long>()

        override fun schedule(task: Task) = Unit
        override fun cancel(taskId: Long) { cancelled += taskId }
    }

    private object NoOpBackupScheduler : TaskBackupScheduler {
        override fun scheduleDebounced() = Unit
    }
}
