package com.marbledo

import com.marble098.marbledo.notifications.NotificationMood
import com.marble098.marbledo.notifications.NotificationState
import com.marble098.marbledo.notifications.PersistentCalendarNotification.NotificationBoxContent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Spoken content must follow the visible rows, without hidden tasks or duplicate dates. */
class NotificationAccessibilityTest {
    private fun content() = NotificationBoxContent(
        title = "Jalali date",
        secondaryDatesLine = "Gregorian and lunar dates",
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
            "Jalali date. Due today. Gregorian and lunar dates. Tasks. First task. 09:00. " +
                "Second task. 10:00. Two more tasks. Occasions. Occasion name",
            content().accessibilityText(expanded = true),
        )
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
            "Jalali date. Due today. Gregorian and lunar dates. Nothing due",
            empty.accessibilityText(expanded = true),
        )
    }
}
