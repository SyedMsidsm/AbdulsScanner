package com.syedmsidsm.abdulsscanner

import android.app.Application
import com.syedmsidsm.abdulsscanner.di.AppContainer
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class AbdulsScannerApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        PDFBoxResourceLoader.init(this)

        // Clear cache/share/ on app start (Section 11.6)
        container.fileStore.clearShareDir()

        // Schedule daily trash purge (Section 12.6)
        com.syedmsidsm.abdulsscanner.domain.safety.TrashPurgeWorker.schedule(this)
    }
}
