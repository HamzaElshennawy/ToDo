package com.hamza.todo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hamza.todo.core.model.Priority
import com.hamza.todo.core.model.Task
import com.hamza.todo.core.model.TaskList
import com.hamza.todo.core.views.DueFormatter
import com.hamza.todo.core.views.TaskViews
import com.hamza.todo.ui.MainViewModel
import com.hamza.todo.ui.components.EmptyState
import com.hamza.todo.ui.components.ListDot
import com.hamza.todo.ui.components.ScreenTitle
import com.hamza.todo.ui.components.TaskCheck
import com.hamza.todo.ui.theme.Todo
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthTitle = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
private val dayName = DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)
private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val weekdayShort = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)

@Composable
fun UpcomingScreen(
    vm: MainViewModel,
    wide: Boolean,
    onOpenTask: (String) -> Unit,
    onAddOnDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (wide) WeekBoard(vm, onOpenTask, onAddOnDay, modifier) else UpcomingList(vm, onOpenTask, modifier)
}

@Composable
private fun UpcomingList(vm: MainViewModel, onOpenTask: (String) -> Unit, modifier: Modifier) {
    val now by vm.now.collectAsStateWithLifecycle()
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val lists by vm.lists.collectAsStateWithLifecycle()
    val subtasks by vm.subtasks.collectAsStateWithLifecycle()
    val today = now.toLocalDate()
    var daysAhead by rememberSaveable { mutableStateOf(14L) }
    val groups = remember(tasks, today, daysAhead) { TaskViews.upcoming(tasks, today, today.plusDays(daysAhead)) }
    val later = remember(tasks, today, daysAhead) { tasks.count { !it.completed && it.dueDate?.isAfter(today.plusDays(daysAhead)) == true } }
    val listById = remember(lists) { lists.associateBy { it.id } }
    val strip = (0L until 7L).map { today.plusDays(it) }
    val withTasks = remember(tasks) { tasks.filter { !it.completed }.mapNotNull { it.dueDate }.toSet() }
    var selected by rememberSaveable { mutableLongStateOf(today.plusDays(1).toEpochDay()) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Index of each day's header in the LazyColumn below (title + strip come first).
    val headerIndex = remember(groups) {
        var i = 2
        groups.associate { g -> (g.date to i).also { i += 1 + g.tasks.size } }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "title") { ScreenTitle("Upcoming", today.format(monthTitle)) }
        item(key = "strip") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                strip.forEach { day ->
                    val isSelected = day.toEpochDay() == selected
                    Column(
                        Modifier
                            .weight(1f)
                            .height(72.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else Todo.colors.card)
                            .clickable(role = Role.Tab) {
                                selected = day.toEpochDay()
                                headerIndex[day]?.let { scope.launch { listState.animateScrollToItem(it) } }
                            }
                            .semantics { contentDescription = day.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH)) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        val fg = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        Text(day.format(weekdayShort), style = MaterialTheme.typography.labelSmall, color = fg)
                        Text("${day.dayOfMonth}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = fg)
                        Box(
                            Modifier.size(5.dp).clip(CircleShape).background(
                                when {
                                    day !in withTasks -> androidx.compose.ui.graphics.Color.Transparent
                                    isSelected -> MaterialTheme.colorScheme.onPrimary
                                    else -> MaterialTheme.colorScheme.primary
                                },
                            ),
                        )
                    }
                }
            }
        }
        if (groups.isEmpty()) {
            item(key = "empty") { EmptyState("Nothing coming up", "Tasks with a date after today show up here.") }
        }
        groups.forEach { g ->
            item(key = "h-${g.date}") {
                Row(Modifier.padding(start = 12.dp, top = 12.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val name = if (g.date == today.plusDays(1)) "Tomorrow" else g.date.format(dayName)
                    Text(name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(g.date.format(dayMonth), style = MaterialTheme.typography.bodySmall, color = Todo.colors.muted)
                }
            }
            taskItems(g.tasks, listById, subtasks, today, vm, onOpenTask, null, showDate = false)
        }
        if (later > 0) {
            item(key = "later") {
                OutlinedButton(onClick = { daysAhead += 60 }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text("Show $later later task${if (later == 1) "" else "s"}")
                }
            }
        }
    }
}

/** Tablet: the week as 7 columns, with an overdue banner. */
@Composable
private fun WeekBoard(vm: MainViewModel, onOpenTask: (String) -> Unit, onAddOnDay: (LocalDate) -> Unit, modifier: Modifier) {
    val now by vm.now.collectAsStateWithLifecycle()
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val lists by vm.lists.collectAsStateWithLifecycle()
    val today = now.toLocalDate()
    var weekStart by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    val start = LocalDate.ofEpochDay(weekStart)
    val days = remember(tasks, weekStart) { TaskViews.week(tasks, start) }
    val overdue = remember(tasks, today) { tasks.filter { !it.completed && it.dueDate?.isBefore(today) == true } }
    val listById = remember(lists) { lists.associateBy { it.id } }
    val end = start.plusDays(6)

    Column(modifier.fillMaxSize().padding(end = 24.dp, bottom = 24.dp)) {
        ScreenTitle(
            "Upcoming",
            "${start.dayOfMonth} ${start.format(DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH))} – ${end.dayOfMonth} ${end.format(monthTitle)}",
            Modifier.padding(start = 0.dp),
        ) {
            OutlinedButton(onClick = { weekStart = today.toEpochDay() }, modifier = Modifier.padding(end = 8.dp)) { Text("This week") }
            IconButton(onClick = { weekStart -= 7 }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous week") }
            IconButton(onClick = { weekStart += 7 }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next week") }
        }

        if (overdue.isNotEmpty()) {
            Row(
                Modifier
                    .padding(start = 24.dp, bottom = 12.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Todo.colors.dangerContainer)
                    .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Outlined.Schedule, null, tint = Todo.colors.danger)
                Text(
                    "${overdue.size} overdue: " + overdue.joinToString(", ") { it.title },
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Button(
                    onClick = { vm.moveToToday(overdue.map { it.id }) },
                    colors = ButtonDefaults.buttonColors(containerColor = Todo.colors.danger),
                ) { Text("Move to today") }
            }
        }

        Row(Modifier.weight(1f).padding(start = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            days.forEach { day ->
                val isToday = day.date == today
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isToday) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            day.date.format(weekdayShort).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer else Todo.colors.muted,
                        )
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(if (isToday) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${day.date.dayOfMonth}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (day.tasks.isEmpty()) {
                            item {
                                Text(
                                    "Nothing planned",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Todo.colors.muted,
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                )
                            }
                        }
                        items(day.tasks, key = { it.id }) { t ->
                            BoardCard(t, listById[t.listId], onToggle = { vm.toggleDone(t) }, onClick = { onOpenTask(t.id) })
                        }
                    }
                    TextButton(onClick = { onAddOnDay(day.date) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Add, null, Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Add")
                    }
                }
            }
        }
    }
}

@Composable
private fun BoardCard(task: Task, list: TaskList?, onToggle: () -> Unit, onClick: () -> Unit) {
    val colors = Todo.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.card)
            .clickable(onClick = onClick)
            .padding(end = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(width = 36.dp, height = 44.dp), contentAlignment = Alignment.Center) {
            val ring = colors.priority(task.priority)
            Box(
                Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .then(if (task.completed) Modifier.background(MaterialTheme.colorScheme.primary) else Modifier.border(2.dp, ring, CircleShape))
                    .clickable(role = Role.Checkbox, onClick = onToggle)
                    .semantics { contentDescription = "Mark as done: ${task.title}" },
                contentAlignment = Alignment.Center,
            ) {
                if (task.completed) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(12.dp))
            }
        }
        Column(Modifier.weight(1f).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                textDecoration = if (task.completed) TextDecoration.LineThrough else null,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                task.dueMinutes?.let {
                    Text(DueFormatter.time(it), style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = colors.muted)
                }
                if (list != null) {
                    ListDot(colors.listColor(list.colorArgb), 7.dp)
                    Text(list.name, style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp), color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (task.priority != Priority.NONE) {
                Text(task.priority.label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = colors.priority(task.priority))
            }
        }
    }
}
