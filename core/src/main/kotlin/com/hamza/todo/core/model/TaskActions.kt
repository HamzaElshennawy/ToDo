package com.hamza.todo.core.model

import com.hamza.todo.core.recurrence.RecurrenceRules
import java.time.LocalDate

data class Completion(
    val completed: Task,
    /** For a repeating task: the next occurrence, open, with its subtasks reset. */
    val next: Task? = null,
    val nextSubtasks: List<Subtask> = emptyList(),
)

object TaskActions {

    /**
     * Marks [task] done. A repeating task also gets its next occurrence, dated after its
     * current due date (or after today if it had none), with all subtasks unticked.
     */
    fun complete(
        task: Task,
        subtasks: List<Subtask>,
        now: Long,
        today: LocalDate,
        newId: () -> String,
    ): Completion {
        val done = task.copy(completed = true, completedAt = now, updatedAt = now)
        val rule = task.recurrence ?: return Completion(done)
        val from = task.dueDate ?: today
        var nextDate = RecurrenceRules.next(rule, from)
        // An overdue repeating task skips ahead to its first date after today.
        while (!nextDate.isAfter(today) && nextDate.isAfter(from)) {
            nextDate = RecurrenceRules.next(rule, nextDate)
        }
        val next = task.copy(
            id = newId(),
            dueEpochDay = nextDate.toEpochDay(),
            completed = false,
            completedAt = null,
            createdAt = now,
            updatedAt = now,
        )
        val nextSubs = subtasks.filter { !it.deleted }.map {
            it.copy(id = newId(), taskId = next.id, done = false, updatedAt = now)
        }
        // The finished occurrence no longer repeats; the new one carries the rule.
        return Completion(done.copy(recurrence = null), next, nextSubs)
    }

    fun uncomplete(task: Task, now: Long): Task = task.copy(completed = false, completedAt = null, updatedAt = now)
}
