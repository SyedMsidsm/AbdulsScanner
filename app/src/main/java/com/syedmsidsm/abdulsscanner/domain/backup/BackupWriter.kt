package com.syedmsidsm.abdulsscanner.domain.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.syedmsidsm.abdulsscanner.data.backup.BackupData
import com.syedmsidsm.abdulsscanner.data.backup.BackupDocumentDto
import com.syedmsidsm.abdulsscanner.data.backup.BackupDocumentTagDto
import com.syedmsidsm.abdulsscanner.data.backup.BackupManifest
import com.syedmsidsm.abdulsscanner.data.backup.BackupPageDto
import com.syedmsidsm.abdulsscanner.data.backup.BackupQrDto
import com.syedmsidsm.abdulsscanner.data.backup.BackupSignatureDto
import com.syedmsidsm.abdulsscanner.data.backup.BackupTagDto
import com.syedmsidsm.abdulsscanner.data.db.AppDatabase
import com.syedmsidsm.abdulsscanner.data.files.FileStore
import com.syedmsidsm.abdulsscanner.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class BackupWriter(
    private val context: Context,
    private val database: AppDatabase,
    private val fileStore: FileStore,
    private val settingsRepository: SettingsRepository
) {
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    companion object {
        fun generateBackupFileName(date: Date = Date()): String {
            val sdf = SimpleDateFormat("yyyyMMdd-HHmm", Locale.ENGLISH)
            return "AbdulsScanner-backup-${sdf.format(date)}.zip"
        }

        fun computeCrc32(file: File): Long {
            val crc = CRC32()
            FileInputStream(file).use { input ->
                val buffer = ByteArray(16384)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    crc.update(buffer, 0, bytesRead)
                }
            }
            return crc.value
        }
    }

    suspend fun createBackupZip(
        onProgress: (current: Int, total: Int, message: String) -> Unit = { _, _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        // Section 12.7: Low storage check
        val storageCheck = com.syedmsidsm.abdulsscanner.domain.safety.StorageSafety.checkStorage(context)
        if (storageCheck is com.syedmsidsm.abdulsscanner.domain.safety.StorageSafety.StorageCheckResult.Blocked) {
            throw IllegalStateException(storageCheck.message)
        }

        onProgress(0, 100, "Reading database snapshot...")

        // 1. Snapshot database inside read transaction into DTOs
        val allDocs = database.documentDao().getAllDocuments()
        val allPages = database.pageDao().getAllPages()
        val allTags = database.tagDao().getAll()
        val allDocTags = database.tagDao().getAllDocumentTags()
        val allQr = database.qrDao().getAll()
        val allSignatures = database.signatureDao().getAll()

        val backupData = BackupData(
            documents = allDocs.map {
                BackupDocumentDto(
                    id = it.id,
                    name = it.name,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt,
                    kind = it.kind,
                    pageSize = it.pageSize,
                    isLocked = it.locked,
                    deletedAt = it.deletedAt
                )
            },
            pages = allPages.map {
                BackupPageDto(
                    id = it.id,
                    docId = it.documentId,
                    position = it.position,
                    filter = it.filter,
                    rotation = it.rotation,
                    cropQuad = it.cropQuad,
                    brightness = it.brightness,
                    contrast = it.contrast,
                    overlays = it.overlays,
                    ocrText = it.ocrText,
                    ocrLang = it.ocrLang,
                    ocrStatus = it.ocrStatus
                )
            },
            tags = allTags.map {
                BackupTagDto(
                    id = it.id,
                    name = it.name,
                    color = "#13A06F",
                    createdAt = it.createdAt
                )
            },
            documentTags = allDocTags.map {
                BackupDocumentTagDto(
                    docId = it.documentId,
                    tagId = it.tagId
                )
            },
            qrHistory = allQr.map {
                BackupQrDto(
                    id = it.id,
                    rawValue = it.rawValue,
                    format = it.format,
                    valueType = it.valueType,
                    source = it.source,
                    createdAt = it.scannedAt
                )
            },
            signatures = allSignatures.map {
                BackupSignatureDto(
                    id = it.id,
                    createdAt = it.createdAt
                )
            }
        )

        // Gather all physical files to write
        val filesToWrite = mutableListOf<Pair<String, File>>() // Pair(zipPath, localFile)
        for (page in allPages) {
            val src = fileStore.getPageSrcFile(page.documentId, page.id)
            if (src.exists()) {
                filesToWrite.add("files/docs/${page.documentId}/src/${page.id}.jpg" to src)
            }
            val orig = fileStore.getPageOrigFile(page.documentId, page.id)
            if (orig.exists()) {
                filesToWrite.add("files/docs/${page.documentId}/orig/${page.id}.jpg" to orig)
            }
            val rendered = fileStore.getPageRenderedFile(page.documentId, page.id)
            if (rendered.exists()) {
                filesToWrite.add("files/docs/${page.documentId}/pages/${page.id}.jpg" to rendered)
            }
        }
        for (sig in allSignatures) {
            val sigFile = fileStore.getSignatureFile(sig.id)
            if (sigFile.exists()) {
                filesToWrite.add("files/signatures/${sig.id}.png" to sigFile)
            }
        }

        val manifest = BackupManifest(
            format = BackupManifest.FORMAT_NAME,
            formatVersion = BackupManifest.CURRENT_VERSION,
            appVersionCode = 200L,
            createdAt = System.currentTimeMillis(),
            documentCount = allDocs.size,
            pageCount = allPages.size,
            fileCount = filesToWrite.size
        )

        // 2. Write the zip to cacheDir/tmp/backup.zip
        val tmpDir = File(context.cacheDir, "tmp").apply { mkdirs() }
        val tempZip = File(tmpDir, "backup.zip")
        if (tempZip.exists()) tempZip.delete()

        ZipOutputStream(FileOutputStream(tempZip).buffered()).use { zipOut ->
            // manifest.json (DEFLATED)
            val manifestBytes = json.encodeToString(manifest).toByteArray(Charsets.UTF_8)
            val manifestEntry = ZipEntry("manifest.json").apply {
                method = ZipEntry.DEFLATED
            }
            zipOut.putNextEntry(manifestEntry)
            zipOut.write(manifestBytes)
            zipOut.closeEntry()

            // data.json (DEFLATED)
            val dataBytes = json.encodeToString(backupData).toByteArray(Charsets.UTF_8)
            val dataEntry = ZipEntry("data.json").apply {
                method = ZipEntry.DEFLATED
            }
            zipOut.putNextEntry(dataEntry)
            zipOut.write(dataBytes)
            zipOut.closeEntry()

            // Image files (STORED)
            val totalFiles = filesToWrite.size
            filesToWrite.forEachIndexed { index, (entryPath, file) ->
                onProgress(index + 1, totalFiles, "Writing files (${index + 1}/$totalFiles)...")
                val storedEntry = ZipEntry(entryPath).apply {
                    method = ZipEntry.STORED
                    size = file.length()
                    compressedSize = file.length()
                    crc = computeCrc32(file)
                }
                zipOut.putNextEntry(storedEntry)
                FileInputStream(file).use { input ->
                    input.copyTo(zipOut)
                }
                zipOut.closeEntry()
            }
        }

        // 3. Verify: reopen the zip, check manifest.json parses and entry count equals fileCount + 2
        onProgress(filesToWrite.size, filesToWrite.size, "Verifying backup archive...")
        ZipFile(tempZip).use { zipFile ->
            val manifestEntry = zipFile.getEntry("manifest.json")
                ?: throw IllegalStateException("Backup verification failed: manifest.json missing")
            val manifestText = zipFile.getInputStream(manifestEntry).bufferedReader().use { it.readText() }
            val readManifest = json.decodeFromString<BackupManifest>(manifestText)
            if (readManifest.format != BackupManifest.FORMAT_NAME) {
                throw IllegalStateException("Invalid backup format: ${readManifest.format}")
            }
            val expectedEntries = readManifest.fileCount + 2
            val actualEntries = zipFile.size()
            if (actualEntries != expectedEntries) {
                throw IllegalStateException("Verification failed: expected $expectedEntries entries, found $actualEntries")
            }
        }

        tempZip
    }

    suspend fun writeManualBackupToUri(
        destinationUri: Uri,
        onProgress: (current: Int, total: Int, message: String) -> Unit = { _, _, _ -> }
    ): Long = withContext(Dispatchers.IO) {
        val tempZip = createBackupZip(onProgress)
        val tempSize = tempZip.length()

        onProgress(100, 100, "Writing to destination...")
        val outStream: OutputStream = context.contentResolver.openOutputStream(destinationUri)
            ?: throw IllegalStateException("Unable to open output stream for destination: $destinationUri")

        var bytesCopied: Long = 0
        outStream.use { out ->
            FileInputStream(tempZip).use { input ->
                bytesCopied = input.copyTo(out)
            }
        }

        if (bytesCopied != tempSize) {
            throw IllegalStateException("Verification failed: copied $bytesCopied bytes, expected $tempSize bytes")
        }

        settingsRepository.setLastBackupAt(System.currentTimeMillis())
        tempZip.delete()
        bytesCopied
    }

    suspend fun writeAutoBackupToTree(
        treeUri: Uri,
        keepCount: Int = 3,
        onProgress: (current: Int, total: Int, message: String) -> Unit = { _, _, _ -> }
    ): DocumentFile = withContext(Dispatchers.IO) {
        val pickedDir = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IllegalStateException("Cannot access directory from URI: $treeUri")

        if (!pickedDir.canWrite()) {
            throw IllegalStateException("Directory is not writable: $treeUri")
        }

        val tempZip = createBackupZip(onProgress)
        val tempSize = tempZip.length()

        val fileName = generateBackupFileName()
        val backupDocFile = pickedDir.createFile("application/zip", fileName)
            ?: throw IllegalStateException("Failed to create file $fileName in destination folder")

        val outStream = context.contentResolver.openOutputStream(backupDocFile.uri)
            ?: throw IllegalStateException("Cannot write to $fileName")

        var bytesCopied: Long = 0
        outStream.use { out ->
            FileInputStream(tempZip).use { input ->
                bytesCopied = input.copyTo(out)
            }
        }

        if (bytesCopied != tempSize) {
            backupDocFile.delete()
            throw IllegalStateException("Verification failed: copied $bytesCopied bytes, expected $tempSize bytes")
        }

        // 5. Update lastBackupAt
        settingsRepository.setLastBackupAt(System.currentTimeMillis())
        tempZip.delete()

        // Purge older automatic backups beyond keepCount
        purgeOldAutoBackups(pickedDir, keepCount)

        backupDocFile
    }

    private fun purgeOldAutoBackups(dir: DocumentFile, keepCount: Int) {
        try {
            val backupFiles = dir.listFiles()
                .filter { file ->
                    val name = file.name ?: ""
                    name.startsWith("AbdulsScanner-backup-") && name.endsWith(".zip")
                }
                .sortedByDescending { it.name ?: "" }

            if (backupFiles.size > keepCount) {
                val toDelete = backupFiles.drop(keepCount)
                for (oldFile in toDelete) {
                    oldFile.delete()
                }
            }
        } catch (_: Exception) {
            // Ignore purge error
        }
    }
}
