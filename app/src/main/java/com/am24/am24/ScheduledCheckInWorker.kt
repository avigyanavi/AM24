package com.am24.am24.workers

import android.content.Context
import androidx.work.*
import androidx.work.WorkerParameters
import com.am24.am24.AI
import com.am24.am24.KupidXChatViewModel
import kotlinx.coroutines.coroutineScope
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * A Worker that runs every hour (or you can schedule every 15m / 3h).
 * We'll call 'checkInIfNeeded' for Rhea or Revaan, if the conditions are met.
 */
class ScheduledCheckInWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = coroutineScope {
        // In a real app, you'd retrieve or share your KupidXChatViewModel.
        // If your code structure allows a static reference or a repository, do that.
        // For demonstration, let's say we do something like:
        KupidXChatViewModelHolder.instance?.checkInIfNeeded(AI.RHEA)
        KupidXChatViewModelHolder.instance?.checkInIfNeeded(AI.REVAAN)

        // If you keep separate logic for which AI the user last spoke to,
        // you can track a variable for "lastSpokenAI" in your local DB or shared prefs and only call that one.

        // Worker finishes
        Result.success()
    }

    companion object {
        // Schedules the worker to run every hour (or every 3 hours, etc.)
        fun schedulePeriodicCheckIns(context: Context) {
            // Suppose we schedule it to run every hour and let checkInIfNeeded
            // handle whether it’s time to do a check-in (3hr or 6hr).
            val request = PeriodicWorkRequestBuilder<ScheduledCheckInWorker>(
                1, TimeUnit.HOURS
            )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "ScheduledCheckIns",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}

/**
 * A simple "holder" for your KupidXChatViewModel, if you want to share it with the Worker.
 * In production, you'd typically use a repository or persist the states in a DB.
 */
object KupidXChatViewModelHolder {
    var instance: KupidXChatViewModel? = null
}
