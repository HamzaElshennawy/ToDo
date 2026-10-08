package com.hamza.todo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hamza.todo.core.model.Priority
import com.hamza.todo.core.model.Subtask
import com.hamza.todo.core.model.Task
import com.hamza.todo.core.model.TaskList
import com.hamza.todo.core.views.DueFormatter
import com.hamza.todo.ui.theme.Todo
import java.time.LocalDate

/** Round checkbox whose ring shows the priority color. 48dp touch target. */
@Composable
fun TaskCheck(done: Boolean, priority: Priority, title: String, onToggle: () -> Unit, size: Dp = 22.dp) {
    val ring = Todo.colors.priority(priority)
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Checkbox, onClick = onToggle)
            .semantics { contentDescription = (if (done) "Mark as not done: " else "Mark as done: ") + title },
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Box(
                Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(size * 0.65f))
            }
        } else {
            Box(Modifier.size(size).border(2.dp, ring, CircleShape))
        }
    }
}

@Composable
fun ListDot(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

/**
 * One task as a card: checkbox, title, and a line of details (due, list, priority,
 * subtasks, repeat, tags).
 */
@Composable
fun TaskRow(
    task: Task,
    list: TaskList?,
    subtasks: List<Subtask>,
    today: LocalDate,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDate: Boolean = true,
    showList: Boolean = true,
    selected: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = Todo.colors
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else colors.card)
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(start = 2.dp, end = if (trailing == null) 16.dp else 2.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        TaskCheck(task.completed, task.priority, task.title, onToggle)
        Column(
            Modifier.weight(1f).padding(top = 13.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = if (task.completed) colors.muted else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (task.completed) TextDecoration.LineThrough else null,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            TaskMeta(task, list, subtasks, today, showDate, showList)
        }
        if (trailing != null) Box(Modifier.padding(top = 4.dp)) { trailing() }
    }
}

@Composable
private fun TaskMeta(task: Task, list: TaskList?, subtasks: List<Subtask>, today: LocalDate, showDate: Boolean, showList: Boolean) {
    val colors = Todo.colors
    val due = task.dueDate
    val overdue = due != null && due.isBefore(today) && !task.completed
    val dueText = when {
        due == null -> null
        !showDate && due == today -> task.dueMinutes?.let(DueFormatter::time)
        else -> DueFormatter.label(due, task.dueMinutes, today)
    }
    val small = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (dueText != null) {
            MetaItem(Icons.Outlined.Schedule, dueText, if (overdue) colors.danger else colors.muted, bold = true)
        }
        if (showList && list != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ListDot(colors.listColor(list.colorArgb))
                Text(list.name, style = small, color = colors.muted)
            }
        }
        if (task.priority != Priority.NONE && !task.completed) {
            MetaItem(Icons.Outlined.Flag, task.priority.label, colors.priority(task.priority), bold = true)
        }
        if (subtasks.isNotEmpty()) {
            MetaItem(Icons.Outlined.Checklist, "${subtasks.count { it.done }}/${subtasks.size}", colors.muted)
        }
        if (task.recurrence != null) {
            MetaItem(Icons.Outlined.Repeat, "Repeats", colors.muted)
        }
        task.tags.forEach { Text("#$it", style = small, color = colors.muted) }
    }
}

@Composable
private fun MetaItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color, bold: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal),
            color = color,
        )
    }
}

/** Swipe left to delete. The caller shows the Undo snackbar. */
@Composable
fun SwipeToDelete(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        },
    )
    // A restored (undone) row must come back un-swiped.
    LaunchedEffect(Unit) {
        if (state.currentValue != SwipeToDismissBoxValue.Settled) state.snapTo(SwipeToDismissBoxValue.Settled)
    }
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(Todo.colors.danger).padding(end = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Delete, null, tint = Color.White)
                    Text("Delete", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
    ) { content() }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, color: Color = Todo.colors.muted) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = modifier.padding(start = 12.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
fun ProgressCard(done: Int, total: Int, modifier: Modifier = Modifier) {
    val left = total - done
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("$done of $total done", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Text(if (left == 0) "All done" else "$left left", style = MaterialTheme.typography.bodySmall, color = Todo.colors.muted)
        }
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else done.toFloat() / total },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            trackColor = Todo.colors.track,
            drawStopIndicator = {},
            gapSize = 0.dp,
        )
    }
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 24.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = Todo.colors.muted)
            }
            Text(title, style = MaterialTheme.typography.displaySmall)
        }
        Row { actions() }
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Todo.colors.muted)
    }
}

/** White rounded group used for settings and details. */
@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Todo.colors.card).padding(vertical = 4.dp),
        content = content,
    )
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit, divider: Boolean = false) {
    Column {
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(Todo.colors.line))
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clickable(role = Role.Switch) { onChange(!checked) }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Todo.colors.muted)
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

@Composable
fun ValueRow(title: String, subtitle: String?, value: String?, onClick: () -> Unit, divider: Boolean = false, valueColor: Color? = null) {
    Column {
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(Todo.colors.line))
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (valueColor != null) FontWeight.SemiBold else FontWeight.Medium),
                    color = valueColor ?: MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Todo.colors.muted)
            }
            if (value != null) Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = Todo.colors.muted)
        }
    }
}

@Composable
fun PrioritySelector(selected: Priority, onSelect: (Priority) -> Unit, modifier: Modifier = Modifier) {
    val options = Priority.entries
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { index, p ->
            SegmentedButton(
                selected = p == selected,
                onClick = { onSelect(p) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = Todo.colors.priorityContainer(p),
                    activeContentColor = if (p == Priority.NONE) MaterialTheme.colorScheme.onSurface else Todo.colors.priority(p),
                ),
            ) { Text(p.label, maxLines = 1) }
        }
    }
}

@Composable
fun ColorChoices(colors: List<Long>, selected: Long, onSelect: (Long) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.forEach { argb ->
            val isSelected = argb == selected
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.RadioButton) { onSelect(argb) }
                    .semantics { contentDescription = if (isSelected) "Selected color" else "Color" },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Todo.colors.listColor(argb))
                        .then(if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier),
                )
            }
        }
    }
}
