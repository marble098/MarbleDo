package com.marbledo.feature.tasks

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** A task's date and optional time. A null [time] means the task is all-day. */
internal data class TaskSchedule(val date: LocalDate?, val time: LocalTime?) {
    val isEmpty: Boolean get() = date == null && time == null
}

/** Schedule currently stored in epoch form. Dates without a time are represented as all-day. */
internal fun scheduleOf(epochMillis: Long?, allDay: Boolean, zone: ZoneId): TaskSchedule {
    if (epochMillis == null) return TaskSchedule(null, null)
    val zoned = Instant.ofEpochMilli(epochMillis).atZone(zone)
    return TaskSchedule(zoned.toLocalDate(), if (allDay) null else zoned.toLocalTime())
}

/**
 * Due instant for a schedule. A date without a time is due at 09:00 so the morning reminder still fires.
 * A missing date defaults to [today].
 */
internal fun TaskSchedule.toDueMillis(zone: ZoneId, today: LocalDate): Long? {
    if (isEmpty) return null
    val day = date ?: today
    return ZonedDateTime.of(day, time ?: LocalTime.of(9, 0), zone).toInstant().toEpochMilli()
}

/**
 * Date to use when only a time was picked: today while that time is still ahead, otherwise tomorrow.
 * This matches the rule the parser applies to typed times.
 */
internal fun defaultDateFor(time: LocalTime?, now: ZonedDateTime): LocalDate =
    if (time == null || time.isAfter(now.toLocalTime())) now.toLocalDate() else now.toLocalDate().plusDays(1)

/** True for an open task whose due moment has passed. All-day tasks count as overdue only after their date. */
fun taskIsOverdue(
    isCompleted: Boolean,
    isArchived: Boolean,
    dueAtEpochMillis: Long?,
    isAllDay: Boolean,
    nowMillis: Long,
    zone: ZoneId,
): Boolean {
    if (isCompleted || isArchived) return false
    val due = dueAtEpochMillis ?: return false
    if (!isAllDay) return due < nowMillis
    val dueDate = Instant.ofEpochMilli(due).atZone(zone).toLocalDate()
    return dueDate.isBefore(Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate())
}
