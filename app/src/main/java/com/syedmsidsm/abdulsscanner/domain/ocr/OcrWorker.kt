package com.syedmsidsm.abdulsscanner.domain.ocr

import android.content.Context
import android.graphics.BitmapFactory
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.syedmsidsm.abdulsscanner.AbdulsScannerApp
import com.syedmsidsm.abdulsscanner.data.db.entity.OcrLanguage
import com.syedmsidsm.abdulsscanner.data.db.entity.OcrStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class OcrWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as? AbdulsScannerApp ?: return@withContext Result.failure()
        val container = app.container
        val docId = inputData.getString(KEY_DOC_ID)

        val ocrLangStr = container.settingsRepository.ocrSearchLanguage.first()
        val ocrLang = try {
            OcrLanguage.valueOf(ocrLangStr)
        } catch (_: Exception) {
            OcrLanguage.BOTH
        }

        val pendingPages = if (!docId.isNullOrBlank()) {
            container.database.pageDao().getPendingOcrPages(docId)
        } else {
            container.database.pageDao().getAllPendingOcrPages()
        }

        if (pendingPages.isEmpty()) {
            return@withContext Result.success()
        }

        val touchedDocIds = mutableSetOf<String>()

        for (page in pendingPages) {
            if (isStopped) break
            val renderedFile = container.fileStore.getPageRenderedFile(page.documentId, page.id)
            if (!renderedFile.exists()) {
                container.database.pageDao().updateOcr(
                    pageId = page.id,
                    text = null,
                    lang = ocrLang.name,
                    status = OcrStatus.FAILED.name
                )
                continue
            }

            try {
                val bitmap = BitmapFactory.decodeFile(renderedFile.absolutePath)
                if (bitmap != null) {
                    val recognizedText = container.ocrService.recognize(bitmap, ocrLang)
                    container.database.pageDao().updateOcr(
                        pageId = page.id,
                        text = recognizedText,
                        lang = ocrLang.name,
                        status = OcrStatus.DONE.name
                    )
                    touchedDocIds.add(page.documentId)
                } else {
                    container.database.pageDao().updateOcr(
                        pageId = page.id,
                        text = null,
                        lang = ocrLang.name,
                        status = OcrStatus.FAILED.name
                    )
                }
            } catch (e: Exception) {
                container.database.pageDao().updateOcr(
                    pageId = page.id,
                    text = null,
                    lang = ocrLang.name,
                    status = OcrStatus.FAILED.name
                )
            }
        }

        // Rebuild FTS search body for all touched documents
        for (dId in touchedDocIds) {
            val doc = container.database.documentDao().getById(dId)
            if (doc != null) {
                val pages = container.database.pageDao().getByDocumentId(dId)
                val pageTexts = pages.mapNotNull { it.ocrText }
                container.searchRepository.indexDocument(dId, doc.name, pageTexts)
            }
        }

        Result.success()
    }

    companion object {
        const val KEY_DOC_ID = "doc_id"

        fun enqueueForDocument(context: Context, docId: String) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val request = OneTimeWorkRequestBuilder<OcrWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(KEY_DOC_ID to docId))
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "ocr-$docId",
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request
            )
        }

        fun enqueueAll(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val request = OneTimeWorkRequestBuilder<OcrWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "ocr-all",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
