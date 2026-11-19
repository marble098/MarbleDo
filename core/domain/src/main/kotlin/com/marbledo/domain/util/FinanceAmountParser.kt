package com.marbledo.domain.util

import com.marbledo.domain.model.NumeralMode
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
        // A half-typed "12." is part of typing "12.5"; it is the whole number, not an error.
        normalized = normalized.removeSuffix(".")

        if (!normalized.matches(Regex("(?:\\d+(?:\\.\\d{0,2})?|\\.\\d{1,2})"))) return null
        val amount = runCatching { BigDecimal(normalized).setScale(2, RoundingMode.UNNECESSARY).toDouble() }.getOrNull()
            ?: return null
        return amount.takeIf { it.isFinite() && it > 0.0 }
    }

    /**
     * Formats a half-typed amount the way banking apps do: every three digits of the integer
     * part gain a grouping mark, counted from the left edge of the number, and the digits are
     * rendered in the user's [numeralMode]. Decimal digits are never grouped.
     *
     * This is a pure display pass over text that is kept raw for [parse]: no character is added
     * to or removed from the raw input, so the text field's caret keeps working. [GroupedAmountDisplay.boundary]
     * maps each raw character boundary onto its position in the formatted text for exactly that
     * reason.
     */
    fun groupedDisplay(raw: String, numeralMode: NumeralMode): GroupedAmountDisplay {
        val grouped = TextNormalizer.formatDigits(raw, numeralMode)
        val groupingMark = if (numeralMode == NumeralMode.LATIN) ',' else '٬'
        val decimalIndex = grouped.indexOfFirst { it == '.' || it == '٫' }
        val integerEnd = if (decimalIndex >= 0) decimalIndex else grouped.length
        val integerDigits = grouped.take(integerEnd).count { it.isDigit() }
        val boundary = IntArray(grouped.length + 1)
        val out = StringBuilder(grouped.length + integerDigits / 3 + 1)
        var digitIndex = 0
        grouped.forEachIndexed { index, char ->
            if (char.isDigit() && index < integerEnd) {
                if (digitIndex > 0 && (integerDigits - digitIndex) % 3 == 0) out.append(groupingMark)
                digitIndex++
            }
            out.append(char)
            boundary[index + 1] = out.length
        }
        return GroupedAmountDisplay(out.toString(), boundary)
    }

    /** Normalizes a stored legacy code: Rial amounts become Toman at the fixed 10 Rial = 1 Toman rate. */
    fun taman(amount: Double, currency: String): Pair<Double, String> =
        if (currency.trim().uppercase() == "IRR") {
            BigDecimal.valueOf(amount).divide(BigDecimal.TEN).setScale(2, RoundingMode.HALF_UP).toDouble() to "IRT"
        } else {
            amount to currency
        }
}

/**
 * The formatted look of a typed amount plus the raw-boundary map a [androidx.compose.ui.text.input.VisualTransformation]
 * needs to keep the caret honest while characters are inserted for display.
 */
data class GroupedAmountDisplay(
    val text: String,
    /** `boundary[k]` is the index in [text] right after its first `k` raw characters. */
    val boundary: IntArray,
) {
    override fun equals(other: Any?): Boolean =
        other is GroupedAmountDisplay && other.text == text && other.boundary.contentEquals(boundary)

    override fun hashCode(): Int = 31 * text.hashCode() + boundary.contentHashCode()
}
