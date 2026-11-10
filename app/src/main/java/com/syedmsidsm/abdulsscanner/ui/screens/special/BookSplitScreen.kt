package com.syedmsidsm.abdulsscanner.ui.screens.special

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syedmsidsm.abdulsscanner.R
import com.syedmsidsm.abdulsscanner.data.db.entity.DocumentKind
import com.syedmsidsm.abdulsscanner.data.files.FileStore
import com.syedmsidsm.abdulsscanner.data.settings.SettingsRepository
import com.syedmsidsm.abdulsscanner.domain.imaging.BitmapIo
import com.syedmsidsm.abdulsscanner.domain.scan.SaveScanUseCase
import com.syedmsidsm.abdulsscanner.domain.scan.special.BookSplit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookSplitScreen(
    docId: String? = null,
    pageId: String? = null,
    spreadUris: List<Uri> = emptyList(),
    saveScanUseCase: SaveScanUseCase,
    fileStore: FileStore,
    settingsRepository: SettingsRepository,
    onNavigateBack: () -> Unit,
    onNavigateToDocument: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var readingOrder by remember { mutableStateOf(BookSplit.ReadingOrder.LTR) }
    var currentSpreadIndex by remember { mutableIntStateOf(0) }
    var currentSpreadBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var splitFraction by remember { mutableFloatStateOf(0.50f) }
    var applyToAllSpreads by remember { mutableStateOf(false) }
    var globalSplitFraction by remember { mutableFloatStateOf(0.50f) }

    val splitResults = remember { mutableListOf<Pair<Bitmap, Bitmap>>() }
    var isProcessing by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val savedDir = settingsRepository.bookDirection.first()
        readingOrder = if (savedDir == "RTL") BookSplit.ReadingOrder.RTL else BookSplit.ReadingOrder.LTR

        withContext(Dispatchers.IO) {
            if (docId != null && pageId != null) {
                // Single page split from existing document
                val srcFile = fileStore.getSrcFile(docId, pageId)
                if (srcFile.exists()) {
                    currentSpreadBitmap = BitmapIo.decodeUpright(srcFile, maxLongSide = 3000)
                }
            } else if (spreadUris.isNotEmpty()) {
                // Batch spread scan
                currentSpreadBitmap = BitmapIo.decodeFromUri(context, spreadUris[0], maxLongSide = 3000)
            }
            withContext(Dispatchers.Main) {
                isProcessing = false
            }
        }
    }

    fun finishAllSpreads() {
        isProcessing = true
        scope.launch(Dispatchers.IO) {
            try {
                if (docId != null && pageId != null) {
                    val success = saveScanUseCase.splitExistingPage(
                        docId = docId,
                        pageId = pageId,
                        splitFraction = splitFraction,
                        order = readingOrder
                    )
                    withContext(Dispatchers.Main) {
                        isProcessing = false
                        if (success) {
                            Toast.makeText(context, "Spread split into 2 pages", Toast.LENGTH_SHORT).show()
                            onNavigateBack()
                        } else {
                            Toast.makeText(context, "Split failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    // Collect all split pages in sequence
                    val allPages = mutableListOf<Bitmap>()
                    for (pair in splitResults) {
                        allPages.add(pair.first)
                        allPages.add(pair.second)
                    }

                    val createdDocId = saveScanUseCase.saveBitmapScan(
                        bitmaps = allPages,
                        kind = DocumentKind.BOOK,
                        pageSize = "AUTO"
                    )

                    withContext(Dispatchers.Main) {
                        isProcessing = false
                        onNavigateToDocument(createdDocId)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isProcessing = false
                    Toast.makeText(context, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun proceedNextSpread() {
        val bmp = currentSpreadBitmap ?: return
        val frac = if (applyToAllSpreads) globalSplitFraction else splitFraction
        val (pageA, pageB) = BookSplit.splitBitmap(bmp, frac, readingOrder)
        splitResults.add(Pair(pageA, pageB))

        if (currentSpreadIndex < spreadUris.size - 1) {
            currentSpreadIndex++
            isProcessing = true
            scope.launch(Dispatchers.IO) {
                val nextBmp = BitmapIo.decodeFromUri(context, spreadUris[currentSpreadIndex], maxLongSide = 3000)
                withContext(Dispatchers.Main) {
                    currentSpreadBitmap = nextBmp
                    if (!applyToAllSpreads) {
                        splitFraction = 0.50f
                    }
                    isProcessing = false
                }
            }
        } else {
            finishAllSpreads()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.book_split), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // RTL / LTR toggle
                    FilterChip(
                        selected = readingOrder == BookSplit.ReadingOrder.RTL,
                        onClick = {
                            val next = if (readingOrder == BookSplit.ReadingOrder.LTR) {
                                BookSplit.ReadingOrder.RTL
                            } else {
                                BookSplit.ReadingOrder.LTR
                            }
                            readingOrder = next
                            scope.launch {
                                settingsRepository.setBookDirection(next.name)
                            }
                        },
                        label = {
                            Text(if (readingOrder == BookSplit.ReadingOrder.RTL) "RTL (Arabic)" else "LTR (English)")
                        },
                        leadingIcon = {
                            Icon(Icons.Outlined.SwapHoriz, contentDescription = null)
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val bmp = currentSpreadBitmap

            if (bmp != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Interactive Canvas with Image & Draggable Split Line
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        val containerW = maxWidth.value
                        val containerH = maxHeight.value
                        val imgW = bmp.width.toFloat()
                        val imgH = bmp.height.toFloat()

                        val scale = minOf(containerW / imgW, containerH / imgH)
                        val drawW = imgW * scale
                        val drawH = imgH * scale

                        Box(
                            modifier = Modifier
                                .width(drawW.dp)
                                .height(drawH.dp)
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawImage(
                                    image = bmp.asImageBitmap(),
                                    dstSize = IntSize(size.width.toInt(), size.height.toInt())
                                )
                            }

                            // Overlay with draggable line and A / B labels
                            Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectDragGestures { change, _ ->
                                            change.consume()
                                            val newFrac = (change.position.x / size.width).coerceIn(
                                                BookSplit.MIN_SPLIT_FRACTION,
                                                BookSplit.MAX_SPLIT_FRACTION
                                            )
                                            splitFraction = newFrac
                                            if (applyToAllSpreads) {
                                                globalSplitFraction = newFrac
                                            }
                                        }
                                    }
                            ) {
                                val splitX = size.width * splitFraction
                                val trimPx = size.width * BookSplit.GUTTER_TRIM_RATIO

                                // Gutter trim shaded areas
                                drawRect(
                                    color = Color(0x55000000),
                                    topLeft = Offset(splitX - trimPx, 0f),
                                    size = androidx.compose.ui.geometry.Size(trimPx * 2, size.height)
                                )

                                // Dashed green split line
                                drawLine(
                                    color = Color(0xFF00E676),
                                    start = Offset(splitX, 0f),
                                    end = Offset(splitX, size.height),
                                    strokeWidth = 3.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 12f), 0f)
                                )

                                // Circular drag handle at the center
                                drawCircle(
                                    color = Color(0xFF00E676),
                                    radius = 16.dp.toPx(),
                                    center = Offset(splitX, size.height / 2)
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = 8.dp.toPx(),
                                    center = Offset(splitX, size.height / 2)
                                )
                            }

                            // Translucent A and B labels
                            val isLtr = readingOrder == BookSplit.ReadingOrder.LTR
                            Row(modifier = Modifier.fillMaxSize()) {
                                Box(
                                    modifier = Modifier
                                        .weight(splitFraction)
                                        .fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (isLtr) "A\n(Page 1)" else "B\n(Page 2)",
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White.copy(alpha = 0.6f),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f - splitFraction)
                                        .fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (isLtr) "B\n(Page 2)" else "A\n(Page 1)",
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White.copy(alpha = 0.6f),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (spreadUris.size > 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = applyToAllSpreads,
                                onCheckedChange = {
                                    applyToAllSpreads = it
                                    if (it) globalSplitFraction = splitFraction
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Apply this line to all spreads", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (spreadUris.size > 1) {
                            Text(
                                text = "Spread ${currentSpreadIndex + 1} of ${spreadUris.size}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Text("Drag green line to adjust spine gutter", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Button(
                            onClick = {
                                if (docId != null && pageId != null) {
                                    finishAllSpreads()
                                } else {
                                    proceedNextSpread()
                                }
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            val isLast = currentSpreadIndex == spreadUris.size - 1 || (docId != null)
                            Icon(Icons.Outlined.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isLast) "Done" else "Next")
                        }
                    }
                }
            }

            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
