package com.marbledo.domain.repository

import com.marbledo.domain.model.Check
import com.marbledo.domain.model.Debt
import kotlinx.coroutines.flow.Flow

/** A point-in-time view of the user's locally stored financial records. */
data class FinanceSnapshot(
    val debts: List<Debt> = emptyList(),
    val checks: List<Check> = emptyList(),
)

/** Offline-first storage for debts and cheque records. */
interface FinanceRepository {
    fun observe(): Flow<FinanceSnapshot>

    suspend fun snapshot(): FinanceSnapshot

    /** Inserts a new record when its id is zero, or replaces the matching record otherwise. */
    suspend fun saveDebt(debt: Debt): Long

    /** Inserts a new record when its id is zero, or replaces the matching record otherwise. */
    suspend fun saveCheck(check: Check): Long

    suspend fun setDebtPaid(id: Long, paid: Boolean)

    suspend fun setCheckCleared(id: Long, cleared: Boolean)

    suspend fun deleteDebt(id: Long)

    suspend fun deleteCheck(id: Long)

    /** Atomically replaces financial records when a user restores a backup. */
    suspend fun replaceAll(snapshot: FinanceSnapshot)
}
