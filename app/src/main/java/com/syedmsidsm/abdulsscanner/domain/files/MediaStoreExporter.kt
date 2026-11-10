package com.syedmsidsm.abdulsscanner.domain.files

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.syedmsidsm.abdulsscanner.data.db.entity.DocumentEntity
import com.syedmsidsm.abdulsscanner.data.db.entity.PageEntity
import com.syedmsidsm.abdulsscanner.data.files.FileStore
import com.syedmsidsm.abdulsscanner.data.files.MirrorStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

object MediaStoreExporter {

    suspend fun exportJpgsToPictures(
        context: Context,
        doc: DocumentEntity,
        pages: List<PageEntity>,
        fileStore: FileStore
    ): Int = withContext(Dispatchers.IO) {
        val safeDocName = MirrorStore.sanitizeFileName(doc.name)
        val relativeSubfolder = "Pictures/Abul's Scanner/$safeDocName"
        var exportedCount = 0

        val resolver = context.contentResolver

        pages.forEachIndexed { index, page ->
            val pageNum = index + 1
            val fileName = "$safeDocName - p$pageNum.jpg"
            val renderedFile = fileStore.getPageRenderedFile(doc.id, page.id)
            if (!renderedFile.exists()) return@forEachIndexed

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativeSubfolder)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        FileInputStream(renderedFile).use { it.copyTo(out) }
                    }
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                    exportedCount++
                }
            } else {
                @Suppress("DEPRECATION")
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val targetDir = File(picturesDir, "Abul's Scanner/$safeDocName").apply { mkdirs() }
                val targetFile = File(targetDir, fileName)
                renderedFile.copyTo(targetFile, overwrite = true)
                exportedCount++
            }
        }

        exportedCount
    }

    suspend fun exportBitmapToGallery(context: Context, bitmap: android.graphics.Bitmap, title: String) = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val fileName = "${title.replace(Regex("[/\\\\:*?\"<>|]"), "_")}.jpg"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Abul's Scanner")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)
                }
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
        } else {
            @Suppress("DEPRECATION")
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val targetDir = File(picturesDir, "Abul's Scanner").apply { mkdirs() }
            val targetFile = File(targetDir, fileName)
            targetFile.outputStream().use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)
            }
        }
    }
}
