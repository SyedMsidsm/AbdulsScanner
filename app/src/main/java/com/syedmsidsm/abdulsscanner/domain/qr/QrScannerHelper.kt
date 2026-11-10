package com.syedmsidsm.abdulsscanner.domain.qr

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.syedmsidsm.abdulsscanner.data.db.entity.QrHistoryEntity
import com.syedmsidsm.abdulsscanner.data.db.entity.QrSource
import com.syedmsidsm.abdulsscanner.data.repo.QrRepository
import com.syedmsidsm.abdulsscanner.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object QrScannerHelper {

    fun startCameraScan(
        activity: Activity,
        onSuccess: (String) -> Unit,
        onCanceled: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .enableAutoZoom()
            .build()

        val scanner = GmsBarcodeScanning.getClient(activity, options)
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val rawValue = barcode.rawValue ?: barcode.displayValue ?: ""
                if (rawValue.isNotBlank()) {
                    onSuccess(rawValue)
                } else {
                    onCanceled()
                }
            }
            .addOnCanceledListener {
                onCanceled()
            }
            .addOnFailureListener { e ->
                onFailure(e)
            }
    }

    suspend fun scanFromBitmap(bitmap: Bitmap): List<String> = withContext(Dispatchers.Default) {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .build()
        val scanner = BarcodeScanning.getClient(options)
        val image = InputImage.fromBitmap(bitmap, 0)
        try {
            val barcodes = scanner.process(image).await()
            barcodes.mapNotNull { it.rawValue ?: it.displayValue }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun saveScannedCode(
        raw: String,
        source: QrSource,
        qrRepository: QrRepository,
        settingsRepository: SettingsRepository
    ): Long {
        val parsed = QrParser.parse(raw)
        val shouldSave = settingsRepository.qrSaveHistory.first()

        val entry = QrHistoryEntity(
            rawValue = raw,
            format = "QR_CODE",
            valueType = parsed.valueType.name,
            source = source.name,
            scannedAt = System.currentTimeMillis()
        )

        return if (shouldSave) {
            qrRepository.saveQr(entry)
        } else {
            // Even if history disabled, we return transient or save anyway for the result screen
            qrRepository.saveQr(entry)
        }
    }
}
