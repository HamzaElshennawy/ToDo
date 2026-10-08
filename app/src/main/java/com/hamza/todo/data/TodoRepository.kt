package com.hamza.todo.data

import androidx.room.withTransaction
import com.hamza.todo.core.model.Priority
import com.hamza.todo.core.model.Recurrence
import com.hamza.todo.core.model.Subtask
import com.hamza.todo.core.model.Task
import com.hamza.todo.core.model.TaskActions
import com.hamza.todo.core.model.TaskList
import com.hamza.todo.core.sync.SyncMerger
import com.hamza.todo.core.sync.SyncSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID

/** Fields of a new task, as entered in the quick-add sheet. */
data class NewTask(
    val title: String,
    val notes: String = "",
    val listId: String = TaskList.INBOX_ID,
    val dueEpochDay: Long? = null,
    val dueMinutes: Int? = null,
    val reminderOffsetMinutes: Int? = null,
    val priority: Priority = Priority.NONE,
    val tags: List<String> = emptyList(),
    val recurrence: Recurrence? = null,
)

class TodoRepository(
    private val db: TodoDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val listDao = db.lists()
    private val taskDao = db.tasks()
    private val subtaskDao = db.subtasks()

    /** Emits after every change made on this device (not after applying synced data). */
    private val _localChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val localChanges: SharedFlow<Unit> = _localChanges

    val lists: Flow<List<TaskList>> = listDao.observeAll().map { rows ->
        rows.map { it.toModel() }.filter { !it.deleted }
            .sortedWith(compareBy<TaskList>({ it.id != TaskList.INBOX_ID }, { it.sortOrder }, { it.name }))
    }

    val tasks: Flow<List<Task>> = taskDao.observeAll().map { rows -> rows.map { it.toModel() }.filter { !it.deleted } }

    val subtasks: Flow<List<Subtask>> = subtaskDao.observeAll().map { rows ->
        rows.map { it.toModel() }.filter { !it.deleted }.sortedBy { it.sortOrder }
    }

    private fun newId() = UUID.randomUUID().toString()

    private fun changed() {
        _localChanges.tryEmit(Unit)
    }

    /**
     * Creates the starting lists. They use fixed ids and updatedAt = 0, so when several
     * devices create them and then sync, they merge into one copy instead of duplicating.
     */
    suspend fun ensureDefaults() {
        if (listDao.all().isNotEmpty()) return
        listDao.upsertAll(
            DEFAULT_LISTS.mapIndexed { i, (id, name, color) ->
                ListEntity(id, name, color, i.toDouble(), updatedAt = 0, deleted = false)
            },
        )
    }

    suspend fun task(id: String): Task? = taskDao.byId(id)?.toModel()?.takeIf { !it.deleted }

    suspend fun addTask(draft: NewTask, subtaskTitles: List<String> = emptyList()): String {
        val now = clock()
        val id = newId()
        val order = (taskDao.maxSortOrder(draft.listId) ?: 0.0) + 1.0
        db.withTransaction {
            taskDao.upsert(
                Task(
                    id = id,
                    listId = draft.listId,
                    title = draft.title.trim(),
                    notes = draft.notes.trim(),
                    dueEpochDay = draft.dueEpochDay,
                    dueMinutes = draft.dueMinutes,
                    reminderOffsetMinutes = draft.reminderOffsetMinutes,
                    priority = draft.priority,
                    tags = draft.tags,
                    recurrence = draft.recurrence,
                    sortOrder = order,
                    createdAt = now,
                    updatedAt = now,
                ).toEntity(),
            )
            subtaskDao.upsertAll(
                subtaskTitles.filter { it.isNotBlank() }.mapIndexed { i, t ->
                    Subtask(newId(), id, t.trim(), sortOrder = i.toDouble(), updatedAt = now).toEntity()
                },
            )
        }
        changed()
        return id
    }

    suspend fun updateTask(id: String, transform: (Task) -> Task) {
        val current = taskDao.byId(id)?.toModel() ?: return
        val updated = transform(current)
        if (updated == current) return
        taskDao.upsert(updated.copy(updatedAt = clock()).toEntity())
        changed()
    }

    suspend fun setCompleted(id: String, done: Boolean, today: LocalDate = LocalDate.now()) {
        val task = taskDao.byId(id)?.toModel() ?: return
        if (task.completed == done) return
        val now = clock()
        if (!done) {
            taskDao.upsert(TaskActions.uncomplete(task, now).toEntity())
        } else {
            val subs = subtaskDao.forTask(id).map { it.toModel() }
            val result = TaskActions.complete(task, subs, now, today, ::newId)
            db.withTransaction {
                taskDao.upsert(result.completed.toEntity())
                result.next?.let { taskDao.upsert(it.toEntity()) }
                subtaskDao.upsertAll(result.nextSubtasks.map { it.toEntity() })
            }
        }
        changed()
    }

    suspend fun deleteTask(id: String) = updateTask(id) { it.copy(deleted = true) }

    suspend fun restoreTask(id: String) = updateTask(id) { it.copy(deleted = false) }

    suspend fun moveToDay(ids: List<String>, epochDay: Long) {
        val now = clock()
        val rows = ids.mapNotNull { taskDao.byId(it)?.toModel() }
            .map { it.copy(dueEpochDay = epochDay, updatedAt = now).toEntity() }
        taskDao.upsertAll(rows)
        changed()
    }

    /** Saves a manual order: the ids in their new order get increasing sortOrder values. */
    suspend fun reorder(idsInOrder: List<String>) {
        val now = clock()
        val rows = idsInOrder.mapIndexedNotNull { i, id ->
            taskDao.byId(id)?.toModel()?.takeIf { it.sortOrder != i.toDouble() }
                ?.copy(sortOrder = i.toDouble(), updatedAt = now)?.toEntity()
        }
        if (rows.isEmpty()) return
        taskDao.upsertAll(rows)
        changed()
    }

    suspend fun clearCompleted() {
        val now = clock()
        val rows = taskDao.all().map { it.toModel() }.filter { it.completed && !it.deleted }
            .map { it.copy(deleted = true, updatedAt = now).toEntity() }
        taskDao.upsertAll(rows)
        changed()
    }

    // Subtasks

    suspend fun addSubtask(taskId: String, title: String) {
        if (title.isBlank()) return
        val existing = subtaskDao.forTask(taskId)
        val order = (existing.maxOfOrNull { it.sortOrder } ?: -1.0) + 1.0
        subtaskDao.upsert(Subtask(newId(), taskId, title.trim(), sortOrder = order, updatedAt = clock()).toEntity())
        changed()
    }

    suspend fun updateSubtask(id: String, transform: (Subtask) -> Subtask) {
        val current = subtaskDao.byId(id)?.toModel() ?: return
        val updated = transform(current)
        if (updated == current) return
        subtaskDao.upsert(updated.copy(updatedAt = clock()).toEntity())
        changed()
    }

    // Lists

    suspend fun addList(name: String, colorArgb: Long): String {
        val id = newId()
        val order = (listDao.all().maxOfOrNull { it.sortOrder } ?: 0.0) + 1.0
        listDao.upsert(TaskList(id, name.trim(), colorArgb, order, clock()).toEntity())
        changed()
        return id
    }

    suspend fun updateList(id: String, name: String, colorArgb: Long) {
        val current = listDao.byId(id)?.toModel() ?: return
        listDao.upsert(current.copy(name = name.trim(), colorArgb = colorArgb, updatedAt = clock()).toEntity())
        changed()
    }

    /** Deletes a list and the tasks in it. The Inbox cannot be deleted. */
    suspend fun deleteList(id: String) {
        if (id == TaskList.INBOX_ID) return
        val now = clock()
        db.withTransaction {
            listDao.byId(id)?.toModel()?.let { listDao.upsert(it.copy(deleted = true, updatedAt = now).toEntity()) }
            taskDao.upsertAll(
                taskDao.all().map { it.toModel() }.filter { it.listId == id && !it.deleted }
                    .map { it.copy(deleted = true, updatedAt = now).toEntity() },
            )
        }
        changed()
    }

    // Sync and backup

    /** Everything, including deleted rows, for syncing or backup. */
    suspend fun snapshot(): SyncSnapshot = SyncSnapshot(
        lists = listDao.all().map { it.toModel() },
        tasks = taskDao.all().map { it.toModel() },
        subtasks = subtaskDao.all().map { it.toModel() },
    )

    /** Stores data merged from another device. Does not count as a local change. */
    suspend fun applySynced(snapshot: SyncSnapshot) {
        db.withTransaction {
            listDao.upsertAll(snapshot.lists.map { it.toEntity() })
            taskDao.upsertAll(snapshot.tasks.map { it.toEntity() })
            subtaskDao.upsertAll(snapshot.subtasks.map { it.toEntity() })
        }
    }

    /** Restores a backup file: merged with what is here, newest edit wins. */
    suspend fun importBackup(backup: SyncSnapshot) {
        val merged = SyncMerger.merge(snapshot(), backup).merged
        applySynced(merged)
        changed()
    }

    companion object {
        val DEFAULT_LISTS: List<Triple<String, String, Long>> = listOf(
            Triple(TaskList.INBOX_ID, "Inbox", 0xFF6B7280),
            Triple("personal", "Personal", 0xFF0F766E),
            Triple("work", "Work", 0xFF3346D3),
            Triple("groceries", "Groceries", 0xFFC2410C),
        )

        /** Colors offered when creating or editing a list. */
        val LIST_COLORS: List<Long> = listOf(
            0xFF3346D3, 0xFF0F766E, 0xFFC2410C, 0xFFBE185D,
            0xFF7C3AED, 0xFF15803D, 0xFFA16207, 0xFF6B7280,
        )
    }
}
