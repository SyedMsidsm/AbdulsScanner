package com.syedmsidsm.abdulsscanner.domain.office

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient

class HtmlPrinter(private val context: Context) {

    private var activeWebView: WebView? = null

    fun printHtml(
        html: String,
        jobName: String,
        isLandscape: Boolean = false,
        onStarted: () -> Unit,
        onError: (String) -> Unit
    ) {
        val webView = WebView(context)
        activeWebView = webView

        val mediaSize = if (isLandscape) {
            PrintAttributes.MediaSize.ISO_A4.asLandscape()
        } else {
            PrintAttributes.MediaSize.ISO_A4
        }

        val attributes = PrintAttributes.Builder()
            .setMediaSize(mediaSize)
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                try {
                    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                    if (printManager != null && view != null) {
                        printManager.print(
                            jobName,
                            view.createPrintDocumentAdapter(jobName),
                            attributes
                        )
                        onStarted()
                    } else {
                        onError("Print service is not available on this device")
                    }
                } catch (e: Exception) {
                    onError("Failed to initiate print: ${e.message}")
                }
            }
        }

        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    fun release() {
        activeWebView?.destroy()
        activeWebView = null
    }
}
