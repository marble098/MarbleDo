package com.marbledo.feature.tasks

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmartTaskParserTest {
    private val zone = ZoneId.systemDefault()
    private val now = LocalDateTime.of(2026, 10, 6, 10, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `combines a relative date with an explicit hour and minute`() {
        val parsed = SmartTaskParser.parse("Review roadmap tomorrow at 14:30", now)

        assertNotNull(parsed)
        assertEquals("Review roadmap", parsed!!.task.title)
        assertEquals(
            LocalDateTime.of(2026, 10, 7, 14, 30).atZone(zone).toInstant().toEpochMilli(),
            parsed.task.dueAtEpochMillis,
        )
        assertTrue(parsed.recognizedDate)
        assertTrue(parsed.recognizedTime)
    }

    @Test
    fun `understands relative day counts and Persian digit times`() {
        val parsed = SmartTaskParser.parse("Send the invoice in 3 days ساعت ۱۴ و ۳۰ دقیقه", now)

        assertNotNull(parsed)
        assertEquals("Send the invoice", parsed!!.task.title)
        assertEquals(
            LocalDateTime.of(2026, 10, 9, 14, 30).atZone(zone).toInstant().toEpochMilli(),
            parsed.task.dueAtEpochMillis,
        )
    }

    @Test
    fun `a time that has passed moves to tomorrow`() {
        val parsed = SmartTaskParser.parse("Call the clinic at 08:15", now)

        assertNotNull(parsed)
        assertEquals("Call the clinic", parsed!!.task.title)
        assertEquals(
            LocalDateTime.of(2026, 10, 7, 8, 15).atZone(zone).toInstant().toEpochMilli(),
            parsed.task.dueAtEpochMillis,
        )
    }
    @Test
    fun `reads a spoken day distance and keeps the current clock`() {
        val parsed = SmartTaskParser.parseSchedule("ده روز بعد", now)

        assertNotNull(parsed)
        assertEquals("", parsed!!.title)
        assertTrue(parsed.recognizedDate)
        assertTrue(parsed.recognizedTime)
        assertEquals(
            LocalDateTime.of(2026, 10, 16, 10, 0).atZone(zone).toInstant().toEpochMilli(),
            parsed.dueAtEpochMillis,
        )
    }

    @Test
    fun `reads tomorrow at the same time of day`() {
        val parsed = SmartTaskParser.parseSchedule("فردا همین زمان", now)

        assertNotNull(parsed)
        assertEquals("", parsed!!.title)
        assertTrue(parsed.recognizedDate)
        assertTrue(parsed.recognizedTime)
        assertEquals(
            LocalDateTime.of(2026, 10, 7, 10, 0).atZone(zone).toInstant().toEpochMilli(),
            parsed.dueAtEpochMillis,
        )
    }

    @Test
    fun `reads spoken weeks and merges an explicit hour`() {
        val parsed = SmartTaskParser.parseSchedule("دو هفته بعد ساعت ۹ صبح", now)

        assertNotNull(parsed)
        assertEquals("", parsed!!.title)
        assertEquals(
            LocalDateTime.of(2026, 10, 20, 9, 0).atZone(zone).toInstant().toEpochMilli(),
            parsed.dueAtEpochMillis,
        )
    }

    @Test
    fun `a spoken minute distance can cross midnight`() {
        val lateNight = LocalDateTime.of(2026, 10, 6, 23, 40).atZone(zone).toInstant().toEpochMilli()
        val parsed = SmartTaskParser.parseSchedule("نیم ساعت بعد", lateNight)

        assertNotNull(parsed)
        assertEquals(
            LocalDateTime.of(2026, 10, 7, 0, 10).atZone(zone).toInstant().toEpochMilli(),
            parsed!!.dueAtEpochMillis,
        )
    }

    @Test
    fun `reads spoken months and years`() {
        val months = SmartTaskParser.parse("تمدید قرارداد سه ماه بعد", now)
        assertNotNull(months)
        assertEquals("تمدید قرارداد", months!!.task.title)
        assertEquals(
            LocalDateTime.of(2027, 1, 6, 10, 0).atZone(zone).toInstant().toEpochMilli(),
            months.task.dueAtEpochMillis,
        )

        val years = SmartTaskParser.parse("بازبینی سال بعد", now)
        assertNotNull(years)
        assertEquals("بازبینی", years!!.task.title)
    }

    @Test
    fun `reads the weekend and the half hour clock`() {
        val weekend = SmartTaskParser.parse("کوه آخر هفته", now)
        assertNotNull(weekend)
        assertEquals("کوه", weekend!!.task.title)
        assertEquals(
            LocalDateTime.of(2026, 10, 9, 9, 0).atZone(zone).toInstant().toEpochMilli(),
            weekend.task.dueAtEpochMillis,
        )

        val halfPast = SmartTaskParser.parse("جلسه ساعت ۳ و نیم بعد از ظهر", now)
        assertNotNull(halfPast)
        assertEquals("جلسه", halfPast!!.task.title)
        assertEquals(
            LocalDateTime.of(2026, 10, 6, 15, 30).atZone(zone).toInstant().toEpochMilli(),
            halfPast.task.dueAtEpochMillis,
        )
    }

    @Test
    fun `reads english spoken distances`() {
        val parsed = SmartTaskParser.parse("Renew the passport in 10 days", now)

        assertNotNull(parsed)
        assertEquals("Renew the passport", parsed!!.task.title)
        assertEquals(
            LocalDateTime.of(2026, 10, 16, 10, 0).atZone(zone).toInstant().toEpochMilli(),
            parsed.task.dueAtEpochMillis,
        )
    }
}

