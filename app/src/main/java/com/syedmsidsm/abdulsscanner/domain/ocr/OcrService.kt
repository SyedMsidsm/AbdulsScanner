package com.syedmsidsm.abdulsscanner.domain.ocr

import android.graphics.Bitmap
import com.syedmsidsm.abdulsscanner.data.db.entity.OcrLanguage
import com.syedmsidsm.abdulsscanner.data.settings.ArabicOcrEngine
import com.syedmsidsm.abdulsscanner.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first

class OcrService(
    val mlKitLatinEngine: MlKitLatinEngine,
    val tesseractEngine: TesseractEngine,
    val paddleEngine: PaddleEngine,
    private val settingsRepository: SettingsRepository
) {

    suspend fun getActiveArabicEngine(): OcrEngine {
        val selected = settingsRepository.arabicEngine.first()
        return if (selected == ArabicOcrEngine.PADDLE.name && paddleEngine.isEngineAvailable()) {
            paddleEngine
        } else {
            tesseractEngine
        }
    }

    suspend fun recognize(bitmap: Bitmap, language: OcrLanguage): String {
        val arabicEngine = getActiveArabicEngine()

        return when (language) {
            OcrLanguage.EN -> {
                mlKitLatinEngine.recognize(bitmap)
            }
            OcrLanguage.AR -> {
                arabicEngine.recognize(bitmap)
            }
            OcrLanguage.BOTH -> {
                // Tesseract runs ara+eng directly
                arabicEngine.recognize(bitmap)
            }
        }
    }
}
