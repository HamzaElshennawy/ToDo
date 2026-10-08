package com.hamza.todo.core.export

import com.hamza.todo.core.model.Priority
import com.hamza.todo.core.recurrence.RecurrenceRules
import com.hamza.todo.core.sync.SyncSnapshot
import com.hamza.todo.core.views.DueFormatter

object Exporter {

    /** Full backup that can be restored with [readBackup]. Device records are left out. */
    fun toJson(snapshot: SyncSnapshot): String = snapshot.copy(devices = emptyList()).normalized().toJson(pretty = true)

    fun readBackup(text: String): SyncSnapshot = SyncSnapshot.fromJson(text).copy(devices = emptyList())

    /** One row per task (deleted tasks left out), for spreadsheets. */
    fun toCsv(snapshot: SyncSnapshot): String {
        val lists = snapshot.lists.associate { it.id to it.name }
        val subtasks = snapshot.subtasks.filter { !it.deleted }.groupBy { it.taskId }
        val header = listOf(
            "Title", "List", "Due date", "Due time", "Priority", "Tags", "Repeat",
            "Completed", "Notes", "Subtasks",
        )
        val rows = snapshot.tasks
            .filter { !it.deleted }
            .sortedWith(compareBy({ lists[it.listId] ?: "" }, { it.sortOrder }))
            .map { t ->
                listOf(
                    t.title,
                    lists[t.listId] ?: "",
                    t.dueDate?.toString() ?: "",
                    t.dueMinutes?.let(DueFormatter::time) ?: "",
                    if (t.priority == Priority.NONE) "" else t.priority.label,
                    t.tags.joinToString(" ") { "#$it" },
                    t.recurrence?.let(RecurrenceRules::describe) ?: "",
                    if (t.completed) "Yes" else "No",
                    t.notes,
                    subtasks[t.id].orEmpty().sortedBy { it.sortOrder }
                        .joinToString("; ") { (if (it.done) "[x] " else "[ ] ") + it.title },
                )
            }
        return (listOf(header) + rows).joinToString("\r\n") { row -> row.joinToString(",") { escape(it) } } + "\r\n"
    }

    private fun escape(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + field.replace("\"", "\"\"") + "\""
        else field
}
