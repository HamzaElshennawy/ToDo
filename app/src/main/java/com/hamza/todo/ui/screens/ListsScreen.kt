package com.hamza.todo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hamza.todo.core.model.TaskList
import com.hamza.todo.data.TodoRepository
import com.hamza.todo.ui.MainViewModel
import com.hamza.todo.ui.components.ColorChoices
import com.hamza.todo.ui.components.ListDot
import com.hamza.todo.ui.components.ScreenTitle
import com.hamza.todo.ui.components.SectionHeader
import com.hamza.todo.ui.theme.Todo

const val ALL_TASKS = "all"
const val COMPLETED_TASKS = "completed"

@Composable
fun ListsScreen(
    vm: MainViewModel,
    onOpenList: (String) -> Unit,
    onOpenToday: () -> Unit,
    onOpenUpcoming: () -> Unit,
    onSettings: (() -> Unit)?,
    modifier: Modifier = Modifier,
    selectedListId: String? = null,
    compact: Boolean = false,
) {
    val now by vm.now.collectAsStateWithLifecycle()
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val lists by vm.lists.collectAsStateWithLifecycle()
    val today = now.toLocalDate()
    val open = remember(tasks) { tasks.filter { !it.completed } }
    val todayCount = open.count { it.dueDate?.isAfter(today) == false }
    val upcomingCount = open.count { it.dueDate?.isAfter(today) == true }
    val counts = remember(open) { open.groupingBy { it.listId }.eachCount() }
    var newList by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenTitle("Lists", modifier = Modifier.padding(horizontal = 0.dp)) {
                if (onSettings != null) IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Settings") }
            }
        }
        if (compact) {
            item { SectionHeader("Smart views") }
            item { NavRow(Icons.Outlined.WbSunny, MaterialTheme.colorScheme.primary, "Today", todayCount, false, onOpenToday) }
            item { NavRow(Icons.Outlined.CalendarMonth, Todo.colors.priority(com.hamza.todo.core.model.Priority.HIGH), "Upcoming", upcomingCount, false, onOpenUpcoming) }
            item { NavRow(Icons.AutoMirrored.Filled.List, Todo.colors.muted, "All tasks", open.size, selectedListId == ALL_TASKS) { onOpenList(ALL_TASKS) } }
            item { NavRow(Icons.Outlined.CheckCircle, Todo.colors.success, "Completed", tasks.size - open.size, selectedListId == COMPLETED_TASKS) { onOpenList(COMPLETED_TASKS) } }
        } else {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SmartCard(Icons.Outlined.WbSunny, MaterialTheme.colorScheme.primary, "Today", todayCount, Modifier.weight(1f), onOpenToday)
                        SmartCard(Icons.Outlined.CalendarMonth, Todo.colors.priority(com.hamza.todo.core.model.Priority.HIGH), "Upcoming", upcomingCount, Modifier.weight(1f), onOpenUpcoming)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SmartCard(Icons.AutoMirrored.Filled.List, Todo.colors.muted, "All tasks", open.size, Modifier.weight(1f)) { onOpenList(ALL_TASKS) }
                        SmartCard(Icons.Outlined.CheckCircle, Todo.colors.success, "Completed", tasks.size - open.size, Modifier.weight(1f)) { onOpenList(COMPLETED_TASKS) }
                    }
                }
            }
        }
        item { SectionHeader("My lists") }
        items(lists, key = { it.id }) { l ->
            ListRow(l, counts[l.id] ?: 0, selected = l.id == selectedListId) { onOpenList(l.id) }
        }
        item {
            OutlinedButton(onClick = { newList = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Icon(Icons.Filled.Add, null)
                Text("  New list", fontWeight = FontWeight.Bold)
            }
        }
    }

    if (newList) {
        ListDialog(
            title = "New list",
            initialName = "",
            initialColor = TodoRepository.LIST_COLORS.first(),
            onDismiss = { newList = false },
        ) { name, color ->
            vm.addList(name, color) { onOpenList(it) }
            newList = false
        }
    }
}

@Composable
private fun SmartCard(icon: ImageVector, tint: Color, label: String, count: Int, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .height(104.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Todo.colors.card)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
            Text("$count", style = MaterialTheme.typography.headlineSmall)
        }
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun NavRow(icon: ImageVector, tint: Color, label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text("$count", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = Todo.colors.muted)
    }
}

@Composable
private fun ListRow(list: TaskList, count: Int, selected: Boolean, onClick: () -> Unit) {
    val color = Todo.colors.listColor(list.colorArgb)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Todo.colors.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            if (list.id == TaskList.INBOX_ID) Icon(Icons.Outlined.Inbox, null, tint = color, modifier = Modifier.size(20.dp))
            else ListDot(color, 12.dp)
        }
        Text(list.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
        Text("$count", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = Todo.colors.muted)
    }
}

@Composable
fun ListDialog(
    title: String,
    initialName: String,
    initialColor: Long,
    onDismiss: () -> Unit,
    onSave: (String, Long) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var color by rememberSaveable { mutableStateOf(initialColor) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("listNameInput"))
                ColorChoices(TodoRepository.LIST_COLORS, color) { color = it }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, color) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
