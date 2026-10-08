package com.hamza.todo.core

import com.hamza.todo.core.export.Exporter
import com.hamza.todo.core.model.DeviceInfo
import com.hamza.todo.core.model.Priority
import com.hamza.todo.core.model.Subtask
import com.hamza.todo.core.model.Task
import com.hamza.todo.core.model.TaskList
import com.hamza.todo.core.sync.SyncMerger
import com.hamza.todo.core.sync.SyncSnapshot
import com.hamza.todo.core.views.SortMode
import com.hamza.todo.core.views.TaskViews
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SyncAndViewsTest {
    private val today = LocalDate.of(2026, 10, 8)

    private fun task(id: String, updated: Long, title: String = id, due: LocalDate? = null, deleted: Boolean = false) =
        Task(id = id, title = title, dueEpochDay = due?.toEpochDay(), createdAt = 0, updatedAt = updated, deleted = deleted)

    @Test fun `newest edit wins and both sides get everything`() {
        val local = SyncSnapshot(tasks = listOf(task("a", 5, "local A"), task("b", 1)))
        val remote = SyncSnapshot(tasks = listOf(task("a", 9, "remote A"), task("c", 1)))
        val result = SyncMerger.merge(local, remote)
        assertEquals(listOf("a", "b", "c"), result.merged.tasks.map { it.id })
        assertEquals("remote A", result.merged.tasks.first { it.id == "a" }.title)
        assertTrue(result.localChanged)
        assertTrue(result.remoteChanged)
    }

    @Test fun `a delete stays deleted, also on a tie`() {
        val local = SyncSnapshot(tasks = listOf(task("a", 5, deleted = true)))
        val remote = SyncSnapshot(tasks = listOf(task("a", 5)))
        assertTrue(SyncMerger.merge(local, remote).merged.tasks.single().deleted)
        assertTrue(SyncMerger.merge(remote, local).merged.tasks.single().deleted)
    }

    @Test fun `nothing changes when both sides match`() {
        val snap = SyncSnapshot(
            lists = listOf(TaskList("inbox", "Inbox", 0xFF6B7280, updatedAt = 1)),
            tasks = listOf(task("a", 1)),
            subtasks = listOf(Subtask("s", "a", "x", updatedAt = 1)),
            devices = listOf(DeviceInfo("d1", "Phone", lastSyncAt = 3)),
        )
        val result = SyncMerger.merge(snap, snap)
        assertFalse(result.localChanged)
        assertFalse(result.remoteChanged)
    }

    @Test fun `devices keep their latest sync time`() {
        val a = SyncSnapshot(devices = listOf(DeviceInfo("d1", "Phone", lastSyncAt = 10)))
        val b = SyncSnapshot(devices = listOf(DeviceInfo("d1", "Phone", lastSyncAt = 4), DeviceInfo("d2", "Tablet", true, 7)))
        val merged = SyncMerger.merge(a, b).merged
        assertEquals(listOf(10L, 7L), merged.devices.map { it.lastSyncAt })
    }

    @Test fun `snapshot json round trip ignores unknown fields`() {
        val snap = SyncSnapshot(tasks = listOf(task("a", 1).copy(priority = Priority.HIGH, tags = listOf("x"))))
        assertEquals(snap, SyncSnapshot.fromJson(snap.toJson()))
        val withExtra = snap.toJson().replaceFirst("{", "{\"futureField\":1,")
        assertEquals(snap, SyncSnapshot.fromJson(withExtra))
    }

    @Test fun `today view splits overdue, today and done`() {
        val tasks = listOf(
            task("late", 1, due = today.minusDays(1)),
            task("now", 1, due = today),
            task("later", 1, due = today.plusDays(1)),
            task("done", 1, due = today).copy(completed = true, completedAt = 500),
            task("gone", 1, due = today, deleted = true),
        )
        val view = TaskViews.today(tasks, today, completedSinceMillis = 100)
        assertEquals(listOf("late"), view.overdue.map { it.id })
        assertEquals(listOf("now"), view.today.map { it.id })
        assertEquals(listOf("done"), view.completedToday.map { it.id })
        assertEquals(3, view.total)
    }

    @Test fun `upcoming groups by day and week board has 7 days`() {
        val tasks = listOf(task("fri", 1, due = today.plusDays(1)), task("mon", 1, due = today.plusDays(4)), task("far", 1, due = today.plusDays(30)))
        assertEquals(listOf(today.plusDays(1), today.plusDays(4)), TaskViews.upcoming(tasks, today, today.plusDays(14)).map { it.date })
        val week = TaskViews.week(tasks, today)
        assertEquals(7, week.size)
        assertEquals(listOf("fri"), week[1].tasks.map { it.id })
    }

    @Test fun `search matches words and tags`() {
        val tasks = listOf(
            task("a", 1, "Buy groceries").copy(tags = listOf("home")),
            task("b", 1, "Send report").copy(notes = "include groceries budget"),
            task("c", 1, "Call mom"),
        )
        assertEquals(listOf("a", "b"), TaskViews.search(tasks, "groceries").map { it.id }.sorted())
        assertEquals(listOf("a"), TaskViews.search(tasks, "#home").map { it.id })
        assertEquals(emptyList<String>(), TaskViews.search(tasks, "  ").map { it.id })
    }

    @Test fun `priority sort puts high first`() {
        val tasks = listOf(task("low", 1).copy(priority = Priority.LOW), task("high", 1).copy(priority = Priority.HIGH))
        assertEquals(listOf("high", "low"), TaskViews.sort(tasks, SortMode.PRIORITY).map { it.id })
    }

    @Test fun `csv escapes commas and quotes`() {
        val snap = SyncSnapshot(
            lists = listOf(TaskList("inbox", "Inbox", 0, updatedAt = 1)),
            tasks = listOf(task("a", 1, "Milk, eggs \"fresh\"", due = today)),
        )
        val csv = Exporter.toCsv(snap)
        assertTrue(csv.contains("\"Milk, eggs \"\"fresh\"\"\",Inbox,2026-10-08"))
        assertEquals(snap.copy(devices = emptyList()).normalized(), Exporter.readBackup(Exporter.toJson(snap)))
    }
}
