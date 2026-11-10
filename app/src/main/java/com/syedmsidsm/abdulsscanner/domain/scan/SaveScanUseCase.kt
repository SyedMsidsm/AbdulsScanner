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
import com.syedmsidsm.abdulsscanner.domain.imaging.PerspectiveCrop
import com.syedmsidsm.abdulsscanner.domain.imaging.Quad
import com.syedmsidsm.abdulsscanner.domain.pdf.PdfBuilder
import com.syedmsidsm.abdulsscanner.domain.scan.special.BookSplit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class CameraCaptureItem(
    val rawImageFile: File,
    val cropQuad: Quad? = null,
    val rotation: Int = 0
)

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

        // Section 12.7: Low storage check
        val storageCheck = com.syedmsidsm.abdulsscanner.domain.safety.StorageSafety.checkStorage(context)
        if (storageCheck is com.syedmsidsm.abdulsscanner.domain.safety.StorageSafety.StorageCheckResult.Blocked) {
            throw IllegalStateException(storageCheck.message)
        }

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

        // 5. Initial name index in FTS
        val fts = com.syedmsidsm.abdulsscanner.data.db.entity.DocumentSearchFts(
            docId = docId,
            name = com.syedmsidsm.abdulsscanner.domain.search.SearchNormalizer.normalize(documentEntity.name),
            body = ""
        )
        database.searchDao().insertOrUpdate(fts)

        // 6. Enqueue background OCR indexing
        com.syedmsidsm.abdulsscanner.domain.ocr.OcrWorker.enqueueForDocument(context, docId)

        docId
    }

    suspend fun saveBitmapScan(
        bitmaps: List<Bitmap>,
        kind: DocumentKind = DocumentKind.SCAN,
        customName: String? = null,
        pageSize: String = "A4",
        autoTagIdCard: Boolean = false,
        filter: String = PageFilter.ORIGINAL.name
    ): String = withContext(Dispatchers.IO) {
        require(bitmaps.isNotEmpty()) { "Cannot save empty scan" }

        val storageCheck = com.syedmsidsm.abdulsscanner.domain.safety.StorageSafety.checkStorage(context)
        if (storageCheck is com.syedmsidsm.abdulsscanner.domain.safety.StorageSafety.StorageCheckResult.Blocked) {
            throw IllegalStateException(storageCheck.message)
        }

        val mirrorEnabled = settingsRepository.mirrorEnabled.first()
        val docId = UUID.randomUUID().toString()

        val docName = customName ?: run {
            val prefix = when (kind) {
                DocumentKind.ID_CARD -> "ID Card "
                DocumentKind.BOOK -> "Book "
                DocumentKind.COLLAGE -> "Collage "
                else -> "Scan "
            }
            "$prefix${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH).format(Date())}"
        }

        val newPages = mutableListOf<PageEntity>()

        for ((index, bitmap) in bitmaps.withIndex()) {
            val pageId = UUID.randomUUID().toString()
            val srcFile = fileStore.getSrcFile(docId, pageId)

            fileStore.writeAtomically(srcFile) { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, fos)
                fos.flush()
            }

            val pageEntity = PageEntity(
                id = pageId,
                documentId = docId,
                position = index,
                sourcePath = "docs/$docId/src/$pageId.jpg",
                renderedPath = "docs/$docId/pages/$pageId.jpg",
                thumbPath = "docs/$docId/thumbs/$pageId.jpg",
                widthPx = bitmap.width,
                heightPx = bitmap.height,
                filter = filter
            )
            newPages.add(pageEntity)
        }

        val documentEntity = DocumentEntity(
            id = docId,
            name = docName,
            kind = kind.name,
            pageCount = newPages.size,
            coverPageId = newPages.firstOrNull()?.id,
            pageSize = pageSize,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        // 1. Database transaction
        database.withTransaction {
            database.documentDao().insert(documentEntity)
            database.pageDao().insertAll(newPages)

            if (autoTagIdCard) {
                val existingTag = database.tagDao().getByName("ID Card")
                val tagId = existingTag?.id ?: database.tagDao().insert(
                    com.syedmsidsm.abdulsscanner.data.db.entity.TagEntity(name = "ID Card")
                )
                database.tagDao().addTagToDocument(
                    DocumentTagCrossRef(documentId = docId, tagId = tagId)
                )
            }
        }

        // 2. Render each new page
        for (page in newPages) {
            val (w, h) = pageRenderer.renderPage(page)
            database.pageDao().update(page.copy(widthPx = w, heightPx = h))
        }

        // 3. Build PDF
        val fullPagesList = database.pageDao().getByDocumentId(docId)
        val exportPdf = pdfBuilder.buildPdf(documentEntity, fullPagesList)
        database.documentDao().updatePdfBuiltAt(docId, System.currentTimeMillis())

        // 4. Mirror
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

        // 5. Index search
        val fts = com.syedmsidsm.abdulsscanner.data.db.entity.DocumentSearchFts(
            docId = docId,
            name = com.syedmsidsm.abdulsscanner.domain.search.SearchNormalizer.normalize(documentEntity.name),
            body = ""
        )
        database.searchDao().insertOrUpdate(fts)

        // 6. OCR worker
        com.syedmsidsm.abdulsscanner.domain.ocr.OcrWorker.enqueueForDocument(context, docId)

        docId
    }

    suspend fun splitExistingPage(
        docId: String,
        pageId: String,
        splitFraction: Float,
        order: BookSplit.ReadingOrder
    ): Boolean = withContext(Dispatchers.IO) {
        val doc = database.documentDao().getById(docId) ?: return@withContext false
        val allPages = database.pageDao().getByDocumentId(docId).sortedBy { it.position }
        val targetIndex = allPages.indexOfFirst { it.id == pageId }
        if (targetIndex < 0) return@withContext false
        val targetPage = allPages[targetIndex]

        val srcFile = fileStore.getSrcFile(docId, pageId)
        if (!srcFile.exists()) return@withContext false

        val originalBmp = BitmapIo.decodeUpright(srcFile, maxLongSide = 4000)
        val (pageA, pageB) = BookSplit.splitBitmap(originalBmp, splitFraction, order)
        originalBmp.recycle()

        val idA = UUID.randomUUID().toString()
        val idB = UUID.randomUUID().toString()

        val wA = pageA.width
        val hA = pageA.height
        val wB = pageB.width
        val hB = pageB.height

        val fileA = fileStore.getSrcFile(docId, idA)
        fileStore.writeAtomically(fileA) { fos ->
            pageA.compress(Bitmap.CompressFormat.JPEG, 90, fos)
        }
        pageA.recycle()

        val fileB = fileStore.getSrcFile(docId, idB)
        fileStore.writeAtomically(fileB) { fos ->
            pageB.compress(Bitmap.CompressFormat.JPEG, 90, fos)
        }
        pageB.recycle()

        val pageEntityA = PageEntity(
            id = idA,
            documentId = docId,
            position = targetIndex,
            sourcePath = "docs/$docId/src/$idA.jpg",
            renderedPath = "docs/$docId/pages/$idA.jpg",
            thumbPath = "docs/$docId/thumbs/$idA.jpg",
            widthPx = wA,
            heightPx = hA,
            filter = targetPage.filter
        )

        val pageEntityB = PageEntity(
            id = idB,
            documentId = docId,
            position = targetIndex + 1,
            sourcePath = "docs/$docId/src/$idB.jpg",
            renderedPath = "docs/$docId/pages/$idB.jpg",
            thumbPath = "docs/$docId/thumbs/$idB.jpg",
            widthPx = wB,
            heightPx = hB,
            filter = targetPage.filter
        )

        val updatedPages = mutableListOf<PageEntity>()
        var pos = 0
        for ((idx, page) in allPages.withIndex()) {
            if (idx == targetIndex) {
                updatedPages.add(pageEntityA.copy(position = pos++))
                updatedPages.add(pageEntityB.copy(position = pos++))
            } else {
                updatedPages.add(page.copy(position = pos++))
            }
        }

        database.withTransaction {
            database.pageDao().deleteById(pageId)
            fileStore.getPageRenderedFile(docId, pageId).delete()
            fileStore.getPageThumbFile(docId, pageId).delete()
            srcFile.delete()

            database.pageDao().insert(pageEntityA)
            database.pageDao().insert(pageEntityB)
            for (p in updatedPages) {
                database.pageDao().update(p)
            }
            database.documentDao().update(doc.copy(pageCount = updatedPages.size, updatedAt = System.currentTimeMillis()))
        }

        // Render new pages
        pageRenderer.renderPage(pageEntityA)
        pageRenderer.renderPage(pageEntityB)

        // Rebuild PDF
        val refreshed = database.pageDao().getByDocumentId(docId)
        val exportPdf = pdfBuilder.buildPdf(doc.copy(pageCount = refreshed.size), refreshed)
        database.documentDao().updatePdfBuiltAt(docId, System.currentTimeMillis())

        if (settingsRepository.mirrorEnabled.first()) {
            mirrorStore.mirrorPdf(
                pdfFile = exportPdf,
                docName = doc.name,
                existingUriString = doc.mirrorUri,
                isLocked = doc.locked
            )
        }

        com.syedmsidsm.abdulsscanner.domain.ocr.OcrWorker.enqueueForDocument(context, docId)
        true
    }

    suspend fun saveCameraScans(
        captures: List<CameraCaptureItem>,
        kind: DocumentKind = DocumentKind.SCAN,
        customName: String? = null,
        targetDocId: String? = null,
        autoTagIdCard: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        require(captures.isNotEmpty()) { "Cannot save empty scan" }

        val storageCheck = com.syedmsidsm.abdulsscanner.domain.safety.StorageSafety.checkStorage(context)
        if (storageCheck is com.syedmsidsm.abdulsscanner.domain.safety.StorageSafety.StorageCheckResult.Blocked) {
            throw IllegalStateException(storageCheck.message)
        }

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

        val existingPages = if (isAppend) database.pageDao().getByDocumentId(docId) else emptyList()
        val startPosition = existingPages.size
        val newPages = mutableListOf<PageEntity>()

        for ((index, item) in captures.withIndex()) {
            val pageId = UUID.randomUUID().toString()
            val origFile = fileStore.getOrigFile(docId, pageId)
            val srcFile = fileStore.getSrcFile(docId, pageId)

            // Save raw photo to orig/
            fileStore.writeAtomically(origFile) { fos ->
                item.rawImageFile.inputStream().use { it.copyTo(fos) }
            }

            // Decode raw upright
            val rawBmp = BitmapIo.decodeUpright(origFile, maxLongSide = if (hdMode) 8000 else 4000)

            // Crop to src/ if quad provided
            val finalSrcBmp = if (item.cropQuad != null && PerspectiveCrop.isValidQuad(item.cropQuad)) {
                PerspectiveCrop.crop(rawBmp, item.cropQuad)
            } else {
                rawBmp
            }

            fileStore.writeAtomically(srcFile) { fos ->
                finalSrcBmp.compress(Bitmap.CompressFormat.JPEG, if (hdMode) 95 else 90, fos)
                fos.flush()
            }

            if (finalSrcBmp != rawBmp) {
                finalSrcBmp.recycle()
            }
            rawBmp.recycle()

            val quadJson = item.cropQuad?.let { Json.encodeToString(it.toNormalizedList()) }

            val pageEntity = PageEntity(
                id = pageId,
                documentId = docId,
                position = startPosition + index,
                sourcePath = "docs/$docId/src/$pageId.jpg",
                originalPath = "docs/$docId/orig/$pageId.jpg",
                renderedPath = "docs/$docId/pages/$pageId.jpg",
                thumbPath = "docs/$docId/thumbs/$pageId.jpg",
                widthPx = 0,
                heightPx = 0,
                cropQuad = quadJson,
                rotation = item.rotation,
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

        database.withTransaction {
            database.documentDao().insert(documentEntity)
            database.pageDao().insertAll(newPages)
            if (autoTagIdCard && !isAppend) {
                val idCardTag = database.tagDao().getByName("ID Card")
                if (idCardTag != null) {
                    database.tagDao().addTagToDocument(
                        DocumentTagCrossRef(documentId = docId, tagId = idCardTag.id)
                    )
                }
            }
        }

        for (page in newPages) {
            val (w, h) = pageRenderer.renderPage(page)
            database.pageDao().update(page.copy(widthPx = w, heightPx = h))
        }

        val fullPagesList = database.pageDao().getByDocumentId(docId)
        val exportPdf = pdfBuilder.buildPdf(documentEntity, fullPagesList)
        database.documentDao().updatePdfBuiltAt(docId, System.currentTimeMillis())

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

        val fts = com.syedmsidsm.abdulsscanner.data.db.entity.DocumentSearchFts(
            docId = docId,
            name = com.syedmsidsm.abdulsscanner.domain.search.SearchNormalizer.normalize(documentEntity.name),
            body = ""
        )
        database.searchDao().insertOrUpdate(fts)

        com.syedmsidsm.abdulsscanner.domain.ocr.OcrWorker.enqueueForDocument(context, docId)

        docId
    }
}
