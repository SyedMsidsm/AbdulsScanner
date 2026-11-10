package com.syedmsidsm.abdulsscanner.domain.pdf

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.room.withTransaction
import com.syedmsidsm.abdulsscanner.data.db.AppDatabase
import com.syedmsidsm.abdulsscanner.data.db.entity.DocumentEntity
import com.syedmsidsm.abdulsscanner.data.db.entity.DocumentKind
import com.syedmsidsm.abdulsscanner.data.db.entity.PageEntity
import com.syedmsidsm.abdulsscanner.data.files.FileStore
import com.syedmsidsm.abdulsscanner.data.files.MirrorStore
import com.syedmsidsm.abdulsscanner.data.settings.SettingsRepository
import com.syedmsidsm.abdulsscanner.domain.imaging.OverlayCompositor
import com.syedmsidsm.abdulsscanner.domain.imaging.PageOverlay
import com.syedmsidsm.abdulsscanner.domain.imaging.PageRenderer
import com.syedmsidsm.abdulsscanner.domain.scan.MergeDocumentsUseCase
import com.syedmsidsm.abdulsscanner.domain.scan.SaveScanUseCase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.multipdf.Splitter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

sealed interface ToolInput {
    data class AppDoc(val docId: String) : ToolInput
    data class PdfFile(val uri: Uri, val displayName: String) : ToolInput
}

enum class CompressionPreset(val maxLongSide: Int, val quality: Int) {
    SMALL(1240, 50),
    MEDIUM(1754, 65),
    LARGE(2480, 80)
}

data class CompressionResult(
    val outputFile: File,
    val beforeSizeBytes: Long,
    val afterSizeBytes: Long,
    val isSmaller: Boolean
)

class PdfToolsService(
    private val context: Context,
    private val database: AppDatabase,
    private val fileStore: FileStore,
    private val pageRenderer: PageRenderer,
    private val pdfBuilder: PdfBuilder,
    private val mirrorStore: MirrorStore,
    private val settingsRepository: SettingsRepository,
    private val mergeDocumentsUseCase: MergeDocumentsUseCase,
    private val saveScanUseCase: SaveScanUseCase
) {

    suspend fun getDocDisplayName(input: ToolInput): String = withContext(Dispatchers.IO) {
        when (input) {
            is ToolInput.AppDoc -> database.documentDao().getById(input.docId)?.name ?: "Document"
            is ToolInput.PdfFile -> input.displayName.removeSuffix(".pdf")
        }
    }

    suspend fun exportToToolsFolder(sourceFile: File, baseName: String, suffix: String): Uri? = withContext(Dispatchers.IO) {
        val safeName = MirrorStore.sanitizeFileName("$baseName ($suffix).pdf")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/Abdul's Scanner/Tools")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(sourceFile).use { input -> input.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                return@withContext uri
            }
        } else {
            @Suppress("DEPRECATION")
            val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val toolsDir = File(docsDir, "Abdul's Scanner/Tools").apply { mkdirs() }
            val targetFile = File(toolsDir, safeName)
            sourceFile.copyTo(targetFile, overwrite = true)
            return@withContext Uri.fromFile(targetFile)
        }
        null
    }

    suspend fun merge(inputs: List<ToolInput>, customName: String? = null): File = withContext(Dispatchers.IO) {
        require(inputs.size >= 2) { "Need at least 2 inputs to merge" }

        val allAreAppDocs = inputs.all { it is ToolInput.AppDoc }
        val firstName = getDocDisplayName(inputs.first())
        val outputName = customName ?: firstName

        val tempMergedFile = fileStore.createTempFile("merge_", ".pdf")

        val merger = PDFMergerUtility()
        merger.destinationFileName = tempMergedFile.absolutePath

        val tempFilesToClean = mutableListOf<File>()

        try {
            for (input in inputs) {
                when (input) {
                    is ToolInput.AppDoc -> {
                        val pdf = fileStore.getExportPdfFile(input.docId)
                        if (pdf.exists()) {
                            merger.addSource(pdf)
                        } else {
                            val doc = database.documentDao().getById(input.docId)
                            val pages = database.pageDao().getByDocumentId(input.docId)
                            if (doc != null && pages.isNotEmpty()) {
                                val generated = pdfBuilder.buildPdf(doc, pages)
                                merger.addSource(generated)
                            }
                        }
                    }
                    is ToolInput.PdfFile -> {
                        val tempInput = fileStore.createTempFile("merge_input_", ".pdf")
                        tempFilesToClean.add(tempInput)
                        context.contentResolver.openInputStream(input.uri)?.use { ins ->
                            tempInput.outputStream().use { outs -> ins.copyTo(outs) }
                        }
                        merger.addSource(tempInput)
                    }
                }
            }
            merger.mergeDocuments(null)
        } finally {
            for (f in tempFilesToClean) {
                f.delete()
            }
        }

        tempMergedFile
    }

    suspend fun splitRanges(
        input: ToolInput,
        rangesString: String
    ): List<File> = withContext(Dispatchers.IO) {
        val tempPdf = getTempPdfFromInput(input)
        val document = PDDocument.load(tempPdf)
        val totalPages = document.numberOfPages
        val outputFiles = mutableListOf<File>()

        try {
            val ranges = parsePageRanges(rangesString, totalPages)
            for ((idx, pageIndices) in ranges.withIndex()) {
                val splitDoc = PDDocument()
                for (pageIndex in pageIndices) {
                    if (pageIndex in 0 until totalPages) {
                        splitDoc.addPage(document.getPage(pageIndex))
                    }
                }
                val outputFile = fileStore.createTempFile("split_${idx + 1}_", ".pdf")
                splitDoc.save(outputFile)
                splitDoc.close()
                outputFiles.add(outputFile)
            }
        } finally {
            document.close()
            tempPdf.delete()
        }

        outputFiles
    }

    suspend fun compressPdf(input: ToolInput, preset: CompressionPreset): CompressionResult = withContext(Dispatchers.IO) {
        val tempInput = getTempPdfFromInput(input)
        val originalSize = tempInput.length()
        val tempOutput = fileStore.createTempFile("comp_", ".pdf")

        val document = PDDocument.load(tempInput)

        try {
            for (page in document.pages) {
                val resources = page.resources ?: continue
                for (name in resources.xObjectNames) {
                    if (resources.isImageXObject(name)) {
                        val image = resources.getXObject(name) as? PDImageXObject ?: continue
                        // Skip masked or transparent images
                        if (image.mask != null || image.softMask != null) continue

                        val width = image.width
                        val height = image.height
                        val maxSide = maxOf(width, height)
                        if (maxSide <= preset.maxLongSide) continue

                        val bmp = image.image ?: continue
                        val scale = preset.maxLongSide.toFloat() / maxSide
                        val newW = (width * scale).toInt().coerceAtLeast(1)
                        val newH = (height * scale).toInt().coerceAtLeast(1)

                        val scaledBmp = Bitmap.createScaledBitmap(bmp, newW, newH, true)
                        val newImage = JPEGFactory.createFromImage(document, scaledBmp, preset.quality / 100f)
                        resources.put(name, newImage)

                        if (scaledBmp != bmp) scaledBmp.recycle()
                        bmp.recycle()
                    }
                }
            }
            document.save(tempOutput)
        } finally {
            document.close()
            tempInput.delete()
        }

        val afterSize = tempOutput.length()
        CompressionResult(
            outputFile = tempOutput,
            beforeSizeBytes = originalSize,
            afterSizeBytes = afterSize,
            isSmaller = afterSize < originalSize
        )
    }

    suspend fun applyWatermark(input: ToolInput, watermark: PageOverlay.Watermark): File = withContext(Dispatchers.IO) {
        when (input) {
            is ToolInput.AppDoc -> {
                // Add watermark overlay to all pages of the document
                val pages = database.pageDao().getByDocumentId(input.docId)
                for (p in pages) {
                    val existing = OverlayCompositor.parseOverlays(p.overlays).toMutableList()
                    existing.removeAll { it is PageOverlay.Watermark }
                    existing.add(watermark)
                    val json = OverlayCompositor.serializeOverlays(existing)
                    database.pageDao().update(p.copy(overlays = json))
                    pageRenderer.renderPage(p.copy(overlays = json))
                }
                val doc = database.documentDao().getById(input.docId)!!
                val fullPages = database.pageDao().getByDocumentId(input.docId)
                val exportPdf = pdfBuilder.buildPdf(doc, fullPages)
                exportPdf
            }
            is ToolInput.PdfFile -> {
                val tempInput = getTempPdfFromInput(input)
                val tempOutput = fileStore.createTempFile("wm_", ".pdf")
                val document = PDDocument.load(tempInput)

                try {
                    for (page in document.pages) {
                        val mediaBox = page.mediaBox
                        val w = mediaBox.width.toInt().coerceAtLeast(100)
                        val h = mediaBox.height.toInt().coerceAtLeast(100)

                        val transparentBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        val overlaid = OverlayCompositor.applyOverlays(transparentBmp, listOf(watermark)) { null }

                        val pdImage = LosslessFactory.createFromImage(document, overlaid)
                        overlaid.recycle()

                        val contentStream = PDPageContentStream(
                            document,
                            page,
                            PDPageContentStream.AppendMode.APPEND,
                            true,
                            true
                        )
                        contentStream.drawImage(pdImage, 0f, 0f, mediaBox.width, mediaBox.height)
                        contentStream.close()
                    }
                    document.save(tempOutput)
                } finally {
                    document.close()
                    tempInput.delete()
                }
                tempOutput
            }
        }
    }

    suspend fun passwordProtect(input: ToolInput, password: String): File = withContext(Dispatchers.IO) {
        require(password.isNotBlank()) { "Password cannot be empty" }
        val tempInput = getTempPdfFromInput(input)
        val tempOutput = fileStore.createTempFile("protected_", ".pdf")

        val document = PDDocument.load(tempInput)
        try {
            val ap = AccessPermission()
            val ownerPassword = UUID.randomUUID().toString().replace("-", "")
            val spp = StandardProtectionPolicy(ownerPassword, password, ap).apply {
                encryptionKeyLength = 256
            }
            document.protect(spp)
            document.save(tempOutput)
        } finally {
            document.close()
            tempInput.delete()
        }

        tempOutput
    }

    suspend fun removePassword(input: ToolInput, password: String): File = withContext(Dispatchers.IO) {
        val tempInput = getTempPdfFromInput(input)
        val tempOutput = fileStore.createTempFile("unlocked_", ".pdf")

        val document = PDDocument.load(tempInput, password)
        try {
            document.isAllSecurityToBeRemoved = true
            document.save(tempOutput)
        } finally {
            document.close()
            tempInput.delete()
        }

        tempOutput
    }

    suspend fun stampSignature(
        input: ToolInput,
        pageIndex: Int,
        signatureId: String,
        normX: Float,
        normY: Float,
        normW: Float,
        rotation: Int = 0
    ): File = withContext(Dispatchers.IO) {
        when (input) {
            is ToolInput.AppDoc -> {
                val pages = database.pageDao().getByDocumentId(input.docId).sortedBy { it.position }
                val targetPage = pages.getOrNull(pageIndex) ?: pages.first()

                val overlay = PageOverlay.Signature(
                    signatureId = signatureId,
                    x = normX,
                    y = normY,
                    w = normW,
                    rotation = rotation
                )

                val existing = OverlayCompositor.parseOverlays(targetPage.overlays).toMutableList()
                existing.add(overlay)
                val json = OverlayCompositor.serializeOverlays(existing)
                database.pageDao().update(targetPage.copy(overlays = json))
                pageRenderer.renderPage(targetPage.copy(overlays = json))

                val doc = database.documentDao().getById(input.docId)!!
                val fullPages = database.pageDao().getByDocumentId(input.docId)
                pdfBuilder.buildPdf(doc, fullPages)
            }
            is ToolInput.PdfFile -> {
                val tempInput = getTempPdfFromInput(input)
                val tempOutput = fileStore.createTempFile("sig_", ".pdf")
                val document = PDDocument.load(tempInput)

                try {
                    val page = document.getPage(pageIndex.coerceIn(0, document.numberOfPages - 1))
                    val sigFile = fileStore.getSignatureFile(signatureId)
                    val sigBmp = BitmapFactory.decodeFile(sigFile.absolutePath)
                    val pdImage = LosslessFactory.createFromImage(document, sigBmp)
                    sigBmp.recycle()

                    val sigAspect = sigBmp.height.toFloat() / sigBmp.width
                    val normH = normW * sigAspect

                    val coords = PdfCoordinates.mapToPdfSpace(
                        normX = normX,
                        normY = normY,
                        normW = normW,
                        normH = normH,
                        pageWidth = page.mediaBox.width,
                        pageHeight = page.mediaBox.height,
                        rotation = page.rotation
                    )

                    val stream = PDPageContentStream(
                        document,
                        page,
                        PDPageContentStream.AppendMode.APPEND,
                        true,
                        true
                    )
                    stream.drawImage(pdImage, coords.x, coords.y, coords.width, coords.height)
                    stream.close()

                    document.save(tempOutput)
                } finally {
                    document.close()
                    tempInput.delete()
                }
                tempOutput
            }
        }
    }

    suspend fun reorderExternalPdf(input: ToolInput.PdfFile, newPageOrder: List<Int>): File = withContext(Dispatchers.IO) {
        val tempInput = getTempPdfFromInput(input)
        val tempOutput = fileStore.createTempFile("reordered_", ".pdf")
        val document = PDDocument.load(tempInput)
        val newDoc = PDDocument()

        try {
            for (idx in newPageOrder) {
                if (idx in 0 until document.numberOfPages) {
                    newDoc.addPage(document.getPage(idx))
                }
            }
            newDoc.save(tempOutput)
        } finally {
            document.close()
            newDoc.close()
            tempInput.delete()
        }

        tempOutput
    }

    suspend fun pdfToJpg(input: ToolInput): Int = withContext(Dispatchers.IO) {
        when (input) {
            is ToolInput.AppDoc -> {
                val doc = database.documentDao().getById(input.docId) ?: return@withContext 0
                val pages = database.pageDao().getByDocumentId(input.docId)
                com.syedmsidsm.abdulsscanner.domain.files.MediaStoreExporter.exportJpgsToPictures(
                    context,
                    doc,
                    pages,
                    fileStore
                )
            }
            is ToolInput.PdfFile -> {
                val tempPdf = getTempPdfFromInput(input)
                val pfd = ParcelFileDescriptor.open(tempPdf, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                val baseName = input.displayName.removeSuffix(".pdf")
                var savedCount = 0

                try {
                    for (i in 0 until renderer.pageCount) {
                        val page = renderer.openPage(i)
                        // 200 DPI scale: 72 DPI base -> scale = 200f / 72f = ~2.77
                        val scale = 200f / 72f
                        val w = (page.width * scale).toInt()
                        val h = (page.height * scale).toInt()
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        page.close()

                        saveJpegToPictures(bmp, baseName, i + 1)
                        bmp.recycle()
                        savedCount++
                    }
                } finally {
                    renderer.close()
                    pfd.close()
                    tempPdf.delete()
                }
                savedCount
            }
        }
    }

    suspend fun importPdf(uri: Uri, password: String? = null): String = withContext(Dispatchers.IO) {
        val tempPdf = fileStore.createTempFile("import_", ".pdf")
        context.contentResolver.openInputStream(uri)?.use { ins ->
            tempPdf.outputStream().use { outs -> ins.copyTo(outs) }
        }

        val bitmaps = mutableListOf<Bitmap>()
        try {
            if (password != null) {
                // If encrypted, load through PdfBox and save unencrypted first for PdfRenderer
                val doc = PDDocument.load(tempPdf, password)
                doc.isAllSecurityToBeRemoved = true
                val unencrypted = fileStore.createTempFile("unencrypted_", ".pdf")
                doc.save(unencrypted)
                doc.close()
                tempPdf.delete()
                unencrypted.copyTo(tempPdf, overwrite = true)
                unencrypted.delete()
            }

            val pfd = ParcelFileDescriptor.open(tempPdf, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val scale = 200f / 72f

            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val w = ((page.width * scale).toInt()).coerceAtMost(4000)
                val h = ((page.height * scale).toInt()).coerceAtMost(4000)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                page.close()
                bitmaps.add(bmp)
            }
            renderer.close()
            pfd.close()
        } finally {
            tempPdf.delete()
        }

        saveScanUseCase.saveBitmapScan(
            bitmaps = bitmaps,
            kind = DocumentKind.IMPORT_PDF,
            pageSize = "AUTO"
        )
    }

    private fun saveJpegToPictures(bitmap: Bitmap, folderName: String, pageNumber: Int) {
        val safeFolder = MirrorStore.sanitizeFileName(folderName)
        val fileName = "$safeFolder - $pageNumber.jpg"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Abdul's Scanner/$safeFolder")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            }
        } else {
            @Suppress("DEPRECATION")
            val picsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val dir = File(picsDir, "Abdul's Scanner/$safeFolder").apply { mkdirs() }
            val file = File(dir, fileName)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
        }
    }

    private suspend fun getTempPdfFromInput(input: ToolInput): File = withContext(Dispatchers.IO) {
        val temp = fileStore.createTempFile("input_", ".pdf")
        when (input) {
            is ToolInput.AppDoc -> {
                val exportPdf = fileStore.getExportPdfFile(input.docId)
                if (exportPdf.exists()) {
                    exportPdf.copyTo(temp, overwrite = true)
                } else {
                    val doc = database.documentDao().getById(input.docId)!!
                    val pages = database.pageDao().getByDocumentId(input.docId)
                    val generated = pdfBuilder.buildPdf(doc, pages)
                    generated.copyTo(temp, overwrite = true)
                }
            }
            is ToolInput.PdfFile -> {
                context.contentResolver.openInputStream(input.uri)?.use { ins ->
                    temp.outputStream().use { outs -> ins.copyTo(outs) }
                }
            }
        }
        temp
    }

    private fun parsePageRanges(rangesString: String, totalPages: Int): List<List<Int>> {
        if (rangesString.trim().equals("all", ignoreCase = true)) {
            return (0 until totalPages).map { listOf(it) }
        }

        val result = mutableListOf<List<Int>>()
        val parts = rangesString.split(",")
        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.contains("-")) {
                val bounds = trimmed.split("-")
                val start = (bounds.getOrNull(0)?.toIntOrNull() ?: 1) - 1
                val end = (bounds.getOrNull(1)?.toIntOrNull() ?: totalPages) - 1
                val range = (start.coerceAtLeast(0)..end.coerceAtMost(totalPages - 1)).toList()
                if (range.isNotEmpty()) result.add(range)
            } else {
                val page = (trimmed.toIntOrNull() ?: continue) - 1
                if (page in 0 until totalPages) {
                    result.add(listOf(page))
                }
            }
        }
        return if (result.isEmpty()) listOf((0 until totalPages).toList()) else result
    }
}
