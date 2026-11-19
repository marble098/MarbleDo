package com.marbledo.domain.util

import com.marbledo.domain.model.Check
import com.marbledo.domain.model.Debt
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class FinanceInsightsTest {
    private val zone = ZoneId.of("Asia/Tehran")
    private val today = LocalDate.of(2026, 11, 15)

    @Test
    fun `open totals remain separated by currency and settled records are excluded`() {
        val insights = FinanceInsights.build(
            debts = listOf(
                Debt(id = 1, title = "Rent", amount = 125.50, currency = "EUR"),
                Debt(id = 2, title = "Repair", amount = 10_000.0, currency = "IRR"),
                Debt(id = 3, title = "Paid loan", amount = 500.0, currency = "EUR", isPaid = true),
            ),
            checks = listOf(Check(id = 4, title = "Rent cheque", amount = 20.0, currency = "EUR")),
            today = today,
            zone = zone,
        )

        assertEquals(mapOf("EUR" to BigDecimal("125.5"), "IRR" to BigDecimal("10000.0")), insights.outstandingDebts)
        assertEquals(mapOf("EUR" to BigDecimal("20.0")), insights.pendingChecks)
        assertEquals(2, insights.openDebtCount)
        assertEquals(1, insights.openCheckCount)
    }

    @Test
    fun `due-state counts distinguish overdue today coming week and undated obligations`() {
        fun due(days: Long) = today.plusDays(days).atStartOfDay(zone).toInstant().toEpochMilli()
        val insights = FinanceInsights.build(
            debts = listOf(
                Debt(title = "Late", amount = 10.0, dueAtEpochMillis = due(-1)),
                Debt(title = "Today", amount = 20.0, dueAtEpochMillis = due(0)),
                Debt(title = "Next week", amount = 30.0, dueAtEpochMillis = due(7)),
                Debt(title = "No date", amount = 40.0),
                Debt(title = "Next month", amount = 50.0, dueAtEpochMillis = due(8)),
                Debt(title = "Settled", amount = 60.0, dueAtEpochMillis = due(-5), isPaid = true),
            ),
            checks = emptyList(),
            today = today,
            zone = zone,
        )

        assertEquals(1, insights.overdueCount)
        assertEquals(1, insights.dueTodayCount)
        assertEquals(1, insights.dueSoonCount)
        assertEquals(1, insights.undatedCount)
    }

    @Test
    fun `overdue money and this month's settlements are tracked per currency`() {
        fun due(days: Long) = today.plusDays(days).atStartOfDay(zone).toInstant().toEpochMilli()
        val monthStart = today.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val insights = FinanceInsights.build(
            debts = listOf(
                Debt(title = "Late toman", amount = 60_000.0, currency = "IRT", dueAtEpochMillis = due(-3)),
                Debt(title = "Late euro", amount = 40.0, currency = "EUR", dueAtEpochMillis = due(-1)),
                Debt(title = "Open", amount = 10.0, currency = "IRT", dueAtEpochMillis = due(10)),
                Debt(title = "Settled now", amount = 500.0, currency = "IRT", isPaid = true, updatedAtEpochMillis = monthStart + 1_000),
                Debt(title = "Settled earlier", amount = 700.0, currency = "EUR", isPaid = true, updatedAtEpochMillis = monthStart - 1_000),
            ),
            checks = listOf(
                Check(title = "Cleared cheque", amount = 25.0, currency = "EUR", isCleared = true, updatedAtEpochMillis = monthStart + 2_000),
                Check(title = "Late cheque", amount = 9.0, currency = "EUR", dueAtEpochMillis = due(-2)),
            ),
            today = today,
            zone = zone,
        )

        assertEquals(mapOf("IRT" to BigDecimal("60000.0"), "EUR" to BigDecimal("49.0")), insights.overdueAmounts)
        assertEquals(2, insights.settledThisMonthCount)
        assertEquals(mapOf("IRT" to BigDecimal("500.0"), "EUR" to BigDecimal("25.0")), insights.settledThisMonthAmounts)
    }

    @Test
    fun `due state treats the local calendar day as the boundary`() {
        val endOfToday = today.plusDays(1).atStartOfDay(zone).toInstant().minusMillis(1).toEpochMilli()
        assertEquals(FinanceDueState.DUE_TODAY, FinanceInsights.dueState(endOfToday, settled = false, today, zone))
        assertEquals(FinanceDueState.SETTLED, FinanceInsights.dueState(null, settled = true, today, zone))
        assertNotEquals(FinanceDueState.OVERDUE, FinanceInsights.dueState(null, settled = false, today, zone))
    }
}
