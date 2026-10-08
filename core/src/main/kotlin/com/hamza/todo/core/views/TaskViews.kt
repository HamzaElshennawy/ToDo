package com.hamza.todo.core.views

import com.hamza.todo.core.model.Task
import java.time.LocalDate

enum class SortMode(val label: String) {
    MANUAL("Manual order"),
    DUE_DATE("Due date"),
    PRIORITY("Priority"),
    CREATED("Date created"),
    ALPHABETICAL("Alphabetical"),
}

data class TodayView(
    val overdue: List<Task>,
    val today: List<Task>,
    val completedToday: List<Task>,
) {
    val total: Int get() = overdue.size + today.size + completedToday.size
    val doneCount: Int get() = completedToday.size
}

data class DayGroup(val date: LocalDate, val tasks: List<Task>)

/** Pure functions that turn the task table into what each screen shows. */
object TaskViews {

    fun active(tasks: List<Task>) = tasks.filter { !it.deleted }

    /**
     * Today: open tasks due before today (overdue), open tasks due today, and tasks
     * completed today (whatever their due date) so progress can be shown.
     */
    fun today(tasks: List<Task>, today: LocalDate, completedSinceMillis: Long, overdueFirst: Boolean = true): TodayView {
        val live = active(tasks)
        val open = live.filter { !it.completed }
        val overdue = open.filter { it.dueDate?.isBefore(today) == true }.sortedWith(byDue)
        val dueToday = open.filter { it.dueDate == today }.sortedWith(byDue)
        val done = live.filter { it.completed && (it.completedAt ?: 0) >= completedSinceMillis }
            .sortedByDescending { it.completedAt ?: 0 }
        return if (overdueFirst) TodayView(overdue, dueToday, done)
        else TodayView(emptyList(), (overdue + dueToday).sortedWith(byDue), done)
    }

    /** Open tasks grouped by due date, from tomorrow up to and including [until]. */
    fun upcoming(tasks: List<Task>, today: LocalDate, until: LocalDate): List<DayGroup> =
        active(tasks)
            .filter { !it.completed }
            .mapNotNull { t -> t.dueDate?.let { d -> if (d.isAfter(today) && !d.isAfter(until)) d to t else null } }
            .groupBy({ it.first }, { it.second })
            .toSortedMap()
            .map { (d, ts) -> DayGroup(d, ts.sortedWith(byDue)) }

    /** Open tasks due on each of the 7 days starting at [weekStart] (for the tablet week board). */
    fun week(tasks: List<Task>, weekStart: LocalDate): List<DayGroup> {
        val open = active(tasks).filter { !it.completed }
        return (0L until 7L).map { offset ->
            val day = weekStart.plusDays(offset)
            DayGroup(day, open.filter { it.dueDate == day }.sortedWith(byDue))
        }
    }

    fun inList(tasks: List<Task>, listId: String, sort: SortMode): List<Task> =
        sort(active(tasks).filter { it.listId == listId && !it.completed }, sort)

    fun completedInList(tasks: List<Task>, listId: String): List<Task> =
        active(tasks).filter { it.listId == listId && it.completed }.sortedByDescending { it.completedAt ?: 0 }

    fun sort(tasks: List<Task>, mode: SortMode): List<Task> = when (mode) {
        SortMode.MANUAL -> tasks.sortedBy { it.sortOrder }
        SortMode.DUE_DATE -> tasks.sortedWith(byDue)
        SortMode.PRIORITY -> tasks.sortedWith(compareByDescending<Task> { it.priority.level }.then(byDue))
        SortMode.CREATED -> tasks.sortedByDescending { it.createdAt }
        SortMode.ALPHABETICAL -> tasks.sortedBy { it.title.lowercase() }
    }

    /** Matches the words of [query] against title, notes and tags; a word starting with # must be a tag. */
    fun search(tasks: List<Task>, query: String, includeCompleted: Boolean = true): List<Task> {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        return active(tasks)
            .filter { includeCompleted || !it.completed }
            .filter { t ->
                val text = (t.title + " " + t.notes).lowercase()
                val tags = t.tags.map { it.lowercase() }
                words.all { w ->
                    if (w.startsWith("#")) w.removePrefix("#") in tags
                    else w in text || tags.any { it.contains(w) }
                }
            }
            .sortedWith(compareBy<Task> { it.completed }.then(byDue))
    }

    fun allTags(tasks: List<Task>): List<String> =
        active(tasks).flatMap { it.tags }.map { it.lowercase() }.distinct().sorted()

    /** Due date first (no date last), then time (no time last), then priority, then manual order. */
    val byDue: Comparator<Task> = compareBy<Task>(
        { it.dueEpochDay ?: Long.MAX_VALUE },
        { it.dueMinutes ?: Int.MAX_VALUE },
        { -it.priority.level },
        { it.sortOrder },
    )
}
