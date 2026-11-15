package com.marbledo.domain.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
    fun `rejects empty zero negative malformed and over-precision amounts`() {
        listOf("", "0", "-12", "1.234", "12..3", "one hundred").forEach { value ->
            assertNull(FinanceAmountParser.parse(value), "accepted '$value'")
        }
    }
}
