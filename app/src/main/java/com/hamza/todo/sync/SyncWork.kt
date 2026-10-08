package com.hamza.todo.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.hamza.todo.TodoApplication
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as TodoApplication
        return when (val result = app.container.syncManager.sync()) {
            is SyncResult.Failed -> if (result.retryable && runAttemptCount < 3) Result.retry() else Result.failure()
            else -> Result.success()
        }
    }
}

/** Runs sync in the background: shortly after each local change, and every hour. */
class SyncScheduler(context: Context) {
    private val work = WorkManager.getInstance(context)

    private fun constraints(wifiOnly: Boolean) = Constraints.Builder()
        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .build()

    fun requestSoon(wifiOnly: Boolean) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInitialDelay(5, TimeUnit.SECONDS)
            .setConstraints(constraints(wifiOnly))
            .build()
        work.enqueueUniqueWork(ONE_TIME, ExistingWorkPolicy.REPLACE, request)
    }

    fun apply(enabled: Boolean, wifiOnly: Boolean) {
        if (!enabled) {
            work.cancelUniqueWork(PERIODIC)
            work.cancelUniqueWork(ONE_TIME)
            return
        }
        val request = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
            .setConstraints(constraints(wifiOnly))
            .build()
        work.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private companion object {
        const val ONE_TIME = "sync-soon"
        const val PERIODIC = "sync-hourly"
    }
}
