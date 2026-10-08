package com.hamza.todo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hamza.todo.core.model.Priority
import com.hamza.todo.core.model.Recurrence
import com.hamza.todo.core.model.TaskList
import com.hamza.todo.core.parse.DetectedKind
import com.hamza.todo.core.parse.QuickAddParser
import com.hamza.todo.core.views.DueFormatter
import com.hamza.todo.data.NewTask
import com.hamza.todo.ui.components.ChoiceMenu
import com.hamza.todo.ui.components.DueDialog
import com.hamza.todo.ui.components.ListDot
import com.hamza.todo.ui.components.PrioritySelector
import com.hamza.todo.ui.components.REMINDER_OPTIONS
import com.hamza.todo.ui.components.repeatLabel
import com.hamza.todo.ui.components.repeatOptions
import com.hamza.todo.ui.theme.Todo
import java.time.LocalDate
import java.time.LocalDateTime

/** Where the add-task UI was opened from, to pre-fill the date and list. */
data class AddTaskRequest(val date: LocalDate? = null, val listId: String? = null)

/** Bottom sheet on phones, centered dialog on tablets. */
@Composable
fun AddTaskHost(
    request: AddTaskRequest,
    asDialog: Boolean,
    lists: List<TaskList>,
    defaultListId: String,
    now: LocalDateTime,
    parser: QuickAddParser,
    onDismiss: () -> Unit,
    onAdd: (NewTask, List<String>) -> Unit,
) {
    if (asDialog) {
        Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Todo.colors.card,
                modifier = Modifier.widthIn(max = 600.dp).padding(24.dp),
            ) {
                AddTaskForm(request, lists, defaultListId, now, parser, onDismiss, onAdd, dialog = true)
            }
        }
    } else {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Todo.colors.card) {
            AddTaskForm(request, lists, defaultListId, now, parser, onDismiss, onAdd, dialog = false)
        }
    }
}

@Composable
private fun AddTaskForm(
    request: AddTaskRequest,
    lists: List<TaskList>,
    defaultListId: String,
    now: LocalDateTime,
    parser: QuickAddParser,
    onDismiss: () -> Unit,
    onAdd: (NewTask, List<String>) -> Unit,
    dialog: Boolean,
) {
    var text by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val disabled = remember { mutableStateListOf<DetectedKind>() }
    // Choices made with the buttons win over what was typed.
    var manualDue by remember { mutableStateOf<Pair<LocalDate?, Int?>?>(null) }
    var manualListId by remember { mutableStateOf(request.listId) }
    var manualPriority by remember { mutableStateOf<Priority?>(null) }
    var manualRepeat by remember { mutableStateOf<Pair<Recurrence?, Boolean>?>(null) }
    var reminder by remember { mutableStateOf<Int?>(null) }
    val subtasks = remember { mutableStateListOf<String>() }
    var showSubtasks by remember { mutableStateOf(false) }
    var dueDialog by remember { mutableStateOf(false) }
    var reminderMenu by remember { mutableStateOf(false) }
    var repeatMenu by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    val parsed = remember(text, disabled.toList(), now.toLocalDate()) { parser.parse(text, now, disabled.toSet()) }
    val today = now.toLocalDate()
    val md = manualDue
    val dueDate = if (md != null) md.first else parsed.dueDate ?: request.date
    val dueMinutes = if (md != null) md.second else parsed.dueMinutes
    val listId = manualListId ?: parsed.listName?.let { name -> lists.firstOrNull { it.name == name }?.id } ?: defaultListId
    val priority = manualPriority ?: parsed.priority ?: Priority.NONE
    val mr = manualRepeat
    val repeat = if (mr != null) mr.first else parsed.recurrence
    val canAdd = parsed.title.isNotBlank()

    fun submit() {
        if (!canAdd) return
        onAdd(
            NewTask(
                title = parsed.title,
                notes = notes,
                listId = listId,
                dueEpochDay = dueDate?.toEpochDay(),
                dueMinutes = if (dueDate == null) null else dueMinutes,
                reminderOffsetMinutes = if (dueDate == null) null else reminder,
                priority = priority,
                tags = parsed.tags,
                recurrence = repeat,
            ),
            subtasks.filter { it.isNotBlank() },
        )
        onDismiss()
    }

    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (dialog) 28.dp else 20.dp)
            .padding(top = if (dialog) 24.dp else 0.dp, bottom = 20.dp)
            .then(if (dialog) Modifier else Modifier.navigationBarsPadding().imePadding())
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.Enter && e.isCtrlPressed) {
                    submit()
                    true
                } else {
                    false
                }
            },
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (dialog) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("New task", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Close") }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(if (dialog) "Task" else "New task") },
                placeholder = { Text("e.g. Call mom tomorrow 5pm !high") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            if (parsed.detected.isNotEmpty()) {
                Text("Detected from your text", style = MaterialTheme.typography.labelMedium, color = Todo.colors.muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    parsed.detected.forEach { d ->
                        InputChip(
                            selected = true,
                            onClick = { disabled += d.kind },
                            label = { Text(d.label) },
                            leadingIcon = { Icon(iconFor(d.kind), null, Modifier.size(16.dp)) },
                            trailingIcon = { Icon(Icons.Filled.Close, "Remove ${d.label}", Modifier.size(16.dp)) },
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes") },
            placeholder = { Text("Add details") },
            minLines = 2,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("List", style = MaterialTheme.typography.labelMedium, color = Todo.colors.muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                lists.forEach { l ->
                    FilterChip(
                        selected = l.id == listId,
                        onClick = { manualListId = l.id },
                        label = { Text(l.name) },
                        leadingIcon = { ListDot(Todo.colors.listColor(l.colorArgb), 10.dp) },
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Priority", style = MaterialTheme.typography.labelMedium, color = Todo.colors.muted)
            PrioritySelector(priority, onSelect = { manualPriority = it })
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = { dueDialog = true },
                label = { Text(dueDate?.let { DueFormatter.label(it, dueMinutes, today) } ?: "Date") },
                leadingIcon = { Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(18.dp)) },
            )
            Box {
                AssistChip(
                    onClick = { reminderMenu = true },
                    enabled = dueDate != null,
                    label = { Text("Reminder: " + DueFormatter.reminder(reminder).lowercase()) },
                    leadingIcon = { Icon(Icons.Outlined.Notifications, null, Modifier.size(18.dp)) },
                )
                ChoiceMenu(reminderMenu, REMINDER_OPTIONS, reminder, { DueFormatter.reminder(it) }, { reminderMenu = false }) { reminder = it }
            }
            Box {
                AssistChip(
                    onClick = { repeatMenu = true },
                    label = { Text(repeatLabel(repeat)) },
                    leadingIcon = { Icon(Icons.Outlined.Repeat, null, Modifier.size(18.dp)) },
                )
                ChoiceMenu(repeatMenu, repeatOptions(dueDate), repeat, ::repeatLabel, { repeatMenu = false }) { manualRepeat = it to true }
            }
            AssistChip(
                onClick = {
                    showSubtasks = true
                    if (subtasks.isEmpty()) subtasks += ""
                },
                label = { Text("Add subtasks") },
                leadingIcon = { Icon(Icons.Outlined.Checklist, null, Modifier.size(18.dp)) },
            )
        }

        if (showSubtasks) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                subtasks.forEachIndexed { i, value ->
                    OutlinedTextField(
                        value = value,
                        onValueChange = { subtasks[i] = it },
                        placeholder = { Text("Subtask ${i + 1}") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { if (i == subtasks.lastIndex && value.isNotBlank()) subtasks += "" }),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TextButton(onClick = { subtasks += "" }) { Text("Add another") }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (dialog) {
                Text("Ctrl + Enter to add", style = MaterialTheme.typography.bodySmall, color = Todo.colors.muted)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Cancel") }
            } else {
                Spacer(Modifier.weight(1f))
            }
            Button(onClick = { submit() }, enabled = canAdd) {
                Text("Add task", fontWeight = FontWeight.Bold)
                if (!dialog) {
                    Spacer(Modifier.size(8.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp))
                }
            }
        }
    }

    if (dueDialog) {
        DueDialog(dueDate, dueMinutes, onDismiss = { dueDialog = false }) { d, m ->
            manualDue = d to m
            dueDialog = false
        }
    }
}

private fun iconFor(kind: DetectedKind) = when (kind) {
    DetectedKind.DATE -> Icons.Outlined.CalendarMonth
    DetectedKind.PRIORITY -> Icons.Outlined.Flag
    DetectedKind.LIST -> Icons.Outlined.Checklist
    DetectedKind.TAG -> Icons.Outlined.Sell
    DetectedKind.REPEAT -> Icons.Outlined.Repeat
}
