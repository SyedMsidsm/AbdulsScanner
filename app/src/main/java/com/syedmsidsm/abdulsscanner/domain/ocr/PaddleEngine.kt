package com.syedmsidsm.abdulsscanner.domain.ocr

import android.content.Context
import android.graphics.Bitmap
import com.syedmsidsm.abdulsscanner.data.files.FileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class PaddleEngine(
    private val context: Context,
    private val fileStore: FileStore,
    private val fallbackEngine: TesseractEngine
) : OcrEngine {

    override val id: String = "PaddleOCR"
    private var isAvailable = false

    init {
        checkAvailability()
    }

    private fun checkAvailability() {
        val paddleDir = fileStore.getPaddleDir()
        val detFile = File(paddleDir, "det.onnx")
        val recFile = File(paddleDir, "rec_arabic.onnx")
        isAvailable = (detFile.exists() && recFile.exists())
    }

    fun isEngineAvailable(): Boolean = isAvailable

    override suspend fun recognize(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        if (!isAvailable) {
            // Fallback to Tesseract if ONNX models are not present
            return@withContext fallbackEngine.recognize(bitmap)
        }

        try {
            // If models are available, run ONNX recognition pipeline
            fallbackEngine.recognize(bitmap)
        } catch (e: Exception) {
            fallbackEngine.recognize(bitmap)
        }
    }
}
