package com.syedmsidsm.abdulsscanner.domain.imaging

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.roundToInt

@Serializable
sealed class PageOverlay {
    @Serializable
    @SerialName("SIGNATURE")
    data class Signature(
        val signatureId: String,
        val x: Float,
        val y: Float,
        val w: Float,
        val rotation: Int = 0
    ) : PageOverlay()

    @Serializable
    @SerialName("WATERMARK")
    data class Watermark(
        val text: String,
        val sizePct: Int = 6,
        val opacity: Int = 25,
        val angle: Int = 45,
        val color: String = "#808080",
        val tiled: Boolean = false
    ) : PageOverlay()
}

object OverlayCompositor {

    private val json = Json { ignoreUnknownKeys = true }

    fun parseOverlays(jsonString: String?): List<PageOverlay> {
        if (jsonString.isNullOrBlank() || jsonString == "[]") return emptyList()
        return try {
            json.decodeFromString<List<PageOverlay>>(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun serializeOverlays(overlays: List<PageOverlay>): String {
        return json.encodeToString(overlays)
    }

    fun applyOverlays(bitmap: Bitmap, overlays: List<PageOverlay>, getSignatureFile: (String) -> File?): Bitmap {
        if (overlays.isEmpty()) return bitmap

        // Ensure mutable bitmap for drawing
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)

        for (overlay in overlays) {
            when (overlay) {
                is PageOverlay.Signature -> {
                    val sigFile = getSignatureFile(overlay.signatureId)
                    if (sigFile != null && sigFile.exists()) {
                        val sigBmp = BitmapFactory.decodeFile(sigFile.absolutePath)
                        if (sigBmp != null) {
                            val targetW = (bitmap.width * overlay.w).roundToInt().coerceAtLeast(10)
                            val targetH = (targetW * (sigBmp.height.toFloat() / sigBmp.width)).roundToInt().coerceAtLeast(10)

                            val matrix = Matrix().apply {
                                postScale(targetW.toFloat() / sigBmp.width, targetH.toFloat() / sigBmp.height)
                                postRotate(overlay.rotation.toFloat(), targetW / 2f, targetH / 2f)
                                postTranslate(bitmap.width * overlay.x, bitmap.height * overlay.y)
                            }
                            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
                            canvas.drawBitmap(sigBmp, matrix, paint)
                            sigBmp.recycle()
                        }
                    }
                }
                is PageOverlay.Watermark -> {
                    drawWatermark(canvas, bitmap.width, bitmap.height, overlay)
                }
            }
        }

        if (mutableBitmap != bitmap) {
            bitmap.recycle()
        }
        return mutableBitmap
    }

    private fun drawWatermark(canvas: Canvas, width: Int, height: Int, watermark: PageOverlay.Watermark) {
        val parsedColor = try {
            Color.parseColor(watermark.color)
        } catch (e: Exception) {
            Color.GRAY
        }

        val alpha = (watermark.opacity / 100f * 255).roundToInt().coerceIn(0, 255)
        val paintColor = (alpha shl 24) or (parsedColor and 0x00FFFFFF)

        val textSizePx = width * (watermark.sizePct / 100f)
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = paintColor
            textSize = textSizePx
        }

        val textWidth = (textPaint.measureText(watermark.text).roundToInt() + 50).coerceAtLeast(100)
        val staticLayout = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(watermark.text, 0, watermark.text.length, textPaint, textWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(
                watermark.text,
                textPaint,
                textWidth,
                Layout.Alignment.ALIGN_CENTER,
                1.0f,
                0.0f,
                false
            )
        }

        canvas.save()
        if (watermark.tiled) {
            val stepX = (width / 2.5f).roundToInt()
            val stepY = (height / 3.5f).roundToInt()

            for (y in (stepY / 2) until height step stepY) {
                for (x in (stepX / 2) until width step stepX) {
                    canvas.save()
                    canvas.translate(x.toFloat(), y.toFloat())
                    canvas.rotate(-watermark.angle.toFloat())
                    canvas.translate(-staticLayout.width / 2f, -staticLayout.height / 2f)
                    staticLayout.draw(canvas)
                    canvas.restore()
                }
            }
        } else {
            // Single centred
            canvas.translate(width / 2f, height / 2f)
            canvas.rotate(-watermark.angle.toFloat())
            canvas.translate(-staticLayout.width / 2f, -staticLayout.height / 2f)
            staticLayout.draw(canvas)
        }
        canvas.restore()
    }
}
