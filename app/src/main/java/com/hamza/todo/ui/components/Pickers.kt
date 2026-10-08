package com.hamza.todo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hamza.todo.core.model.Frequency
import com.hamza.todo.core.model.Recurrence
import com.hamza.todo.core.recurrence.RecurrenceRules
import com.hamza.todo.core.views.DueFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Date picker with an optional time. Returns null date for "no date". */
@Composable
fun DueDialog(
    initialDate: LocalDate?,
    initialMinutes: Int?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate?, Int?) -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = (initialDate ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    var minutes by remember { mutableStateOf(initialMinutes) }
    var pickTime by remember { mutableStateOf(false) }

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val date = state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                onConfirm(date, if (date == null) null else minutes)
            }) { Text("Done") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onConfirm(null, null) }) { Text("No date") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    ) {
        DatePicker(state = state, showModeToggle = false, title = null, headline = null)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AssistChip(
                onClick = { pickTime = true },
                label = { Text(minutes?.let { "Time: " + DueFormatter.time(it) } ?: "Add time") },
                leadingIcon = { Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp)) },
            )
            if (minutes != null) TextButton(onClick = { minutes = null }) { Text("Remove time") }
        }
    }

    if (pickTime) {
        TimeDialog(minutes ?: (9 * 60), onDismiss = { pickTime = false }) {
            minutes = it
            pickTime = false
        }
    }
}

@Composable
fun TimeDialog(initialMinutes: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = initialMinutes / 60, initialMinute = initialMinutes % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = state) } },
    )
}

val REMINDER_OPTIONS: List<Int?> = listOf(null, 0, 10, 30, 60, 1440)

/** A menu of choices shown under its anchor; [selected] gets a check mark. */
@Composable
fun <T> ChoiceMenu(
    expanded: Boolean,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        options.forEach { option ->
            DropdownMenuItem(
                text = { Text(label(option)) },
                onClick = {
                    onSelect(option)
                    onDismiss()
                },
                trailingIcon = if (option == selected) ({ Icon(Icons.Filled.Check, null) }) else null,
            )
        }
    }
}

/** Repeat choices that make sense for the given due date (e.g. "Every month on the 2nd Tuesday"). */
fun repeatOptions(due: LocalDate?): List<Recurrence?> {
    val base = due ?: LocalDate.now()
    val ordinal = ((base.dayOfMonth - 1) / 7) + 1
    val isLast = base.plusWeeks(1).month != base.month
    return listOfNotNull(
        null,
        Recurrence(Frequency.DAILY),
        Recurrence(Frequency.WEEKLY, weekdays = setOf(1, 2, 3, 4, 5)),
        Recurrence(Frequency.WEEKLY),
        Recurrence(Frequency.WEEKLY, interval = 2),
        Recurrence(Frequency.MONTHLY),
        Recurrence(Frequency.MONTHLY, monthWeekOrdinal = if (isLast && ordinal >= 4) -1 else ordinal, monthWeekday = base.dayOfWeek.value),
        Recurrence(Frequency.YEARLY),
    )
}

fun repeatLabel(rule: Recurrence?): String = RecurrenceRules.describe(rule)
