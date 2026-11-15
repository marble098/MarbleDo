package com.marbledo.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface DebtDao {
    @Query("SELECT * FROM debts ORDER BY isPaid ASC, CASE WHEN dueAtEpochMillis IS NULL THEN 1 ELSE 0 END, dueAtEpochMillis ASC, id DESC")
    fun observeAll(): Flow<List<DebtEntity>>

    @Query("SELECT * FROM debts WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DebtEntity?

    @Query("SELECT * FROM debts ORDER BY isPaid ASC, CASE WHEN dueAtEpochMillis IS NULL THEN 1 ELSE 0 END, dueAtEpochMillis ASC, id DESC")
    suspend fun snapshot(): List<DebtEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(debt: DebtEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(debts: List<DebtEntity>)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertNewDebts(debts: List<DebtEntity>): List<Long>

    @Query("UPDATE debts SET isPaid = :paid, updatedAtEpochMillis = :updatedAt WHERE id = :id")
    suspend fun setPaid(id: Long, paid: Boolean, updatedAt: Long): Int

    @Query("DELETE FROM debts WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM debts")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(debts: List<DebtEntity>) {
        deleteAll()
        insertAll(debts)
    }
}

@Dao
interface CheckDao {
    @Query("SELECT * FROM checks ORDER BY isCleared ASC, CASE WHEN dueAtEpochMillis IS NULL THEN 1 ELSE 0 END, dueAtEpochMillis ASC, id DESC")
    fun observeAll(): Flow<List<CheckEntity>>

    @Query("SELECT * FROM checks WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CheckEntity?

    @Query("SELECT * FROM checks ORDER BY isCleared ASC, CASE WHEN dueAtEpochMillis IS NULL THEN 1 ELSE 0 END, dueAtEpochMillis ASC, id DESC")
    suspend fun snapshot(): List<CheckEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(check: CheckEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(checks: List<CheckEntity>)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertNewChecks(checks: List<CheckEntity>): List<Long>

    @Query("UPDATE checks SET isCleared = :cleared, updatedAtEpochMillis = :updatedAt WHERE id = :id")
    suspend fun setCleared(id: Long, cleared: Boolean, updatedAt: Long): Int

    @Query("DELETE FROM checks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM checks")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(checks: List<CheckEntity>) {
        deleteAll()
        insertAll(checks)
    }
}
