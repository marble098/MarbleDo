package com.marbledo.core.data.repository

import com.marbledo.core.data.db.CheckEntity
import com.marbledo.core.data.db.DebtEntity
import com.marbledo.core.data.db.MarbleDatabase
import androidx.room.withTransaction
import com.marbledo.domain.model.Check
import com.marbledo.domain.model.Debt
import com.marbledo.domain.repository.FinanceRepository
import com.marbledo.domain.repository.FinanceSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class RoomFinanceRepository(
    private val database: MarbleDatabase,
) : FinanceRepository {
    private val debtDao get() = database.debtDao()
    private val checkDao get() = database.checkDao()

    override fun observe(): Flow<FinanceSnapshot> = combine(
        debtDao.observeAll(),
        checkDao.observeAll(),
    ) { debts, checks ->
        FinanceSnapshot(
            debts = debts.map(DebtEntity::toDomain),
            checks = checks.map(CheckEntity::toDomain),
        )
    }

    override suspend fun snapshot(): FinanceSnapshot = FinanceSnapshot(
        debts = debtDao.snapshot().map(DebtEntity::toDomain),
        checks = checkDao.snapshot().map(CheckEntity::toDomain),
    )

    override suspend fun saveDebt(debt: Debt): Long {
        val now = System.currentTimeMillis()
        return debtDao.insert(
            DebtEntity(
                id = debt.id,
                title = debt.title.trim(),
                amount = debt.amount,
                currency = debt.currency.trim().uppercase(),
                dueAtEpochMillis = debt.dueAtEpochMillis,
                isPaid = debt.isPaid,
                creditor = debt.creditor.trim(),
                description = debt.description.trim(),
                createdAtEpochMillis = debt.createdAtEpochMillis,
                updatedAtEpochMillis = now,
            ),
        )
    }

    override suspend fun saveCheck(check: Check): Long {
        val now = System.currentTimeMillis()
        return checkDao.insert(
            CheckEntity(
                id = check.id,
                title = check.title.trim(),
                amount = check.amount,
                currency = check.currency.trim().uppercase(),
                dueAtEpochMillis = check.dueAtEpochMillis,
                isCleared = check.isCleared,
                bankName = check.bankName.trim(),
                accountNumber = check.accountNumber.trim(),
                description = check.description.trim(),
                createdAtEpochMillis = check.createdAtEpochMillis,
                updatedAtEpochMillis = now,
            ),
        )
    }

    override suspend fun setDebtPaid(id: Long, paid: Boolean) {
        debtDao.setPaid(id, paid, System.currentTimeMillis())
    }

    override suspend fun setCheckCleared(id: Long, cleared: Boolean) {
        checkDao.setCleared(id, cleared, System.currentTimeMillis())
    }

    override suspend fun deleteDebt(id: Long) = debtDao.delete(id)

    override suspend fun deleteCheck(id: Long) = checkDao.delete(id)

    override suspend fun replaceAll(snapshot: FinanceSnapshot) = database.withTransaction {
        debtDao.replaceAll(snapshot.debts.map { it.toEntity() })
        checkDao.replaceAll(snapshot.checks.map { it.toEntity() })
    }

    private fun Debt.toEntity(): DebtEntity = DebtEntity(
        id = id,
        title = title.trim(),
        amount = amount,
        currency = currency.trim().uppercase(),
        dueAtEpochMillis = dueAtEpochMillis,
        isPaid = isPaid,
        creditor = creditor.trim(),
        description = description.trim(),
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )

    private fun Check.toEntity(): CheckEntity = CheckEntity(
        id = id,
        title = title.trim(),
        amount = amount,
        currency = currency.trim().uppercase(),
        dueAtEpochMillis = dueAtEpochMillis,
        isCleared = isCleared,
        bankName = bankName.trim(),
        accountNumber = accountNumber.trim(),
        description = description.trim(),
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )

    private fun DebtEntity.toDomain(): Debt = Debt(
        id = id,
        title = title,
        amount = amount,
        currency = currency,
        dueAtEpochMillis = dueAtEpochMillis,
        isPaid = isPaid,
        creditor = creditor,
        description = description,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )

    private fun CheckEntity.toDomain(): Check = Check(
        id = id,
        title = title,
        amount = amount,
        currency = currency,
        dueAtEpochMillis = dueAtEpochMillis,
        isCleared = isCleared,
        bankName = bankName,
        accountNumber = accountNumber,
        description = description,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}
