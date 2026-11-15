package com.marbledo.domain.util

import java.math.BigDecimal
import java.math.RoundingMode

/** Parses an amount typed with Persian, Arabic or Latin digits and common grouping marks. */
object FinanceAmountParser {
    fun parse(raw: String): Double? {
        var normalized = TextNormalizer.digitsToLatin(raw)
            .trim()
            .replace("\u00A0", "")
            .replace(" ", "")
            .replace("٬", "")
            .replace("'", "")
            .replace("’", "")
            .replace("٫", ".")
        if (normalized.isBlank()) return null

        val lastDot = normalized.lastIndexOf('.')
        val lastComma = normalized.lastIndexOf(',')
        normalized = when {
            lastDot >= 0 && lastComma >= 0 && lastComma > lastDot ->
                normalized.replace(".", "").replace(',', '.')
            lastDot >= 0 && lastComma >= 0 -> normalized.replace(",", "")
            lastComma >= 0 -> {
                val commaCount = normalized.count { it == ',' }
                val decimals = normalized.length - lastComma - 1
                if (commaCount == 1 && decimals in 1..2) normalized.replace(',', '.')
                else normalized.replace(",", "")
            }
            else -> normalized
        }

        if (!normalized.matches(Regex("(?:\\d+(?:\\.\\d{0,2})?|\\.\\d{1,2})"))) return null
        val amount = runCatching { BigDecimal(normalized).setScale(2, RoundingMode.UNNECESSARY).toDouble() }.getOrNull()
            ?: return null
        return amount.takeIf { it.isFinite() && it > 0.0 }
    }
}
