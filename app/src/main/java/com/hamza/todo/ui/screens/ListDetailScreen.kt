package com.hamza.todo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hamza.todo.core.model.Task
import com.hamza.todo.core.model.TaskList
import com.hamza.todo.core.views.SortMode
import com.hamza.todo.core.views.TaskViews
import com.hamza.todo.ui.MainViewModel
import com.hamza.todo.ui.components.ChoiceMenu
import com.hamza.todo.ui.components.EmptyState
import com.hamza.todo.ui.components.ListDot
import com.hamza.todo.ui.components.SectionHeader
import com.hamza.todo.ui.components.SwipeToDelete
import com.hamza.todo.ui.components.TaskRow
import com.hamza.todo.ui.theme.Todo
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun ListDetailScreen(
    vm: MainViewModel,
    listId: String,
    onBack: (() -> Unit)?,
    onOpenTask: (String) -> Unit,
    onAddTask: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val now by vm.now.collectAsStateWithLifecycle()
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val lists by vm.lists.collectAsStateWithLifecycle()
    val subtasks by vm.subtasks.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val today = now.toLocalDate()
    val listById = remember(lists) { lists.associateBy { it.id } }
    val list = listById[listId]
    val special = listId == ALL_TASKS || listId == COMPLETED_TASKS
    if (!special && list == null) {
        // The list was deleted (here or on another device).
        LaunchedEffect(Unit) { onBack?.invoke() }
        return
    }

    val sort = settings.listSort[listId] ?: if (special) SortMode.DUE_DATE else SortMode.MANUAL
    var showDone by rememberSaveable(listId) { mutableStateOf(settings.showCompleted) }
    val open = remember(tasks, listId, sort) {
        when (listId) {
            ALL_TASKS -> TaskViews.sort(tasks.filter { !it.completed }, sort)
            COMPLETED_TASKS -> emptyList()
            else -> TaskViews.inList(tasks, listId, sort)
        }
    }
    val done = remember(tasks, listId) {
        when (listId) {
            ALL_TASKS, COMPLETED_TASKS -> tasks.filter { it.completed }.sortedByDescending { it.completedAt ?: 0 }
            else -> TaskViews.completedInList(tasks, listId)
        }
    }
    val title = when (listId) {
        ALL_TASKS -> "All tasks"
        COMPLETED_TASKS -> "Completed"
        else -> list!!.name
    }

    // Local copy for smooth drag-and-drop; saved when the drag ends.
    var ordered by remember(open) { mutableStateOf(open) }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIdx = ordered.indexOfFirst { it.id == from.key }
        val toIdx = ordered.indexOfFirst { it.id == to.key }
        if (fromIdx >= 0 && toIdx >= 0) {
            ordered = ordered.toMutableList().apply { add(toIdx, removeAt(fromIdx)) }
        }
    }
    val canReorder = !special && sort == SortMode.MANUAL

    var menu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Box(Modifier.weight(1f))
            if (!special) {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "List options") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Rename or recolor") }, onClick = { menu = false; editing = true })
                        if (listId != TaskList.INBOX_ID) {
                            DropdownMenuItem(text = { Text("Delete list", color = Todo.colors.danger) }, onClick = { menu = false; confirmDelete = true })
                        }
                    }
                }
            }
        }
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (list != null) ListDot(Todo.colors.listColor(list.colorArgb), 14.dp)
                Text(title, style = MaterialTheme.typography.headlineMedium)
            }
            Text(
                if (listId == COMPLETED_TASKS) "${done.size} completed" else "${open.size} open task${if (open.size == 1) "" else "s"}",
                style = MaterialTheme.typography.bodyMedium,
                color = Todo.colors.muted,
            )
        }
        if (listId != COMPLETED_TASKS) {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    AssistChip(
                        onClick = { sortMenu = true },
                        label = { Text(sort.label) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, null, Modifier.size(18.dp)) },
                    )
                    val modes = if (special) SortMode.entries.filter { it != SortMode.MANUAL } else SortMode.entries
                    ChoiceMenu(sortMenu, modes, sort, { it.label }, { sortMenu = false }) { vm.setListSort(listId, it) }
                }
                FilterChip(
                    selected = showDone,
                    onClick = { showDone = !showDone },
                    label = { Text("Show completed") },
                    leadingIcon = if (showDone) ({ Icon(Icons.Filled.Check, null, Modifier.size(16.dp)) }) else null,
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (listId != COMPLETED_TASKS && ordered.isEmpty()) {
                item(key = "empty") { EmptyState("No open tasks", "Add one with the button below.") }
            }
            items(ordered, key = { it.id }) { t ->
                ReorderableItem(reorderState, key = t.id, enabled = canReorder) { _ ->
                    SwipeToDelete(onDelete = { vm.deleteTask(t.id) }) {
                        TaskRow(
                            task = t,
                            list = listById[t.listId],
                            subtasks = subtasks[t.id].orEmpty(),
                            today = today,
                            onToggle = { vm.toggleDone(t) },
                            onClick = { onOpenTask(t.id) },
                            showList = special,
                            trailing = if (canReorder) {
                                {
                                    IconButton(
                                        onClick = {},
                                        modifier = Modifier.draggableHandle(onDragStopped = { vm.reorder(ordered.map(Task::id)) }),
                                    ) { Icon(Icons.Filled.DragHandle, "Drag to reorder: ${t.title}", tint = Todo.colors.muted) }
                                }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
            if (!special) {
                item(key = "add") {
                    TextButton(onClick = { onAddTask(listId) }) {
                        Icon(Icons.Filled.Add, null)
                        Text("  Add a task to $title")
                    }
                }
            }
            if ((showDone || listId == COMPLETED_TASKS) && done.isNotEmpty()) {
                if (listId != COMPLETED_TASKS) item(key = "h-done") { SectionHeader("Completed · ${done.size}") }
                taskItems(done, listById, subtasks, today, vm, onOpenTask, null, showDate = true, showList = special)
            }
        }
    }

    if (editing && list != null) {
        ListDialog("Edit list", list.name, list.colorArgb, onDismiss = { editing = false }) { name, color ->
            vm.updateList(list.id, name, color)
            editing = false
        }
    }
    if (confirmDelete && list != null) {
        val count = tasks.count { it.listId == list.id }
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete “${list.name}”?") },
            text = { Text(if (count == 0) "This list is empty." else "Its $count task${if (count == 1) "" else "s"} will be deleted too.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteList(list.id)
                    onBack?.invoke()
                }) { Text("Delete", color = Todo.colors.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
