package com.marbledo.domain.util

import com.marbledo.domain.model.Check
import com.marbledo.domain.model.Debt
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Semantic timing of an unsettled financial item, relative to the user's local day. */
enum class FinanceDueState {
    SETTLED,
    OVERDUE,
    DUE_TODAY,
    DUE_SOON,
    SCHEDULED,
    UNDATED,
}

data class FinanceInsights(
    /** Unpaid debts, grouped by currency. Different currencies are never added together. */
    val outstandingDebts: Map<String, BigDecimal>,
    /** Uncleared cheques, grouped by currency. Different currencies are never added together. */
    val pendingChecks: Map<String, BigDecimal>,
    val overdueCount: Int,
    val dueTodayCount: Int,
    val dueSoonCount: Int,
    val undatedCount: Int,
    val openDebtCount: Int,
    val openCheckCount: Int,
    /** Money already past its due date, grouped by currency. */
    val overdueAmounts: Map<String, BigDecimal>,
    /** Items settled since the first day of the current local month. */
    val settledThisMonthCount: Int,
    /** What was settled this month, grouped by currency, counted at the moment of settling. */
    val settledThisMonthAmounts: Map<String, BigDecimal>,
) {
    companion object {
        fun build(
            debts: List<Debt>,
            checks: List<Check>,
            today: LocalDate = LocalDate.now(),
            zone: ZoneId = ZoneId.systemDefault(),
        ): FinanceInsights {
            val openDebts = debts.filterNot { it.isPaid }
            val openChecks = checks.filterNot { it.isCleared }
            val allOpen = openDebts.map { it.dueAtEpochMillis } + openChecks.map { it.dueAtEpochMillis }
            val states = allOpen.map { dueAt -> dueState(dueAt, settled = false, today, zone) }

            val overduePairs = openDebts.filter { dueState(it.dueAtEpochMillis, false, today, zone) == FinanceDueState.OVERDUE }
                .map { it.currency to it.amount } +
                openChecks.filter { dueState(it.dueAtEpochMillis, false, today, zone) == FinanceDueState.OVERDUE }
                    .map { it.currency to it.amount }

            // "This month" is the local calendar month of the user's today; an item counts where
            // it was settled (its last update), which is what the ledger's timestamp records.
            val monthStart = today.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val monthEnd = today.withDayOfMonth(1).plusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val settledDebtPairs = debts.filter { it.isPaid && it.updatedAtEpochMillis in monthStart until monthEnd }
                .map { it.currency to it.amount }
            val settledCheckPairs = checks.filter { it.isCleared && it.updatedAtEpochMillis in monthStart until monthEnd }
                .map { it.currency to it.amount }
            val settledPairs = settledDebtPairs + settledCheckPairs

            return FinanceInsights(
                outstandingDebts = totalsByCurrency(openDebts.map { it.currency to it.amount }),
                pendingChecks = totalsByCurrency(openChecks.map { it.currency to it.amount }),
                overdueCount = states.count { it == FinanceDueState.OVERDUE },
                dueTodayCount = states.count { it == FinanceDueState.DUE_TODAY },
                dueSoonCount = states.count { it == FinanceDueState.DUE_SOON },
                undatedCount = states.count { it == FinanceDueState.UNDATED },
                openDebtCount = openDebts.size,
                openCheckCount = openChecks.size,
                overdueAmounts = totalsByCurrency(overduePairs),
                settledThisMonthCount = settledPairs.size,
                settledThisMonthAmounts = totalsByCurrency(settledPairs),
            )
        }

        fun dueState(
            dueAtEpochMillis: Long?,
            settled: Boolean,
            today: LocalDate = LocalDate.now(),
            zone: ZoneId = ZoneId.systemDefault(),
        ): FinanceDueState {
            if (settled) return FinanceDueState.SETTLED
            val due = dueAtEpochMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
                ?: return FinanceDueState.UNDATED
            return when {
                due.isBefore(today) -> FinanceDueState.OVERDUE
                due == today -> FinanceDueState.DUE_TODAY
                !due.isAfter(today.plusDays(7)) -> FinanceDueState.DUE_SOON
                else -> FinanceDueState.SCHEDULED
            }
        }

        private fun totalsByCurrency(amounts: List<Pair<String, Double>>): Map<String, BigDecimal> =
            amounts
                .groupBy({ it.first.ifBlank { "—" } }, { it.second })
                .toSortedMap()
                .mapValues { (_, values) -> values.fold(BigDecimal.ZERO) { total, amount -> total + BigDecimal.valueOf(amount) } }
    }
}
