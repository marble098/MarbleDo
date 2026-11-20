package com.marbledo

import com.marble098.marbledo.notifications.NotificationMood
import com.marble098.marbledo.notifications.NotificationState
import com.marble098.marbledo.notifications.PersistentCalendarNotification.NotificationBoxContent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Spoken content must follow the visible rows, without hidden tasks or duplicate dates. */
class NotificationAccessibilityTest {
    private fun content() = NotificationBoxContent(
        title = "Jalali date",
        state = NotificationState.TODAY,
        stateLabel = "Due today",
        tasksLabel = "Tasks",
        taskTitle = "First task",
        taskMetaLine = "09:00",
        secondTaskTitle = "Second task",
        secondTaskMetaLine = "10:00",
        moreTasksLine = "Two more tasks",
        emptyTaskText = "Nothing due",
        occasionsLine = "Occasion name",
        occasionsLabel = "Occasions",
        focusTaskId = 1L,
        overdueCount = 0,
        overdueTasks = emptyList(),
        mood = NotificationMood.TASKS_DUE,
        dayDigits = "20",
        badgeDayDigits = "29",
        monthLabel = "Aban",
    )

    @Test
    fun `collapsed speech includes full task text but not expanded only rows`() {
        assertEquals(
            "Jalali date. Due today. First task. 09:00",
            content().accessibilityText(expanded = false),
        )
    }

    @Test
    fun `expanded speech follows date tasks then occasions including both task metadata`() {
        assertEquals(
            "Jalali date. Due today. Tasks. First task. 09:00. " +
                "Second task. 10:00. Two more tasks. Occasions. Occasion name",
            content().accessibilityText(expanded = true),
        )
    }

    @Test
    fun `the state is spoken once and no cross-check date is spoken at all`() {
        // The card states "۳ عقب‌افتاده" in its chip only; the old spoken text said it twice (once
        // in the header, once over the task list) and added a Gregorian/lunar line nobody asked
        // for. Both duplicates are gone, in the speech as well as on the card.
        for (expanded in listOf(false, true)) {
            val spoken = content().accessibilityText(expanded)
            assertEquals(1, spoken.split("Due today").size - 1, "the state is spoken more than once: $spoken")
            assertTrue(!spoken.contains("Gregorian"), "a cross-check date is still spoken: $spoken")
        }
    }

    @Test
    fun `empty day does not announce invisible task or occasion headings`() {
        val empty = content().copy(
            hasTask = false,
            taskTitle = null,
            taskMetaLine = null,
            secondTaskTitle = null,
            secondTaskMetaLine = null,
            moreTasksLine = null,
            occasionsLine = "",
            hasOccasions = false,
        )
        assertEquals("Jalali date. Due today. Nothing due", empty.accessibilityText(expanded = false))
        assertEquals(
            "Jalali date. Due today. Nothing due",
            empty.accessibilityText(expanded = true),
        )
    }
}
