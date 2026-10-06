package com.marbledo.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query(
        "SELECT * FROM tasks ORDER BY isArchived ASC, isCompleted ASC, isPinned DESC, " +
            "CASE WHEN dueAtEpochMillis IS NULL THEN 1 ELSE 0 END, dueAtEpochMillis ASC, " +
            "CASE priority WHEN 'URGENT' THEN 0 WHEN 'HIGH' THEN 1 WHEN 'NORMAL' THEN 2 ELSE 3 END ASC",
    )
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks ORDER BY isArchived ASC, isCompleted ASC, dueAtEpochMillis ASC")
    suspend fun snapshot(): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<TaskEntity>)

    @Query("UPDATE tasks SET isCompleted = :completed, updatedAtEpochMillis = :updatedAt WHERE id = :id")
    suspend fun setCompleted(id: Long, completed: Boolean, updatedAt: Long): Int

    @Query("UPDATE tasks SET isArchived = :archived, updatedAtEpochMillis = :updatedAt WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean, updatedAt: Long): Int

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(tasks: List<TaskEntity>) {
        deleteAll()
        insertAll(tasks)
    }
}
