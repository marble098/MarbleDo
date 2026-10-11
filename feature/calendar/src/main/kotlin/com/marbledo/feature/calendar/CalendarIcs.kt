package com.marbledo.feature.calendar

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

data class CalendarExportEntry(
    val id: String,
    val title: String,
    val date: LocalDate,
    val description: String = "",
    val durationDays: Int = 1,
)

data class CalendarExportRequest(val filename: String, val entries: List<CalendarExportEntry>)

/** RFC 5545 all-day export. These are resolved dates, NOT an incorrect solar/lunar RRULE. */
object CalendarIcs {
    fun encode(entries: List<CalendarExportEntry>, generatedAt: Instant = Instant.now()): String {
        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC).format(generatedAt)
        val lines = buildList {
            add("BEGIN:VCALENDAR")
            add("VERSION:2.0")
            add("PRODID:-//MarbleDo//Occasions//EN")
            add("CALSCALE:GREGORIAN")
            entries.distinctBy { it.id to it.date }.sortedWith(compareBy({ it.date }, { it.id })).forEach { entry ->
                require(entry.durationDays in 1..31)
                val uid = UUID.nameUUIDFromBytes("${entry.id}|${entry.date}".toByteArray(Charsets.UTF_8))
                add("BEGIN:VEVENT")
                add("UID:$uid@marbledo.local")
                add("DTSTAMP:$stamp")
                add("DTSTART;VALUE=DATE:${entry.date.format(DateTimeFormatter.BASIC_ISO_DATE)}")
                add("DTEND;VALUE=DATE:${entry.date.plusDays(entry.durationDays.toLong()).format(DateTimeFormatter.BASIC_ISO_DATE)}")
                add("SUMMARY:${escape(entry.title)}")
                if (entry.description.isNotBlank()) add("DESCRIPTION:${escape(entry.description)}")
                add("TRANSP:TRANSPARENT")
                add("END:VEVENT")
            }
            add("END:VCALENDAR")
        }
        return lines.joinToString("\r\n", postfix = "\r\n") { fold(it) }
    }

    private fun escape(text: String): String = text.replace("\\", "\\\\")
        .replace("\r\n", "\n").replace("\r", "\n").replace("\n", "\\n")
        .replace(";", "\\;").replace(",", "\\,")
        .filter { it >= ' ' || it == '\t' }

    /** Fold at 75 UTF-8 octets (not UTF-16 characters); never split a Persian character or emoji. */
    private fun fold(line: String): String = buildString {
        var bytes = 0
        line.codePoints().toArray().forEach { point ->
            val char = String(Character.toChars(point))
            val size = char.toByteArray(Charsets.UTF_8).size
            if (bytes + size > 75) {
                append("\r\n ")
                bytes = 1
            }
            append(char)
            bytes += size
        }
    }
}
