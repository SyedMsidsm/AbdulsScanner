package com.syedmsidsm.abdulsscanner.ui.screens.pageeditor

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.Tune
import com.syedmsidsm.abdulsscanner.domain.imaging.OverlayCompositor
import com.syedmsidsm.abdulsscanner.domain.imaging.PageOverlay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.syedmsidsm.abdulsscanner.data.db.entity.PageEntity
import com.syedmsidsm.abdulsscanner.data.db.entity.PageFilter
import com.syedmsidsm.abdulsscanner.data.files.FileStore
import com.syedmsidsm.abdulsscanner.data.repo.DocumentRepository
import com.syedmsidsm.abdulsscanner.domain.imaging.BitmapIo
import com.syedmsidsm.abdulsscanner.domain.imaging.PageRenderer
import com.syedmsidsm.abdulsscanner.domain.imaging.Quad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageViewerScreen(
    docId: String,
    initialPageId: String,
    documentRepository: DocumentRepository,
    pageRenderer: PageRenderer,
    fileStore: FileStore,
    onNavigateBack: () -> Unit,
    onNavigateToSign: ((docId: String, pageId: String) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val pages by documentRepository.observePages(docId).collectAsState(initial = emptyList())
    val initialIndex = remember(pages) {
        val idx = pages.indexOfFirst { it.id == initialPageId }
        if (idx >= 0) idx else 0
    }
    val pagerState = rememberPagerState(initialPage = initialIndex, pageCount = { pages.size })

    val currentPage = pages.getOrNull(pagerState.currentPage)

    // Editing states
    var showAdjustPanel by remember { mutableStateOf(false) }
    var isCropMode by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showOverlaysDialog by remember { mutableStateOf(false) }
    var sourceBitmapForCrop by remember { mutableStateOf<Bitmap?>(null) }

    // Undo state
    var previousPageState by remember { mutableStateOf<PageEntity?>(null) }

    // Retake activity result launcher (ML Kit scanner pageLimit = 1)
    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val pageUri = scanResult?.pages?.firstOrNull()?.imageUri
            if (pageUri != null && currentPage != null) {
                scope.launch {
                    val tempFile = fileStore.createTempFile("retake_", ".jpg")
                    context.contentResolver.openInputStream(pageUri)?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    documentRepository.retakePage(docId, currentPage.id, tempFile, pageRenderer)
                    tempFile.delete()
                    snackbarHostState.showSnackbar("Page retaken")
                }
            }
        }
    }

    fun launchRetake() {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(false)
            .setPageLimit(1)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        val scanner = GmsDocumentScanning.getClient(options)
        val activity = context as? Activity ?: return
        scanner.getStartScanIntent(activity)
            .addOnSuccessListener { intentSender ->
                scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
    }

    fun applyPageChange(updated: PageEntity, undoMessage: String = "Edit applied") {
        val current = currentPage ?: return
        previousPageState = current
        scope.launch {
            documentRepository.updatePage(updated, pageRenderer)
            val snack = snackbarHostState.showSnackbar(
                message = undoMessage,
                actionLabel = "Undo"
            )
            if (snack == SnackbarResult.ActionPerformed) {
                previousPageState?.let { prev ->
                    documentRepository.updatePage(prev, pageRenderer)
                }
            }
        }
    }

    if (isCropMode && sourceBitmapForCrop != null && currentPage != null) {
        val initialQuad = remember(currentPage) {
            if (!currentPage.cropQuad.isNullOrBlank()) {
                try {
                    val list = Json.decodeFromString<List<Float>>(currentPage.cropQuad)
                    Quad.fromNormalizedList(list)
                } catch (_: Exception) {
                    Quad.fullQuad()
                }
            } else {
                Quad.fullQuad()
            }
        }

        CropView(
            sourceBitmap = sourceBitmapForCrop!!,
            initialQuad = initialQuad,
            onApply = { newQuad ->
                val quadJson = Json.encodeToString(newQuad.toNormalizedList())
                applyPageChange(currentPage.copy(cropQuad = quadJson), "Crop applied")
                isCropMode = false
                sourceBitmapForCrop?.recycle()
                sourceBitmapForCrop = null
            },
            onCancel = {
                isCropMode = false
                sourceBitmapForCrop?.recycle()
                sourceBitmapForCrop = null
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val currentNum = if (pages.isNotEmpty()) pagerState.currentPage + 1 else 0
                    Text(
                        text = "Page $currentNum of ${pages.size}",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showOverlaysDialog = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Layers,
                            contentDescription = "Signatures & watermarks",
                            tint = Color.White
                        )
                    }
                    if (onNavigateToSign != null && currentPage != null) {
                        IconButton(onClick = { onNavigateToSign(docId, currentPage.id) }) {
                            Icon(
                                imageVector = Icons.Outlined.Draw,
                                contentDescription = "Sign page",
                                tint = Color.White
                            )
                        }
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Delete page",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.85f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Black
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Main Zoomable Page Image
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { pageIndex ->
                    val page = pages.getOrNull(pageIndex)
                    if (page != null) {
                        val renderedFile = remember(page.renderedPath, page.filter, page.rotation, page.brightness, page.contrast, page.cropQuad) {
                            fileStore.getPageRenderedFile(docId, page.id)
                        }

                        var scale by remember { mutableFloatStateOf(1f) }
                        var offset by remember { mutableStateOf(Offset.Zero) }

                        val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
                            scale = (scale * zoomChange).coerceIn(1f, 5f)
                            if (scale > 1f) {
                                offset += offsetChange
                            } else {
                                offset = Offset.Zero
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black)
                                .transformable(state = transformState),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(renderedFile)
                                    .memoryCacheKey("${renderedFile.absolutePath}_${renderedFile.lastModified()}")
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Page ${pageIndex + 1}",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer(
                                        scaleX = scale,
                                        scaleY = scale,
                                        translationX = offset.x,
                                        translationY = offset.y
                                    )
                            )
                        }
                    }
                }
            }

            // Adjust Panel (Brightness & Contrast Sliders)
            if (showAdjustPanel && currentPage != null) {
                var brightness by remember(currentPage.brightness) { mutableFloatStateOf(currentPage.brightness.toFloat()) }
                var contrast by remember(currentPage.contrast) { mutableFloatStateOf(currentPage.contrast.toFloat()) }

                Surface(
                    color = Color.Black.copy(alpha = 0.9f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Brightness & Contrast",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White
                            )
                            TextButton(
                                onClick = {
                                    brightness = 0f
                                    contrast = 0f
                                    applyPageChange(
                                        currentPage.copy(brightness = 0, contrast = 0),
                                        "Adjustments reset"
                                    )
                                }
                            ) {
                                Text("Reset", color = MaterialTheme.colorScheme.primary)
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Brightness", color = Color.LightGray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(72.dp))
                            Slider(
                                value = brightness,
                                onValueChange = { brightness = it },
                                onValueChangeFinished = {
                                    applyPageChange(
                                        currentPage.copy(brightness = brightness.toInt()),
                                        "Brightness adjusted"
                                    )
                                },
                                valueRange = -100f..100f,
                                modifier = Modifier.weight(1f)
                            )
                            Text("${brightness.toInt()}", color = Color.White, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(36.dp))
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Contrast", color = Color.LightGray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(72.dp))
                            Slider(
                                value = contrast,
                                onValueChange = { contrast = it },
                                onValueChangeFinished = {
                                    applyPageChange(
                                        currentPage.copy(contrast = contrast.toInt()),
                                        "Contrast adjusted"
                                    )
                                },
                                valueRange = -100f..100f,
                                modifier = Modifier.weight(1f)
                            )
                            Text("${contrast.toInt()}", color = Color.White, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(36.dp))
                        }
                    }
                }
            }

            // Filter Strip (Section 10.3: Original, Enhanced, Grayscale, B&W)
            if (currentPage != null) {
                Surface(
                    color = Color.Black.copy(alpha = 0.85f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val filters = listOf(
                            PageFilter.ORIGINAL to "Original",
                            PageFilter.ENHANCED to "Enhanced",
                            PageFilter.GRAYSCALE to "Gray",
                            PageFilter.BW to "B&W"
                        )

                        val activeFilter = currentPage.filter

                        filters.forEach { (filterType, label) ->
                            val isSelected = activeFilter.equals(filterType.name, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        if (!isSelected) {
                                            applyPageChange(
                                                currentPage.copy(filter = filterType.name),
                                                "Filter changed to $label"
                                            )
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Actions Bar (Crop, Rotate, Adjust, Retake)
            if (currentPage != null) {
                Surface(
                    color = Color.Black,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Crop
                        BottomEditorAction(
                            icon = Icons.Outlined.Crop,
                            label = "Crop",
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    val orig = fileStore.getPageOrigFile(docId, currentPage.id)
                                    val baseFile = if (orig.exists()) orig else fileStore.getPageSrcFile(docId, currentPage.id)
                                    val bmp = BitmapIo.decodeUpright(baseFile, maxLongSide = 2000)
                                    withContext(Dispatchers.Main) {
                                        sourceBitmapForCrop = bmp
                                        isCropMode = true
                                    }
                                }
                            }
                        )

                        // Rotate (+90°)
                        BottomEditorAction(
                            icon = Icons.Outlined.RotateRight,
                            label = "Rotate",
                            onClick = {
                                val nextRot = (currentPage.rotation + 90) % 360
                                applyPageChange(currentPage.copy(rotation = nextRot), "Rotated +90°")
                            }
                        )

                        // Adjust
                        BottomEditorAction(
                            icon = Icons.Outlined.Tune,
                            label = "Adjust",
                            isSelected = showAdjustPanel,
                            onClick = { showAdjustPanel = !showAdjustPanel }
                        )

                        // Retake
                        BottomEditorAction(
                            icon = Icons.Outlined.CameraAlt,
                            label = "Retake",
                            onClick = { launchRetake() }
                        )

                        // Sign
                        if (onNavigateToSign != null) {
                            BottomEditorAction(
                                icon = Icons.Outlined.Draw,
                                label = "Sign",
                                onClick = { onNavigateToSign(docId, currentPage.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Delete Page Dialog
    if (showDeleteDialog && currentPage != null) {
        val isLastPage = pages.size <= 1
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(if (isLastPage) "Move document to recycle bin?" else "Delete page?") },
            text = {
                Text(
                    if (isLastPage) "This is the only page. Deleting it will move the entire document to the recycle bin."
                    else "Delete this page from the document?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        scope.launch {
                            val movedDoc = documentRepository.deletePage(docId, currentPage.id)
                            if (movedDoc || pages.size <= 1) {
                                onNavigateBack()
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Overlays Dialog (Signatures & Watermarks)
    if (showOverlaysDialog && currentPage != null) {
        val currentOverlays = OverlayCompositor.parseOverlays(currentPage.overlays)
        AlertDialog(
            onDismissRequest = { showOverlaysDialog = false },
            title = { Text("Signatures & Watermarks") },
            text = {
                if (currentOverlays.isEmpty()) {
                    Text("No signatures or watermarks applied to this page.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        currentOverlays.forEachIndexed { index, overlay ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val label = when (overlay) {
                                    is PageOverlay.Signature -> "Signature"
                                    is PageOverlay.Watermark -> "Watermark: ${overlay.text}"
                                }
                                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                TextButton(
                                    onClick = {
                                        val mutable = currentOverlays.toMutableList()
                                        mutable.removeAt(index)
                                        val json = OverlayCompositor.serializeOverlays(mutable)
                                        applyPageChange(
                                            currentPage.copy(overlays = json),
                                            "Removed $label"
                                        )
                                        showOverlaysDialog = false
                                    }
                                ) {
                                    Text("Remove", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showOverlaysDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun BottomEditorAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray
        )
    }
}
