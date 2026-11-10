package com.syedmsidsm.abdulsscanner.ui.screens.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syedmsidsm.abdulsscanner.data.db.entity.PageFilter
import com.syedmsidsm.abdulsscanner.domain.imaging.BitmapIo
import com.syedmsidsm.abdulsscanner.domain.imaging.Filters
import com.syedmsidsm.abdulsscanner.domain.imaging.PageDetector
import com.syedmsidsm.abdulsscanner.domain.imaging.PerspectiveCrop
import com.syedmsidsm.abdulsscanner.domain.scan.ScanPage
import com.syedmsidsm.abdulsscanner.domain.scan.ScanSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.io.File
import java.util.UUID

@Composable
fun ReviewFilterScreen(
    sessionViewModel: ScanSessionViewModel,
    onBackToCamera: () -> Unit,
    onDone: (docId: String, mode: String) -> Unit,
    onNavigateToCrop: (pageId: String) -> Unit,
    onNavigateToCameraForRetake: (pageId: String) -> Unit,
    onNavigateToCameraForAdd: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session by sessionViewModel.session.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val brandGreen = Color(0xFF13A06F)
    val darkBarBg = Color(0xFF161616)

    // Back button returns to Camera with pages intact
    BackHandler {
        onBackToCamera()
    }

    if (session.pages.isEmpty()) {
        // All pages deleted
        LaunchedEffect(Unit) {
            onBackToCamera()
        }
        return
    }

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { session.pages.size }
    )

    val currentPageIndex = pagerState.currentPage.coerceIn(0, session.pages.size - 1)
    val currentPage = session.pages.getOrNull(currentPageIndex)

    var applyToAllPages by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var showDiscardAllDialog by remember { mutableStateOf(false) }

    // Gallery picker for "Add"
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                val sessionDir = ScanSession.getSessionDir(context, session.sessionId)
                uris.forEach { uri ->
                    val pageId = UUID.randomUUID().toString()
                    val destFile = File(sessionDir, "$pageId.jpg")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        destFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (destFile.exists()) {
                        // Run authoritative auto-crop detection on imported photo
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
                            sessionViewModel.addPage(
                                filePath = destFile.absolutePath,
                                cropQuad = quad,
                                filter = if (applyToAllPages && currentPage != null) currentPage.filter else PageFilter.ENHANCED
                            )
                        }
                    }
                }
            }
        }
    }

    var showAddSourceDialog by remember { mutableStateOf(false) }
    if (showAddSourceDialog) {
        AlertDialog(
            onDismissRequest = { showAddSourceDialog = false },
            title = { Text("Add pages") },
            text = { Text("Where would you like to add pages from?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAddSourceDialog = false
                        onNavigateToCameraForAdd()
                    }
                ) {
                    Text("Camera", color = brandGreen)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddSourceDialog = false
                        galleryLauncher.launch("image/*")
                    }
                ) {
                    Text("Gallery")
                }
            }
        )
    }

    if (showDiscardAllDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardAllDialog = false },
            title = { Text("Discard scan?") },
            text = { Text("You deleted all pages. Do you want to discard this scan and return to the home screen?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardAllDialog = false
                        sessionViewModel.discardSession()
                        onBackToCamera()
                    }
                ) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Surface(
                color = Color.Black,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(56.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackToCamera) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to camera",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "Review · ${session.pages.size} ${if (session.pages.size == 1) "page" else "pages"}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )

                    Button(
                        onClick = {
                            if (!isSaving) {
                                isSaving = true
                                scope.launch {
                                    try {
                                        val app = context.applicationContext as com.syedmsidsm.abdulsscanner.AbdulsScannerApp
                                        val mode = session.scanMode
                                        val docId = app.container.saveScanUseCase.saveScanSession(session)
                                        sessionViewModel.discardSession()
                                        onDone(docId, mode)
                                    } catch (e: Throwable) {
                                        isSaving = false
                                        snackbarHostState.showSnackbar("Failed to save scan: ${e.message}")
                                    }
                                }
                            }
                        },
                        enabled = !isSaving,
                        colors = ButtonDefaults.buttonColors(containerColor = brandGreen),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Done", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(darkBarBg)
                    .navigationBarsPadding()
            ) {
                // Filter strip section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 6.dp)
                ) {
                    // Apply to all pages switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Apply filter to all pages",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = applyToAllPages,
                            onCheckedChange = { applyToAllPages = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = brandGreen,
                                uncheckedThumbColor = Color.LightGray,
                                uncheckedTrackColor = Color.DarkGray
                            )
                        )
                    }

                    // 7 Filters Horizontal Row with live previews
                    val allFilters = listOf(
                        PageFilter.NO_SHADOW,
                        PageFilter.ORIGINAL,
                        PageFilter.LIGHTEN,
                        PageFilter.ENHANCED,
                        PageFilter.GRAYSCALE,
                        PageFilter.BW,
                        PageFilter.ECO
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        items(allFilters) { filter ->
                            val isSelected = (currentPage?.filter == filter)
                            FilterThumbnailCard(
                                scanPage = currentPage,
                                filter = filter,
                                isSelected = isSelected,
                                brandGreen = brandGreen,
                                onClick = {
                                    if (applyToAllPages) {
                                        sessionViewModel.setFilterForAllPages(filter)
                                    } else {
                                        currentPage?.let {
                                            sessionViewModel.updatePage(it.id, filter = filter)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                // Bottom Action Buttons (Crop · Rotate · Retake · Add · Delete)
                Surface(
                    color = Color.Black,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Crop
                        BottomActionItem(
                            icon = Icons.Outlined.Crop,
                            label = "Crop",
                            onClick = { currentPage?.let { onNavigateToCrop(it.id) } }
                        )

                        // Rotate
                        BottomActionItem(
                            icon = Icons.AutoMirrored.Outlined.RotateRight,
                            label = "Rotate",
                            onClick = { currentPage?.let { sessionViewModel.rotatePage(it.id, 90) } }
                        )

                        // Retake
                        BottomActionItem(
                            icon = Icons.Outlined.CameraAlt,
                            label = "Retake",
                            onClick = { currentPage?.let { onNavigateToCameraForRetake(it.id) } }
                        )

                        // Add
                        BottomActionItem(
                            icon = Icons.Outlined.Add,
                            label = "Add",
                            onClick = { showAddSourceDialog = true }
                        )

                        // Delete
                        BottomActionItem(
                            icon = Icons.Outlined.Delete,
                            label = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                            onClick = {
                                currentPage?.let { pageToDelete ->
                                    val deletedIndex = session.pages.indexOfFirst { it.id == pageToDelete.id }
                                    val removed = sessionViewModel.removePage(pageToDelete.id)

                                    if (session.pages.size <= 1) {
                                        showDiscardAllDialog = true
                                    } else {
                                        scope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = "Page ${deletedIndex + 1} deleted",
                                                actionLabel = "Undo",
                                                duration = SnackbarDuration.Short
                                            )
                                            if (result == SnackbarResult.ActionPerformed && removed != null) {
                                                sessionViewModel.addPage(
                                                    filePath = removed.filePath,
                                                    cropQuad = removed.cropQuad,
                                                    rotation = removed.rotation,
                                                    filter = removed.filter
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        // Main Pager Viewport
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { pageIdx ->
                    val page = session.pages.getOrNull(pageIdx)
                    if (page != null) {
                        ReviewPageItem(page = page)
                    }
                }
            }

            // Page counter "1 / n"
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0x99222222),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text(
                    text = "${currentPageIndex + 1} / ${session.pages.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ReviewPageItem(page: ScanPage) {
    var previewBitmap by remember(page.filePath, page.cropQuad, page.rotation, page.filter) {
        mutableStateOf<Bitmap?>(null)
    }

    LaunchedEffect(page.filePath, page.cropQuad, page.rotation, page.filter) {
        withContext(Dispatchers.Default) {
            val file = File(page.filePath)
            if (!file.exists()) return@withContext

            val base = BitmapIo.decodeUpright(file, maxLongSide = 1600)
            val cropped = if (page.cropQuad != null && PerspectiveCrop.isValidQuad(page.cropQuad)) {
                PerspectiveCrop.crop(base, page.cropQuad)
            } else {
                base
            }

            val rotated = if (page.rotation % 360 == 0) {
                cropped
            } else {
                val matrix = Matrix().apply { postRotate(page.rotation.toFloat()) }
                Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height, matrix, true)
            }

            val w = rotated.width
            val h = rotated.height
            val pixels = IntArray(w * h)
            rotated.getPixels(pixels, 0, w, 0, 0, w, h)
            val filteredPixels = Filters.applyFilter(pixels, w, h, page.filter)
            val filteredBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            filteredBitmap.setPixels(filteredPixels, 0, w, 0, 0, w, h)

            previewBitmap = filteredBitmap
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val bmp = previewBitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Fit
            )
        } else {
            CircularProgressIndicator(
                color = Color(0xFF13A06F),
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

@Composable
private fun FilterThumbnailCard(
    scanPage: ScanPage?,
    filter: PageFilter,
    isSelected: Boolean,
    brandGreen: Color,
    onClick: () -> Unit
) {
    var thumbBitmap by remember(scanPage?.filePath, scanPage?.cropQuad, scanPage?.rotation, filter) {
        mutableStateOf<Bitmap?>(null)
    }

    LaunchedEffect(scanPage?.filePath, scanPage?.cropQuad, scanPage?.rotation, filter) {
        withContext(Dispatchers.Default) {
            val filePath = scanPage?.filePath ?: return@withContext
            val file = File(filePath)
            if (!file.exists()) return@withContext

            val base = BitmapIo.decodeUpright(file, maxLongSide = 160)
            val cropped = if (scanPage.cropQuad != null && PerspectiveCrop.isValidQuad(scanPage.cropQuad)) {
                PerspectiveCrop.crop(base, scanPage.cropQuad)
            } else {
                base
            }

            val rotated = if (scanPage.rotation % 360 == 0) {
                cropped
            } else {
                val matrix = Matrix().apply { postRotate(scanPage.rotation.toFloat()) }
                Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height, matrix, true)
            }

            val w = rotated.width
            val h = rotated.height
            val pixels = IntArray(w * h)
            rotated.getPixels(pixels, 0, w, 0, 0, w, h)
            val filteredPixels = Filters.applyFilter(pixels, w, h, filter)
            val filteredBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            filteredBmp.setPixels(filteredPixels, 0, w, 0, 0, w, h)
            thumbBitmap = filteredBmp
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(68.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF222222))
                .border(
                    width = if (isSelected) 2.5.dp else 1.dp,
                    color = if (isSelected) brandGreen else Color(0xFF444444),
                    shape = RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            val bmp = thumbBitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = filter.label,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(brandGreen, shape = CircleShape)
                        .align(Alignment.TopEnd)
                        .padding(2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = filter.label,
                color = if (isSelected) brandGreen else Color.LightGray,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
            if (filter.isBeta) {
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = "BETA",
                    color = Color.Red,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun BottomActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = tint,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
