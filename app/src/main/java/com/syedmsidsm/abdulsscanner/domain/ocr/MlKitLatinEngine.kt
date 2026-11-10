package com.syedmsidsm.abdulsscanner.domain.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

class MlKitLatinEngine : OcrEngine {

    override val id: String = "MLKitLatin"

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override suspend fun recognize(bitmap: Bitmap): String {
        val image = InputImage.fromBitmap(bitmap, 0)
        val visionText = recognizer.process(image).await()

        // Sort text blocks top-to-bottom, left-to-right, separated by blank lines
        val sortedBlocks = visionText.textBlocks.sortedWith(
            compareBy<com.google.mlkit.vision.text.Text.TextBlock> { it.boundingBox?.top ?: 0 }
                .thenBy { it.boundingBox?.left ?: 0 }
        )

        return sortedBlocks.joinToString("\n\n") { it.text.trim() }
    }
}
