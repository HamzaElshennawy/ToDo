package com.hamza.todo.ui

import android.app.PendingIntent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hamza.todo.AppContainer
import com.hamza.todo.TodoApplication
import com.hamza.todo.core.export.Exporter
import com.hamza.todo.core.model.Subtask
import com.hamza.todo.core.model.Task
import com.hamza.todo.core.model.TaskList
import com.hamza.todo.core.parse.QuickAddParser
import com.hamza.todo.core.views.SortMode
import com.hamza.todo.data.AppSettings
import com.hamza.todo.data.NewTask
import com.hamza.todo.data.ThemeMode
import com.hamza.todo.sync.SyncResult
import com.hamza.todo.sync.SyncState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** A snackbar message, optionally with an action such as Undo. */
data class UiMessage(val text: String, val actionLabel: String? = null, val action: (() -> Unit)? = null)

class MainViewModel(private val c: AppContainer) : ViewModel() {
    private val repo = c.repository

    /** Ticks every 30 s so "Today" and overdue states roll over at midnight. */
    val now: StateFlow<LocalDateTime> = flow {
        while (true) {
            emit(LocalDateTime.now())
            delay(30_000)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LocalDateTime.now())

    val lists: StateFlow<List<TaskList>> = repo.lists.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val tasks: StateFlow<List<Task>> = repo.tasks.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val subtasks: StateFlow<Map<String, List<Subtask>>> = repo.subtasks
        .map { all -> all.groupBy { it.taskId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val settings: StateFlow<AppSettings> = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val syncState: StateFlow<SyncState> = c.syncManager.state

    private val _authRequests = MutableSharedFlow<PendingIntent>(extraBufferCapacity = 1)
    val authRequests: SharedFlow<PendingIntent> = _authRequests

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    fun parser(): QuickAddParser = QuickAddParser(lists.value.map { it.name })

    // Tasks

    fun addTask(draft: NewTask, subtaskTitles: List<String> = emptyList()) = launch { repo.addTask(draft, subtaskTitles) }

    fun toggleDone(task: Task) = launch { repo.setCompleted(task.id, !task.completed, now.value.toLocalDate()) }

    fun updateTask(id: String, transform: (Task) -> Task) = launch { repo.updateTask(id, transform) }

    fun deleteTask(id: String) = launch {
        repo.deleteTask(id)
        _messages.emit(UiMessage("Task deleted", "Undo") { restoreTask(id) })
    }

    fun restoreTask(id: String) = launch { repo.restoreTask(id) }

    fun moveToToday(ids: List<String>) = launch { repo.moveToDay(ids, now.value.toLocalDate().toEpochDay()) }

    fun reorder(idsInOrder: List<String>) = launch { repo.reorder(idsInOrder) }

    fun clearCompleted() = launch {
        repo.clearCompleted()
        _messages.emit(UiMessage("Completed tasks cleared"))
    }

    // Subtasks

    fun addSubtask(taskId: String, title: String) = launch { repo.addSubtask(taskId, title) }

    fun toggleSubtask(sub: Subtask) = launch { repo.updateSubtask(sub.id) { it.copy(done = !it.done) } }

    fun renameSubtask(id: String, title: String) = launch { repo.updateSubtask(id) { it.copy(title = title) } }

    fun deleteSubtask(id: String) = launch { repo.updateSubtask(id) { it.copy(deleted = true) } }

    // Lists

    fun addList(name: String, color: Long, onCreated: (String) -> Unit = {}) = launch { onCreated(repo.addList(name, color)) }

    fun updateList(id: String, name: String, color: Long) = launch { repo.updateList(id, name, color) }

    fun deleteList(id: String) = launch { repo.deleteList(id) }

    // Settings

    fun setTheme(mode: ThemeMode) = launch { c.settings.setTheme(mode) }
    fun setDefaultList(id: String) = launch { c.settings.setDefaultList(id) }
    fun setShowCompleted(on: Boolean) = launch { c.settings.setShowCompleted(on) }
    fun setOverdueFirst(on: Boolean) = launch { c.settings.setOverdueFirst(on) }
    fun setReminders(on: Boolean) = launch { c.settings.setReminders(on) }
    fun setDailySummary(on: Boolean) = launch { c.settings.setDailySummary(on) }
    fun setWifiOnly(on: Boolean) = launch { c.settings.setWifiOnly(on) }
    fun setListSort(listId: String, mode: SortMode) = launch { c.settings.setListSort(listId, mode) }

    fun setSyncEnabled(on: Boolean) = launch {
        c.settings.setSyncEnabled(on)
        if (on) runSync(interactive = true)
    }

    // Sync

    fun syncNow() = launch { runSync(interactive = true) }

    /** Pull changes from other devices whenever the app comes to the foreground. */
    fun onResume() = launch { if (c.settings.current().syncEnabled) runSync(interactive = false) }

    private suspend fun runSync(interactive: Boolean) {
        when (val result = c.syncManager.sync()) {
            is SyncResult.NeedsPermission -> if (interactive) _authRequests.emit(result.intent)
            is SyncResult.Failed -> if (interactive) _messages.emit(UiMessage(result.message))
            else -> Unit
        }
    }

    /** Called after the Drive permission screen closes. */
    fun onDriveConsent(granted: Boolean) = launch {
        if (granted) runSync(interactive = true)
        else _messages.emit(UiMessage("Sync needs access to the app’s folder in Google Drive"))
    }

    // Backup

    suspend fun exportJson(): String = Exporter.toJson(repo.snapshot())

    suspend fun exportCsv(): String = Exporter.toCsv(repo.snapshot())

    fun importBackup(text: String) = launch {
        val backup = runCatching { Exporter.readBackup(text) }.getOrNull()
        if (backup == null) {
            _messages.emit(UiMessage("That file isn’t a ToDo backup"))
        } else {
            repo.importBackup(backup)
            _messages.emit(UiMessage("Backup restored"))
        }
    }

    fun message(text: String) = launch { _messages.emit(UiMessage(text)) }

    companion object {
        val Factory = viewModelFactory {
            initializer { MainViewModel((this[APPLICATION_KEY] as TodoApplication).container) }
        }
    }
}
