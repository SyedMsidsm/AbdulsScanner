package com.syedmsidsm.abdulsscanner.data.files

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream

class MirrorStore(private val context: Context) {

    companion object {
        const val MIRROR_FOLDER_RELATIVE = "Documents/Abdul's Scanner"

        fun sanitizeFileName(name: String): String {
            val invalidChars = Regex("[/\\\\:*?\"<>|\\x00-\\x1F]")
            val cleaned = name.replace(invalidChars, "_").trim()
            return if (cleaned.length > 100) cleaned.substring(0, 100).trim() else if (cleaned.isEmpty()) "Document" else cleaned
        }
    }

    /**
     * Mirrors the export PDF to Documents/Abdul's Scanner.
     * Returns the MediaStore or file URI of the mirrored PDF.
     */
    fun mirrorPdf(
        pdfFile: File,
        docName: String,
        existingUriString: String?,
        isLocked: Boolean
    ): String? {
        // A locked document must not sit readable in a public folder
        if (isLocked) {
            existingUriString?.let { deleteMirror(it) }
            return null
        }

        if (!pdfFile.exists() || pdfFile.length() == 0L) {
            return null
        }

        val safeName = "${sanitizeFileName(docName)}.pdf"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (existingUriString != null) {
                try {
                    val uri = Uri.parse(existingUriString)
                    context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                        FileInputStream(pdfFile).use { `in` -> `in`.copyTo(out) }
                    }
                    return existingUriString
                } catch (e: Exception) {
                    // Stale URI or file removed; fallback to creating a fresh entry
                }
            }

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, MIRROR_FOLDER_RELATIVE)
            }

            val uri = context.contentResolver.insert(
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                contentValues
            ) ?: return null

            try {
                context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                    FileInputStream(pdfFile).use { `in` -> `in`.copyTo(out) }
                }
                return uri.toString()
            } catch (e: Exception) {
                return null
            }
        } else {
            // Android 8-9 (API 26-28)
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "Abdul's Scanner"
            ).apply { mkdirs() }
            val target = File(dir, safeName)
            try {
                pdfFile.copyTo(target, overwrite = true)
                return Uri.fromFile(target).toString()
            } catch (e: Exception) {
                return null
            }
        }
    }

    fun deleteMirror(uriString: String?) {
        if (uriString == null) return
        try {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "file") {
                uri.path?.let { File(it).delete() }
            } else {
                context.contentResolver.delete(uri, null, null)
            }
        } catch (_: Exception) {}
    }

    fun renameMirror(uriString: String?, newDocName: String) {
        if (uriString == null) return
        val safeName = "${sanitizeFileName(newDocName)}.pdf"
        try {
            val uri = Uri.parse(uriString)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val cv = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
                }
                context.contentResolver.update(uri, cv, null, null)
            } else if (uri.scheme == "file") {
                val oldFile = File(uri.path ?: return)
                val newFile = File(oldFile.parentFile, safeName)
                oldFile.renameTo(newFile)
            }
        } catch (_: Exception) {}
    }
}
