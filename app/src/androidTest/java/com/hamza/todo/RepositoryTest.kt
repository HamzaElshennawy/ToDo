package com.hamza.todo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hamza.todo.core.model.Frequency
import com.hamza.todo.core.model.Recurrence
import com.hamza.todo.core.model.Task
import com.hamza.todo.core.sync.SyncSnapshot
import com.hamza.todo.data.NewTask
import com.hamza.todo.data.TodoDatabase
import com.hamza.todo.data.TodoRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** The Room database and repository, on a real device, with an in-memory database. */
@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private lateinit var db: TodoDatabase
    private lateinit var repo: TodoRepository
    private val today = LocalDate.of(2026, 10, 8)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TodoDatabase::class.java).build()
        repo = TodoRepository(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun defaultListsAreCreatedOnce() = runBlocking {
        repo.ensureDefaults()
        repo.ensureDefaults()
        assertEquals(listOf("Inbox", "Personal", "Work", "Groceries"), repo.lists.first().map { it.name })
    }

    @Test
    fun completingARepeatingTaskCreatesTheNextOne() = runBlocking {
        val id = repo.addTask(
            NewTask("Call mom", dueEpochDay = today.toEpochDay(), recurrence = Recurrence(Frequency.WEEKLY)),
            listOf("Ask about the weekend"),
        )
        repo.setCompleted(id, true, today)
        val tasks = repo.tasks.first()
        assertEquals(2, tasks.size)
        val next = tasks.single { !it.completed }
        assertEquals(today.plusWeeks(1), next.dueDate)
        val subs = repo.subtasks.first().filter { it.taskId == next.id }
        assertEquals(listOf("Ask about the weekend"), subs.map { it.title })
        assertTrue(subs.none { it.done })
    }

    @Test
    fun deleteKeepsATombstoneAndRestoreBringsItBack() = runBlocking {
        val id = repo.addTask(NewTask("Pay rent"))
        repo.deleteTask(id)
        assertTrue(repo.tasks.first().isEmpty())
        assertTrue(repo.snapshot().tasks.single().deleted)
        repo.restoreTask(id)
        assertEquals(listOf("Pay rent"), repo.tasks.first().map { it.title })
    }

    @Test
    fun deletingAListDeletesItsTasksButNotTheInbox() = runBlocking {
        repo.ensureDefaults()
        val listId = repo.addList("Errands", 0xFF3346D3)
        repo.addTask(NewTask("Post letter", listId = listId))
        repo.addTask(NewTask("Inbox task"))
        repo.deleteList(listId)
        repo.deleteList("inbox")
        assertEquals(listOf("Inbox task"), repo.tasks.first().map { it.title })
        assertTrue(repo.lists.first().any { it.id == "inbox" })
    }

    @Test
    fun dataFromAnotherDeviceIsStored() = runBlocking {
        val id = repo.addTask(NewTask("Local title"))
        val local = repo.snapshot().tasks.single()
        val fromOtherDevice = Task(id = "remote", title = "From tablet", createdAt = 1, updatedAt = 1)
        val newerEdit = local.copy(title = "Edited on tablet", updatedAt = local.updatedAt + 1000)
        repo.applySynced(SyncSnapshot(tasks = listOf(fromOtherDevice, newerEdit)))
        val titles = repo.tasks.first().associate { it.id to it.title }
        assertEquals("Edited on tablet", titles[id])
        assertEquals("From tablet", titles["remote"])
    }

    @Test
    fun manualReorderIsSaved() = runBlocking {
        val a = repo.addTask(NewTask("A"))
        val b = repo.addTask(NewTask("B"))
        val c = repo.addTask(NewTask("C"))
        repo.reorder(listOf(c, a, b))
        assertEquals(listOf("C", "A", "B"), repo.tasks.first().sortedBy { it.sortOrder }.map { it.title })
    }
}
