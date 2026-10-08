package com.hamza.todo.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hamza.todo.R
import com.hamza.todo.TodoApplication
import com.hamza.todo.core.model.Task
import com.hamza.todo.core.views.DueFormatter
import com.hamza.todo.ui.MainActivity
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

object Notifications {
    const val CHANNEL_REMINDERS = "reminders"
    const val CHANNEL_SUMMARY = "summary"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "Task reminders", NotificationManager.IMPORTANCE_HIGH),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SUMMARY, "Daily summary", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun post(context: Context, id: Int, notification: android.app.Notification) {
        if (!canPost(context)) return
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Permission was revoked between the check and the call.
        }
    }

    fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/**
 * Keeps one alarm per open task that has a reminder in the future. Called with the full
 * task list whenever it changes, so edits, completions and synced changes are all covered.
 */
class ReminderScheduler(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)

    fun reschedule(tasks: List<Task>, enabled: Boolean, now: Long = System.currentTimeMillis()) {
        val zone = ZoneId.systemDefault()
        val wanted: Map<String, Pair<Task, Long>> = if (!enabled) emptyMap() else tasks
            .filter { !it.completed && !it.deleted }
            .mapNotNull { t -> t.reminderAt(zone)?.takeIf { it > now }?.let { t.id to (t to it) } }
            .toMap()

        val previous = prefs.getStringSet(KEY_IDS, emptySet()).orEmpty()
        (previous - wanted.keys).forEach { cancel(it) }
        wanted.values.forEach { (task, at) -> schedule(task, at) }
        prefs.edit().putStringSet(KEY_IDS, wanted.keys).apply()
    }

    private fun schedule(task: Task, at: Long) {
        val pi = pendingIntent(task.id, task)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        if (exact) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            // Without the exact-alarm permission Android may delay this by a few minutes.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun cancel(taskId: String) = alarms.cancel(pendingIntent(taskId, null))

    private fun pendingIntent(taskId: String, task: Task?): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_TASK_ID, taskId)
        if (task != null) {
            intent.putExtra(EXTRA_TITLE, task.title)
            intent.putExtra(EXTRA_TEXT, task.dueDate?.let { DueFormatter.label(it, task.dueMinutes, LocalDate.now()) })
        }
        return PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
        private const val KEY_IDS = "scheduled_ids"
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(ReminderScheduler.EXTRA_TASK_ID) ?: return
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE) ?: "Task reminder"
        val text = intent.getStringExtra(ReminderScheduler.EXTRA_TEXT)
        val markDone = PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            Intent(context, MarkDoneReceiver::class.java).putExtra(ReminderScheduler.EXTRA_TASK_ID, taskId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, Notifications.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(Notifications.openAppIntent(context))
            .addAction(0, "Mark done", markDone)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        Notifications.post(context, taskId.hashCode(), notification)
    }
}

class MarkDoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(ReminderScheduler.EXTRA_TASK_ID) ?: return
        NotificationManagerCompat.from(context).cancel(taskId.hashCode())
        val app = context.applicationContext as TodoApplication
        val pending = goAsync()
        app.scope.launch {
            try {
                app.container.repository.setCompleted(taskId, true)
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * Alarms are cleared on reboot and on clock changes. Starting the app process is enough:
 * TodoApplication re-schedules reminders from the database when it starts.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as TodoApplication
        val pending = goAsync()
        app.scope.launch {
            try {
                app.container.rescheduleReminders()
            } finally {
                pending.finish()
            }
        }
    }
}
