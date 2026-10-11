package com.marbledo.feature.calendar

import java.time.Instant
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CalendarIcsTest {
    private val stamp = Instant.parse("2026-10-11T00:00:00Z")
    private val date = LocalDate.of(2026, 12, 31)

    @Test fun `all-day end is exclusive and UID is stable independent of locale and order`() {
        val event = CalendarExportEntry("id", "نوروز", date, durationDays = 7)
        val first = CalendarIcs.encode(listOf(event), stamp)
        assertTrue(first.contains("DTSTART;VALUE=DATE:20261231\r\n"))
        assertTrue(first.contains("DTEND;VALUE=DATE:20270107\r\n"))
        val second = CalendarIcs.encode(listOf(event.copy(title = "English")), stamp.plusSeconds(1))
        assertEquals(first.lineSequence().single { it.startsWith("UID:") }, second.lineSequence().single { it.startsWith("UID:") })
        assertFalse(first.contains("RRULE"))
        assertFalse(first.contains("VALARM"))
    }

    @Test fun `UTF8 folding preserves Persian and emoji and stays within 75 octets`() {
        val title = "یلدا و فرهنگ ایرانی 🌙 ".repeat(12)
        val encoded = CalendarIcs.encode(listOf(CalendarExportEntry("id", title, date)), stamp)
        encoded.split("\r\n").forEach { assertTrue(it.toByteArray(Charsets.UTF_8).size <= 75, it) }
        val unfolded = encoded.replace("\r\n ", "")
        assertTrue(unfolded.contains("SUMMARY:$title\r\n"))
        assertFalse(encoded.contains('\uFFFD'))
    }

    @Test fun `escape delimiters and CRLF injection and remove duplicates`() {
        val event = CalendarExportEntry("id", "a,b;c\\d\r\nBEGIN:VEVENT", date, "line1\nline2")
        val encoded = CalendarIcs.encode(listOf(event, event), stamp)
        assertEquals(1, encoded.lineSequence().count { it == "BEGIN:VEVENT" })
        assertTrue(encoded.contains("SUMMARY:a\\,b\\;c\\\\d\\nBEGIN:VEVENT"))
        assertTrue(encoded.contains("DESCRIPTION:line1\\nline2"))
        assertTrue(encoded.endsWith("END:VCALENDAR\r\n"))
    }

    @Test fun `same lunar occasion twice in a solar year has distinct UIDs`() {
        val event = CalendarExportEntry("eid", "Eid", date)
        val encoded = CalendarIcs.encode(listOf(event, event.copy(date = date.plusDays(354))), stamp)
        assertEquals(2, encoded.lineSequence().filter { it.startsWith("UID:") }.toSet().size)
    }
}
