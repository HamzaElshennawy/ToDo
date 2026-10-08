package com.hamza.todo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hamza.todo.core.model.Subtask
import com.hamza.todo.core.model.Task
import com.hamza.todo.core.views.DueFormatter
import com.hamza.todo.ui.MainViewModel
import com.hamza.todo.ui.components.Card
import com.hamza.todo.ui.components.ChoiceMenu
import com.hamza.todo.ui.components.DueDialog
import com.hamza.todo.ui.components.EmptyState
import com.hamza.todo.ui.components.ListDot
import com.hamza.todo.ui.components.PrioritySelector
import com.hamza.todo.ui.components.REMINDER_OPTIONS
import com.hamza.todo.ui.components.SectionHeader
import com.hamza.todo.ui.components.TaskCheck
import com.hamza.todo.ui.components.repeatLabel
import com.hamza.todo.ui.components.repeatOptions
import com.hamza.todo.ui.theme.Todo
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val stamp = DateTimeFormatter.ofPattern("EEE, d MMM 'at' HH:mm", Locale.ENGLISH)

/** Full-screen on phones; the right-hand pane on tablets (then [onBack] is null). */
@Composable
fun TaskDetailScreen(
    vm: MainViewModel,
    taskId: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val task = tasks.firstOrNull { it.id == taskId }
    if (task == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState("Task not found", "It may have been deleted, here or on another device.")
        }
        return
    }
    TaskDetail(vm, task, onBack, modifier)
}

@Composable
private fun TaskDetail(vm: MainViewModel, task: Task, onBack: (() -> Unit)?, modifier: Modifier) {
    val now by vm.now.collectAsStateWithLifecycle()
    val lists by vm.lists.collectAsStateWithLifecycle()
    val allSubtasks by vm.subtasks.collectAsStateWithLifecycle()
    val subtasks = allSubtasks[task.id].orEmpty()
    val today = now.toLocalDate()
    val list = lists.firstOrNull { it.id == task.listId }

    // Text fields keep their own state so typing is smooth; every change is saved.
    var title by rememberSaveable(task.id) { mutableStateOf(task.title) }
    var notes by rememberSaveable(task.id) { mutableStateOf(task.notes) }
    var newSubtask by rememberSaveable(task.id) { mutableStateOf("") }
    var newTag by rememberSaveable(task.id) { mutableStateOf("") }
    var dueDialog by remember { mutableStateOf(false) }
    var reminderMenu by remember { mutableStateOf(false) }
    var repeatMenu by remember { mutableStateOf(false) }
    var listMenu by remember { mutableStateOf(false) }

    fun update(transform: (Task) -> Task) = vm.updateTask(task.id, transform)

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Box(Modifier.weight(1f))
            IconButton(onClick = {
                vm.deleteTask(task.id)
                onBack?.invoke()
            }) { Icon(Icons.Outlined.Delete, "Delete task") }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 12.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                TaskCheck(task.completed, task.priority, task.title, onToggle = { vm.toggleDone(task) }, size = 28.dp)
                Column(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BasicTextField(
                        value = title,
                        onValueChange = {
                            title = it
                            if (it.isNotBlank()) update { t -> t.copy(title = it.trim()) }
                        },
                        textStyle = MaterialTheme.typography.headlineMedium.copy(
                            color = if (task.completed) Todo.colors.muted else MaterialTheme.colorScheme.onSurface,
                            textDecoration = if (task.completed) TextDecoration.LineThrough else null,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Task title" },
                    )
                    BasicTextField(
                        value = notes,
                        onValueChange = {
                            notes = it
                            update { t -> t.copy(notes = it) }
                        },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Notes" },
                        decorationBox = { inner ->
                            if (notes.isEmpty()) Text("Add notes", style = MaterialTheme.typography.bodyLarge, color = Todo.colors.muted)
                            inner()
                        },
                    )
                }
            }

            Card(Modifier.padding(start = 8.dp)) {
                FieldRow(Icons.Outlined.CalendarMonth, "Due", task.dueDate?.let { DueFormatter.label(it, task.dueMinutes, today) } ?: "No date") { dueDialog = true }
                Box {
                    FieldRow(Icons.Outlined.Notifications, "Reminder", if (task.dueDate == null) "Set a date first" else reminderText(task), divider = true) {
                        if (task.dueDate != null) reminderMenu = true
                    }
                    ChoiceMenu(reminderMenu, REMINDER_OPTIONS, task.reminderOffsetMinutes, { DueFormatter.reminder(it) }, { reminderMenu = false }) { offset ->
                        update { it.copy(reminderOffsetMinutes = offset) }
                    }
                }
                Box {
                    FieldRow(Icons.Outlined.Repeat, "Repeat", repeatLabel(task.recurrence), divider = true) { repeatMenu = true }
                    val options = (repeatOptions(task.dueDate) + listOfNotNull(task.recurrence)).distinct()
                    ChoiceMenu(repeatMenu, options, task.recurrence, ::repeatLabel, { repeatMenu = false }) { rule ->
                        update { t ->
                            // A repeating task needs a date; start it today if it has none.
                            if (rule != null && t.dueEpochDay == null) t.copy(recurrence = rule, dueEpochDay = today.toEpochDay())
                            else t.copy(recurrence = rule)
                        }
                    }
                }
                Box {
                    Column {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Todo.colors.line))
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable { listMenu = true }.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                                ListDot(Todo.colors.listColor(list?.colorArgb ?: 0xFF6B7280), 12.dp)
                            }
                            Column {
                                Text("List", style = MaterialTheme.typography.bodySmall, color = Todo.colors.muted)
                                Text(list?.name ?: "Inbox", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                            }
                        }
                    }
                    ChoiceMenu(listMenu, lists, list, { it?.name ?: "" }, { listMenu = false }) { chosen ->
                        if (chosen != null) update { it.copy(listId = chosen.id) }
                    }
                }
            }

            Column(Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("Priority", Modifier.padding(start = 0.dp))
                PrioritySelector(task.priority, onSelect = { p -> update { it.copy(priority = p) } })
            }

            Column(Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("Tags", Modifier.padding(start = 0.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    task.tags.forEach { tag ->
                        InputChip(
                            selected = false,
                            onClick = { update { it.copy(tags = it.tags - tag) } },
                            label = { Text("#$tag") },
                            trailingIcon = { Icon(Icons.Filled.Close, "Remove tag $tag", Modifier.size(16.dp)) },
                        )
                    }
                }
                OutlinedTextField(
                    value = newTag,
                    onValueChange = { newTag = it.replace(" ", "").removePrefix("#") },
                    placeholder = { Text("Add a tag") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        val tag = newTag.trim().lowercase()
                        if (tag.isNotEmpty()) update { it.copy(tags = (it.tags + tag).distinct()) }
                        newTag = ""
                    }),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Column(Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(
                    if (subtasks.isEmpty()) "Subtasks" else "Subtasks · ${subtasks.count { it.done }}/${subtasks.size}",
                    Modifier.padding(start = 0.dp),
                )
                Card {
                    subtasks.forEach { s -> SubtaskRow(s, onToggle = { vm.toggleSubtask(s) }, onDelete = { vm.deleteSubtask(s.id) }) }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp))
                        BasicTextField(
                            value = newSubtask,
                            onValueChange = { newSubtask = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, capitalization = KeyboardCapitalization.Sentences),
                            keyboardActions = KeyboardActions(onDone = {
                                vm.addSubtask(task.id, newSubtask)
                                newSubtask = ""
                            }),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).padding(vertical = 14.dp).semantics { contentDescription = "Add subtask" },
                            decorationBox = { inner ->
                                if (newSubtask.isEmpty()) Text("Add subtask", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
                                inner()
                            },
                        )
                        if (newSubtask.isNotBlank()) {
                            TextButton(onClick = {
                                vm.addSubtask(task.id, newSubtask)
                                newSubtask = ""
                            }) { Text("Add") }
                        }
                    }
                }
            }

            Text(
                "Created ${format(task.createdAt)} · Edited ${format(task.updatedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = Todo.colors.muted,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
    }

    if (dueDialog) {
        DueDialog(task.dueDate, task.dueMinutes, onDismiss = { dueDialog = false }) { date, minutes ->
            update {
                if (date == null) it.copy(dueEpochDay = null, dueMinutes = null, reminderOffsetMinutes = null, recurrence = null)
                else it.copy(dueEpochDay = date.toEpochDay(), dueMinutes = minutes)
            }
            dueDialog = false
        }
    }
}

private fun reminderText(task: Task): String {
    val offset = task.reminderOffsetMinutes ?: return "None"
    val at = task.reminderAt(ZoneId.systemDefault()) ?: return DueFormatter.reminder(offset)
    val time = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalTime()
    return "${DueFormatter.reminder(offset)} (${DueFormatter.time(time.hour * 60 + time.minute)})"
}

private fun format(millis: Long): String =
    if (millis <= 0) "—" else Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(stamp)

@Composable
private fun FieldRow(icon: ImageVector, label: String, value: String, divider: Boolean = false, onClick: () -> Unit) {
    Column {
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(Todo.colors.line))
        Row(
            Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = onClick).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(icon, null, tint = Todo.colors.muted, modifier = Modifier.size(22.dp))
            Column {
                Text(label, style = MaterialTheme.typography.bodySmall, color = Todo.colors.muted)
                Text(value, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
            }
        }
    }
}

@Composable
private fun SubtaskRow(sub: Subtask, onToggle: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Checkbox, onClick = onToggle).padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .then(
                    if (sub.done) Modifier.background(MaterialTheme.colorScheme.primary)
                    else Modifier.border(2.dp, Todo.colors.muted, RoundedCornerShape(6.dp)),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (sub.done) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
        }
        Text(
            sub.title,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = if (sub.done) Todo.colors.muted else MaterialTheme.colorScheme.onSurface,
            textDecoration = if (sub.done) TextDecoration.LineThrough else null,
        )
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Close, "Delete subtask ${sub.title}", tint = Todo.colors.muted) }
    }
}
