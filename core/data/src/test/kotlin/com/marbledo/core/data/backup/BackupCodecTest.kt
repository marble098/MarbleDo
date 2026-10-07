package com.marbledo.core.data.backup

import com.marbledo.domain.model.AppSettings
import com.marbledo.domain.model.CalendarDisplayMode
import com.marbledo.domain.model.ChecklistItem
import com.marbledo.domain.model.RecurrenceRule
import com.marbledo.domain.model.RepeatCalendar
import com.marbledo.domain.model.RepeatFrequency
import com.marbledo.domain.model.Task
import com.marbledo.domain.model.TaskPriority
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BackupCodecTest {
    private val task = Task(
        id = 42,
        title = "Call home",
        description = "After work",
        dueAtEpochMillis = 1_800_000_000_000,
        priority = TaskPriority.HIGH,
        tags = listOf("family", "personal"),
        category = "Home",
        isPinned = true,
        checklist = listOf(ChecklistItem("item-1", "Ask about the trip", true)),
        recurrence = RecurrenceRule(RepeatFrequency.MONTHLY, interval = 2, calendar = RepeatCalendar.PERSIAN),
    )

    @Test
    fun `plain backup round trips tasks and settings`() {
        val settings = AppSettings(
            languageTag = "en",
            lunarOffsetDays = 1,
            reduceMotion = true,
            countdownCalendar = CalendarDisplayMode.ISLAMIC_CIVIL,
            taskCategories = listOf("Home", "Work"),
            persistentDateNotificationEnabled = true,
        )
        val encoded = BackupCodec.encode(listOf(task), settings)

        assertFalse(BackupCodec.isEncrypted(encoded))
        val decoded = BackupCodec.decode(encoded)
        assertEquals(listOf(task), decoded.tasks)
        assertEquals(settings, decoded.settings)
    }

    @Test
    fun `encrypted backup round trips and rejects a wrong passphrase`() {
        val password = "Marble-Safe-42".toCharArray()
        val encoded = BackupCodec.encode(listOf(task), AppSettings(), password)

        assertTrue(BackupCodec.isEncrypted(encoded))
        assertEquals(listOf(task), BackupCodec.decode(encoded, "Marble-Safe-42".toCharArray()).tasks)
        assertThrows(Exception::class.java) { BackupCodec.decode(encoded, "not-the-password".toCharArray()) }
    }

    @Test
    fun `backup corruption is detected before decoding`() {
        val encoded = BackupCodec.encode(listOf(task), AppSettings())
        val corrupted = encoded.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 0x01).toByte() }

        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(corrupted) }
    }

    @Test
    fun `encryption requires a nontrivial passphrase`() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.encode(listOf(task), AppSettings(), "short".toCharArray())
        }
    }
}
