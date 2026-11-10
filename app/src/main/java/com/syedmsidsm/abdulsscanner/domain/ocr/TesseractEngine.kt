package com.syedmsidsm.abdulsscanner.domain.ocr

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import com.syedmsidsm.abdulsscanner.data.files.FileStore
import com.syedmsidsm.abdulsscanner.domain.imaging.Filters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

class TesseractEngine(
    private val context: Context,
    private val fileStore: FileStore
) : OcrEngine {

    override val id: String = "Tesseract"
    private val mutex = Mutex()
    private var tessBaseApi: TessBaseAPI? = null
    private var isInitialized = false

    private suspend fun ensureInitialized() = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext

        val tessdataDir = fileStore.getTessdataDir()
        val araFile = File(tessdataDir, "ara.traineddata")
        val engFile = File(tessdataDir, "eng.traineddata")

        if (!araFile.exists() || araFile.length() == 0L) {
            context.assets.open("tessdata/ara.traineddata").use { input ->
                fileStore.copyStreamAtomically(input, araFile)
            }
        }
        if (!engFile.exists() || engFile.length() == 0L) {
            context.assets.open("tessdata/eng.traineddata").use { input ->
                fileStore.copyStreamAtomically(input, engFile)
            }
        }

        val api = TessBaseAPI()
        // tessBaseApi takes the parent directory containing the "tessdata" folder
        val success = api.init(fileStore.filesDir.absolutePath, "ara+eng", TessBaseAPI.OEM_LSTM_ONLY)
        if (success) {
            api.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
            tessBaseApi = api
            isInitialized = true
        } else {
            api.recycle()
            throw IllegalStateException("Failed to initialize Tesseract with ara+eng")
        }
    }

    override suspend fun recognize(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        mutex.withLock {
            ensureInitialized()
            val api = tessBaseApi ?: throw IllegalStateException("Tesseract not initialized")

            // Preprocessing: Grayscale and upscale x1.5 if height < 1800 px
            var processed = bitmap
            if (bitmap.height < 1800) {
                val scale = 1.5f
                val w = (bitmap.width * scale).roundToInt()
                val h = (bitmap.height * scale).roundToInt()
                processed = Bitmap.createScaledBitmap(bitmap, w, h, true)
            }

            // Convert to grayscale
            val w = processed.width
            val h = processed.height
            val pixels = IntArray(w * h)
            processed.getPixels(pixels, 0, w, 0, 0, w, h)
            val grayPixels = Filters.applyGrayscale(pixels, w, h)
            val grayBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            grayBitmap.setPixels(grayPixels, 0, w, 0, 0, w, h)

            try {
                api.setImage(grayBitmap)
                val text = api.utF8Text ?: ""
                api.clear()
                text.trim()
            } finally {
                if (processed != bitmap) processed.recycle()
                grayBitmap.recycle()
            }
        }
    }

    fun shutdown() {
        tessBaseApi?.recycle()
        tessBaseApi = null
        isInitialized = false
    }
}
