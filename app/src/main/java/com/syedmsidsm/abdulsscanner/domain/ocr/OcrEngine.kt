package com.syedmsidsm.abdulsscanner.domain.ocr

import android.graphics.Bitmap

interface OcrEngine {
    val id: String
    suspend fun recognize(bitmap: Bitmap): String
}
