package com.syedmsidsm.abdulsscanner.domain.imaging

import android.graphics.Bitmap
import android.graphics.Matrix
import com.syedmsidsm.abdulsscanner.data.db.entity.PageEntity
import com.syedmsidsm.abdulsscanner.data.db.entity.PageFilter
import com.syedmsidsm.abdulsscanner.data.files.FileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

class PageRenderer(private val fileStore: FileStore) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun renderPage(page: PageEntity): Pair<Int, Int> = withContext(Dispatchers.Default) {
        val srcFile = if (File(page.sourcePath).isAbsolute) File(page.sourcePath) else File(fileStore.filesDir, page.sourcePath)
        val origFile = if (!page.originalPath.isNullOrBlank()) {
            val f = File(page.originalPath)
            if (f.isAbsolute) f else File(fileStore.filesDir, page.originalPath)
        } else null

        val baseFile = if (origFile != null && origFile.exists()) origFile else srcFile
        require(baseFile.exists()) { "Source file not found at ${baseFile.absolutePath}" }

        // 1. Decode base image (raw photo if available, otherwise src)
        var currentBitmap = BitmapIo.decodeUpright(baseFile, maxLongSide = 4000)

        // 2. Perspective crop if cropQuad != null
        if (!page.cropQuad.isNullOrBlank() && page.cropQuad != "null") {
            try {
                val coords = json.decodeFromString<List<Float>>(page.cropQuad)
                if (coords.size == 8) {
                    val quad = Quad.fromNormalizedList(coords)
                    if (PerspectiveCrop.isValidQuad(quad)) {
                        val cropped = PerspectiveCrop.crop(currentBitmap, quad)
                        if (cropped != currentBitmap) {
                            currentBitmap.recycle()
                            currentBitmap = cropped
                        }
                    }
                }
            } catch (e: Exception) {
                // If crop fails, continue with uncropped image
            }
        }

        // If baseFile was orig, ensure srcFile holds the cropped version
        if (baseFile == origFile && srcFile.parentFile != null) {
            fileStore.writeAtomically(srcFile) { fos ->
                currentBitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)
                fos.flush()
            }
        }

        // 3. Rotate by rotation (clockwise)
        if (page.rotation % 360 != 0) {
            val matrix = Matrix().apply { postRotate(page.rotation.toFloat()) }
            val rotated = Bitmap.createBitmap(
                currentBitmap, 0, 0,
                currentBitmap.width, currentBitmap.height,
                matrix, true
            )
            if (rotated != currentBitmap) {
                currentBitmap.recycle()
                currentBitmap = rotated
            }
        }

        // 4 & 5. Filter and Brightness/Contrast
        val filterEnum = try {
            PageFilter.valueOf(page.filter)
        } catch (e: Exception) {
            PageFilter.ENHANCED
        }

        val width = currentBitmap.width
        val height = currentBitmap.height
        val pixels = IntArray(width * height)
        currentBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var filteredPixels = when (filterEnum) {
            PageFilter.ORIGINAL -> pixels
            PageFilter.ENHANCED -> Filters.applyEnhanced(pixels, width, height)
            PageFilter.GRAYSCALE -> Filters.applyGrayscale(pixels, width, height)
            PageFilter.BW -> Filters.applyBw(pixels, width, height)
        }

        if (page.brightness != 0 || page.contrast != 0) {
            filteredPixels = Filters.adjustBrightnessContrast(
                filteredPixels,
                page.brightness,
                page.contrast
            )
        }

        val filteredBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        filteredBitmap.setPixels(filteredPixels, 0, width, 0, 0, width, height)
        if (currentBitmap != filteredBitmap) {
            currentBitmap.recycle()
        }
        currentBitmap = filteredBitmap

        // 6. Overlays
        val overlays = OverlayCompositor.parseOverlays(page.overlays)
        if (overlays.isNotEmpty()) {
            val overlaid = OverlayCompositor.applyOverlays(currentBitmap, overlays) { sigId ->
                fileStore.getSignatureFile(sigId)
            }
            currentBitmap = overlaid
        }

        val outW = currentBitmap.width
        val outH = currentBitmap.height

        // 7. Encode rendered image (JPEG quality 90) atomically
        val renderedFile = if (File(page.renderedPath).isAbsolute) File(page.renderedPath) else File(fileStore.filesDir, page.renderedPath)
        fileStore.writeAtomically(renderedFile) { fos ->
            currentBitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
            fos.flush()
        }

        // 8. Generate thumbnail (360px on long side, quality 80) atomically
        val thumbFile = if (File(page.thumbPath).isAbsolute) File(page.thumbPath) else File(fileStore.filesDir, page.thumbPath)
        val tempThumb = fileStore.createTempFile(prefix = "thumb_", suffix = ".jpg")
        BitmapIo.createThumbnail(currentBitmap, tempThumb, targetLongSide = 360, quality = 80)
        fileStore.writeAtomically(thumbFile) { fos ->
            tempThumb.inputStream().use { it.copyTo(fos) }
        }
        tempThumb.delete()

        currentBitmap.recycle()

        Pair(outW, outH)
    }
}
