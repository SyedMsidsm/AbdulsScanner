package com.syedmsidsm.abdulsscanner.domain.pdf

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.syedmsidsm.abdulsscanner.AbdulsScannerApp
import kotlinx.coroutines.flow.first

class PdfRebuildWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_DOC_ID = "doc_id"

        fun enqueue(context: Context, docId: String) {
            val request = OneTimeWorkRequestBuilder<PdfRebuildWorker>()
                .setInputData(workDataOf(KEY_DOC_ID to docId))
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "pdf-$docId",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        val docId = inputData.getString(KEY_DOC_ID) ?: return Result.failure()
        val app = applicationContext as? AbdulsScannerApp ?: return Result.failure()
        val container = app.container

        val doc = container.database.documentDao().getById(docId) ?: return Result.success()
        if (doc.deletedAt != null) return Result.success()

        val pages = container.database.pageDao().getByDocumentId(docId)
        if (pages.isEmpty()) return Result.success()

        try {
            val exportFile = container.pdfBuilder.buildPdf(doc, pages)
            container.database.documentDao().updatePdfBuiltAt(docId)

            val mirrorEnabled = container.settingsRepository.mirrorEnabled.first()
            if (mirrorEnabled && !doc.locked) {
                val mirrorUriString = container.mirrorStore.mirrorPdf(
                    pdfFile = exportFile,
                    docName = doc.name,
                    existingUriString = doc.mirrorUri,
                    isLocked = doc.locked
                )
                container.database.documentDao().updateMirrorUri(docId, mirrorUriString)
            }
            return Result.success()
        } catch (e: Exception) {
            return Result.retry()
        }
    }
}
