package com.marbledo.core.data.repository

import com.marbledo.core.data.db.CheckEntity
import com.marbledo.core.data.db.DebtEntity
import com.marbledo.core.data.db.MarbleDatabase
import androidx.room.withTransaction
import com.marbledo.domain.model.Check
import com.marbledo.domain.model.Debt
import com.marbledo.domain.repository.FinanceRepository
import com.marbledo.domain.repository.FinanceSnapshot
import com.marbledo.domain.util.FinanceAmountParser
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
            debts = debts.map { it.toDomain() },
            checks = checks.map { it.toDomain() },
        )
    }

    override suspend fun snapshot(): FinanceSnapshot = FinanceSnapshot(
        debts = debtDao.snapshot().map { it.toDomain() },
        checks = checkDao.snapshot().map { it.toDomain() },
    )

    override suspend fun saveDebt(debt: Debt): Long {
        val now = System.currentTimeMillis()
        val (tomanAmount, tomanCurrency) = taman(debt.amount, debt.currency)
        return debtDao.insert(
            DebtEntity(
                id = debt.id,
                title = debt.title.trim(),
                amount = tomanAmount,
                currency = tomanCurrency.trim().uppercase(),
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
        val (tomanAmount, tomanCurrency) = taman(check.amount, check.currency)
        return checkDao.insert(
            CheckEntity(
                id = check.id,
                title = check.title.trim(),
                amount = tomanAmount,
                currency = tomanCurrency.trim().uppercase(),
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

    private fun Debt.toEntity(): DebtEntity {
        val (tomanAmount, tomanCurrency) = taman(amount, currency)
        return DebtEntity(
        id = id,
        title = title.trim(),
        amount = tomanAmount,
        currency = tomanCurrency.trim().uppercase(),
        dueAtEpochMillis = dueAtEpochMillis,
        isPaid = isPaid,
        creditor = creditor.trim(),
        description = description.trim(),
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
        )
    }

    private fun Check.toEntity(): CheckEntity {
        val (tomanAmount, tomanCurrency) = taman(amount, currency)
        return CheckEntity(
        id = id,
        title = title.trim(),
        amount = tomanAmount,
        currency = tomanCurrency.trim().uppercase(),
        dueAtEpochMillis = dueAtEpochMillis,
        isCleared = isCleared,
        bankName = bankName.trim(),
        accountNumber = accountNumber.trim(),
        description = description.trim(),
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
        )
    }

    /**
     * Rial left the ledger when Toman became the only Iranian currency. Legacy rows are folded
     * into Toman at the fixed ten-to-one rate at this boundary — reads, saves and restored
     * backups all pass through here — so a stored Rial amount keeps its real value and the UI
     * never meets the old code again.
     */
    private fun taman(amount: Double, currency: String): Pair<Double, String> =
        FinanceAmountParser.taman(amount, currency)

    private fun DebtEntity.toDomain(): Debt {
        val (tomanAmount, tomanCurrency) = taman(amount, currency)
        return Debt(
        id = id,
        title = title,
        amount = tomanAmount,
        currency = tomanCurrency,
        dueAtEpochMillis = dueAtEpochMillis,
        isPaid = isPaid,
        creditor = creditor,
        description = description,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
        )
    }

    private fun CheckEntity.toDomain(): Check {
        val (tomanAmount, tomanCurrency) = taman(amount, currency)
        return Check(
        id = id,
        title = title,
        amount = tomanAmount,
        currency = tomanCurrency,
        dueAtEpochMillis = dueAtEpochMillis,
        isCleared = isCleared,
        bankName = bankName,
        accountNumber = accountNumber,
        description = description,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
        )
    }
}
