package com.syedmsidsm.abdulsscanner

import android.app.Application
import com.syedmsidsm.abdulsscanner.di.AppContainer
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.launch

class AbdulsScannerApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        PDFBoxResourceLoader.init(this)
        try {
            org.opencv.android.OpenCVLoader.initLocal()
        } catch (e: Throwable) {
            android.util.Log.e("AbdulsScannerApp", "OpenCV init failed", e)
        }

        // Clear cache/share/ on app start (Section 11.6)
        container.fileStore.clearShareDir()

        // Schedule daily trash purge (Section 12.6)
        com.syedmsidsm.abdulsscanner.domain.safety.TrashPurgeWorker.schedule(this)

        // Perform one-time migration to Abul's Scanner if needed (docs/RENAME-TO-ABULS-SCANNER.md)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            container.mirrorStore.migrateToAbulScanner(
                documentDao = container.database.documentDao(),
                fileStore = container.fileStore,
                settingsRepository = container.settingsRepository
            )
        }
    }
}
