package com.hamza.todo

import android.app.Application
import android.content.Context
import com.hamza.todo.data.SettingsRepository
import com.hamza.todo.data.TodoDatabase
import com.hamza.todo.data.TodoRepository
import com.hamza.todo.reminders.DailySummaryWorker
import com.hamza.todo.reminders.Notifications
import com.hamza.todo.reminders.ReminderScheduler
import com.hamza.todo.sync.SyncManager
import com.hamza.todo.sync.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Hand-wired dependencies; small enough not to need a DI framework. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val database = TodoDatabase.create(appContext)
    val repository = TodoRepository(database)
    val settings = SettingsRepository(appContext)
    val syncManager = SyncManager(appContext, repository, settings)
    val syncScheduler = SyncScheduler(appContext)
    val reminders = ReminderScheduler(appContext)

    suspend fun rescheduleReminders() {
        reminders.reschedule(repository.tasks.first(), settings.current().remindersOn)
    }
}

class TodoApplication : Application() {
    lateinit var container: AppContainer
        private set

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)

        scope.launch { container.repository.ensureDefaults() }

        // Reminders follow the task table (local edits and synced changes alike).
        scope.launch {
            combine(container.repository.tasks, container.settings.settings.map { it.remindersOn }.distinctUntilChanged()) { t, on -> t to on }
                .debounce(500)
                .collect { (tasks, on) -> container.reminders.reschedule(tasks, on) }
        }

        // Background sync and the daily summary follow the settings.
        scope.launch {
            container.settings.settings
                .map { Triple(it.syncEnabled, it.wifiOnly, it.dailySummary) }
                .distinctUntilChanged()
                .collect { (sync, wifiOnly, summary) ->
                    container.syncScheduler.apply(sync, wifiOnly)
                    DailySummaryWorker.apply(this@TodoApplication, summary)
                }
        }

        // Push local edits to Drive a few seconds after they happen.
        scope.launch {
            container.repository.localChanges.collect {
                val s = container.settings.current()
                if (s.syncEnabled) container.syncScheduler.requestSoon(s.wifiOnly)
            }
        }
    }
}
