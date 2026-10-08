package com.hamza.todo.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TabletAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hamza.todo.data.ThemeMode
import com.hamza.todo.ui.MainViewModel
import com.hamza.todo.ui.components.Card
import com.hamza.todo.ui.components.ChoiceMenu
import com.hamza.todo.ui.components.SectionHeader
import com.hamza.todo.ui.components.SwitchRow
import com.hamza.todo.ui.components.ValueRow
import com.hamza.todo.ui.theme.Todo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
fun SettingsScreen(vm: MainViewModel, onBack: (() -> Unit)?, wide: Boolean, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        }
        Text(
            "Settings",
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(start = 24.dp, top = if (onBack == null) 24.dp else 4.dp, bottom = 16.dp),
        )
        if (wide) {
            Row(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 32.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SyncSection(vm)
                    DataSection(vm)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppearanceSection(vm)
                    TasksSection(vm)
                    NotificationsSection(vm)
                    Footer()
                }
            }
        } else {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SyncSection(vm)
                AppearanceSection(vm)
                TasksSection(vm)
                NotificationsSection(vm)
                DataSection(vm)
                Footer()
            }
        }
    }
}

@Composable
private fun SyncSection(vm: MainViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val state by vm.syncState.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()

    SectionHeader("Sync", Modifier.padding(start = 0.dp))
    Card {
        SwitchRow(
            "Sync across my devices",
            "Uses the Google account on this device. No sign-in needed.",
            settings.syncEnabled,
            vm::setSyncEnabled,
        )
        if (!settings.syncEnabled) {
            Divider()
            Text(
                "Sync is off. Your tasks stay on this device only. Turn it on to keep your phones and tablets up to date.",
                style = MaterialTheme.typography.bodyMedium,
                color = Todo.colors.muted,
                modifier = Modifier.padding(16.dp),
            )
            return@Card
        }
        Divider()
        val (title, sub, tint, container) = when {
            state.running -> Quad("Syncing…", "Checking Google Drive for changes", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)
            state.needsPermission -> Quad("Needs permission", "Tap Sync now to allow access to the app’s Drive folder", Todo.colors.danger, Todo.colors.dangerContainer)
            state.error != null -> Quad("Couldn’t sync", state.error!!, Todo.colors.danger, Todo.colors.dangerContainer)
            settings.lastSyncAt == null -> Quad("Not synced yet", "Tap Sync now to start", Todo.colors.muted, MaterialTheme.colorScheme.surfaceVariant)
            else -> Quad("Up to date", "Last synced " + ago(settings.lastSyncAt!!, now), Todo.colors.success, Todo.colors.successContainer)
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(container), contentAlignment = Alignment.Center) {
                Icon(
                    when {
                        state.running -> Icons.Outlined.Sync
                        state.error != null || state.needsPermission -> Icons.Outlined.CloudOff
                        else -> Icons.Outlined.CloudDone
                    },
                    null,
                    tint = tint,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                Text(sub, style = MaterialTheme.typography.bodySmall, color = Todo.colors.muted)
            }
            Button(onClick = vm::syncNow, enabled = !state.running) { Text("Sync now") }
        }
        SwitchRow("Sync on Wi-Fi only", "Save mobile data", settings.wifiOnly, vm::setWifiOnly, divider = true)
        if (settings.devices.isNotEmpty()) {
            Divider()
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("My devices", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = Todo.colors.muted)
                settings.devices.sortedByDescending { it.lastSyncAt }.forEach { d ->
                    val isThis = d.id == settings.deviceId
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.background),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(if (d.isTablet) Icons.Outlined.TabletAndroid else Icons.Outlined.PhoneAndroid, null, Modifier.size(20.dp))
                        }
                        Column {
                            Text(if (isThis) "${d.name} (this device)" else d.name, style = MaterialTheme.typography.bodyLarge)
                            Text("Synced " + ago(d.lastSyncAt, now), style = MaterialTheme.typography.bodySmall, color = Todo.colors.muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppearanceSection(vm: MainViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    SectionHeader("Appearance", Modifier.padding(start = 0.dp))
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Theme", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { i, mode ->
                    SegmentedButton(
                        selected = settings.theme == mode,
                        onClick = { vm.setTheme(mode) },
                        shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                    ) { Text(mode.label) }
                }
            }
        }
    }
}

@Composable
private fun TasksSection(vm: MainViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val lists by vm.lists.collectAsStateWithLifecycle()
    var listMenu by remember { mutableStateOf(false) }
    SectionHeader("Tasks", Modifier.padding(start = 0.dp))
    Card {
        Box {
            ValueRow("Default list", "New tasks go here", lists.firstOrNull { it.id == settings.defaultListId }?.name ?: "Inbox", onClick = { listMenu = true })
            ChoiceMenu(listMenu, lists.map { it.id }, settings.defaultListId, { id -> lists.firstOrNull { it.id == id }?.name ?: id }, { listMenu = false }) {
                vm.setDefaultList(it)
            }
        }
        SwitchRow("Show completed tasks", "Keep finished tasks visible in lists", settings.showCompleted, vm::setShowCompleted, divider = true)
        SwitchRow("Overdue tasks first", "Pin overdue tasks to the top of Today", settings.overdueFirst, vm::setOverdueFirst, divider = true)
    }
}

@Composable
private fun NotificationsSection(vm: MainViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    SectionHeader("Notifications", Modifier.padding(start = 0.dp))
    Card {
        SwitchRow("Task reminders", "Notify me at each task’s reminder time", settings.remindersOn, vm::setReminders)
        SwitchRow("Daily summary", "A list of today’s tasks every morning at 08:00", settings.dailySummary, vm::setDailySummary, divider = true)
    }
}

@Composable
private fun DataSection(vm: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }
    var exportMenu by remember { mutableStateOf(false) }
    val stamp = LocalDate.now().toString()

    val saveJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch { writeText(context, uri, vm.exportJson()); vm.message("Backup saved") }
    }
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch { writeText(context, uri, vm.exportCsv()); vm.message("CSV saved") }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { readText(context, uri)?.let(vm::importBackup) ?: vm.message("Couldn’t read that file") }
    }

    SectionHeader("Data", Modifier.padding(start = 0.dp))
    Card {
        Box {
            ValueRow("Export tasks", "Save a backup (JSON) or a spreadsheet (CSV)", null, onClick = { exportMenu = true })
            ChoiceMenu(exportMenu, listOf("JSON backup", "CSV spreadsheet"), "", { it }, { exportMenu = false }) {
                if (it.startsWith("JSON")) saveJson.launch("todo-backup-$stamp.json") else saveCsv.launch("todo-$stamp.csv")
            }
        }
        ValueRow("Restore from backup", "Merges a JSON backup into your tasks", null, onClick = { open.launch(arrayOf("application/json", "text/plain", "*/*")) }, divider = true)
        ValueRow("Clear all completed tasks", null, null, onClick = { confirmClear = true }, divider = true, valueColor = Todo.colors.danger)
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear completed tasks?") },
            text = { Text("All completed tasks will be deleted on all your devices.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; vm.clearCompleted() }) { Text("Clear", color = Todo.colors.danger) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Footer() {
    Text(
        "ToDo 1.0.0 · Synced tasks are kept in a private app folder in your own Google Drive.",
        style = MaterialTheme.typography.bodySmall,
        color = Todo.colors.muted,
        modifier = Modifier.padding(start = 8.dp, top = 12.dp),
    )
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Todo.colors.line))
}

private data class Quad(val title: String, val sub: String, val tint: Color, val container: Color)

private fun ago(millis: Long, now: java.time.LocalDateTime): String {
    val diff = (now.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() - millis).coerceAtLeast(0) / 1000
    return when {
        diff < 60 -> "just now"
        diff < 3600 -> "${diff / 60} min ago"
        diff < 86400 -> "${diff / 3600} hour${if (diff / 3600 == 1L) "" else "s"} ago"
        else -> {
            val days = (diff / 86400).toInt()
            if (days == 1) "yesterday" else "$days days ago"
        }
    }
}

private suspend fun writeText(context: Context, uri: Uri, text: String) = withContext(Dispatchers.IO) {
    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
}

private suspend fun readText(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
}
