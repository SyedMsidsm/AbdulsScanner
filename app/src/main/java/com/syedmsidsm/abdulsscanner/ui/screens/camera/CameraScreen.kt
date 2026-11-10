package com.syedmsidsm.abdulsscanner.ui.screens.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.FlashAuto
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.syedmsidsm.abdulsscanner.data.db.entity.DocumentKind
import com.syedmsidsm.abdulsscanner.data.settings.BookDirection
import com.syedmsidsm.abdulsscanner.data.settings.SettingsRepository
import com.syedmsidsm.abdulsscanner.di.AppContainer
import com.syedmsidsm.abdulsscanner.domain.imaging.BitmapIo
import com.syedmsidsm.abdulsscanner.domain.imaging.DetectionResult
import com.syedmsidsm.abdulsscanner.domain.imaging.DetectionStatus
import com.syedmsidsm.abdulsscanner.domain.imaging.DocumentDetector
import com.syedmsidsm.abdulsscanner.domain.imaging.Quad
import com.syedmsidsm.abdulsscanner.domain.qr.QrParser
import com.syedmsidsm.abdulsscanner.domain.scan.CameraCaptureItem
import com.syedmsidsm.abdulsscanner.domain.scan.special.IdCardLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

enum class CameraScanMode(val title: String) {
    BOOK("Book"),
    TO_TEXT("To Text"),
    DOCS("Docs"),
    ID_CARD("ID Card"),
    QR_CODE("QR Code")
}

@Composable
fun CameraScreen(
    container: AppContainer,
    initialMode: CameraScanMode = CameraScanMode.DOCS,
    targetDocId: String? = null,
    onNavigateBack: () -> Unit,
    onNavigateToDocument: (String) -> Unit,
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

    // Top Bar State
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_AUTO) }
    var hdMode by remember { mutableStateOf(false) }
    var showGrid by remember { mutableStateOf(false) }
    var torchEnabled by remember { mutableStateOf(false) }

    // Read initial settings
    LaunchedEffect(Unit) {
        hdMode = container.settingsRepository.hdMode.first()
    }

    // CameraX references
    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    // Detection & Overlays
    val detector = remember { DocumentDetector() }
    var liveDetection by remember { mutableStateOf<DetectionResult?>(null) }
    var isCapturing by remember { mutableStateOf(false) }

    // ID Card state
    var idCardFrontFile by remember { mutableStateOf<File?>(null) }

    // QR state
    var qrHandled by remember { mutableStateOf(false) }

    // Book direction
    var bookDirection by remember { mutableStateOf(BookDirection.LTR) }
    LaunchedEffect(Unit) {
        val dir = container.settingsRepository.bookDirection.first()
        bookDirection = try { BookDirection.valueOf(dir) } catch (_: Exception) { BookDirection.LTR }
    }

    // Reset detector when switching modes
    LaunchedEffect(currentMode) {
        detector.reset()
        liveDetection = null
        idCardFrontFile = null
        qrHandled = false
    }

    // Photo capture action
    fun takePhoto(autoQuad: Quad? = null) {
        val capture = imageCapture ?: return
        if (isCapturing) return
        isCapturing = true

        val photoFile = container.fileStore.createTempFile(prefix = "cam_raw_", suffix = ".jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            when (currentMode) {
                                CameraScanMode.DOCS -> {
                                    val quad = autoQuad ?: liveDetection?.smoothedQuad
                                    val item = CameraCaptureItem(rawImageFile = photoFile, cropQuad = quad)
                                    val docId = container.saveScanUseCase.saveCameraScans(
                                        captures = listOf(item),
                                        kind = DocumentKind.SCAN,
                                        targetDocId = targetDocId
                                    )
                                    withContext(Dispatchers.Main) {
                                        isCapturing = false
                                        onNavigateToDocument(docId)
                                    }
                                }
                                CameraScanMode.BOOK -> {
                                    val item = CameraCaptureItem(rawImageFile = photoFile, cropQuad = null)
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
                                        // Save front side and prompt for back
                                        idCardFrontFile = photoFile
                                        withContext(Dispatchers.Main) {
                                            isCapturing = false
                                        }
                                    } else {
                                        // Back side captured: compose both onto A4 canvas
                                        val frontBmp = BitmapIo.decodeUpright(idCardFrontFile!!, maxLongSide = 2000)
                                        val backBmp = BitmapIo.decodeUpright(photoFile, maxLongSide = 2000)
                                        val composed = IdCardLayout.composeIdCard(frontBmp, backBmp)
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
                                CameraScanMode.TO_TEXT -> {
                                    val quad = autoQuad ?: liveDetection?.smoothedQuad
                                    val item = CameraCaptureItem(rawImageFile = photoFile, cropQuad = quad)
                                    val docId = container.saveScanUseCase.saveCameraScans(
                                        captures = listOf(item),
                                        kind = DocumentKind.SCAN,
                                        targetDocId = targetDocId
                                    )
                                    withContext(Dispatchers.Main) {
                                        isCapturing = false
                                        onNavigateToOcr(docId)
                                    }
                                }
                                CameraScanMode.QR_CODE -> {
                                    // QR scanning is handled live by analyzer
                                    isCapturing = false
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                isCapturing = false
                            }
                        } finally {
                            if (currentMode != CameraScanMode.ID_CARD || idCardFrontFile != photoFile) {
                                photoFile.delete()
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

    // Auto-capture watcher in DOCS mode
    LaunchedEffect(liveDetection) {
        val result = liveDetection ?: return@LaunchedEffect
        if (currentMode == CameraScanMode.DOCS && !isCapturing) {
            if (result.status == DetectionStatus.AUTO_CAPTURE_TRIGGERED && result.smoothedQuad != null) {
                takePhoto(result.smoothedQuad)
            }
        }
    }

    // Gallery Import launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                isCapturing = true
                val docId = container.saveScanUseCase.saveScan(
                    imageUris = uris,
                    kind = if (currentMode == CameraScanMode.BOOK) DocumentKind.BOOK else DocumentKind.SCAN,
                    targetDocId = targetDocId
                )
                isCapturing = false
                if (currentMode == CameraScanMode.TO_TEXT) {
                    onNavigateToOcr(docId)
                } else {
                    onNavigateToDocument(docId)
                }
            }
        }
    }

    // Full screen camera layout
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

                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(surfaceProvider)
                        }

                        val capture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .setFlashMode(flashMode)
                            .build()
                        imageCapture = capture

                        val analysisExecutor = Executors.newSingleThreadExecutor()
                        val barcodeScanner = BarcodeScanning.getClient()

                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                            .build()

                        analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                            try {
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
                                } else if (currentMode == CameraScanMode.DOCS || currentMode == CameraScanMode.TO_TEXT) {
                                    val bmp = imageProxy.toBitmap()
                                    val scaled = BitmapIo.downscaleBitmap(bmp, maxLongSide = 640)
                                    val result = detector.processFrame(scaled)
                                    liveDetection = result
                                    if (scaled != bmp) scaled.recycle()
                                    bmp.recycle()
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

        // Viewfinder Grid Overlay (3x3)
        if (showGrid) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val gridStroke = Stroke(width = 1.dp.toPx())
                val gridColor = Color.White.copy(alpha = 0.35f)

                // 2 vertical lines
                drawLine(gridColor, Offset(w / 3f, 0f), Offset(w / 3f, h), strokeWidth = gridStroke.width)
                drawLine(gridColor, Offset(w * 2f / 3f, 0f), Offset(w * 2f / 3f, h), strokeWidth = gridStroke.width)

                // 2 horizontal lines
                drawLine(gridColor, Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth = gridStroke.width)
                drawLine(gridColor, Offset(0f, h * 2f / 3f), Offset(w, h * 2f / 3f), strokeWidth = gridStroke.width)
            }
        }

        // Live Mode Overlays
        when (currentMode) {
            CameraScanMode.DOCS -> {
                DocsModeOverlay(liveDetection = liveDetection)
            }
            CameraScanMode.BOOK -> {
                BookModeOverlay(bookDirection = bookDirection)
            }
            CameraScanMode.ID_CARD -> {
                IdCardModeOverlay(isBackSide = idCardFrontFile != null)
            }
            CameraScanMode.TO_TEXT -> {
                ToTextModeOverlay(liveDetection = liveDetection)
            }
            CameraScanMode.QR_CODE -> {
                QrModeOverlay(
                    torchEnabled = torchEnabled,
                    onToggleTorch = {
                        torchEnabled = !torchEnabled
                        camera?.cameraControl?.enableTorch(torchEnabled)
                    }
                )
            }
        }

        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Flash toggle
            IconButton(
                onClick = {
                    flashMode = when (flashMode) {
                        ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                        ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_OFF
                        else -> ImageCapture.FLASH_MODE_AUTO
                    }
                    imageCapture?.flashMode = flashMode
                }
            ) {
                val icon = when (flashMode) {
                    ImageCapture.FLASH_MODE_ON -> Icons.Outlined.FlashOn
                    ImageCapture.FLASH_MODE_OFF -> Icons.Outlined.FlashOff
                    else -> Icons.Outlined.FlashAuto
                }
                Icon(icon, contentDescription = "Flash", tint = Color.White)
            }

            // HD Toggle
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (hdMode) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.5f),
                border = Stroke(width = 1.dp.value).let { androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)) },
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        hdMode = !hdMode
                        scope.launch { container.settingsRepository.setHdMode(hdMode) }
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "HD",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = if (hdMode) Color.Black else Color.White
                )
            }

            // Grid Toggle
            IconButton(
                onClick = { showGrid = !showGrid }
            ) {
                Icon(
                    Icons.Outlined.GridOn,
                    contentDescription = "Grid",
                    tint = if (showGrid) MaterialTheme.colorScheme.primary else Color.White
                )
            }

            // Settings shortcut
            IconButton(onClick = onNavigateToSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = Color.White)
            }
        }

        // Bottom Controls Container
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(bottom = 32.dp, top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Mode slider
            ModeSlider(
                selectedMode = currentMode,
                onModeSelected = { currentMode = it }
            )

            Spacer(modifier = Modifier.height(24.dp))

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
                    onClick = onNavigateBack,
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
                    onClick = { takePhoto() }
                )

                // Gallery Import
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
    }
}

// -------------------------------------------------------------------------
// Mode Overlays
// -------------------------------------------------------------------------

@Composable
fun DocsModeOverlay(liveDetection: DetectionResult?) {
    val quad = liveDetection?.smoothedQuad
    val brandGreen = MaterialTheme.colorScheme.primary

    Box(modifier = Modifier.fillMaxSize()) {
        // Quad outline
        if (quad != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                val p0 = Offset(quad.p0.x * w, quad.p0.y * h)
                val p1 = Offset(quad.p1.x * w, quad.p1.y * h)
                val p2 = Offset(quad.p2.x * w, quad.p2.y * h)
                val p3 = Offset(quad.p3.x * w, quad.p3.y * h)

                val path = Path().apply {
                    moveTo(p0.x, p0.y)
                    lineTo(p1.x, p1.y)
                    lineTo(p2.x, p2.y)
                    lineTo(p3.x, p3.y)
                    close()
                }

                // Fill translucent green
                drawPath(path = path, color = brandGreen.copy(alpha = 0.18f))
                // Outline border
                drawPath(path = path, color = brandGreen, style = Stroke(width = 3.5.dp.toPx()))
            }
        }

        // Status pill
        val statusText = when (liveDetection?.status) {
            DetectionStatus.HOLD_STILL, DetectionStatus.AUTO_CAPTURE_TRIGGERED -> "Hold still... capturing"
            DetectionStatus.DETECTED -> "Document detected"
            else -> "Searching doc…"
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.Black.copy(alpha = 0.7f),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 120.dp)
        ) {
            Text(
                text = statusText,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
fun BookModeOverlay(bookDirection: BookDirection) {
    val brandGreen = MaterialTheme.colorScheme.primary

    Box(modifier = Modifier.fillMaxSize()) {
        // Horizontal hint at top
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.Black.copy(alpha = 0.7f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 100.dp)
        ) {
            Text(
                text = "Lay the book open and flat",
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // Center green dashed binding line
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

        // Translucent "A" and "B" labels
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
fun IdCardModeOverlay(isBackSide: Boolean) {
    val brandGreen = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Status pill
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

        // ID-1 card outline (ISO 7810 ID-1 ratio: 85.6 : 54 ~= 1.585)
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

                // Top-Left
                drawLine(brandGreen, Offset(0f, 0f), Offset(bracketLen, 0f), strokeWidth = strokeW)
                drawLine(brandGreen, Offset(0f, 0f), Offset(0f, bracketLen), strokeWidth = strokeW)

                // Top-Right
                drawLine(brandGreen, Offset(w, 0f), Offset(w - bracketLen, 0f), strokeWidth = strokeW)
                drawLine(brandGreen, Offset(w, 0f), Offset(w, bracketLen), strokeWidth = strokeW)

                // Bottom-Left
                drawLine(brandGreen, Offset(0f, h), Offset(bracketLen, h), strokeWidth = strokeW)
                drawLine(brandGreen, Offset(0f, h), Offset(0f, h - bracketLen), strokeWidth = strokeW)

                // Bottom-Right
                drawLine(brandGreen, Offset(w, h), Offset(w - bracketLen, h), strokeWidth = strokeW)
                drawLine(brandGreen, Offset(w, h), Offset(w, h - bracketLen), strokeWidth = strokeW)
            }
        }
    }
}

@Composable
fun ToTextModeOverlay(liveDetection: DetectionResult?) {
    Box(modifier = Modifier.fillMaxSize()) {
        DocsModeOverlay(liveDetection = liveDetection)

        // Pill at top
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
        // QR Target Box (260dp x 260dp)
        Box(modifier = Modifier.size(260.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val bracketLen = 32.dp.toPx()
                val strokeW = 4.dp.toPx()
                val w = size.width
                val h = size.height

                // Top-Left
                drawLine(brandGreen, Offset(0f, 0f), Offset(bracketLen, 0f), strokeWidth = strokeW)
                drawLine(brandGreen, Offset(0f, 0f), Offset(0f, bracketLen), strokeWidth = strokeW)

                // Top-Right
                drawLine(brandGreen, Offset(w, 0f), Offset(w - bracketLen, 0f), strokeWidth = strokeW)
                drawLine(brandGreen, Offset(w, 0f), Offset(w, bracketLen), strokeWidth = strokeW)

                // Bottom-Left
                drawLine(brandGreen, Offset(0f, h), Offset(bracketLen, h), strokeWidth = strokeW)
                drawLine(brandGreen, Offset(0f, h), Offset(0f, h - bracketLen), strokeWidth = strokeW)

                // Bottom-Right
                drawLine(brandGreen, Offset(w, h), Offset(w - bracketLen, h), strokeWidth = strokeW)
                drawLine(brandGreen, Offset(w, h), Offset(w, h - bracketLen), strokeWidth = strokeW)

                // Animated scan line
                val lineY = h * scanProgress
                drawLine(
                    color = brandGreen.copy(alpha = 0.85f),
                    start = Offset(8.dp.toPx(), lineY),
                    end = Offset(w - 8.dp.toPx(), lineY),
                    strokeWidth = 2.5.dp.toPx()
                )
            }
        }

        // Torch toggle button below target box
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
                text = "Abdul's Scanner needs camera access to scan documents directly on your phone. All scanning is completely offline and private.",
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
