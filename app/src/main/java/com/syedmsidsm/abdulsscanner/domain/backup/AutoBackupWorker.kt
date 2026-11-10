package com.syedmsidsm.abdulsscanner.domain.backup

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.syedmsidsm.abdulsscanner.AbdulsScannerApp
import com.syedmsidsm.abdulsscanner.MainActivity
import com.syedmsidsm.abdulsscanner.R
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class AutoBackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val UNIQUE_WORK_NAME = "auto-backup"
        const val CHANNEL_ID = "backup_channel"
        const val NOTIFICATION_ID = 1001

        fun schedule(context: Context, intervalDays: Int = 7) {
            val constraints = Constraints.Builder()
                .setRequiresStorageNotLow(true)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(
                intervalDays.toLong().coerceAtLeast(1),
                TimeUnit.DAYS
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        val app = applicationContext as? AbdulsScannerApp ?: return Result.failure()
        val container = app.container
        val settings = container.settingsRepository

        val isEnabled = settings.autoBackupEnabled.first()
        val treeUriStr = settings.autoBackupTreeUri.first()

        if (!isEnabled || treeUriStr.isNullOrBlank()) {
            return Result.success()
        }

        val treeUri = try {
            Uri.parse(treeUriStr)
        } catch (_: Exception) {
            postFailureNotification()
            settings.setAutoBackupFailed(true)
            return Result.failure()
        }

        // Check if anything changed since last backup
        val lastBackupAt = settings.lastBackupAt.first() ?: 0L
        val maxUpdated = container.database.documentDao().getMaxUpdatedAt() ?: 0L
        val docCount = container.database.documentDao().getActiveCount()

        // If documents exist and last backup is newer than any doc updates, skip
        if (docCount > 0 && lastBackupAt > 0 && lastBackupAt >= maxUpdated) {
            return Result.success()
        }

        try {
            val keepCount = settings.backupKeepCount.first()
            val backupWriter = BackupWriter(
                context = applicationContext,
                database = container.database,
                fileStore = container.fileStore,
                settingsRepository = settings
            )

            backupWriter.writeAutoBackupToTree(
                treeUri = treeUri,
                keepCount = keepCount
            )

            settings.setAutoBackupFailed(false)
            return Result.success()
        } catch (e: Exception) {
            postFailureNotification()
            settings.setAutoBackupFailed(true)
            return Result.failure()
        }
    }

    private fun postFailureNotification() {
        val context = applicationContext
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Backup Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for automatic backup status"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.splash_icon)
            .setContentTitle("Abul's Scanner")
            .setContentText("Automatic backup failed — choose the folder again")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // In case notification permission was revoked
        }
    }
}
