package com.syedmsidsm.abdulsscanner.data.repo

import com.syedmsidsm.abdulsscanner.data.db.dao.DocumentDao
import com.syedmsidsm.abdulsscanner.data.db.dao.PageDao
import com.syedmsidsm.abdulsscanner.data.db.entity.DocumentEntity
import com.syedmsidsm.abdulsscanner.data.db.entity.PageEntity
import com.syedmsidsm.abdulsscanner.data.files.FileStore
import com.syedmsidsm.abdulsscanner.data.files.MirrorStore
import com.syedmsidsm.abdulsscanner.data.settings.SettingsRepository
import com.syedmsidsm.abdulsscanner.data.settings.SortOrder
import com.syedmsidsm.abdulsscanner.domain.pdf.PdfBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

class DocumentRepository(
    private val documentDao: DocumentDao,
    private val pageDao: PageDao,
    private val fileStore: FileStore,
    private val mirrorStore: MirrorStore,
    private val pdfBuilder: PdfBuilder,
    private val settingsRepository: SettingsRepository
) {

    fun observeDocuments(sortOrder: SortOrder, tagIdFilter: Long? = null, ungroupedFilter: Boolean = false): Flow<List<DocumentEntity>> {
        return if (ungroupedFilter) {
            documentDao.observeUngroupedUpdatedDesc()
        } else if (tagIdFilter != null) {
            documentDao.observeByTagUpdatedDesc(tagIdFilter)
        } else {
            when (sortOrder) {
                SortOrder.UPDATED -> documentDao.observeAllByUpdatedDesc()
                SortOrder.CREATED -> documentDao.observeAllByCreatedDesc()
                SortOrder.NAME -> documentDao.observeAllByNameAsc()
            }
        }
    }

    suspend fun getDocumentById(id: String): DocumentEntity? = documentDao.getById(id)
    fun observeDocumentById(id: String): Flow<DocumentEntity?> = documentDao.observeById(id)

    suspend fun getPages(documentId: String): List<PageEntity> = pageDao.getByDocumentId(documentId)
    fun observePages(documentId: String): Flow<List<PageEntity>> = pageDao.observeByDocumentId(documentId)

    suspend fun rename(id: String, newName: String) = withContext(Dispatchers.IO) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return@withContext
        val doc = documentDao.getById(id) ?: return@withContext
        documentDao.rename(id, trimmed)
        mirrorStore.renameMirror(doc.mirrorUri, trimmed)
        rebuildPdf(id)
    }

    suspend fun setLocked(id: String, locked: Boolean) = withContext(Dispatchers.IO) {
        val doc = documentDao.getById(id) ?: return@withContext
        documentDao.setLocked(id, locked)
        if (locked) {
            // Delete mirror file when document is locked
            mirrorStore.deleteMirror(doc.mirrorUri)
            documentDao.updateMirrorUri(id, null)
        } else {
            // Re-mirror on unlock
            val pdfFile = fileStore.getExportPdfFile(id)
            if (pdfFile.exists() && settingsRepository.mirrorEnabled.first()) {
                val newMirror = mirrorStore.mirrorPdf(pdfFile, doc.name, null, false)
                documentDao.updateMirrorUri(id, newMirror)
            }
        }
    }

    suspend fun moveToTrash(id: String) = withContext(Dispatchers.IO) {
        val doc = documentDao.getById(id) ?: return@withContext
        mirrorStore.deleteMirror(doc.mirrorUri)
        documentDao.updateMirrorUri(id, null)
        documentDao.moveToTrash(id)
    }

    suspend fun restoreFromTrash(id: String) = withContext(Dispatchers.IO) {
        documentDao.restoreFromTrash(id)
        val doc = documentDao.getById(id) ?: return@withContext
        val pdfFile = fileStore.getExportPdfFile(id)
        if (pdfFile.exists() && settingsRepository.mirrorEnabled.first() && !doc.locked) {
            val newMirror = mirrorStore.mirrorPdf(pdfFile, doc.name, null, false)
            documentDao.updateMirrorUri(id, newMirror)
        }
    }

    suspend fun deletePermanently(id: String) = withContext(Dispatchers.IO) {
        val doc = documentDao.getById(id)
        if (doc?.mirrorUri != null) {
            mirrorStore.deleteMirror(doc.mirrorUri)
        }
        fileStore.deleteDocumentDir(id)
        documentDao.deletePermanently(id)
    }

    suspend fun emptyTrash() = withContext(Dispatchers.IO) {
        val trash = documentDao.getTrashList()
        for (doc in trash) {
            deletePermanently(doc.id)
        }
    }

    fun observeTrash(): Flow<List<DocumentEntity>> = documentDao.observeTrash()

    suspend fun deletePage(documentId: String, pageId: String): Boolean = withContext(Dispatchers.IO) {
        val pages = pageDao.getByDocumentId(documentId)
        if (pages.size <= 1) {
            // If it was the last page, move the whole document to the recycle bin
            moveToTrash(documentId)
            return@withContext true
        }

        fileStore.deletePageFiles(documentId, pageId)
        pageDao.deleteById(pageId)

        // Re-index remaining page positions
        val remaining = pageDao.getByDocumentId(documentId)
        for ((idx, page) in remaining.withIndex()) {
            if (page.position != idx) {
                pageDao.updatePosition(page.id, idx)
            }
        }

        val doc = documentDao.getById(documentId)
        if (doc != null) {
            documentDao.update(
                doc.copy(
                    pageCount = remaining.size,
                    coverPageId = remaining.firstOrNull()?.id,
                    updatedAt = System.currentTimeMillis()
                )
            )
            rebuildPdf(documentId)
        }
        false
    }

    suspend fun deletePages(documentId: String, pageIds: List<String>): Boolean = withContext(Dispatchers.IO) {
        val pages = pageDao.getByDocumentId(documentId)
        val remaining = pages.filter { it.id !in pageIds }
        if (remaining.isEmpty()) {
            moveToTrash(documentId)
            return@withContext true
        }

        for (pageId in pageIds) {
            fileStore.deletePageFiles(documentId, pageId)
            pageDao.deleteById(pageId)
        }

        for ((idx, page) in remaining.withIndex()) {
            if (page.position != idx) {
                pageDao.updatePosition(page.id, idx)
            }
        }

        val doc = documentDao.getById(documentId)
        if (doc != null) {
            documentDao.update(
                doc.copy(
                    pageCount = remaining.size,
                    coverPageId = remaining.firstOrNull()?.id,
                    updatedAt = System.currentTimeMillis()
                )
            )
            rebuildPdf(documentId)
        }
        false
    }

    suspend fun duplicatePages(documentId: String, pageIds: List<String>) = withContext(Dispatchers.IO) {
        val pages = pageDao.getByDocumentId(documentId)
        var maxPos = pages.maxOfOrNull { it.position } ?: -1
        val newPages = mutableListOf<PageEntity>()

        for (pageId in pageIds) {
            val original = pages.firstOrNull { it.id == pageId } ?: continue
            val newPageId = java.util.UUID.randomUUID().toString()
            maxPos += 1

            // Copy files
            val origSrc = fileStore.getPageSrcFile(documentId, original.id)
            val newSrc = fileStore.getPageSrcFile(documentId, newPageId)
            if (origSrc.exists()) {
                origSrc.copyTo(newSrc, overwrite = true)
            }

            val origRendered = fileStore.getPageRenderedFile(documentId, original.id)
            val newRendered = fileStore.getPageRenderedFile(documentId, newPageId)
            if (origRendered.exists()) {
                origRendered.copyTo(newRendered, overwrite = true)
            }

            val origThumb = fileStore.getPageThumbFile(documentId, original.id)
            val newThumb = fileStore.getPageThumbFile(documentId, newPageId)
            if (origThumb.exists()) {
                origThumb.copyTo(newThumb, overwrite = true)
            }

            val duplicated = original.copy(
                id = newPageId,
                position = maxPos,
                createdAt = System.currentTimeMillis()
            )
            newPages.add(duplicated)
        }

        if (newPages.isNotEmpty()) {
            pageDao.insertAll(newPages)
            val allPages = pageDao.getByDocumentId(documentId)
            val doc = documentDao.getById(documentId)
            if (doc != null) {
                documentDao.update(
                    doc.copy(
                        pageCount = allPages.size,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                rebuildPdf(documentId)
            }
        }
    }

    suspend fun movePagesToDocument(fromDocId: String, pageIds: List<String>, toDocId: String) = withContext(Dispatchers.IO) {
        val targetPages = pageDao.getByDocumentId(toDocId)
        var maxPos = targetPages.maxOfOrNull { it.position } ?: -1

        val fromPages = pageDao.getByDocumentId(fromDocId)
        for (pageId in pageIds) {
            val page = fromPages.firstOrNull { it.id == pageId } ?: continue
            maxPos += 1

            // Move files from docs/fromDocId/ to docs/toDocId/
            val oldSrc = fileStore.getPageSrcFile(fromDocId, page.id)
            val newSrc = fileStore.getPageSrcFile(toDocId, page.id)
            if (oldSrc.exists()) oldSrc.copyTo(newSrc, overwrite = true).also { oldSrc.delete() }

            val oldRendered = fileStore.getPageRenderedFile(fromDocId, page.id)
            val newRendered = fileStore.getPageRenderedFile(toDocId, page.id)
            if (oldRendered.exists()) oldRendered.copyTo(newRendered, overwrite = true).also { oldRendered.delete() }

            val oldThumb = fileStore.getPageThumbFile(fromDocId, page.id)
            val newThumb = fileStore.getPageThumbFile(toDocId, page.id)
            if (oldThumb.exists()) oldThumb.copyTo(newThumb, overwrite = true).also { oldThumb.delete() }

            pageDao.update(page.copy(documentId = toDocId, position = maxPos))
        }

        // Reindex source document
        val remainingFrom = pageDao.getByDocumentId(fromDocId)
        if (remainingFrom.isEmpty()) {
            moveToTrash(fromDocId)
        } else {
            for ((idx, p) in remainingFrom.withIndex()) {
                if (p.position != idx) pageDao.updatePosition(p.id, idx)
            }
            val fromDoc = documentDao.getById(fromDocId)
            if (fromDoc != null) {
                documentDao.update(
                    fromDoc.copy(
                        pageCount = remainingFrom.size,
                        coverPageId = remainingFrom.firstOrNull()?.id,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                rebuildPdf(fromDocId)
            }
        }

        // Update target document
        val updatedToPages = pageDao.getByDocumentId(toDocId)
        val toDoc = documentDao.getById(toDocId)
        if (toDoc != null) {
            documentDao.update(
                toDoc.copy(
                    pageCount = updatedToPages.size,
                    coverPageId = updatedToPages.firstOrNull()?.id,
                    updatedAt = System.currentTimeMillis()
                )
            )
            rebuildPdf(toDocId)
        }
    }

    suspend fun movePagesToNewDoc(fromDocId: String, pageIds: List<String>, newDocName: String): String = withContext(Dispatchers.IO) {
        val newDocId = java.util.UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val newDoc = DocumentEntity(
            id = newDocId,
            name = newDocName.trim().ifEmpty { "New Document" },
            kind = com.syedmsidsm.abdulsscanner.data.db.entity.DocumentKind.SCAN.name,
            pageSize = "A4",
            createdAt = now,
            updatedAt = now,
            pageCount = 0
        )
        documentDao.insert(newDoc)
        movePagesToDocument(fromDocId, pageIds, newDocId)
        newDocId
    }

    suspend fun reorderPages(documentId: String, pageIdsInOrder: List<String>) = withContext(Dispatchers.IO) {
        for ((idx, pageId) in pageIdsInOrder.withIndex()) {
            pageDao.updatePosition(pageId, idx)
        }
        val doc = documentDao.getById(documentId)
        if (doc != null) {
            documentDao.update(
                doc.copy(
                    coverPageId = pageIdsInOrder.firstOrNull(),
                    updatedAt = System.currentTimeMillis()
                )
            )
            rebuildPdf(documentId)
        }
    }

    suspend fun updatePage(
        page: PageEntity,
        pageRenderer: com.syedmsidsm.abdulsscanner.domain.imaging.PageRenderer
    ) = withContext(Dispatchers.IO) {
        pageRenderer.renderPage(page)
        pageDao.update(page)
        val doc = documentDao.getById(page.documentId)
        if (doc != null) {
            documentDao.update(doc.copy(updatedAt = System.currentTimeMillis()))
            rebuildPdf(page.documentId)
        }
    }

    suspend fun retakePage(
        documentId: String,
        pageId: String,
        newSourceFile: File,
        pageRenderer: com.syedmsidsm.abdulsscanner.domain.imaging.PageRenderer
    ) = withContext(Dispatchers.IO) {
        val page = pageDao.getById(pageId) ?: return@withContext
        val srcFile = fileStore.getPageSrcFile(documentId, pageId)
        fileStore.writeAtomically(srcFile) { fos ->
            newSourceFile.inputStream().use { it.copyTo(fos) }
        }

        // Reset cropQuad and rotation, keep filter and adjustments
        val updated = page.copy(
            cropQuad = null,
            rotation = 0,
            ocrStatus = com.syedmsidsm.abdulsscanner.data.db.entity.OcrStatus.PENDING.name
        )
        pageRenderer.renderPage(updated)
        pageDao.update(updated)
        val doc = documentDao.getById(documentId)
        if (doc != null) {
            documentDao.update(doc.copy(updatedAt = System.currentTimeMillis()))
            rebuildPdf(documentId)
        }
    }

    suspend fun setPageSize(documentId: String, pageSize: String) = withContext(Dispatchers.IO) {
        documentDao.setPageSize(documentId, pageSize)
        rebuildPdf(documentId)
    }

    suspend fun rebuildPdf(documentId: String) = withContext(Dispatchers.IO) {
        val doc = documentDao.getById(documentId) ?: return@withContext
        val pages = pageDao.getByDocumentId(documentId)
        if (pages.isEmpty()) return@withContext

        val pdfFile = pdfBuilder.buildPdf(doc, pages)
        documentDao.updatePdfBuiltAt(documentId, System.currentTimeMillis())

        if (settingsRepository.mirrorEnabled.first() && !doc.locked) {
            val mirrorUri = mirrorStore.mirrorPdf(
                pdfFile = pdfFile,
                docName = doc.name,
                existingUriString = doc.mirrorUri,
                isLocked = doc.locked
            )
            if (mirrorUri != null) {
                documentDao.updateMirrorUri(documentId, mirrorUri)
            }
        }
    }
}
