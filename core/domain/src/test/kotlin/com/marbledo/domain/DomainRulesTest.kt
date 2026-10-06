package com.marbledo.domain

import com.marbledo.domain.model.NumeralMode
import com.marbledo.domain.model.RecurrenceRule
import com.marbledo.domain.model.RepeatCalendar
import com.marbledo.domain.model.RepeatFrequency
import com.marbledo.domain.util.RecurrenceCalculator
import com.marbledo.domain.util.TextNormalizer
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DomainRulesTest {
    @Test
    fun `Persian and Arabic letters and digits normalize for search`() {
        assertEquals("یادآوری قسط 25", TextNormalizer.searchKey("يادآوری قسط ۲۵").let(TextNormalizer::digitsToLatin))
        assertEquals("کتاب ها", TextNormalizer.searchKey("كتاب\u200cها"))
        assertEquals("۲۵", TextNormalizer.toPersianDigits("٢٥"))
    }

    @Test
    fun `numeral mode formats Latin Persian and Arabic-Indic digits`() {
        assertEquals("123", TextNormalizer.formatDigits("۱۲۳", NumeralMode.LATIN))
        assertEquals("۱۲۳", TextNormalizer.formatDigits("123", NumeralMode.PERSIAN))
        assertEquals("١٢٣", TextNormalizer.formatDigits("123", NumeralMode.ARABIC))
    }

    @Test
    fun `monthly repeat clamps at month end and keeps local time`() {
        val zone = ZoneId.of("Asia/Tehran")
        val previous = LocalDateTime.of(2024, 1, 31, 8, 30).atZone(zone).toInstant().toEpochMilli()
        val next = Instant.ofEpochMilli(
            RecurrenceCalculator.nextAfter(previous, RecurrenceRule(RepeatFrequency.MONTHLY), zone),
        ).atZone(zone)
        assertEquals(LocalDateTime.of(2024, 2, 29, 8, 30), next.toLocalDateTime())
    }

    @Test
    fun `weekly recurrence respects selected days and interval`() {
        val zone = ZoneId.of("UTC")
        val previous = LocalDateTime.of(2025, 1, 6, 9, 0).atZone(zone).toInstant().toEpochMilli()
        val next = Instant.ofEpochMilli(
            RecurrenceCalculator.nextAfter(
                previous,
                RecurrenceRule(RepeatFrequency.WEEKLY, interval = 1, daysOfWeek = setOf(4)),
                zone,
            ),
        ).atZone(zone)
        assertEquals(LocalDateTime.of(2025, 1, 9, 9, 0), next.toLocalDateTime())
    }

    @Test
    fun `Gregorian calculator refuses Persian recurrence rather than silently drifting`() {
        val previous = LocalDateTime.of(2025, 1, 1, 9, 0).atZone(ZoneId.of("UTC")).toInstant().toEpochMilli()
        assertThrows(IllegalArgumentException::class.java) {
            RecurrenceCalculator.nextAfter(
                previous,
                RecurrenceRule(RepeatFrequency.MONTHLY, monthDay = 15, calendar = RepeatCalendar.PERSIAN),
                ZoneId.of("UTC"),
            )
        }
    }
}
