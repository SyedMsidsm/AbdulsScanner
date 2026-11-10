package com.syedmsidsm.abdulsscanner.domain.scan

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.withTransaction
import com.syedmsidsm.abdulsscanner.data.db.AppDatabase
import com.syedmsidsm.abdulsscanner.data.db.entity.DocumentEntity
import com.syedmsidsm.abdulsscanner.data.db.entity.DocumentKind
import com.syedmsidsm.abdulsscanner.data.db.entity.DocumentTagCrossRef
import com.syedmsidsm.abdulsscanner.data.db.entity.PageEntity
import com.syedmsidsm.abdulsscanner.data.db.entity.PageFilter
import com.syedmsidsm.abdulsscanner.data.files.FileStore
import com.syedmsidsm.abdulsscanner.data.files.MirrorStore
import com.syedmsidsm.abdulsscanner.data.settings.SettingsRepository
import com.syedmsidsm.abdulsscanner.domain.imaging.BitmapIo
import com.syedmsidsm.abdulsscanner.domain.imaging.PageRenderer
import com.syedmsidsm.abdulsscanner.domain.pdf.PdfBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class SaveScanUseCase(
    private val context: Context,
    private val database: AppDatabase,
    private val fileStore: FileStore,
    private val mirrorStore: MirrorStore,
    private val pageRenderer: PageRenderer,
    private val pdfBuilder: PdfBuilder,
    private val settingsRepository: SettingsRepository
) {

    suspend fun saveScan(
        imageUris: List<Uri>,
        kind: DocumentKind = DocumentKind.SCAN,
        customName: String? = null,
        targetDocId: String? = null,
        autoTagIdCard: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        require(imageUris.isNotEmpty()) { "Cannot save empty scan" }

        val hdMode = settingsRepository.hdMode.first()
        val defaultFilter = settingsRepository.defaultFilter.first()
        val defaultPageSize = settingsRepository.pageSize.first()
        val mirrorEnabled = settingsRepository.mirrorEnabled.first()

        val isAppend = targetDocId != null
        val docId = targetDocId ?: UUID.randomUUID().toString()

        val docName = customName ?: if (!isAppend) {
            val pattern = settingsRepository.namePattern.first()
            try {
                SimpleDateFormat(pattern, Locale.ENGLISH).format(Date())
            } catch (e: Exception) {
                "Scan ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH).format(Date())}"
            }
        } else {
            database.documentDao().getById(docId)?.name ?: "Scan"
        }

        // Get starting position if appending
        val existingPages = if (isAppend) database.pageDao().getByDocumentId(docId) else emptyList()
        val startPosition = existingPages.size

        val newPages = mutableListOf<PageEntity>()

        for ((index, uri) in imageUris.withIndex()) {
            val pageId = UUID.randomUUID().toString()
            val srcFile = fileStore.getSrcFile(docId, pageId)

            // Copy from Uri to srcFile respecting EXIF and HD setting
            val tempSource = fileStore.createTempFile(prefix = "scan_src_", suffix = ".jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempSource.outputStream().use { output -> input.copyTo(output) }
            }

            val decoded = BitmapIo.decodeUpright(
                tempSource,
                maxLongSide = if (hdMode) 8000 else 2400
            )
            tempSource.delete()

            fileStore.writeAtomically(srcFile) { fos ->
                decoded.compress(Bitmap.CompressFormat.JPEG, if (hdMode) 95 else 88, fos)
                fos.flush()
            }
            decoded.recycle()

            val pageEntity = PageEntity(
                id = pageId,
                documentId = docId,
                position = startPosition + index,
                sourcePath = "docs/$docId/src/$pageId.jpg",
                renderedPath = "docs/$docId/pages/$pageId.jpg",
                thumbPath = "docs/$docId/thumbs/$pageId.jpg",
                widthPx = 0,
                heightPx = 0,
                filter = defaultFilter
            )
            newPages.add(pageEntity)
        }

        val allPagesForDoc = existingPages + newPages

        val documentEntity = if (isAppend) {
            val existing = database.documentDao().getById(docId)!!
            existing.copy(
                pageCount = allPagesForDoc.size,
                updatedAt = System.currentTimeMillis()
            )
        } else {
            DocumentEntity(
                id = docId,
                name = docName,
                kind = kind.name,
                pageCount = allPagesForDoc.size,
                coverPageId = newPages.firstOrNull()?.id,
                pageSize = defaultPageSize,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        }

        // 1. Insert/update Room database in transaction
        database.withTransaction {
            database.documentDao().insert(documentEntity)
            database.pageDao().insertAll(newPages)

            if (autoTagIdCard && !isAppend) {
                // Auto-tag ID card
                val idCardTag = database.tagDao().getByName("ID Card")
                if (idCardTag != null) {
                    database.tagDao().addTagToDocument(
                        DocumentTagCrossRef(documentId = docId, tagId = idCardTag.id)
                    )
                }
            }
        }

        // 2. Render each new page
        for (page in newPages) {
            val (w, h) = pageRenderer.renderPage(page)
            database.pageDao().update(page.copy(widthPx = w, heightPx = h))
        }

        // 3. Build multi-page PDF
        val fullPagesList = database.pageDao().getByDocumentId(docId)
        val exportPdf = pdfBuilder.buildPdf(documentEntity, fullPagesList)
        database.documentDao().updatePdfBuiltAt(docId, System.currentTimeMillis())

        // 4. Mirror to Documents/Abdul's Scanner if enabled
        if (mirrorEnabled) {
            val mirrorUri = mirrorStore.mirrorPdf(
                pdfFile = exportPdf,
                docName = documentEntity.name,
                existingUriString = documentEntity.mirrorUri,
                isLocked = documentEntity.locked
            )
            if (mirrorUri != null) {
                database.documentDao().updateMirrorUri(docId, mirrorUri)
            }
        }

        docId
    }
}
