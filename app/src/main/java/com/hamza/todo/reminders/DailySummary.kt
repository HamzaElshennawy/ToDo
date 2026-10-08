package com.hamza.todo.reminders

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.hamza.todo.R
import com.hamza.todo.TodoApplication
import com.hamza.todo.core.views.TaskViews
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** Posts "N tasks today" once a day at 08:00. */
class DailySummaryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as TodoApplication
        if (!app.container.settings.current().dailySummary) return Result.success()
        val tasks = app.container.repository.tasks.first()
        val view = TaskViews.today(tasks, LocalDate.now(), completedSinceMillis = Long.MAX_VALUE)
        val open = view.overdue + view.today
        if (open.isEmpty()) return Result.success()
        val title = if (open.size == 1) "1 task today" else "${open.size} tasks today"
        val text = open.take(5).joinToString(", ") { it.title } + if (open.size > 5) "…" else ""
        val notification = NotificationCompat.Builder(applicationContext, Notifications.CHANNEL_SUMMARY)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(Notifications.openAppIntent(applicationContext))
            .setAutoCancel(true)
            .build()
        Notifications.post(applicationContext, SUMMARY_ID, notification)
        return Result.success()
    }

    companion object {
        private const val SUMMARY_ID = 1
        private const val WORK = "daily-summary"
        private val AT: LocalTime = LocalTime.of(8, 0)

        fun apply(context: Context, enabled: Boolean) {
            val work = WorkManager.getInstance(context)
            if (!enabled) {
                work.cancelUniqueWork(WORK)
                return
            }
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(AT)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<DailySummaryWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                .build()
            work.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
