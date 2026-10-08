package com.hamza.todo.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import com.hamza.todo.core.model.Priority
import com.hamza.todo.core.model.Recurrence
import com.hamza.todo.core.model.Subtask
import com.hamza.todo.core.model.Task
import com.hamza.todo.core.model.TaskList
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

// Rows keep deleted items (tombstones) so deletes sync to the other devices.

@Entity(tableName = "lists")
data class ListEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorArgb: Long,
    val sortOrder: Double,
    val updatedAt: Long,
    val deleted: Boolean,
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val listId: String,
    val title: String,
    val notes: String,
    val dueEpochDay: Long?,
    val dueMinutes: Int?,
    val reminderOffsetMinutes: Int?,
    val priority: Int,
    val tagsJson: String,
    val recurrenceJson: String?,
    val completed: Boolean,
    val completedAt: Long?,
    val sortOrder: Double,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean,
)

@Entity(tableName = "subtasks")
data class SubtaskEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val title: String,
    val done: Boolean,
    val sortOrder: Double,
    val updatedAt: Long,
    val deleted: Boolean,
)

private val json = Json { ignoreUnknownKeys = true }
private val tagsSerializer = ListSerializer(String.serializer())

fun ListEntity.toModel() = TaskList(id, name, colorArgb, sortOrder, updatedAt, deleted)
fun TaskList.toEntity() = ListEntity(id, name, colorArgb, sortOrder, updatedAt, deleted)

fun TaskEntity.toModel() = Task(
    id = id,
    listId = listId,
    title = title,
    notes = notes,
    dueEpochDay = dueEpochDay,
    dueMinutes = dueMinutes,
    reminderOffsetMinutes = reminderOffsetMinutes,
    priority = Priority.fromLevel(priority),
    tags = runCatching { json.decodeFromString(tagsSerializer, tagsJson) }.getOrDefault(emptyList()),
    recurrence = recurrenceJson?.let { runCatching { json.decodeFromString(Recurrence.serializer(), it) }.getOrNull() },
    completed = completed,
    completedAt = completedAt,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deleted = deleted,
)

fun Task.toEntity() = TaskEntity(
    id = id,
    listId = listId,
    title = title,
    notes = notes,
    dueEpochDay = dueEpochDay,
    dueMinutes = dueMinutes,
    reminderOffsetMinutes = reminderOffsetMinutes,
    priority = priority.level,
    tagsJson = json.encodeToString(tagsSerializer, tags),
    recurrenceJson = recurrence?.let { json.encodeToString(Recurrence.serializer(), it) },
    completed = completed,
    completedAt = completedAt,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deleted = deleted,
)

fun SubtaskEntity.toModel() = Subtask(id, taskId, title, done, sortOrder, updatedAt, deleted)
fun Subtask.toEntity() = SubtaskEntity(id, taskId, title, done, sortOrder, updatedAt, deleted)

@Dao
interface ListDao {
    @Query("SELECT * FROM lists")
    fun observeAll(): Flow<List<ListEntity>>

    @Query("SELECT * FROM lists")
    suspend fun all(): List<ListEntity>

    @Query("SELECT * FROM lists WHERE id = :id")
    suspend fun byId(id: String): ListEntity?

    @Upsert
    suspend fun upsert(item: ListEntity)

    @Upsert
    suspend fun upsertAll(items: List<ListEntity>)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks")
    suspend fun all(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun byId(id: String): TaskEntity?

    @Query("SELECT MAX(sortOrder) FROM tasks WHERE listId = :listId")
    suspend fun maxSortOrder(listId: String): Double?

    @Upsert
    suspend fun upsert(item: TaskEntity)

    @Upsert
    suspend fun upsertAll(items: List<TaskEntity>)
}

@Dao
interface SubtaskDao {
    @Query("SELECT * FROM subtasks")
    fun observeAll(): Flow<List<SubtaskEntity>>

    @Query("SELECT * FROM subtasks")
    suspend fun all(): List<SubtaskEntity>

    @Query("SELECT * FROM subtasks WHERE taskId = :taskId")
    suspend fun forTask(taskId: String): List<SubtaskEntity>

    @Query("SELECT * FROM subtasks WHERE id = :id")
    suspend fun byId(id: String): SubtaskEntity?

    @Upsert
    suspend fun upsert(item: SubtaskEntity)

    @Upsert
    suspend fun upsertAll(items: List<SubtaskEntity>)
}

@Database(
    entities = [ListEntity::class, TaskEntity::class, SubtaskEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class TodoDatabase : RoomDatabase() {
    abstract fun lists(): ListDao
    abstract fun tasks(): TaskDao
    abstract fun subtasks(): SubtaskDao

    companion object {
        fun create(context: Context): TodoDatabase =
            Room.databaseBuilder(context, TodoDatabase::class.java, "todo.db").build()
    }
}
