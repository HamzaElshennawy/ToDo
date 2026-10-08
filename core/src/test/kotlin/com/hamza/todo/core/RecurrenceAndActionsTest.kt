package com.hamza.todo.core

import com.hamza.todo.core.model.Frequency
import com.hamza.todo.core.model.Recurrence
import com.hamza.todo.core.model.Subtask
import com.hamza.todo.core.model.Task
import com.hamza.todo.core.model.TaskActions
import com.hamza.todo.core.recurrence.RecurrenceRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecurrenceAndActionsTest {
    private val thu = LocalDate.of(2026, 10, 8)

    @Test fun `simple frequencies`() {
        assertEquals(thu.plusDays(2), RecurrenceRules.next(Recurrence(Frequency.DAILY, 2), thu))
        assertEquals(thu.plusWeeks(1), RecurrenceRules.next(Recurrence(Frequency.WEEKLY), thu))
        assertEquals(LocalDate.of(2026, 11, 30), RecurrenceRules.next(Recurrence(Frequency.MONTHLY), LocalDate.of(2026, 10, 31)))
        assertEquals(thu.plusYears(1), RecurrenceRules.next(Recurrence(Frequency.YEARLY), thu))
    }

    @Test fun `weekly on chosen days`() {
        val monWedFri = Recurrence(Frequency.WEEKLY, weekdays = setOf(1, 3, 5))
        assertEquals(LocalDate.of(2026, 10, 9), RecurrenceRules.next(monWedFri, thu)) // Fri
        assertEquals(LocalDate.of(2026, 10, 12), RecurrenceRules.next(monWedFri, LocalDate.of(2026, 10, 9))) // Mon
        val everyOtherMon = Recurrence(Frequency.WEEKLY, interval = 2, weekdays = setOf(1))
        assertEquals(LocalDate.of(2026, 10, 19), RecurrenceRules.next(everyOtherMon, thu))
    }

    @Test fun `monthly on the 2nd tuesday and last friday`() {
        val secondTue = Recurrence(Frequency.MONTHLY, monthWeekOrdinal = 2, monthWeekday = 2)
        assertEquals(LocalDate.of(2026, 11, 10), RecurrenceRules.next(secondTue, LocalDate.of(2026, 10, 13)))
        assertEquals(LocalDate.of(2026, 10, 13), RecurrenceRules.firstOnOrAfter(secondTue, LocalDate.of(2026, 10, 1)))
        val lastFri = Recurrence(Frequency.MONTHLY, monthWeekOrdinal = -1, monthWeekday = 5)
        assertEquals(LocalDate.of(2026, 11, 27), RecurrenceRules.next(lastFri, LocalDate.of(2026, 10, 30)))
        assertEquals("Every month on the 2nd Tuesday", RecurrenceRules.describe(secondTue))
    }

    @Test fun `describe`() {
        assertEquals("Doesn’t repeat", RecurrenceRules.describe(null))
        assertEquals("Every 3 days", RecurrenceRules.describe(Recurrence(Frequency.DAILY, 3)))
        assertEquals("Every weekday", RecurrenceRules.describe(Recurrence(Frequency.WEEKLY, weekdays = setOf(1, 2, 3, 4, 5))))
        assertEquals("Every Mon, Wed", RecurrenceRules.describe(Recurrence(Frequency.WEEKLY, weekdays = setOf(3, 1))))
    }

    @Test fun `completing a repeating task creates the next one with fresh subtasks`() {
        val task = Task(
            id = "t", title = "Call mom", dueEpochDay = thu.toEpochDay(), createdAt = 0, updatedAt = 0,
            recurrence = Recurrence(Frequency.WEEKLY),
        )
        val subs = listOf(Subtask("s", "t", "Ask about weekend", done = true, updatedAt = 0))
        var n = 0
        val result = TaskActions.complete(task, subs, now = 100, today = thu) { "new${n++}" }
        assertTrue(result.completed.completed)
        assertNull(result.completed.recurrence)
        val next = result.next!!
        assertFalse(next.completed)
        assertEquals(thu.plusWeeks(1), next.dueDate)
        assertEquals(1, result.nextSubtasks.size)
        assertFalse(result.nextSubtasks[0].done)
        assertEquals(next.id, result.nextSubtasks[0].taskId)
    }

    @Test fun `an overdue daily task skips ahead past today`() {
        val task = Task(
            id = "t", title = "Run", dueEpochDay = thu.minusDays(3).toEpochDay(), createdAt = 0, updatedAt = 0,
            recurrence = Recurrence(Frequency.DAILY),
        )
        val result = TaskActions.complete(task, emptyList(), 1, thu) { "n" }
        assertEquals(thu.plusDays(1), result.next!!.dueDate)
    }

    @Test fun `plain task has no next occurrence`() {
        val task = Task(id = "t", title = "Once", createdAt = 0, updatedAt = 0)
        assertNull(TaskActions.complete(task, emptyList(), 1, thu) { "n" }.next)
    }
}
