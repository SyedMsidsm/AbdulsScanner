package com.syedmsidsm.abdulsscanner.data.files

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

class FileStore(private val context: Context) {

    val filesDir: File = context.filesDir
    val cacheDir: File = context.cacheDir
    val tmpDir: File = File(cacheDir, "tmp").apply { mkdirs() }
    val shareDir: File = File(cacheDir, "share").apply { mkdirs() }

    init {
        clearTmpAndShare()
    }

    fun clearTmpAndShare() {
        tmpDir.listFiles()?.forEach { it.deleteRecursively() }
        shareDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    fun clearShareDir() {
        shareDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    fun getDocDir(docId: String): File = File(filesDir, "docs/$docId").apply { mkdirs() }
    fun getSrcDir(docId: String): File = File(filesDir, "docs/$docId/src").apply { mkdirs() }
    fun getOrigDir(docId: String): File = File(filesDir, "docs/$docId/orig").apply { mkdirs() }
    fun getPagesDir(docId: String): File = File(filesDir, "docs/$docId/pages").apply { mkdirs() }
    fun getThumbsDir(docId: String): File = File(filesDir, "docs/$docId/thumbs").apply { mkdirs() }
    fun getExportDir(docId: String): File = File(filesDir, "docs/$docId/export").apply { mkdirs() }
    fun getSignaturesDir(): File = File(filesDir, "signatures").apply { mkdirs() }
    fun getTessdataDir(): File = File(filesDir, "tessdata").apply { mkdirs() }
    fun getPaddleDir(): File = File(filesDir, "paddle").apply { mkdirs() }

    fun getSrcFile(docId: String, pageId: String): File = File(getSrcDir(docId), "$pageId.jpg")
    fun getOrigFile(docId: String, pageId: String): File = File(getOrigDir(docId), "$pageId.jpg")
    fun getRenderedFile(docId: String, pageId: String): File = File(getPagesDir(docId), "$pageId.jpg")
    fun getThumbFile(docId: String, pageId: String): File = File(getThumbsDir(docId), "$pageId.jpg")
    fun getExportPdfFile(docId: String): File = File(getExportDir(docId), "$docId.pdf")
    fun getSignatureFile(sigId: String): File = File(getSignaturesDir(), "$sigId.png")

    fun getPageSrcFile(docId: String, pageId: String): File = getSrcFile(docId, pageId)
    fun getPageOrigFile(docId: String, pageId: String): File = getOrigFile(docId, pageId)
    fun getPageRenderedFile(docId: String, pageId: String): File = getRenderedFile(docId, pageId)
    fun getPageThumbFile(docId: String, pageId: String): File = getThumbFile(docId, pageId)

    fun getTotalStorageBytes(): Long {
        return calculateDirSize(filesDir)
    }

    private fun calculateDirSize(dir: File): Long {
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) calculateDirSize(file) else file.length()
        }
        return size
    }

    fun createTempFile(prefix: String = "tmp_", suffix: String = ".tmp"): File {
        return File(tmpDir, "${prefix}${UUID.randomUUID()}${suffix}")
    }

    /**
     * Atomically writes data to targetFile by writing to a temp file and renaming.
     */
    fun writeAtomically(targetFile: File, writeAction: (FileOutputStream) -> Unit) {
        val temp = createTempFile(prefix = "atomic_", suffix = ".tmp")
        try {
            FileOutputStream(temp).use { fos ->
                writeAction(fos)
                fos.flush()
            }
            if (targetFile.exists()) {
                targetFile.delete()
            }
            targetFile.parentFile?.mkdirs()
            if (!temp.renameTo(targetFile)) {
                // If rename fails (e.g. across mount points), copy and delete temp
                temp.copyTo(targetFile, overwrite = true)
                temp.delete()
            }
        } finally {
            if (temp.exists()) {
                temp.delete()
            }
        }
    }

    fun copyStreamAtomically(input: InputStream, targetFile: File) {
        writeAtomically(targetFile) { fos ->
            input.copyTo(fos)
        }
    }

    fun deleteDocumentDir(docId: String): Boolean {
        return getDocDir(docId).deleteRecursively()
    }

    fun deletePageFiles(docId: String, pageId: String) {
        getSrcFile(docId, pageId).delete()
        getOrigFile(docId, pageId).delete()
        getRenderedFile(docId, pageId).delete()
        getThumbFile(docId, pageId).delete()
    }
}
