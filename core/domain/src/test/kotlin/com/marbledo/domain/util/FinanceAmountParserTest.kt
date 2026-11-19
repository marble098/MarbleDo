package com.marbledo.domain.util

import org.junit.jupiter.api.Assertions.assertEquals
import com.marbledo.domain.model.NumeralMode
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FinanceAmountParserTest {
    @Test
    fun `parses all supported digit sets and separators`() {
        assertEquals(1234567.5, FinanceAmountParser.parse("۱٬۲۳۴٬۵۶۷٫۵۰"))
        assertEquals(1234.56, FinanceAmountParser.parse("1.234,56"))
        assertEquals(1234.56, FinanceAmountParser.parse("1,234.56"))
        assertEquals(12.5, FinanceAmountParser.parse("١٢٫٥٠"))
    }

    @Test
    fun `groups typed amounts every three digits like banking apps`() {
        assertEquals("1,000,000", FinanceAmountParser.groupedDisplay("1000000", NumeralMode.LATIN).text)
        assertEquals("12,500", FinanceAmountParser.groupedDisplay("12500", NumeralMode.LATIN).text)
        assertEquals("999", FinanceAmountParser.groupedDisplay("999", NumeralMode.LATIN).text)
        assertEquals("1,000.25", FinanceAmountParser.groupedDisplay("1000.25", NumeralMode.LATIN).text)
        val persian = FinanceAmountParser.groupedDisplay("125000", NumeralMode.PERSIAN).text
        assertEquals(TextNormalizer.toPersianDigits("125,000").replace(',', '\u066C'), persian)
    }

    @Test
    fun `grouped display keeps a caret map from raw text to formatted text`() {
        val display = FinanceAmountParser.groupedDisplay("12500", NumeralMode.LATIN)
        assertEquals("12,500", display.text)
        assertEquals(0, display.boundary[0])
        assertEquals(6, display.boundary[5])
        display.boundary.toList().zipWithNext().forEach { (a, b) -> assertTrue(a <= b) }
    }

    @Test
    fun `a half-typed decimal is the whole number, not an error`() {
        assertEquals(12.0, FinanceAmountParser.parse("12."))
    }

    @Test
    fun `legacy Rial amounts fold into Toman at ten to one`() {
        val (amount, currency) = FinanceAmountParser.taman(10_000.0, "irr")
        assertEquals(1_000.0, amount)
        assertEquals("IRT", currency)
        assertEquals(5.0 to "EUR", FinanceAmountParser.taman(5.0, "EUR"))
    }

    @Test
    fun `rejects empty zero negative malformed and over-precision amounts`() {
        listOf("", "0", "-12", "1.234", "12..3", "one hundred").forEach { value ->
            assertNull(FinanceAmountParser.parse(value), "accepted '$value'")
        }
    }
}
