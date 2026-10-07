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
}
