package com.hamza.todo.core

import com.hamza.todo.core.model.Frequency
import com.hamza.todo.core.model.Priority
import com.hamza.todo.core.parse.DetectedKind
import com.hamza.todo.core.parse.QuickAddParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class QuickAddParserTest {
    // Thursday 8 October 2026, 09:00
    private val now = LocalDateTime.of(2026, 10, 8, 9, 0)
    private val parser = QuickAddParser(listOf("Inbox", "Personal", "Work", "Groceries"))

    @Test fun `tomorrow at 5pm with high priority`() {
        val r = parser.parse("Call mom tomorrow 5pm !high", now)
        assertEquals("Call mom", r.title)
        assertEquals(LocalDate.of(2026, 10, 9), r.dueDate)
        assertEquals(17 * 60, r.dueMinutes)
        assertEquals(Priority.HIGH, r.priority)
        assertEquals(listOf("Tomorrow, 17:00", "High priority"), r.detected.map { it.label })
    }

    @Test fun `next monday from a thursday`() {
        val r = parser.parse("Submit expense report next Monday 5pm !high", now)
        assertEquals("Submit expense report", r.title)
        assertEquals(LocalDate.of(2026, 10, 12), r.dueDate)
        assertEquals("Mon, 12 Oct, 17:00", r.detected.first { it.kind == DetectedKind.DATE }.label)
    }

    @Test fun `list tags and repeat`() {
        val r = parser.parse("Water plants @personal #home every 3 days", now)
        assertEquals("Water plants", r.title)
        assertEquals("Personal", r.listName)
        assertEquals(listOf("home"), r.tags)
        assertEquals(Frequency.DAILY, r.recurrence?.frequency)
        assertEquals(3, r.recurrence?.interval)
        // A repeating task with no date starts today.
        assertEquals(LocalDate.of(2026, 10, 8), r.dueDate)
    }

    @Test fun `every monday starts on the next monday`() {
        val r = parser.parse("Team sync every monday at 9:30", now)
        assertEquals("Team sync", r.title)
        assertEquals(setOf(1), r.recurrence?.weekdays)
        assertEquals(LocalDate.of(2026, 10, 12), r.dueDate)
        assertEquals(9 * 60 + 30, r.dueMinutes)
    }

    @Test fun `unknown list stays in the title`() {
        val r = parser.parse("Email @boss", now)
        assertEquals("Email @boss", r.title)
        assertNull(r.listName)
    }

    @Test fun `time alone is today if still ahead, else tomorrow`() {
        assertEquals(LocalDate.of(2026, 10, 8), parser.parse("Gym 18:00", now).dueDate)
        assertEquals(LocalDate.of(2026, 10, 9), parser.parse("Gym 8am", now).dueDate)
    }

    @Test fun `day and month roll over to next year once passed`() {
        assertEquals(LocalDate.of(2026, 12, 24), parser.parse("Wrap gifts 24 dec", now).dueDate)
        assertEquals(LocalDate.of(2027, 3, 1), parser.parse("Taxes mar 1st", now).dueDate)
    }

    @Test fun `in a week and tonight`() {
        assertEquals(LocalDate.of(2026, 10, 15), parser.parse("Follow up in a week", now).dueDate)
        val tonight = parser.parse("Movie tonight", now)
        assertEquals(LocalDate.of(2026, 10, 8), tonight.dueDate)
        assertEquals(20 * 60, tonight.dueMinutes)
    }

    @Test fun `disabled kinds stay in the title`() {
        val r = parser.parse("Call mom tomorrow 5pm !high", now, disabled = setOf(DetectedKind.DATE))
        assertEquals("Call mom tomorrow 5pm", r.title)
        assertNull(r.dueDate)
        assertEquals(Priority.HIGH, r.priority)
    }

    @Test fun `plain text is untouched`() {
        val r = parser.parse("Buy milk and eggs", now)
        assertEquals("Buy milk and eggs", r.title)
        assertEquals(emptyList<Any>(), r.detected)
    }
}
