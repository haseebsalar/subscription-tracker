package com.subtracker.app

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object Reminders {
    private const val WORK_NAME = "renewal-check-v2"
    private const val OLD_WORK_NAME = "renewal-check"
    private const val NOW_WORK_NAME = "renewal-check-now"

    /**
     * Checks once an hour. A reminder goes out in the first check after the time the user picked,
     * so Android's battery saving can delay it a little but it is not lost.
     */
    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(OLD_WORK_NAME)
        wm.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.HOURS).build()
        )
        wm.enqueueUniqueWork(
            NOW_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ReminderWorker>().build()
        )
    }
}
