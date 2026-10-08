package com.hamza.todo.core.model

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

@Serializable
enum class Priority(val level: Int, val label: String) {
    NONE(0, "None"),
    LOW(1, "Low"),
    MEDIUM(2, "Medium"),
    HIGH(3, "High");

    companion object {
        fun fromLevel(level: Int): Priority = entries.firstOrNull { it.level == level } ?: NONE
    }
}

@Serializable
enum class Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }

/**
 * How a task repeats.
 *
 * - [weekdays]: ISO days (1 = Monday … 7 = Sunday) for [Frequency.WEEKLY]. Empty means
 *   "the same weekday as the current due date".
 * - [monthWeekOrdinal] + [monthWeekday]: for [Frequency.MONTHLY], e.g. 2 + 2 = "2nd Tuesday",
 *   -1 + 5 = "last Friday". Null means "the same day of the month".
 */
@Serializable
data class Recurrence(
    val frequency: Frequency,
    val interval: Int = 1,
    val weekdays: Set<Int> = emptySet(),
    val monthWeekOrdinal: Int? = null,
    val monthWeekday: Int? = null,
)

/** Something that syncs between devices: newest [updatedAt] wins, deletes are kept as tombstones. */
interface Syncable {
    val id: String
    val updatedAt: Long
    val deleted: Boolean
}

@Serializable
data class TaskList(
    override val id: String,
    val name: String,
    val colorArgb: Long,
    val sortOrder: Double = 0.0,
    override val updatedAt: Long,
    override val deleted: Boolean = false,
) : Syncable {
    companion object {
        const val INBOX_ID = "inbox"
    }
}

@Serializable
data class Task(
    override val id: String,
    val listId: String = TaskList.INBOX_ID,
    val title: String,
    val notes: String = "",
    /** Due date as [LocalDate.toEpochDay], or null for no date. */
    val dueEpochDay: Long? = null,
    /** Due time as minutes after midnight, or null for "any time that day". */
    val dueMinutes: Int? = null,
    /** Minutes before the due time to remind; 0 = at the due time; null = no reminder. */
    val reminderOffsetMinutes: Int? = null,
    val priority: Priority = Priority.NONE,
    val tags: List<String> = emptyList(),
    val recurrence: Recurrence? = null,
    val completed: Boolean = false,
    val completedAt: Long? = null,
    val sortOrder: Double = 0.0,
    val createdAt: Long,
    override val updatedAt: Long,
    override val deleted: Boolean = false,
) : Syncable {
    val dueDate: LocalDate? get() = dueEpochDay?.let(LocalDate::ofEpochDay)
    val dueTime: LocalTime? get() = dueMinutes?.let { LocalTime.of(it / 60, it % 60) }

    /** When the reminder should fire, or null if there is none. Date-only tasks remind at 09:00. */
    fun reminderAt(zone: ZoneId): Long? {
        val offset = reminderOffsetMinutes ?: return null
        val date = dueDate ?: return null
        val time = dueTime ?: LocalTime.of(DEFAULT_REMINDER_HOUR, 0)
        return LocalDateTime.of(date, time).minusMinutes(offset.toLong())
            .atZone(zone).toInstant().toEpochMilli()
    }

    companion object {
        const val DEFAULT_REMINDER_HOUR = 9
    }
}

@Serializable
data class Subtask(
    override val id: String,
    val taskId: String,
    val title: String,
    val done: Boolean = false,
    val sortOrder: Double = 0.0,
    override val updatedAt: Long,
    override val deleted: Boolean = false,
) : Syncable

@Serializable
data class DeviceInfo(
    val id: String,
    val name: String,
    val isTablet: Boolean = false,
    val lastSyncAt: Long,
)
