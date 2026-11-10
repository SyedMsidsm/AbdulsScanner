package com.syedmsidsm.abdulsscanner.domain.scan

import android.content.Context
import androidx.room.withTransaction
import com.syedmsidsm.abdulsscanner.data.db.AppDatabase
import com.syedmsidsm.abdulsscanner.data.db.entity.DocumentEntity
import com.syedmsidsm.abdulsscanner.data.db.entity.DocumentKind
import com.syedmsidsm.abdulsscanner.data.db.entity.PageEntity
import com.syedmsidsm.abdulsscanner.data.db.entity.PageSizeSetting
import com.syedmsidsm.abdulsscanner.data.files.FileStore
import com.syedmsidsm.abdulsscanner.data.files.MirrorStore
import com.syedmsidsm.abdulsscanner.data.settings.SettingsRepository
import com.syedmsidsm.abdulsscanner.domain.pdf.PdfBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MergeDocumentsUseCase(
    private val context: Context,
    private val database: AppDatabase,
    private val fileStore: FileStore,
    private val mirrorStore: MirrorStore,
    private val pdfBuilder: PdfBuilder,
    private val settingsRepository: SettingsRepository
) {

    suspend fun mergeDocuments(
        sourceDocIds: List<String>,
        customName: String? = null
    ): String = withContext(Dispatchers.IO) {
        require(sourceDocIds.size >= 2) { "At least 2 documents are required for merge" }

        val newDocId = UUID.randomUUID().toString()
        val defaultName = customName ?: run {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH)
            "Merged ${sdf.format(Date())}"
        }

        val newPages = mutableListOf<PageEntity>()
        var globalPosition = 0

        for (sourceDocId in sourceDocIds) {
            val pages = database.pageDao().getByDocumentId(sourceDocId)
            for (p in pages) {
                val newPageId = UUID.randomUUID().toString()

                // Copy source image
                val oldSrc = fileStore.getPageSrcFile(sourceDocId, p.id)
                val newSrc = fileStore.getPageSrcFile(newDocId, newPageId)
                if (oldSrc.exists()) {
                    fileStore.writeAtomically(newSrc) { fos ->
                        oldSrc.inputStream().use { it.copyTo(fos) }
                    }
                }

                // Copy original image if present
                val oldOrig = fileStore.getPageOrigFile(sourceDocId, p.id)
                val newOrig = fileStore.getPageOrigFile(newDocId, newPageId)
                if (oldOrig.exists()) {
                    fileStore.writeAtomically(newOrig) { fos ->
                        oldOrig.inputStream().use { it.copyTo(fos) }
                    }
                }

                // Copy rendered image
                val oldRendered = fileStore.getPageRenderedFile(sourceDocId, p.id)
                val newRendered = fileStore.getPageRenderedFile(newDocId, newPageId)
                if (oldRendered.exists()) {
                    fileStore.writeAtomically(newRendered) { fos ->
                        oldRendered.inputStream().use { it.copyTo(fos) }
                    }
                }

                // Copy thumb image
                val oldThumb = fileStore.getPageThumbFile(sourceDocId, p.id)
                val newThumb = fileStore.getPageThumbFile(newDocId, newPageId)
                if (oldThumb.exists()) {
                    fileStore.writeAtomically(newThumb) { fos ->
                        oldThumb.inputStream().use { it.copyTo(fos) }
                    }
                }

                val newPage = p.copy(
                    id = newPageId,
                    documentId = newDocId,
                    position = globalPosition++,
                    sourcePath = newSrc.absolutePath,
                    originalPath = if (newOrig.exists()) newOrig.absolutePath else null,
                    renderedPath = newRendered.absolutePath,
                    thumbPath = newThumb.absolutePath,
                    createdAt = System.currentTimeMillis()
                )
                newPages.add(newPage)
            }
        }

        val newDoc = DocumentEntity(
            id = newDocId,
            name = defaultName,
            kind = DocumentKind.SCAN.name,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            pageCount = newPages.size,
            coverPageId = newPages.firstOrNull()?.id,
            pageSize = PageSizeSetting.AUTO.name,
            locked = false,
            deletedAt = null
        )

        // Insert in database transaction
        database.withTransaction {
            database.documentDao().insert(newDoc)
            database.pageDao().insertAll(newPages)
        }

        // Build PDF
        val pdfFile = pdfBuilder.buildPdf(newDoc, newPages)
        database.documentDao().updatePdfBuiltAt(newDocId)

        // Mirror if enabled
        val mirrorEnabled = settingsRepository.mirrorEnabled.first()
        if (mirrorEnabled) {
            val mirrorUri = mirrorStore.mirrorPdf(
                pdfFile = pdfFile,
                docName = newDoc.name,
                existingUriString = null,
                isLocked = false
            )
            if (mirrorUri != null) {
                database.documentDao().updateMirrorUri(newDocId, mirrorUri)
            }
        }

        // Search index and background OCR
        val fts = com.syedmsidsm.abdulsscanner.data.db.entity.DocumentSearchFts(
            docId = newDocId,
            name = com.syedmsidsm.abdulsscanner.domain.search.SearchNormalizer.normalize(newDoc.name),
            body = newPages.mapNotNull { it.ocrText }.joinToString("\n")
        )
        database.searchDao().insertOrUpdate(fts)
        com.syedmsidsm.abdulsscanner.domain.ocr.OcrWorker.enqueueForDocument(context, newDocId)

        newDocId
    }
}
