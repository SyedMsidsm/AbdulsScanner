package com.syedmsidsm.abdulsscanner.domain.imaging

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.roundToInt

object BitmapIo {

    fun decodeUpright(file: File, maxLongSide: Int = 4000): Bitmap {
        // First read dimensions and exif
        val exif = try {
            ExifInterface(file.absolutePath)
        } catch (e: Exception) {
            null
        }

        val orientation = exif?.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        ) ?: ExifInterface.ORIENTATION_NORMAL

        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, boundsOptions)
        val origW = boundsOptions.outWidth
        val origH = boundsOptions.outHeight

        var sampleSize = 1
        val maxSide = maxOf(origW, origH)
        while (maxSide / (sampleSize * 2) >= maxLongSide) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
            ?: throw IllegalStateException("Failed to decode image from ${file.absolutePath}")

        val rotationAngle = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }

        return if (rotationAngle != 0f) {
            val matrix = Matrix().apply { postRotate(rotationAngle) }
            val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            if (rotated != decoded) decoded.recycle()
            rotated
        } else {
            decoded
        }
    }

    fun saveJpeg(bitmap: Bitmap, targetFile: File, quality: Int) {
        val parent = targetFile.parentFile
        if (parent != null && !parent.exists()) parent.mkdirs()

        FileOutputStream(targetFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, fos)
            fos.flush()
        }
    }

    fun createThumbnail(bitmap: Bitmap, targetFile: File, targetLongSide: Int = 360, quality: Int = 80) {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        val thumb = if (maxSide > targetLongSide) {
            val scale = targetLongSide.toFloat() / maxSide
            val w = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
            val h = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, w, h, true)
        } else {
            bitmap
        }

        try {
            saveJpeg(thumb, targetFile, quality)
        } finally {
            if (thumb != bitmap) {
                thumb.recycle()
            }
        }
    }
}
