package com.syedmsidsm.abdulsscanner.domain.scan

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File

data class ScreenshotItem(
    val id: Long,
    val uri: Uri,
    val dateAddedMs: Long
)

object ScreenshotDetector {

    /**
     * Query MediaStore for the single newest screenshot taken within the last 10 minutes.
     * Returns null if no such screenshot exists or if it was already dismissed.
     */
    fun findNewestScreenshot(
        context: Context,
        dismissedIds: Set<String>
    ): ScreenshotItem? {
        val tenMinutesAgoSec = (System.currentTimeMillis() - 10 * 60 * 1000L) / 1000L
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED
        )

        val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? AND ${MediaStore.Images.Media.DATE_ADDED} >= ?"
        } else {
            "${MediaStore.Images.Media.DATA} LIKE ? AND ${MediaStore.Images.Media.DATE_ADDED} >= ?"
        }

        val selectionArgs = arrayOf("%Screenshots%", tenMinutesAgoSec.toString())
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        return try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val dateAddedSec = cursor.getLong(dateCol)
                    val idStr = id.toString()

                    if (idStr !in dismissedIds) {
                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id
                        )
                        return ScreenshotItem(
                            id = id,
                            uri = contentUri,
                            dateAddedMs = dateAddedSec * 1000L
                        )
                    }
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
