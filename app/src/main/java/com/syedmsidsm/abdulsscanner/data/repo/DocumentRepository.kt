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
