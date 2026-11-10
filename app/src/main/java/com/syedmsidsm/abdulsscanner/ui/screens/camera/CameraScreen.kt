package com.syedmsidsm.abdulsscanner.ui.screens.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.media.MediaActionSound
import android.util.Size
import android.view.Surface
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.BubbleChart
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.FlashAuto
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.GridOff
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Hd
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.syedmsidsm.abdulsscanner.data.db.entity.DocumentKind
import com.syedmsidsm.abdulsscanner.data.db.entity.PageFilter
import com.syedmsidsm.abdulsscanner.data.settings.BookDirection
import com.syedmsidsm.abdulsscanner.di.AppContainer
import com.syedmsidsm.abdulsscanner.domain.imaging.BitmapIo
import com.syedmsidsm.abdulsscanner.domain.imaging.DetectionResult
import com.syedmsidsm.abdulsscanner.domain.imaging.DetectionStatus
import com.syedmsidsm.abdulsscanner.domain.imaging.DocumentDetector
import com.syedmsidsm.abdulsscanner.domain.imaging.Filters
import com.syedmsidsm.abdulsscanner.domain.imaging.PageDetector
import com.syedmsidsm.abdulsscanner.domain.imaging.PerspectiveCrop
import com.syedmsidsm.abdulsscanner.domain.imaging.Point2D
import com.syedmsidsm.abdulsscanner.domain.imaging.Quad
import com.syedmsidsm.abdulsscanner.domain.scan.CameraCaptureItem
import com.syedmsidsm.abdulsscanner.domain.scan.CaptureGuidance
import com.syedmsidsm.abdulsscanner.domain.scan.GuidanceHint
import com.syedmsidsm.abdulsscanner.domain.scan.GuidanceState
import com.syedmsidsm.abdulsscanner.domain.scan.ScanSession
import com.syedmsidsm.abdulsscanner.domain.scan.special.IdCardLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

enum class CameraScanMode(val title: String) {
    BOOK("Book"),
    TO_TEXT("To Text"),
    DOCS("Docs"),
    ID_CARD("ID Card"),
    QR_CODE("QR Code")
}

enum class CameraPopup {
    FLASH,
    PHOTO_SIZE,
    FILTER,
    SETTINGS
}

data class CropScanState(
    val file: File,
    val initialQuad: Quad?,
    val autoCropSuccess: Boolean = true,
    val pageNumber: Int
)

@Composable
fun CameraScreen(
    container: AppContainer,
    initialMode: CameraScanMode = CameraScanMode.DOCS,
    targetDocId: String? = null,
    retakePageId: String? = null,
    sessionViewModel: ScanSessionViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDocument: (String) -> Unit,
    onNavigateToReview: () -> Unit,
    onNavigateToBookSplit: (docId: String, pageId: String) -> Unit,
    onNavigateToOcr: (docId: String) -> Unit,
    onNavigateToQrResult: (qrId: Long, raw: String) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Permission state
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    if (!hasCameraPermission) {
        CameraPermissionRationale(
            onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onCancel = onNavigateBack
        )
        return
    }

    // Camera Mode
    var currentMode by remember { mutableStateOf(initialMode) }

    // Settings States
    var cameraFlash by remember { mutableStateOf("AUTO") }
    var cameraPhotoSize by remember { mutableStateOf<String?>(null) }
    var cameraGrid by remember { mutableStateOf(false) }
    var autoShutter by remember { mutableStateOf(true) }
    var autoCrop by remember { mutableStateOf(true) }
    var cropAfterEachScan by remember { mutableStateOf(true) }
    var muteShutter by remember { mutableStateOf(false) }
    var defaultFilter by remember { mutableStateOf(PageFilter.ENHANCED) }
    var bookDirection by remember { mutableStateOf(BookDirection.LTR) }

    // Read saved settings
    LaunchedEffect(Unit) {
        cameraFlash = container.settingsRepository.cameraFlash.first()
        cameraPhotoSize = container.settingsRepository.cameraPhotoSize.first()
        cameraGrid = container.settingsRepository.cameraGrid.first()
        autoShutter = container.settingsRepository.autoShutter.first()
        autoCrop = container.settingsRepository.autoCrop.first()
        cropAfterEachScan = container.settingsRepository.cropAfterEachScan.first()
        muteShutter = container.settingsRepository.muteShutter.first()
        val filterName = container.settingsRepository.defaultFilter.first()
        defaultFilter = PageFilter.fromString(filterName)
        val dir = container.settingsRepository.bookDirection.first()
        bookDirection = try { BookDirection.valueOf(dir) } catch (_: Exception) { BookDirection.LTR }
    }

    // Sound effect
    val sound = remember { MediaActionSound() }
    DisposableEffect(Unit) {
        onDispose {
            try {
                sound.release()
            } catch (_: Exception) {}
        }
    }

    // Active popup (only one at a time)
    var activePopup by remember { mutableStateOf<CameraPopup?>(null) }

    // Intercept back key to close popup first
    BackHandler(enabled = activePopup != null) {
        activePopup = null
    }

    // CameraX references
    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var availablePhotoSizes by remember { mutableStateOf<List<Size>>(emptyList()) }

    // Live Guidance & Overlays
    val guidance = remember { CaptureGuidance() }
    val guidanceState = remember { GuidanceState() }
    var liveHint by remember { mutableStateOf(GuidanceHint.SEARCHING) }
    var liveQuad by remember { mutableStateOf<Quad?>(null) }
    var isCapturing by remember { mutableStateOf(false) }

    // Session and Crop states
    val session by sessionViewModel.session.collectAsState()
    var activeCropScan by remember { mutableStateOf<CropScanState?>(null) }
    var lastCapturedThumbnail by remember { mutableStateOf<Bitmap?>(null) }
    var showContinuePill by remember { mutableStateOf(false) }
    var showExitCameraDialog by remember { mutableStateOf(false) }

    LaunchedEffect(showContinuePill) {
        if (showContinuePill) {
            delay(3000)
            showContinuePill = false
        }
    }

    BackHandler(enabled = activeCropScan == null) {
        if (session.pages.isEmpty()) {
            onNavigateBack()
        } else {
            showExitCameraDialog = true
        }
    }

    if (showExitCameraDialog) {
        AlertDialog(
            onDismissRequest = { showExitCameraDialog = false },
            title = { Text("Save ${session.pages.size} ${if (session.pages.size == 1) "page" else "pages"}?") },
            text = { Text("You have scanned pages. Do you want to review and save them, or discard them?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitCameraDialog = false
                        onNavigateToReview()
                    }
                ) {
                    Text("Save", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            showExitCameraDialog = false
                            sessionViewModel.discardSession()
                            onNavigateBack()
                        }
                    ) {
                        Text("Discard", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { showExitCameraDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    // ID Card state
    var idCardFrontFile by remember { mutableStateOf<File?>(null) }

    // QR state
    var qrHandled by remember { mutableStateOf(false) }

    // Filter preview thumbnails
    var filterPreviewBitmaps by remember { mutableStateOf<Map<PageFilter, Bitmap>>(emptyMap()) }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner = remember { BarcodeScanning.getClient() }

    // Query photo sizes once camera is bound
    LaunchedEffect(camera) {
        val cam = camera ?: return@LaunchedEffect
        try {
            val c2Info = Camera2CameraInfo.from(cam.cameraInfo)
            val map = c2Info.getCameraCharacteristic(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val sizes = map?.getOutputSizes(ImageFormat.JPEG) ?: emptyArray()
            val list = sizes.filter { s ->
                val ratio = s.width.toFloat() / s.height.toFloat()
                abs(ratio - (4f / 3f)) < 0.02f && s.width >= 1600 && s.height >= 1200
            }
                .sortedByDescending { it.width.toLong() * it.height.toLong() }
                .distinctBy { it.width to it.height }
                .take(8)
            availablePhotoSizes = list
        } catch (_: Exception) {}
    }

    // Handle Torch Mode
    val isTorchActive = cameraFlash == "TORCH"
    LaunchedEffect(isTorchActive, camera) {
        try {
            camera?.cameraControl?.enableTorch(isTorchActive)
        } catch (_: Exception) {}
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                camera?.cameraControl?.enableTorch(false)
            } catch (_: Exception) {}
            analysisExecutor.shutdown()
            barcodeScanner.close()
        }
    }

    // Reset guidance on mode change
    LaunchedEffect(currentMode) {
        sessionViewModel.setScanMode(currentMode.name)
        liveHint = GuidanceHint.SEARCHING
        liveQuad = null
        guidanceState.hint = GuidanceHint.SEARCHING
        guidanceState.quad = null
        guidanceState.steadySinceMs = null
        guidanceState.prevQuad = null
        guidanceState.lostFrames = 0
        idCardFrontFile = null
        qrHandled = false
    }

    // Generate filter previews when filter popup opens
    LaunchedEffect(activePopup) {
        if (activePopup == CameraPopup.FILTER) {
            val pv = previewView
            val frame = pv?.bitmap
            if (frame != null) {
                withContext(Dispatchers.Default) {
                    val small = BitmapIo.downscaleBitmap(frame, maxLongSide = 160)
                    val w = small.width
                    val h = small.height
                    val pixels = IntArray(w * h)
                    small.getPixels(pixels, 0, w, 0, 0, w, h)

                    val map = mutableMapOf<PageFilter, Bitmap>()
                    for (f in PageFilter.entries) {
                        val filtered = Filters.applyFilter(pixels, w, h, f)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.setPixels(filtered, 0, w, 0, 0, w, h)
                        map[f] = bmp
                    }
                    if (small != frame) small.recycle()
                    frame.recycle()
                    withContext(Dispatchers.Main) {
                        filterPreviewBitmaps = map
                    }
                }
            }
        }
    }

    // Photo capture action
    fun takePhoto(autoQuad: Quad? = null) {
        val capture = imageCapture ?: return
        if (isCapturing || activePopup != null || activeCropScan != null) return
        isCapturing = true

        val sess = sessionViewModel.session.value
        val sessionDir = ScanSession.getSessionDir(context, sess.sessionId)
        val photoFile = File(sessionDir, "${UUID.randomUUID()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        // Shutter sound
        if (!muteShutter) {
            try {
                sound.play(MediaActionSound.SHUTTER_CLICK)
            } catch (_: Exception) {}
        }

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            when (currentMode) {
                                CameraScanMode.DOCS, CameraScanMode.TO_TEXT -> {
                                    // Authoritative auto-crop on saved photo (1:1 with reference_detect.py)
                                    val bmp = BitmapIo.decodeUpright(photoFile, maxLongSide = 1000)
                                    val rgbaMat = Mat()
                                    Utils.bitmapToMat(bmp, rgbaMat)
                                    val bgrMat = Mat()
                                    Imgproc.cvtColor(rgbaMat, bgrMat, Imgproc.COLOR_RGBA2BGR)
                                    val corners = if (autoCrop) PageDetector.detectPage(bgrMat) else null
                                    val autoCropSuccess = (corners != null)
                                    val detectedQuad = if (corners != null) {
                                        PageDetector.pointsToNormalizedQuad(corners, bmp.width.toFloat(), bmp.height.toFloat())
                                    } else null
                                    rgbaMat.release()
                                    bgrMat.release()
                                    bmp.recycle()

                                    if (retakePageId != null) {
                                        sessionViewModel.replacePagePhoto(retakePageId, photoFile.absolutePath, detectedQuad)
                                        withContext(Dispatchers.Main) {
                                            isCapturing = false
                                            onNavigateToReview()
                                        }
                                        return@launch
                                    }

                                    if (cropAfterEachScan) {
                                        // Open Crop-after-scan screen
                                        withContext(Dispatchers.Main) {
                                            isCapturing = false
                                            activeCropScan = CropScanState(
                                                file = photoFile,
                                                initialQuad = detectedQuad,
                                                autoCropSuccess = autoCropSuccess,
                                                pageNumber = sess.pages.size + 1
                                            )
                                        }
                                    } else {
                                        // Batch mode: add directly
                                        sessionViewModel.addPage(
                                            filePath = photoFile.absolutePath,
                                            cropQuad = detectedQuad,
                                            rotation = 0,
                                            filter = defaultFilter
                                        )
                                        val thumb = BitmapIo.decodeUpright(photoFile, maxLongSide = 160)
                                        withContext(Dispatchers.Main) {
                                            lastCapturedThumbnail = thumb
                                            isCapturing = false
                                            showContinuePill = true
                                        }
                                    }
                                }
                                CameraScanMode.BOOK -> {
                                    val bmp = BitmapIo.decodeUpright(photoFile, maxLongSide = 1000)
                                    val rgbaMat = Mat()
                                    Utils.bitmapToMat(bmp, rgbaMat)
                                    val bgrMat = Mat()
                                    Imgproc.cvtColor(rgbaMat, bgrMat, Imgproc.COLOR_RGBA2BGR)
                                    val corners = if (autoCrop) PageDetector.detectPage(bgrMat) else null
                                    val detectedQuad = if (corners != null) {
                                        PageDetector.pointsToNormalizedQuad(corners, bmp.width.toFloat(), bmp.height.toFloat())
                                    } else null
                                    rgbaMat.release()
                                    bgrMat.release()
                                    bmp.recycle()

                                    val item = CameraCaptureItem(rawImageFile = photoFile, cropQuad = detectedQuad)
                                    val docId = container.saveScanUseCase.saveCameraScans(
                                        captures = listOf(item),
                                        kind = DocumentKind.BOOK,
                                        targetDocId = targetDocId
                                    )
                                    val pages = container.database.pageDao().getByDocumentId(docId)
                                    val pageId = pages.firstOrNull()?.id.orEmpty()
                                    withContext(Dispatchers.Main) {
                                        isCapturing = false
                                        onNavigateToBookSplit(docId, pageId)
                                    }
                                }
                                CameraScanMode.ID_CARD -> {
                                    if (idCardFrontFile == null) {
                                        idCardFrontFile = photoFile
                                        withContext(Dispatchers.Main) {
                                            isCapturing = false
                                        }
                                    } else {
                                        val frontBmp = BitmapIo.decodeUpright(idCardFrontFile!!, maxLongSide = 2000)
                                        val backBmp = BitmapIo.decodeUpright(photoFile, maxLongSide = 2000)

                                        fun cropCard(bmp: Bitmap): Bitmap {
                                            val rgbaMat = Mat()
                                            Utils.bitmapToMat(bmp, rgbaMat)
                                            val bgrMat = Mat()
                                            Imgproc.cvtColor(rgbaMat, bgrMat, Imgproc.COLOR_RGBA2BGR)
                                            val corners = PageDetector.detectPage(bgrMat)
                                            val quad = if (corners != null) {
                                                PageDetector.pointsToNormalizedQuad(corners, bmp.width.toFloat(), bmp.height.toFloat())
                                            } else null
                                            rgbaMat.release()
                                            bgrMat.release()

                                            val finalQuad = if (quad != null && PerspectiveCrop.isValidQuad(quad)) {
                                                quad
                                            } else {
                                                val aspect = 1.585f
                                                val wFrac = 0.85f
                                                val hFrac = (wFrac * bmp.width / aspect) / bmp.height
                                                val left = (1f - wFrac) / 2f
                                                val right = left + wFrac
                                                val top = (1f - hFrac.coerceIn(0.1f, 0.9f)) / 2f
                                                val bottom = top + hFrac.coerceIn(0.1f, 0.9f)
                                                Quad(
                                                    p0 = Point2D(left, top),
                                                    p1 = Point2D(right, top),
                                                    p2 = Point2D(right, bottom),
                                                    p3 = Point2D(left, bottom)
                                                )
                                            }
                                            return PerspectiveCrop.crop(bmp, finalQuad) ?: bmp
                                        }

                                        val frontCropped = cropCard(frontBmp)
                                        val backCropped = cropCard(backBmp)
                                        val composed = IdCardLayout.composeIdCard(frontCropped, backCropped)
                                        if (frontCropped != frontBmp) frontCropped.recycle()
                                        if (backCropped != backBmp) backCropped.recycle()
                                        frontBmp.recycle()
                                        backBmp.recycle()
                                        idCardFrontFile?.delete()
                                        idCardFrontFile = null

                                        val docId = container.saveScanUseCase.saveBitmapScan(
                                            bitmaps = listOf(composed),
                                            kind = DocumentKind.ID_CARD,
                                            pageSize = "A4",
                                            autoTagIdCard = true
                                        )
                                        composed.recycle()
                                        withContext(Dispatchers.Main) {
                                            isCapturing = false
                                            onNavigateToDocument(docId)
                                        }
                                    }
                                }
                                CameraScanMode.QR_CODE -> {
                                    isCapturing = false
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                isCapturing = false
                            }
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    isCapturing = false
                }
            }
        )
    }

    // Gallery Import launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                isCapturing = true
                if (currentMode == CameraScanMode.DOCS || currentMode == CameraScanMode.TO_TEXT) {
                    val sess = sessionViewModel.session.value
                    val sessionDir = ScanSession.getSessionDir(context, sess.sessionId)
                    uris.forEach { uri ->
                        val pageId = UUID.randomUUID().toString()
                        val destFile = File(sessionDir, "$pageId.jpg")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            destFile.outputStream().use { output -> input.copyTo(output) }
                        }
                        if (destFile.exists()) {
                            val bmp = BitmapIo.decodeUpright(destFile, maxLongSide = 1000)
                            val rgbaMat = Mat()
                            Utils.bitmapToMat(bmp, rgbaMat)
                            val bgrMat = Mat()
                            Imgproc.cvtColor(rgbaMat, bgrMat, Imgproc.COLOR_RGBA2BGR)
                            val corners = PageDetector.detectPage(bgrMat)
                            val quad = if (corners != null) {
                                PageDetector.pointsToNormalizedQuad(corners, bmp.width.toFloat(), bmp.height.toFloat())
                            } else null
                            rgbaMat.release()
                            bgrMat.release()
                            bmp.recycle()

                            withContext(Dispatchers.Main) {
                                sessionViewModel.addPage(destFile.absolutePath, quad, rotation = 0, filter = defaultFilter)
                            }
                        }
                    }
                    withContext(Dispatchers.Main) {
                        isCapturing = false
                        onNavigateToReview()
                    }
                } else {
                    val docId = container.saveScanUseCase.saveScan(
                        imageUris = uris,
                        kind = if (currentMode == CameraScanMode.BOOK) DocumentKind.BOOK else DocumentKind.SCAN,
                        targetDocId = targetDocId
                    )
                    withContext(Dispatchers.Main) {
                        isCapturing = false
                        if (currentMode == CameraScanMode.TO_TEXT) {
                            onNavigateToOcr(docId)
                        } else {
                            onNavigateToDocument(docId)
                        }
                    }
                }
            }
        }
    }

    val analysisFrameIndex = remember { java.util.concurrent.atomic.AtomicInteger(0) }

    // Main Camera Viewfinder & Controls
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // CameraX Viewfinder
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    previewView = this

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()

                        val preview = Preview.Builder()
                            .setTargetRotation(Surface.ROTATION_0)
                            .build().also {
                                it.setSurfaceProvider(surfaceProvider)
                            }

                        // Build ResolutionSelector from cameraPhotoSize
                        val targetSize = cameraPhotoSize?.let { str ->
                            val parts = str.split("x")
                            if (parts.size == 2) {
                                try { Size(parts[0].toInt(), parts[1].toInt()) } catch (_: Exception) { null }
                            } else null
                        }

                        val resSelector = ResolutionSelector.Builder()
                            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                            .apply {
                                if (targetSize != null) {
                                    setResolutionStrategy(
                                        ResolutionStrategy(targetSize, ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
                                    )
                                }
                            }
                            .build()

                        val captureFlash = when (cameraFlash) {
                            "ON" -> ImageCapture.FLASH_MODE_ON
                            "OFF", "TORCH" -> ImageCapture.FLASH_MODE_OFF
                            else -> ImageCapture.FLASH_MODE_AUTO
                        }

                        val capture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .setResolutionSelector(resSelector)
                            .setFlashMode(captureFlash)
                            .setTargetRotation(Surface.ROTATION_0)
                            .build()
                        imageCapture = capture

                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                            .setTargetRotation(Surface.ROTATION_0)
                            .build()

                        analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                            try {
                                // Pause analysis when popup is open
                                if (activePopup != null || isCapturing || activeCropScan != null) return@setAnalyzer

                                if (currentMode == CameraScanMode.QR_CODE && !qrHandled) {
                                    val mediaImage = imageProxy.image
                                    if (mediaImage != null) {
                                        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                        barcodeScanner.process(inputImage)
                                            .addOnSuccessListener { barcodes ->
                                                val first = barcodes.firstOrNull()
                                                if (first != null && !qrHandled) {
                                                    qrHandled = true
                                                    val raw = first.rawValue.orEmpty()
                                                    scope.launch {
                                                        val savedId = com.syedmsidsm.abdulsscanner.domain.qr.QrScannerHelper.saveScannedCode(
                                                            raw = raw,
                                                            source = com.syedmsidsm.abdulsscanner.data.db.entity.QrSource.CAMERA,
                                                            qrRepository = container.qrRepository,
                                                            settingsRepository = container.settingsRepository
                                                        )
                                                        onNavigateToQrResult(savedId, raw)
                                                    }
                                                }
                                            }
                                    }
                                } else if (currentMode == CameraScanMode.DOCS || currentMode == CameraScanMode.TO_TEXT || currentMode == CameraScanMode.BOOK || currentMode == CameraScanMode.ID_CARD) {
                                    if (analysisFrameIndex.incrementAndGet() % 3 != 0) {
                                        return@setAnalyzer
                                    }
                                    val bmp = imageProxy.toBitmap()
                                    val scaled = BitmapIo.downscaleBitmap(bmp, maxLongSide = 640)
                                    val rgbaMat = Mat()
                                    Utils.bitmapToMat(scaled, rgbaMat)
                                    val bgrMat = Mat()
                                    Imgproc.cvtColor(rgbaMat, bgrMat, Imgproc.COLOR_RGBA2BGR)
                                    val corners = PageDetector.detectPage(bgrMat)
                                    val normQuad = if (corners != null) {
                                        PageDetector.pointsToNormalizedQuad(corners, scaled.width.toFloat(), scaled.height.toFloat())
                                    } else null
                                    val pixelQuad = if (corners != null) {
                                        Quad(
                                            p0 = Point2D(corners[0].x.toFloat(), corners[0].y.toFloat()),
                                            p1 = Point2D(corners[1].x.toFloat(), corners[1].y.toFloat()),
                                            p2 = Point2D(corners[2].x.toFloat(), corners[2].y.toFloat()),
                                            p3 = Point2D(corners[3].x.toFloat(), corners[3].y.toFloat())
                                        )
                                    } else null
                                    rgbaMat.release()
                                    bgrMat.release()
                                    if (scaled != bmp) scaled.recycle()
                                    bmp.recycle()

                                    val (updatedState, shouldCapture) = guidance.update(
                                        state = guidanceState,
                                        quad = pixelQuad,
                                        frameW = scaled.width.toFloat(),
                                        frameH = scaled.height.toFloat(),
                                        nowMs = System.currentTimeMillis(),
                                        autoShutterEnabled = autoShutter
                                    )

                                    scope.launch(Dispatchers.Main) {
                                        liveHint = updatedState.hint
                                        liveQuad = normQuad
                                        if (shouldCapture && !isCapturing && activePopup == null && activeCropScan == null) {
                                            takePhoto(normQuad)
                                        }
                                    }
                                }
                            } catch (_: Exception) {
                            } finally {
                                imageProxy.close()
                            }
                        }

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                        try {
                            cameraProvider.unbindAll()
                            camera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                capture,
                                analysis
                            )
                        } catch (e: Exception) {
                            android.util.Log.e("CameraScreen", "Camera bind failed", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                }
            }
        )

        // 3x3 Grid Overlay (1 dp lines, black at 55% opacity)
        if (cameraGrid) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val gridStroke = Stroke(width = 1.dp.toPx())
                val gridColor = Color(0x8C000000)

                drawLine(gridColor, Offset(w / 3f, 0f), Offset(w / 3f, h), strokeWidth = gridStroke.width)
                drawLine(gridColor, Offset(w * 2f / 3f, 0f), Offset(w * 2f / 3f, h), strokeWidth = gridStroke.width)
                drawLine(gridColor, Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth = gridStroke.width)
                drawLine(gridColor, Offset(0f, h * 2f / 3f), Offset(w, h * 2f / 3f), strokeWidth = gridStroke.width)
            }
        }

        // Live Mode Viewfinder Overlays
        when (currentMode) {
            CameraScanMode.DOCS -> {
                DocsModeOverlay(liveHint = liveHint, liveQuad = liveQuad)
            }
            CameraScanMode.BOOK -> {
                BookModeOverlay(bookDirection = bookDirection, liveHint = liveHint, liveQuad = liveQuad)
            }
            CameraScanMode.ID_CARD -> {
                IdCardModeOverlay(isBackSide = idCardFrontFile != null, liveHint = liveHint, liveQuad = liveQuad)
            }
            CameraScanMode.TO_TEXT -> {
                ToTextModeOverlay(liveHint = liveHint, liveQuad = liveQuad)
            }
            CameraScanMode.QR_CODE -> {
                QrModeOverlay(
                    torchEnabled = isTorchActive,
                    onToggleTorch = {
                        val next = if (isTorchActive) "OFF" else "TORCH"
                        cameraFlash = next
                        scope.launch { container.settingsRepository.setCameraFlash(next) }
                    }
                )
            }
        }

        // Torch On Chip (Top-Right of preview)
        if (isTorchActive) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF2A2A2A).copy(alpha = 0.85f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 96.dp, end = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.FlashlightOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Torch on",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Top Bar: Flash · Photo size (HD) · Filter · Grid · Settings
        Surface(
            color = Color.Black,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 36.dp, bottom = 8.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Flash
                IconButton(
                    onClick = {
                        activePopup = if (activePopup == CameraPopup.FLASH) null else CameraPopup.FLASH
                    }
                ) {
                    val icon = when (cameraFlash) {
                        "ON" -> Icons.Outlined.FlashOn
                        "OFF" -> Icons.Outlined.FlashOff
                        "TORCH" -> Icons.Outlined.FlashlightOn
                        else -> Icons.Outlined.FlashAuto
                    }
                    val isTinted = activePopup == CameraPopup.FLASH || isTorchActive
                    Icon(
                        icon,
                        contentDescription = "Flash",
                        modifier = Modifier.size(28.dp),
                        tint = if (isTinted) MaterialTheme.colorScheme.primary else Color.White
                    )
                }

                // 2. Photo size (HD) (hidden in QR)
                if (currentMode != CameraScanMode.QR_CODE) {
                    IconButton(
                        onClick = {
                            activePopup = if (activePopup == CameraPopup.PHOTO_SIZE) null else CameraPopup.PHOTO_SIZE
                        }
                    ) {
                        Icon(
                            Icons.Outlined.Hd,
                            contentDescription = "Photo Size",
                            modifier = Modifier.size(28.dp),
                            tint = if (activePopup == CameraPopup.PHOTO_SIZE) MaterialTheme.colorScheme.primary else Color.White
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(28.dp))
                }

                // 3. Filter (hidden in QR)
                if (currentMode != CameraScanMode.QR_CODE) {
                    IconButton(
                        onClick = {
                            activePopup = if (activePopup == CameraPopup.FILTER) null else CameraPopup.FILTER
                        }
                    ) {
                        Icon(
                            Icons.Outlined.BubbleChart,
                            contentDescription = "Filters",
                            modifier = Modifier.size(28.dp),
                            tint = if (activePopup == CameraPopup.FILTER) MaterialTheme.colorScheme.primary else Color.White
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(28.dp))
                }

                // 4. Grid Toggle
                IconButton(
                    onClick = {
                        val next = !cameraGrid
                        cameraGrid = next
                        scope.launch { container.settingsRepository.setCameraGrid(next) }
                    }
                ) {
                    val icon = if (cameraGrid) Icons.Outlined.GridOn else Icons.Outlined.GridOff
                    Icon(
                        icon,
                        contentDescription = "Grid",
                        modifier = Modifier.size(28.dp),
                        tint = if (cameraGrid) MaterialTheme.colorScheme.primary else Color.White
                    )
                }

                // 5. Settings
                IconButton(
                    onClick = {
                        activePopup = if (activePopup == CameraPopup.SETTINGS) null else CameraPopup.SETTINGS
                    }
                ) {
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = "Settings",
                        modifier = Modifier.size(28.dp),
                        tint = if (activePopup == CameraPopup.SETTINGS) MaterialTheme.colorScheme.primary else Color.White
                    )
                }
            }
        }

        // Tap-outside overlay to dismiss popups
        if (activePopup != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures {
                            activePopup = null
                        }
                    }
            )
        }

        // ---------------------------------------------------------------------
        // Popups
        // ---------------------------------------------------------------------

        // 1. Flash Menu Popup
        if (activePopup == CameraPopup.FLASH) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xF53A3A3A),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .padding(top = 90.dp, start = 16.dp)
                    .width(180.dp)
                    .align(Alignment.TopStart)
            ) {
                Column {
                    listOf(
                        "AUTO" to "Flash Auto",
                        "ON" to "Flash On",
                        "OFF" to "Flash Off",
                        "TORCH" to "Torch"
                    ).forEachIndexed { idx, (mode, label) ->
                        if (idx > 0) {
                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .clickable {
                                    cameraFlash = mode
                                    scope.launch { container.settingsRepository.setCameraFlash(mode) }
                                    activePopup = null
                                }
                                .padding(horizontal = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, color = Color.White, fontSize = 14.sp)
                            RadioButton(
                                selected = cameraFlash == mode,
                                onClick = {
                                    cameraFlash = mode
                                    scope.launch { container.settingsRepository.setCameraFlash(mode) }
                                    activePopup = null
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }
        }

        // 2. Photo Size Popup
        if (activePopup == CameraPopup.PHOTO_SIZE) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xF53A3A3A),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .padding(top = 90.dp, start = 48.dp, end = 48.dp)
                    .fillMaxWidth(0.75f)
                    .align(Alignment.TopCenter)
            ) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    // Header
                    Text(
                        text = "Photo size · 4:3 · pixels",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

                    if (availablePhotoSizes.isEmpty()) {
                        Text(
                            text = "Standard 4:3",
                            color = Color.White,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(14.dp)
                        )
                    } else {
                        availablePhotoSizes.forEachIndexed { idx, size ->
                            if (idx > 0) {
                                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)
                            }
                            val sizeStr = "${size.width}x${size.height}"
                            val mp = (size.width.toLong() * size.height.toLong() / 1_000_000.0).roundToInt()
                            val label = "${mp}M (${size.width}x${size.height})"
                            val isSelected = (cameraPhotoSize == sizeStr) || (cameraPhotoSize == null && idx == 0)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .clickable {
                                        cameraPhotoSize = sizeStr
                                        scope.launch { container.settingsRepository.setCameraPhotoSize(sizeStr) }
                                        activePopup = null
                                    }
                                    .padding(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(label, color = Color.White, fontSize = 14.sp)
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        cameraPhotoSize = sizeStr
                                        scope.launch { container.settingsRepository.setCameraPhotoSize(sizeStr) }
                                        activePopup = null
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Filter Panel Popup (4-column grid of 7 filters)
        if (activePopup == CameraPopup.FILTER) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xF52A2A2A),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .padding(top = 90.dp, start = 12.dp, end = 12.dp)
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Camera Filter",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    val filterList = listOf(
                        PageFilter.NO_SHADOW,
                        PageFilter.ORIGINAL,
                        PageFilter.LIGHTEN,
                        PageFilter.ENHANCED,
                        PageFilter.GRAYSCALE,
                        PageFilter.BW,
                        PageFilter.ECO
                    )

                    // 4-column layout (Row 1: 4 items, Row 2: 3 items)
                    for (row in 0..1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (col in 0..3) {
                                val index = row * 4 + col
                                if (index < filterList.size) {
                                    val f = filterList[index]
                                    val isSelected = (defaultFilter == f)
                                    val thumbBmp = filterPreviewBitmaps[f]

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                defaultFilter = f
                                                scope.launch { container.settingsRepository.setDefaultFilter(f.name) }
                                                activePopup = null
                                            }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(1f)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFF3A3A3A))
                                                .border(
                                                    width = if (isSelected) 2.dp else 1.dp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f),
                                                    shape = RoundedCornerShape(6.dp)
                                                )
                                        ) {
                                            if (thumbBmp != null) {
                                                Image(
                                                    bitmap = thumbBmp.asImageBitmap(),
                                                    contentDescription = f.label,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            }
                                            if (f == PageFilter.NO_SHADOW) {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .background(Color(0xFFE53935), RoundedCornerShape(bottomStart = 4.dp))
                                                        .padding(horizontal = 3.dp, vertical = 1.dp)
                                                ) {
                                                    Text("BETA", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = f.label,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Camera Settings Panel (Gear icon: full-width black panel sliding down)
        if (activePopup == CameraPopup.SETTINGS) {
            Surface(
                color = Color.Black,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    // Auto shutter
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Auto shutter", color = Color.White, fontSize = 15.sp)
                        Switch(
                            checked = autoShutter,
                            onCheckedChange = {
                                autoShutter = it
                                scope.launch { container.settingsRepository.setAutoShutter(it) }
                            }
                        )
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 8.dp))

                    // Auto crop images
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Auto crop images", color = Color.White, fontSize = 15.sp)
                        Switch(
                            checked = autoCrop,
                            onCheckedChange = {
                                autoCrop = it
                                scope.launch { container.settingsRepository.setAutoCrop(it) }
                            }
                        )
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 8.dp))

                    // Crop images after each scan
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Crop images after each scan", color = Color.White, fontSize = 15.sp)
                        Switch(
                            checked = cropAfterEachScan,
                            onCheckedChange = {
                                cropAfterEachScan = it
                                scope.launch { container.settingsRepository.setCropAfterEachScan(it) }
                            }
                        )
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 8.dp))

                    // Mute mode
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Mute mode", color = Color.White, fontSize = 15.sp)
                        Switch(
                            checked = muteShutter,
                            onCheckedChange = {
                                muteShutter = it
                                scope.launch { container.settingsRepository.setMuteShutter(it) }
                            }
                        )
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 8.dp))

                    // Hint at bottom
                    Text(
                        text = "Also in Settings → Scanning",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clickable {
                                activePopup = null
                                onNavigateToSettings()
                            }
                            .padding(vertical = 4.dp)
                    )
                }
            }
        }

        // Bottom Controls Container (Slider + Shutter + Cancel + Gallery / Continue)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(bottom = 32.dp, top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 3-second pill: "{n} pages · keep scanning or tap Continue" (Section 5)
            AnimatedVisibility(
                visible = showContinuePill && session.pages.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xCC2A2A2A),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Text(
                        text = "${session.pages.size} ${if (session.pages.size == 1) "page" else "pages"} · keep scanning or tap Continue",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }

            // Mode slider
            ModeSlider(
                selectedMode = currentMode,
                onModeSelected = { currentMode = it }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Shutter & actions row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cancel (✕)
                IconButton(
                    onClick = {
                        if (session.pages.isEmpty()) {
                            onNavigateBack()
                        } else {
                            showExitCameraDialog = true
                        }
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Shutter Button
                ShutterButton(
                    isCapturing = isCapturing,
                    onClick = { takePhoto(liveQuad) }
                )

                // Right action: Gallery Import (if 0 pages) OR Thumbnail + Continue (if >= 1 page)
                if (session.pages.isEmpty()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { galleryLauncher.launch("image/*") }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PhotoLibrary,
                            contentDescription = "Import",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "Import",
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onNavigateToReview() }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp, 58.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .border(2.dp, Color.White, RoundedCornerShape(6.dp))
                                .background(Color.DarkGray)
                        ) {
                            val thumb = lastCapturedThumbnail
                            if (thumb != null) {
                                Image(
                                    bitmap = thumb.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                val lastPage = session.pages.lastOrNull()
                                if (lastPage != null) {
                                    val bmp = remember(lastPage.filePath) {
                                        BitmapIo.decodeUpright(File(lastPage.filePath), maxLongSide = 160)
                                    }
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }

                            // Green count badge on thumbnail
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF13A06F),
                                modifier = Modifier
                                    .size(20.dp)
                                    .align(Alignment.TopEnd)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${session.pages.size}",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Continue",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Processing overlay
        if (isCapturing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        // Full-screen Crop overlay if activeCropScan is non-null (keeps CameraX bound underneath!)
        val cropScan = activeCropScan
        if (cropScan != null) {
            Box(modifier = Modifier.fillMaxSize()) {
                CropAfterScanView(
                    rawImageFile = cropScan.file,
                    initialQuad = cropScan.initialQuad,
                    autoCropSuccess = cropScan.autoCropSuccess,
                    pageNumber = cropScan.pageNumber,
                    totalPagesInBatch = session.pages.size + 1,
                    onRetake = {
                        cropScan.file.delete()
                        activeCropScan = null
                    },
                    onNextPage = { finalQuad, rot ->
                        sessionViewModel.addPage(cropScan.file.absolutePath, finalQuad, rot, filter = defaultFilter)
                        val thumb = BitmapIo.decodeUpright(cropScan.file, maxLongSide = 160)
                        lastCapturedThumbnail = thumb
                        activeCropScan = null
                        showContinuePill = true
                    },
                    onDone = { finalQuad, rot ->
                        sessionViewModel.addPage(cropScan.file.absolutePath, finalQuad, rot, filter = defaultFilter)
                        activeCropScan = null
                        onNavigateToReview()
                    }
                )
            }
        }
    }
}

private fun maxCornerDistance(q1: Quad, q2: Quad): Float {
    val d0 = hypot((q1.p0.x - q2.p0.x).toDouble(), (q1.p0.y - q2.p0.y).toDouble()).toFloat()
    val d1 = hypot((q1.p1.x - q2.p1.x).toDouble(), (q1.p1.y - q2.p1.y).toDouble()).toFloat()
    val d2 = hypot((q1.p2.x - q2.p2.x).toDouble(), (q1.p2.y - q2.p2.y).toDouble()).toFloat()
    val d3 = hypot((q1.p3.x - q2.p3.x).toDouble(), (q1.p3.y - q2.p3.y).toDouble()).toFloat()
    return max(max(d0, d1), max(d2, d3))
}

// -------------------------------------------------------------------------
// Mode Overlays
// -------------------------------------------------------------------------

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLiveQuad(
    liveQuad: Quad,
    color: Color,
    frameAspect: Float = 3f / 4f
) {
    val viewW = size.width
    val viewH = size.height
    val viewAspect = viewW / viewH
    val drawW: Float
    val drawH: Float
    val offsetX: Float
    val offsetY: Float
    if (viewAspect > frameAspect) {
        drawW = viewW
        drawH = viewW / frameAspect
        offsetX = 0f
        offsetY = (viewH - drawH) / 2f
    } else {
        drawH = viewH
        drawW = viewH * frameAspect
        offsetX = (viewW - drawW) / 2f
        offsetY = 0f
    }

    val p0 = Offset(offsetX + liveQuad.p0.x * drawW, offsetY + liveQuad.p0.y * drawH)
    val p1 = Offset(offsetX + liveQuad.p1.x * drawW, offsetY + liveQuad.p1.y * drawH)
    val p2 = Offset(offsetX + liveQuad.p2.x * drawW, offsetY + liveQuad.p2.y * drawH)
    val p3 = Offset(offsetX + liveQuad.p3.x * drawW, offsetY + liveQuad.p3.y * drawH)

    val path = Path().apply {
        moveTo(p0.x, p0.y)
        lineTo(p1.x, p1.y)
        lineTo(p2.x, p2.y)
        lineTo(p3.x, p3.y)
        close()
    }

    drawPath(path = path, color = color.copy(alpha = 0.18f))
    drawPath(path = path, color = color, style = Stroke(width = 3.5.dp.toPx()))
}

@Composable
fun DocsModeOverlay(liveHint: GuidanceHint, liveQuad: Quad?) {
    val brandGreen = MaterialTheme.colorScheme.primary

    Box(modifier = Modifier.fillMaxSize()) {
        if (liveQuad != null && liveHint != GuidanceHint.SEARCHING) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawLiveQuad(liveQuad, brandGreen)
            }
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.Black.copy(alpha = 0.7f),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 60.dp)
        ) {
            Text(
                text = liveHint.text,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
fun BookModeOverlay(bookDirection: BookDirection, liveHint: GuidanceHint, liveQuad: Quad?) {
    val brandGreen = MaterialTheme.colorScheme.primary

    Box(modifier = Modifier.fillMaxSize()) {
        if (liveQuad != null && liveHint != GuidanceHint.SEARCHING) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawLiveQuad(liveQuad, brandGreen)
            }
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.Black.copy(alpha = 0.7f),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 60.dp)
        ) {
            Text(
                text = liveHint.text,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val topY = size.height * 0.15f
            val bottomY = size.height * 0.75f
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 15f), 0f)

            drawLine(
                color = brandGreen,
                start = Offset(cx, topY),
                end = Offset(cx, bottomY),
                strokeWidth = 3.dp.toPx(),
                pathEffect = dashEffect
            )
        }

        val leftLabel = if (bookDirection == BookDirection.LTR) "A" else "B"
        val rightLabel = if (bookDirection == BookDirection.LTR) "B" else "A"

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = 48.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = leftLabel,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = rightLabel,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun IdCardModeOverlay(isBackSide: Boolean, liveHint: GuidanceHint, liveQuad: Quad?) {
    val brandGreen = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (liveQuad != null && liveHint != GuidanceHint.SEARCHING) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawLiveQuad(liveQuad, brandGreen)
            }
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.Black.copy(alpha = 0.7f),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 60.dp)
        ) {
            Text(
                text = liveHint.text,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.Black.copy(alpha = 0.7f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 100.dp)
        ) {
            Text(
                text = if (isBackSide) "Back page" else "Front page",
                color = brandGreen,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .aspectRatio(1.585f)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val bracketLen = 32.dp.toPx()
                val strokeW = 4.dp.toPx()
                val w = size.width
                val h = size.height

                drawLine(brandGreen, Offset(0f, 0f), Offset(bracketLen, 0f), strokeWidth = strokeW)
                drawLine(brandGreen, Offset(0f, 0f), Offset(0f, bracketLen), strokeWidth = strokeW)

                drawLine(brandGreen, Offset(w, 0f), Offset(w - bracketLen, 0f), strokeWidth = strokeW)
                drawLine(brandGreen, Offset(w, 0f), Offset(w, bracketLen), strokeWidth = strokeW)

                drawLine(brandGreen, Offset(0f, h), Offset(bracketLen, h), strokeWidth = strokeW)
                drawLine(brandGreen, Offset(0f, h), Offset(0f, h - bracketLen), strokeWidth = strokeW)

                drawLine(brandGreen, Offset(w, h), Offset(w - bracketLen, h), strokeWidth = strokeW)
                drawLine(brandGreen, Offset(w, h), Offset(w, h - bracketLen), strokeWidth = strokeW)
            }
        }
    }
}

@Composable
fun ToTextModeOverlay(liveHint: GuidanceHint, liveQuad: Quad?) {
    Box(modifier = Modifier.fillMaxSize()) {
        DocsModeOverlay(liveHint = liveHint, liveQuad = liveQuad)

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.Black.copy(alpha = 0.7f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 100.dp)
        ) {
            Text(
                text = "OCR in batch",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
fun QrModeOverlay(
    torchEnabled: Boolean,
    onToggleTorch: () -> Unit
) {
    val brandGreen = MaterialTheme.colorScheme.primary
    val infiniteTransition = rememberInfiniteTransition(label = "qr_scan_line")
    val scanProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scan_line_progress"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.size(260.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val bracketLen = 32.dp.toPx()
                val strokeW = 4.dp.toPx()
                val w = size.width
                val h = size.height

                drawLine(brandGreen, Offset(0f, 0f), Offset(bracketLen, 0f), strokeWidth = strokeW)
                drawLine(brandGreen, Offset(0f, 0f), Offset(0f, bracketLen), strokeWidth = strokeW)

                drawLine(brandGreen, Offset(w, 0f), Offset(w - bracketLen, 0f), strokeWidth = strokeW)
                drawLine(brandGreen, Offset(w, 0f), Offset(w, bracketLen), strokeWidth = strokeW)

                drawLine(brandGreen, Offset(0f, h), Offset(bracketLen, h), strokeWidth = strokeW)
                drawLine(brandGreen, Offset(0f, h), Offset(0f, h - bracketLen), strokeWidth = strokeW)

                drawLine(brandGreen, Offset(w, h), Offset(w - bracketLen, h), strokeWidth = strokeW)
                drawLine(brandGreen, Offset(w, h), Offset(w, h - bracketLen), strokeWidth = strokeW)

                val lineY = h * scanProgress
                drawLine(
                    color = brandGreen.copy(alpha = 0.85f),
                    start = Offset(8.dp.toPx(), lineY),
                    end = Offset(w - 8.dp.toPx(), lineY),
                    strokeWidth = 2.5.dp.toPx()
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.Black.copy(alpha = 0.7f),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 340.dp)
                .clickable { onToggleTorch() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (torchEnabled) Icons.Outlined.FlashOff else Icons.Outlined.FlashOn,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (torchEnabled) "Turn light off" else "Turn light on",
                    color = Color.White,
                    fontSize = 13.sp
                )
            }
        }
    }
}

// -------------------------------------------------------------------------
// Mode Slider Component
// -------------------------------------------------------------------------

@Composable
fun ModeSlider(
    selectedMode: CameraScanMode,
    onModeSelected: (CameraScanMode) -> Unit
) {
    val brandGreen = MaterialTheme.colorScheme.primary
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CameraScanMode.entries.forEach { mode ->
            val isSelected = mode == selectedMode
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable { onModeSelected(mode) }
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Text(
                    text = mode.title,
                    color = if (isSelected) brandGreen else Color.White.copy(alpha = 0.7f),
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 15.sp
                )
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .size(5.dp)
                            .background(brandGreen, CircleShape)
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------------------
// Shutter Button
// -------------------------------------------------------------------------

@Composable
fun ShutterButton(
    isCapturing: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(76.dp)
            .border(4.dp, Color.White, CircleShape)
            .clickable(enabled = !isCapturing) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .background(Color.White, CircleShape)
        )
    }
}

// -------------------------------------------------------------------------
// Permission Rationale Screen
// -------------------------------------------------------------------------

@Composable
fun CameraPermissionRationale(
    onRequestPermission: () -> Unit,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Cameraswitch,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Camera permission needed",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Abul's Scanner needs camera access to scan documents directly on your phone. All scanning is completely offline and private.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                Text("Allow Camera", color = Color.Black, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                Text("Cancel", color = Color.White)
            }
        }
    }
}
