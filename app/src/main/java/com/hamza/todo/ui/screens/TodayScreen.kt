package com.hamza.todo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hamza.todo.core.model.Subtask
import com.hamza.todo.core.model.Task
import com.hamza.todo.core.model.TaskList
import com.hamza.todo.core.views.TaskViews
import com.hamza.todo.ui.MainViewModel
import com.hamza.todo.ui.components.EmptyState
import com.hamza.todo.ui.components.ProgressCard
import com.hamza.todo.ui.components.ScreenTitle
import com.hamza.todo.ui.components.SectionHeader
import com.hamza.todo.ui.components.SwipeToDelete
import com.hamza.todo.ui.components.TaskRow
import com.hamza.todo.ui.theme.Todo
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayTitle = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)

@Composable
fun TodayScreen(
    vm: MainViewModel,
    onOpenTask: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    selectedTaskId: String? = null,
) {
    val now by vm.now.collectAsStateWithLifecycle()
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val lists by vm.lists.collectAsStateWithLifecycle()
    val subtasks by vm.subtasks.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val today = now.toLocalDate()
    val startOfDay = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val view = remember(tasks, today, settings.overdueFirst) { TaskViews.today(tasks, today, startOfDay, settings.overdueFirst) }
    val listById = remember(lists) { lists.associateBy { it.id } }
    var showDone by rememberSaveable { mutableStateOf(true) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "title") {
            ScreenTitle("Today", today.format(dayTitle), Modifier.padding(horizontal = 0.dp)) {
                IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, "Search tasks") }
            }
        }
        item(key = "progress") { ProgressCard(view.doneCount, view.total, Modifier.padding(horizontal = 4.dp)) }

        if (view.overdue.isEmpty() && view.today.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    if (view.doneCount > 0) "All done for today" else "Nothing due today",
                    "Tasks due today show up here. Tap New task to add one.",
                )
            }
        }
        if (view.overdue.isNotEmpty()) {
            item(key = "h-overdue") { SectionHeader("Overdue · ${view.overdue.size}", color = Todo.colors.danger) }
            taskItems(view.overdue, listById, subtasks, today, vm, onOpenTask, selectedTaskId, showDate = true)
        }
        if (view.today.isNotEmpty()) {
            item(key = "h-today") { SectionHeader(if (settings.overdueFirst) "Today · ${view.today.size}" else "To do · ${view.today.size}") }
            taskItems(view.today, listById, subtasks, today, vm, onOpenTask, selectedTaskId, showDate = !settings.overdueFirst)
        }
        if (settings.showCompleted && view.completedToday.isNotEmpty()) {
            item(key = "h-done") {
                TextButton(onClick = { showDone = !showDone }, modifier = Modifier.heightIn(min = 44.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "COMPLETED · ${view.completedToday.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Todo.colors.muted,
                        )
                        Icon(
                            if (showDone) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            if (showDone) "Hide completed" else "Show completed",
                            tint = Todo.colors.muted,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            if (showDone) taskItems(view.completedToday, listById, subtasks, today, vm, onOpenTask, selectedTaskId, showDate = false)
        }
    }
}

/** Task cards with swipe-to-delete, shared by the list screens. */
fun LazyListScope.taskItems(
    tasks: List<Task>,
    listById: Map<String, TaskList>,
    subtasks: Map<String, List<Subtask>>,
    today: LocalDate,
    vm: MainViewModel,
    onOpenTask: (String) -> Unit,
    selectedTaskId: String?,
    showDate: Boolean,
    showList: Boolean = true,
) {
    items(tasks, key = { it.id }) { t ->
        SwipeToDelete(onDelete = { vm.deleteTask(t.id) }) {
            TaskRow(
                task = t,
                list = listById[t.listId],
                subtasks = subtasks[t.id].orEmpty(),
                today = today,
                onToggle = { vm.toggleDone(t) },
                onClick = { onOpenTask(t.id) },
                showDate = showDate,
                showList = showList,
                selected = t.id == selectedTaskId,
            )
        }
    }
}
