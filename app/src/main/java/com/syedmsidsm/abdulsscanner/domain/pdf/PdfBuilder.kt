package com.syedmsidsm.abdulsscanner.domain.pdf

import com.syedmsidsm.abdulsscanner.data.db.entity.DocumentEntity
import com.syedmsidsm.abdulsscanner.data.db.entity.PageEntity
import com.syedmsidsm.abdulsscanner.data.db.entity.PageSizeSetting
import com.syedmsidsm.abdulsscanner.data.files.FileStore
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

data class ImagePlacement(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float
)

object PdfLayoutMath {

    fun calculatePageBox(imgWidth: Int, imgHeight: Int, setting: String): Pair<PDRectangle, Float> {
        val sizeEnum = try {
            PageSizeSetting.valueOf(setting)
        } catch (e: Exception) {
            PageSizeSetting.AUTO
        }

        val isPortrait = imgHeight >= imgWidth

        return when (sizeEnum) {
            PageSizeSetting.AUTO -> {
                val box = if (isPortrait) {
                    val w = 595.28f
                    val h = 595.28f * (imgHeight.toFloat() / imgWidth)
                    PDRectangle(w, h)
                } else {
                    val h = 595.28f
                    val w = 595.28f * (imgWidth.toFloat() / imgHeight)
                    PDRectangle(w, h)
                }
                Pair(box, 0f)
            }
            PageSizeSetting.A4 -> {
                val box = if (isPortrait) {
                    PDRectangle(595.28f, 841.89f)
                } else {
                    PDRectangle(841.89f, 595.28f)
                }
                Pair(box, 18f)
            }
            PageSizeSetting.LETTER -> {
                val box = if (isPortrait) {
                    PDRectangle(612f, 792f)
                } else {
                    PDRectangle(792f, 612f)
                }
                Pair(box, 18f)
            }
        }
    }

    fun fitCentered(imgWidth: Int, imgHeight: Int, box: PDRectangle, margin: Float): ImagePlacement {
        val availW = box.width - 2 * margin
        val availH = box.height - 2 * margin

        val scale = minOf(availW / imgWidth.toFloat(), availH / imgHeight.toFloat())
        val drawW = imgWidth * scale
        val drawH = imgHeight * scale

        val drawX = margin + (availW - drawW) / 2f
        val drawY = margin + (availH - drawH) / 2f

        return ImagePlacement(drawX, drawY, drawW, drawH)
    }
}

class PdfBuilder(private val fileStore: FileStore) {

    suspend fun buildPdf(document: DocumentEntity, pages: List<PageEntity>): File = withContext(Dispatchers.IO) {
        val exportFile = fileStore.getExportPdfFile(document.id)
        val tmpFile = fileStore.createTempFile(prefix = "pdf_", suffix = ".pdf")

        PDDocument().use { pdf ->
            val info = PDDocumentInformation().apply {
                title = document.name
                producer = "Abdul's Scanner"
            }
            pdf.documentInformation = info

            for (page in pages) {
                val renderedFile = File(fileStore.filesDir, page.renderedPath)
                if (!renderedFile.exists()) continue

                val (box, margin) = PdfLayoutMath.calculatePageBox(
                    page.widthPx.coerceAtLeast(1),
                    page.heightPx.coerceAtLeast(1),
                    document.pageSize
                )

                val pdPage = PDPage(box)
                pdf.addPage(pdPage)

                val pdImage = FileInputStream(renderedFile).use { fis ->
                    JPEGFactory.createFromStream(pdf, fis)
                }

                PDPageContentStream(pdf, pdPage).use { cs ->
                    val placement = PdfLayoutMath.fitCentered(
                        pdImage.width,
                        pdImage.height,
                        box,
                        margin
                    )
                    cs.drawImage(pdImage, placement.x, placement.y, placement.w, placement.h)
                }
            }

            pdf.save(tmpFile)
        }

        fileStore.writeAtomically(exportFile) { fos ->
            tmpFile.inputStream().use { it.copyTo(fos) }
        }
        tmpFile.delete()

        exportFile
    }
}
