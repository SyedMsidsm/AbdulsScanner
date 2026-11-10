package com.syedmsidsm.abdulsscanner.domain.safety

import android.content.Context
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.syedmsidsm.abdulsscanner.AbdulsScannerApp
import java.util.concurrent.TimeUnit

class TrashPurgeWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val UNIQUE_WORK_NAME = "trash-purge"
        const val PURGE_THRESHOLD_MILLIS = 30L * 24 * 60 * 60 * 1000L // 30 days

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<TrashPurgeWorker>(
                24,
                TimeUnit.HOURS
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        suspend fun purgeDocument(
            container: com.syedmsidsm.abdulsscanner.di.AppContainer,
            docId: String,
            mirrorUri: String? = null
        ) {
            // 1. Delete physical files
            container.fileStore.deleteDocumentDir(docId)
            val exportFile = container.fileStore.getExportPdfFile(docId)
            if (exportFile.exists()) exportFile.delete()

            // Delete mirror copy
            container.mirrorStore.deleteMirror(mirrorUri)

            // 2. Delete database rows in a transaction
            container.database.withTransaction {
                container.database.documentDao().deletePermanently(docId)
                container.database.pageDao().deleteByDocumentId(docId)
                container.database.tagDao().clearTagsForDocument(docId)
                container.database.searchDao().deleteByDocId(docId)
            }
        }
    }

    override suspend fun doWork(): Result {
        val app = applicationContext as? AbdulsScannerApp ?: return Result.failure()
        val container = app.container

        val cutoff = System.currentTimeMillis() - PURGE_THRESHOLD_MILLIS
        val expiredDocs = container.database.documentDao().getDeletedBefore(cutoff)

        for (doc in expiredDocs) {
            purgeDocument(container, doc.id, doc.mirrorUri)
        }

        return Result.success()
    }
}
