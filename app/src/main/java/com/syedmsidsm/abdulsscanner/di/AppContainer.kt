package com.syedmsidsm.abdulsscanner.di

import android.content.Context
import com.syedmsidsm.abdulsscanner.data.db.AppDatabase
import com.syedmsidsm.abdulsscanner.data.files.FileStore
import com.syedmsidsm.abdulsscanner.data.files.MirrorStore
import com.syedmsidsm.abdulsscanner.data.repo.DocumentRepository
import com.syedmsidsm.abdulsscanner.data.repo.QrRepository
import com.syedmsidsm.abdulsscanner.data.repo.SearchRepository
import com.syedmsidsm.abdulsscanner.data.repo.SignatureRepository
import com.syedmsidsm.abdulsscanner.data.repo.TagRepository
import com.syedmsidsm.abdulsscanner.data.settings.SettingsRepository
import com.syedmsidsm.abdulsscanner.domain.imaging.PageRenderer
import com.syedmsidsm.abdulsscanner.domain.ocr.MlKitLatinEngine
import com.syedmsidsm.abdulsscanner.domain.ocr.OcrService
import com.syedmsidsm.abdulsscanner.domain.ocr.PaddleEngine
import com.syedmsidsm.abdulsscanner.domain.ocr.TesseractEngine
import com.syedmsidsm.abdulsscanner.domain.pdf.PdfBuilder
import com.syedmsidsm.abdulsscanner.domain.scan.SaveScanUseCase

class AppContainer(val context: Context) {

    val database: AppDatabase by lazy {
        AppDatabase.buildDatabase(context)
    }

    val fileStore: FileStore by lazy {
        FileStore(context)
    }

    val mirrorStore: MirrorStore by lazy {
        MirrorStore(context)
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(context)
    }

    val pageRenderer: PageRenderer by lazy {
        PageRenderer(fileStore)
    }

    val pdfBuilder: PdfBuilder by lazy {
        PdfBuilder(fileStore)
    }

    val saveScanUseCase: SaveScanUseCase by lazy {
        SaveScanUseCase(
            context = context,
            database = database,
            fileStore = fileStore,
            mirrorStore = mirrorStore,
            pageRenderer = pageRenderer,
            pdfBuilder = pdfBuilder,
            settingsRepository = settingsRepository
        )
    }

    val documentRepository: DocumentRepository by lazy {
        DocumentRepository(
            documentDao = database.documentDao(),
            pageDao = database.pageDao(),
            fileStore = fileStore,
            mirrorStore = mirrorStore,
            pdfBuilder = pdfBuilder,
            settingsRepository = settingsRepository
        )
    }

    val tagRepository: TagRepository by lazy {
        TagRepository(database.tagDao())
    }

    val searchRepository: SearchRepository by lazy {
        SearchRepository(database.searchDao())
    }

    val qrRepository: QrRepository by lazy {
        QrRepository(database.qrDao())
    }

    val signatureRepository: SignatureRepository by lazy {
        SignatureRepository(database.signatureDao(), fileStore)
    }

    val mlKitLatinEngine: MlKitLatinEngine by lazy {
        MlKitLatinEngine()
    }

    val tesseractEngine: TesseractEngine by lazy {
        TesseractEngine(context, fileStore)
    }

    val paddleEngine: PaddleEngine by lazy {
        PaddleEngine(context, fileStore, tesseractEngine)
    }

    val ocrService: OcrService by lazy {
        OcrService(
            mlKitLatinEngine = mlKitLatinEngine,
            tesseractEngine = tesseractEngine,
            paddleEngine = paddleEngine,
            settingsRepository = settingsRepository
        )
    }
}
