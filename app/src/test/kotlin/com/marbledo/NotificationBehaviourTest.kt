package com.marbledo

import com.marbledo.domain.model.AppSettings
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The behaviour the notification redesign promises, checked where it is arithmetic rather than
 * pixels: the three snooze moments, the split channels, and the two contracts the shade relies on
 * (a channel id per kind of interruption, and three actions at most with an icon each).
 *
 * Android-free on purpose: these run on the plain JVM unit-test source set, where the notification
 * classes that touch `Context` cannot be instantiated.
 */
class NotificationBehaviourTest {

    /**
     * Gradle runs a module's tests with the module directory as the working directory, but an IDE
     * may not; every path here goes through [resolve] so the same file is found either way. This is
     * the pattern `NotificationLayoutContractTest` already uses.
     */
    private fun resolve(vararg candidates: String): File = candidates
        .map(::File)
        .firstOrNull(File::exists)
        ?: File(candidates.first())

    private fun read(name: String): String = resolve(
        "src/main/kotlin/com/marbledo/app/notifications/$name",
        "../app/src/main/kotlin/com/marbledo/app/notifications/$name",
    ).readText(Charsets.UTF_8)

    private fun readApp(vararg candidates: String): String = resolve(*candidates).readText(Charsets.UTF_8)

    // ---- snooze ---------------------------------------------------------------------------

    /**
     * The three choices are pinned here as behaviour, not as strings: an hour, the 18:00 that ends
     * the working day, or 08:00 tomorrow. Reimplemented in the test because the production helper
     * is `internal` to the app module and needs a `Context` for its labels — the *moments* are what
     * break silently, and they are pure `ZonedDateTime` arithmetic.
     */
    private fun choices(now: ZonedDateTime): List<Long> {
        val todayEvening = now.withHour(18).withMinute(0).withSecond(0).withNano(0)
        val evening = if (todayEvening.isAfter(now.plusMinutes(30))) todayEvening else now.plusHours(1)
        return listOf(
            now.plusHours(1).toInstant().toEpochMilli(),
            evening.toInstant().toEpochMilli(),
            now.toLocalDate().plusDays(1).atTime(8, 0).atZone(now.zone).toInstant().toEpochMilli(),
        )
    }

    @Test
    fun `a morning snooze offers one hour, this evening and tomorrow morning`() {
        val now = ZonedDateTime.of(2026, 11, 19, 9, 0, 0, 0, ZoneId.of("Asia/Tehran"))
        val (hour, evening, tomorrow) = choices(now)
        assertEquals(now.plusHours(1).toInstant().toEpochMilli(), hour)
        // 09:00 -> this evening is the 18:00 of the same day, not "in an hour".
        assertEquals(ZonedDateTime.of(2026, 11, 19, 18, 0, 0, 0, now.zone).toInstant().toEpochMilli(), evening)
        assertEquals(ZonedDateTime.of(2026, 11, 20, 8, 0, 0, 0, now.zone).toInstant().toEpochMilli(), tomorrow)
    }

    @Test
    fun `a late-night snooze never schedules a reminder for the middle of the night`() {
        // 23:40 is exactly when people swipe a reminder away; "an hour" must not become 00:40 and
        // "this evening" must not become the 18:00 that already happened.
        val now = ZonedDateTime.of(2026, 11, 19, 23, 40, 0, 0, ZoneId.of("Asia/Tehran"))
        val (hour, evening, tomorrow) = choices(now)
        assertEquals(now.plusHours(1).toInstant().toEpochMilli(), hour)
        assertEquals(now.plusHours(1).toInstant().toEpochMilli(), evening)
        val nextMorning = ZonedDateTime.of(2026, 11, 20, 8, 0, 0, 0, now.zone)
        assertEquals(nextMorning.toInstant().toEpochMilli(), tomorrow)
        assertTrue(tomorrow > now.toInstant().toEpochMilli())
    }

    @Test
    fun `the snooze picker is a transparent activity that finishes itself`() {
        val picker = read("SnoozePickerActivity.kt")
        assertTrue(picker.contains("setFinishOnTouchOutside(true)"), "the picker must cancel on an outside tap")
        assertTrue(picker.contains("SnoozeOptions.choices("), "the picker must use the tested arithmetic")
        val manifest = readApp("src/main/AndroidManifest.xml", "../app/src/main/AndroidManifest.xml")
        assertTrue(manifest.contains(".notifications.SnoozePickerActivity"))
        assertTrue(manifest.contains("@style/Theme.MarbleDo.SnoozePicker"))
        assertTrue(manifest.contains("android:excludeFromRecents=\"true\""))
    }

    // ---- channels -------------------------------------------------------------------------

    @Test
    fun `every reminder kind owns a channel and the briefing one is silent`() {
        val channels = read("NotificationChannels.kt")
        // Distinct ids: a channel's importance is frozen at creation, so sharing one would mean a
        // silent briefing could never be silent on an install that already had the loud channel.
        val ids = Regex("const val (\\w+) = \"(marbledo_[a-z_]+)\"")
            .findAll(channels)
            .associate { it.groupValues[1] to it.groupValues[2] }
        assertEquals(ids.values.size, ids.values.toSet().size, "two channels share an id: $ids")
        for (name in listOf("OVERDUE", "TODAY", "OCCASIONS", "CALENDAR", "PERSISTENT_DATE")) {
            assertTrue(name in ids, "missing channel $name")
        }
        val occasions = channels.substringAfter("NotificationChannel(OCCASIONS")
            .substringBefore("NotificationChannel(CALENDAR")
        assertTrue(occasions.contains("IMPORTANCE_LOW"), "the briefing must stay low importance")
        assertTrue(occasions.contains("setSound(null, null)"), "the briefing must be silent")
        assertTrue(occasions.contains("enableVibration(false)"), "the briefing must not buzz")
        val overdue = channels.substringAfter("NotificationChannel(OVERDUE")
            .substringBefore("NotificationChannel(TODAY")
        assertTrue(overdue.contains("IMPORTANCE_HIGH"), "overdue must be the loud channel")
    }

    @Test
    fun `settings can switch each channel off independently`() {
        val settings = AppSettings()
        assertTrue(settings.overdueNotificationsEnabled)
        assertTrue(settings.todayRemindersEnabled)
        assertTrue(settings.calendarNotificationsEnabled)
        // The three switches are independent values, not one shared flag.
        val off = settings.copy(overdueNotificationsEnabled = false)
        assertNotEquals(off.todayRemindersEnabled, off.overdueNotificationsEnabled)
        assertTrue(off.todayRemindersEnabled)
        val screen = readApp(
            "src/main/kotlin/com/marbledo/SettingsScreen.kt",
            "../app/src/main/kotlin/com/marbledo/SettingsScreen.kt",
        )
        assertTrue(screen.contains("settings.copy(overdueNotificationsEnabled = it)"))
        assertTrue(screen.contains("settings.copy(todayRemindersEnabled = it)"))
        assertTrue(screen.contains("settings.copy(occasionNotificationHour = hour)"))
    }

    // ---- custom card sizing ----------------------------------------------------------------

    @Test
    fun `the compact full-width card is used collapsed and expanded content stays user-controlled`() {
        val notification = read("PersistentCalendarNotification.kt")
        assertTrue(notification.contains(".setCustomContentView(it)"), "the compact card should replace the title-only collapsed row")
        assertTrue(notification.contains(".setCustomHeadsUpContentView(it)"), "heads-up should use the same compact card")
        assertTrue(notification.contains(".setCustomBigContentView(expandedView)"), "the detailed card should be available when expanded")
    }

    // ---- actions and grouping -------------------------------------------------------------

    @Test
    fun `the task card offers three labelled actions and each carries an icon`() {
        val receiver = read("ReminderReceiver.kt")
        // Done, Later, Open — never more, and never one without a word next to its mark.
        val actions = Regex("\\.addAction\\(R\\.drawable\\.(\\w+), context\\.getString\\(R\\.string\\.(\\w+)\\)")
            .findAll(receiver).toList()
        assertEquals(
            listOf("ic_notification_done", "ic_notification_snooze", "ic_notification_open"),
            actions.map { it.groupValues[1] },
            "the task card's action set changed",
        )
        assertEquals(
            listOf("notification_done", "notification_snooze", "notification_open_task"),
            actions.map { it.groupValues[2] },
            "an action lost its label — an icon alone is not a TalkBack label",
        )
        // The card deep-links to that task, and so does the Open action.
        assertTrue(receiver.contains("NotificationActions.openTask(context, id"))
        assertTrue(receiver.contains(".setContentIntent(contentIntent)"))
    }

    @Test
    fun `an overdue task joins the group and a summary counts what it hides`() {
        val receiver = read("ReminderReceiver.kt")
        assertTrue(receiver.contains("builder.setGroup(GroupedNotification.GROUP_OVERDUE)"))
        val grouped = read("GroupedNotification.kt")
        assertTrue(grouped.contains("const val GROUP_OVERDUE = \"marbledo_overdue_tasks\""))
        assertTrue(grouped.contains(".setGroupSummary(true)"))
        assertTrue(grouped.contains("NotificationCompat.InboxStyle()"))
        assertTrue(grouped.contains("R.string.notification_overdue_more"))
        assertEquals(3, Regex("const val VISIBLE_TASKS = (\\d+)").find(grouped)?.groupValues?.get(1)?.toInt())
        // The summary never makes its own noise: the cards already did.
        assertTrue(grouped.contains(".setSilent(true)"))
    }

    @Test
    fun `done dismisses the card before the app is ever involved`() {
        val receiver = read("ReminderReceiver.kt")
        val done = receiver.substringAfter("ACTION_DONE -> completionMutex.withLock {").substringBefore("ACTION_SNOOZE_AT ->")
        assertTrue(done.contains("repository.setCompleted(id, true)"))
        assertTrue(done.contains("NotificationManagerCompat.from(context).cancel(notificationId(id))"))
        // Haptic + toast, and no activity start: the whole point is that the app stays closed.
        assertTrue(done.contains("confirmDone(context)"))
        assertTrue(receiver.contains("VibrationEffect.createOneShot(30L"))
        assertTrue(receiver.contains("R.string.notification_done_toast"))
        assertTrue(!done.contains("startActivity"), "Done must not open the app")
    }

    @Test
    fun `the briefing runs once a day at the configured hour`() {
        val worker = read("CalendarOccurrenceWorker.kt")
        assertTrue(worker.contains("fun ensureDaily(context: Context, hour: Int = ReminderPreferences.occasionHour(context))"))
        assertTrue(worker.contains("ReminderPreferences.delayUntilHour(hour)"))
        assertTrue(worker.contains("NotificationChannels.OCCASIONS"))
        val app = readApp(
            "src/main/kotlin/com/marbledo/MarbleDoApplication.kt",
            "../app/src/main/kotlin/com/marbledo/MarbleDoApplication.kt",
        )
        assertTrue(
            app.contains("CalendarNotificationWorkInitializer.ensureDaily(this@MarbleDoApplication, settings.occasionNotificationHour)"),
            "changing the briefing hour must re-arm the daily work",
        )
    }

    @Test
    fun `a notification tap opens that task through the deep link`() {
        val activity = readApp(
            "src/main/kotlin/com/marbledo/MainActivity.kt",
            "../app/src/main/kotlin/com/marbledo/MainActivity.kt",
        )
        assertTrue(activity.contains("it.host == \"task\""))
        assertTrue(activity.contains("lastPathSegment?.toLongOrNull()"))
        val manifest = readApp("src/main/AndroidManifest.xml", "../app/src/main/AndroidManifest.xml")
        assertTrue(manifest.contains("android:scheme=\"marbledo\""))
        assertTrue(manifest.contains("android:host=\"task\""))
        val actions = read("NotificationActions.kt")
        assertTrue(actions.contains("Uri.parse(\"marbledo://task/\$taskId\")"))
        assertTrue(actions.contains("putExtra(MainActivity.EXTRA_TASK_ID, taskId)"))
    }
}
