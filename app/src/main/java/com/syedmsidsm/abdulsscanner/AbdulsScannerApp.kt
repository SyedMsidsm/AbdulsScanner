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
    }
}
