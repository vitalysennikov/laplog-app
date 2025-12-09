package com.laplog.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.laplog.app.data.PreferencesManager
import com.laplog.app.util.AppLogger
import com.laplog.app.worker.BackupWorker
import java.util.concurrent.TimeUnit

/**
 * Receiver that reschedules periodic backup after device reboot
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            AppLogger.i("BootReceiver", "Device boot completed, checking auto-backup settings")

            val preferencesManager = PreferencesManager(context)

            // Reschedule backup if auto-backup is enabled
            if (preferencesManager.autoBackupEnabled) {
                AppLogger.i("BootReceiver", "Auto-backup is enabled, rescheduling periodic backup")
                schedulePeriodicBackup(context)
            } else {
                AppLogger.d("BootReceiver", "Auto-backup is disabled, skipping rescheduling")
            }
        }
    }

    private fun schedulePeriodicBackup(context: Context) {
        // Calculate initial delay to next 3 AM
        val calendar = java.util.Calendar.getInstance()
        val now = calendar.timeInMillis

        calendar.set(java.util.Calendar.HOUR_OF_DAY, 3)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)

        // If 3 AM has passed today, schedule for tomorrow
        if (calendar.timeInMillis <= now) {
            calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
        }

        val initialDelay = calendar.timeInMillis - now

        AppLogger.i("BootReceiver", "Scheduling backup with initial delay: ${initialDelay / 1000 / 60} minutes")

        val workRequest = PeriodicWorkRequestBuilder<BackupWorker>(
            1, TimeUnit.DAYS
        ).setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
         .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "backup_work",
            ExistingPeriodicWorkPolicy.REPLACE,
            workRequest
        )

        AppLogger.i("BootReceiver", "Periodic backup rescheduled successfully")
    }
}
