package com.marbledo.core.data.backup

import com.marbledo.domain.model.AppSettings
import com.marbledo.domain.model.CalendarDisplayMode
import com.marbledo.domain.model.ChecklistItem
import com.marbledo.domain.model.RecurrenceRule
import com.marbledo.domain.model.RepeatCalendar
import com.marbledo.domain.model.RepeatFrequency
import com.marbledo.domain.model.StatusBarIconStyle
import com.marbledo.domain.model.Task
import com.marbledo.domain.model.TaskPriority
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream
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
            educationModeEnabled = true,
            thursdayWeekendEnabled = true,
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

    @Test
    fun `a backup file written with the retired status-icon ids still restores`() {
        // The enum constant behind "BOLD_DIGITS" was renamed; one renamed look must not make a user's
        // whole backup unreadable.
        val payload = """
            {"schemaVersion":2,"createdAtEpochMillis":1,"settings":{"languageTag":"fa","statusBarIconStyle":"BOLD_DIGITS"},"tasks":[],"habits":[],"habitEntries":[]}
        """.trimIndent()
        val decoded = BackupCodec.decode(legacyBackupFile(payload))

        assertEquals(StatusBarIconStyle.NUMERAL, decoded.settings.statusBarIconStyle)
    }

    /** Rebuilds the container the codec expects around raw envelope JSON, as an older release wrote it. */
    private fun legacyBackupFile(json: String): ByteArray {
        val payload = ByteArrayOutputStream().use { output ->
            GZIPOutputStream(output).use { it.write(json.toByteArray(Charsets.UTF_8)) }
            output.toByteArray()
        }
        val checksum = MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }
        return "MARBLEDO|2|plain|$checksum|-|-\n".toByteArray(Charsets.US_ASCII) + payload
    }
}
