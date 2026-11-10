package com.syedmsidsm.abdulsscanner.domain.backup

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.room.withTransaction
import com.syedmsidsm.abdulsscanner.data.backup.BackupData
import com.syedmsidsm.abdulsscanner.data.backup.BackupManifest
import com.syedmsidsm.abdulsscanner.data.db.AppDatabase
import com.syedmsidsm.abdulsscanner.data.db.entity.DocumentEntity
import com.syedmsidsm.abdulsscanner.data.db.entity.DocumentKind
import com.syedmsidsm.abdulsscanner.data.db.entity.DocumentSearchFts
import com.syedmsidsm.abdulsscanner.data.db.entity.DocumentTagCrossRef
import com.syedmsidsm.abdulsscanner.data.db.entity.OcrStatus
import com.syedmsidsm.abdulsscanner.data.db.entity.PageEntity
import com.syedmsidsm.abdulsscanner.data.db.entity.PageFilter
import com.syedmsidsm.abdulsscanner.data.db.entity.PageSizeSetting
import com.syedmsidsm.abdulsscanner.data.db.entity.QrHistoryEntity
import com.syedmsidsm.abdulsscanner.data.db.entity.SignatureEntity
import com.syedmsidsm.abdulsscanner.data.db.entity.TagEntity
import com.syedmsidsm.abdulsscanner.data.files.FileStore
import com.syedmsidsm.abdulsscanner.domain.imaging.BitmapIo
import com.syedmsidsm.abdulsscanner.domain.pdf.PdfBuilder
import com.syedmsidsm.abdulsscanner.domain.search.SearchNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

data class BackupSummary(
    val manifest: BackupManifest,
    val data: BackupData,
    val tempFile: File
)

data class RestoreResult(
    val restoredDocs: Int,
    val skippedDocs: Int,
    val restoredPages: Int,
    val message: String
)

class BackupReader(
    private val context: Context,
    private val database: AppDatabase,
    private val fileStore: FileStore,
    private val pdfBuilder: PdfBuilder
) {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    suspend fun inspectBackup(uri: Uri): BackupSummary = withContext(Dispatchers.IO) {
        val tmpDir = File(context.cacheDir, "tmp").apply { mkdirs() }
        val tempZip = File(tmpDir, "restore_${System.currentTimeMillis()}.zip")
        if (tempZip.exists()) tempZip.delete()

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempZip).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalArgumentException("Could not open file from chosen URI")

        ZipFile(tempZip).use { zip ->
            val manifestEntry = zip.getEntry("manifest.json")
                ?: throw IllegalArgumentException("Invalid backup: missing manifest.json")
            val manifestStr = zip.getInputStream(manifestEntry).bufferedReader().use { it.readText() }
            val manifest = json.decodeFromString<BackupManifest>(manifestStr)

            if (manifest.format != BackupManifest.FORMAT_NAME) {
                throw IllegalArgumentException("Not a valid Abdul's Scanner backup file")
            }
            if (manifest.formatVersion > BackupManifest.CURRENT_VERSION) {
                throw IllegalArgumentException("Backup is from a newer version (v${manifest.formatVersion}). Please update the app to restore.")
            }

            val dataEntry = zip.getEntry("data.json")
                ?: throw IllegalArgumentException("Invalid backup: missing data.json")
            val dataStr = zip.getInputStream(dataEntry).bufferedReader().use { it.readText() }
            val data = json.decodeFromString<BackupData>(dataStr)

            BackupSummary(
                manifest = manifest,
                data = data,
                tempFile = tempZip
            )
        }
    }

    suspend fun restoreBackup(
        summary: BackupSummary,
        onProgress: (current: Int, total: Int, message: String) -> Unit = { _, _, _ -> }
    ): RestoreResult = withContext(Dispatchers.IO) {
        val existingDocIds = database.documentDao().getAllDocuments().map { it.id }.toSet()
        val docsToRestore = summary.data.documents.filter { !existingDocIds.contains(it.id) }
        val skippedDocCount = summary.data.documents.size - docsToRestore.size
        val docIdsToRestore = docsToRestore.map { it.id }.toSet()

        val pagesToRestore = summary.data.pages.filter { docIdsToRestore.contains(it.docId) }

        onProgress(0, 100, "Extracting files...")

        // 1. Extract physical files first
        ZipFile(summary.tempFile).use { zip ->
            // Extract page images
            val totalPages = pagesToRestore.size
            pagesToRestore.forEachIndexed { index, page ->
                onProgress(index + 1, totalPages.coerceAtLeast(1), "Extracting pages (${index + 1}/$totalPages)...")

                val srcEntry = zip.getEntry("files/docs/${page.docId}/src/${page.id}.jpg")
                if (srcEntry != null) {
                    val destSrc = fileStore.getPageSrcFile(page.docId, page.id)
                    extractEntryToFile(zip, srcEntry, destSrc)
                }

                val origEntry = zip.getEntry("files/docs/${page.docId}/orig/${page.id}.jpg")
                if (origEntry != null) {
                    val destOrig = fileStore.getPageOrigFile(page.docId, page.id)
                    extractEntryToFile(zip, origEntry, destOrig)
                }

                val renderedEntry = zip.getEntry("files/docs/${page.docId}/pages/${page.id}.jpg")
                if (renderedEntry != null) {
                    val destRendered = fileStore.getPageRenderedFile(page.docId, page.id)
                    extractEntryToFile(zip, renderedEntry, destRendered)

                    // Generate thumbnail from rendered file
                    try {
                        val thumbFile = fileStore.getPageThumbFile(page.docId, page.id)
                        val bmp = BitmapFactory.decodeFile(destRendered.absolutePath)
                        if (bmp != null) {
                            BitmapIo.createThumbnail(bmp, thumbFile)
                            bmp.recycle()
                        }
                    } catch (_: Exception) {}
                }
            }

            // Extract signatures
            val existingSignatures = database.signatureDao().getAll().map { it.id }.toSet()
            for (sig in summary.data.signatures) {
                if (!existingSignatures.contains(sig.id)) {
                    val sigEntry = zip.getEntry("files/signatures/${sig.id}.png")
                    if (sigEntry != null) {
                        val destSig = fileStore.getSignatureFile(sig.id)
                        extractEntryToFile(zip, sigEntry, destSig)
                    }
                }
            }
        }

        onProgress(80, 100, "Merging database records...")

        // 2. Merge tags by name
        val existingTags = database.tagDao().getAll()
        val tagNameToId = existingTags.associate { it.name.lowercase() to it.id }.toMutableMap()
        val backupTagIdToMergedId = mutableMapOf<Long, Long>()

        for (bTag in summary.data.tags) {
            val existingId = tagNameToId[bTag.name.lowercase()]
            if (existingId != null) {
                backupTagIdToMergedId[bTag.id] = existingId
            } else {
                val newTag = TagEntity(
                    id = 0,
                    name = bTag.name,
                    createdAt = bTag.createdAt
                )
                val newId = database.tagDao().insert(newTag)
                tagNameToId[bTag.name.lowercase()] = newId
                backupTagIdToMergedId[bTag.id] = newId
            }
        }

        // 3. Database transaction for documents, pages, tags, search FTS
        database.withTransaction {
            // Insert documents
            for (d in docsToRestore) {
                val docPages = pagesToRestore.filter { it.docId == d.id }
                val entity = DocumentEntity(
                    id = d.id,
                    name = d.name,
                    createdAt = d.createdAt,
                    updatedAt = d.updatedAt,
                    pageCount = docPages.size,
                    coverPageId = docPages.firstOrNull()?.id,
                    kind = d.kind,
                    pageSize = d.pageSize,
                    locked = d.isLocked,
                    deletedAt = d.deletedAt
                )
                database.documentDao().insert(entity)
            }

            // Insert pages
            for (p in pagesToRestore) {
                val srcFile = fileStore.getPageSrcFile(p.docId, p.id)
                val renderedFile = fileStore.getPageRenderedFile(p.docId, p.id)
                val origFile = fileStore.getPageOrigFile(p.docId, p.id)
                val thumbFile = fileStore.getPageThumbFile(p.docId, p.id)

                val entity = PageEntity(
                    id = p.id,
                    documentId = p.docId,
                    position = p.position,
                    sourcePath = srcFile.absolutePath,
                    originalPath = if (origFile.exists()) origFile.absolutePath else null,
                    renderedPath = renderedFile.absolutePath,
                    thumbPath = thumbFile.absolutePath,
                    widthPx = 1000,
                    heightPx = 1400,
                    filter = p.filter,
                    rotation = p.rotation,
                    cropQuad = p.cropQuad,
                    brightness = p.brightness,
                    contrast = p.contrast,
                    overlays = p.overlays,
                    ocrText = p.ocrText,
                    ocrLang = p.ocrLang,
                    ocrStatus = p.ocrStatus,
                    createdAt = System.currentTimeMillis()
                )
                database.pageDao().insert(entity)
            }

            // Insert document tags
            for (dt in summary.data.documentTags) {
                if (docIdsToRestore.contains(dt.docId)) {
                    val mergedTagId = backupTagIdToMergedId[dt.tagId]
                    if (mergedTagId != null) {
                        database.tagDao().addTagToDocument(
                            DocumentTagCrossRef(documentId = dt.docId, tagId = mergedTagId)
                        )
                    }
                }
            }

            // Insert QR history
            for (qr in summary.data.qrHistory) {
                database.qrDao().insertIgnore(
                    QrHistoryEntity(
                        id = qr.id,
                        rawValue = qr.rawValue,
                        format = qr.format,
                        valueType = qr.valueType,
                        source = qr.source,
                        scannedAt = qr.createdAt
                    )
                )
            }

            // Insert signatures
            for (sig in summary.data.signatures) {
                val sigFile = fileStore.getSignatureFile(sig.id)
                database.signatureDao().insertIgnore(
                    SignatureEntity(
                        id = sig.id,
                        path = sigFile.absolutePath,
                        createdAt = sig.createdAt
                    )
                )
            }

            // Populate FTS for restored documents
            for (doc in docsToRestore) {
                val docPages = pagesToRestore.filter { it.docId == doc.id }
                val bodyText = docPages.mapNotNull { it.ocrText }
                    .filter { it.isNotBlank() }
                    .joinToString("\n")
                val normTitle = SearchNormalizer.normalize(doc.name)
                val normBody = SearchNormalizer.normalize(bodyText)
                database.searchDao().insertOrUpdate(
                    DocumentSearchFts(
                        docId = doc.id,
                        name = normTitle,
                        body = normBody
                    )
                )
            }
        }

        // 4. Rebuild PDFs and mirror copies for restored documents
        onProgress(90, 100, "Rebuilding PDFs...")
        for (doc in docsToRestore) {
            try {
                val docEntity = database.documentDao().getById(doc.id) ?: continue
                val docPages = database.pageDao().getByDocumentId(doc.id)
                if (docPages.isNotEmpty()) {
                    pdfBuilder.buildPdf(docEntity, docPages)
                    database.documentDao().updatePdfBuiltAt(doc.id)
                }
            } catch (_: Exception) {}
        }

        // Clean up temp file
        if (summary.tempFile.exists()) {
            summary.tempFile.delete()
        }

        val restoredCount = docsToRestore.size
        val message = if (skippedDocCount > 0) {
            "Restored $restoredCount documents. $skippedDocCount were already on this phone."
        } else {
            "Restored $restoredCount documents."
        }

        RestoreResult(
            restoredDocs = restoredCount,
            skippedDocs = skippedDocCount,
            restoredPages = pagesToRestore.size,
            message = message
        )
    }

    private fun extractEntryToFile(zip: ZipFile, entry: ZipEntry, destFile: File) {
        val parent = destFile.parentFile ?: return
        if (!parent.exists()) parent.mkdirs()

        val tmpFile = File(parent, "${destFile.name}.tmp")
        zip.getInputStream(entry).use { input ->
            FileOutputStream(tmpFile).use { output ->
                input.copyTo(output)
            }
        }
        if (destFile.exists()) destFile.delete()
        tmpFile.renameTo(destFile)
    }
}
