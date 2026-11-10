package com.syedmsidsm.abdulsscanner.ui.screens.tools

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.NavigateBefore
import androidx.compose.material.icons.outlined.NavigateNext
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.syedmsidsm.abdulsscanner.data.db.entity.PageEntity
import com.syedmsidsm.abdulsscanner.di.AppContainer
import com.syedmsidsm.abdulsscanner.domain.pdf.ToolInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolSignatureScreen(
    docIdArg: String? = null,
    pageIdArg: String? = null,
    container: AppContainer,
    onNavigateBack: () -> Unit,
    onNavigateToDoc: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showSourcePicker by remember { mutableStateOf(docIdArg == null) }
    var selectedInput by remember {
        mutableStateOf<ToolInput?>(if (!docIdArg.isNullOrBlank()) ToolInput.AppDoc(docIdArg) else null)
    }

    var appPages by remember { mutableStateOf<List<PageEntity>>(emptyList()) }
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var totalPages by remember { mutableIntStateOf(1) }
    var pdfPageBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var activeSignatureId by remember { mutableStateOf<String?>(null) }
    var showPad by remember { mutableStateOf(true) }

    // Normalised position: x, y, w (0..1)
    var normX by remember { mutableFloatStateOf(0.35f) }
    var normY by remember { mutableFloatStateOf(0.75f) }
    var normW by remember { mutableFloatStateOf(0.30f) } // default 30% page width, min 5%

    var isProcessing by remember { mutableStateOf(false) }
    var resultUri by remember { mutableStateOf<Uri?>(null) }
    var resultFileName by remember { mutableStateOf("") }

    // Load AppDoc pages or external PDF info
    LaunchedEffect(selectedInput, currentPageIndex) {
        val inp = selectedInput ?: return@LaunchedEffect
        when (inp) {
            is ToolInput.AppDoc -> {
                withContext(Dispatchers.IO) {
                    val pages = container.database.pageDao().getByDocumentId(inp.docId).sortedBy { it.position }
                    appPages = pages
                    totalPages = pages.size.coerceAtLeast(1)
                    if (!pageIdArg.isNullOrBlank()) {
                        val idx = pages.indexOfFirst { it.id == pageIdArg }
                        if (idx >= 0) currentPageIndex = idx
                    }
                }
            }
            is ToolInput.PdfFile -> {
                withContext(Dispatchers.IO) {
                    try {
                        val tempPdf = container.fileStore.createTempFile("sig_render_", ".pdf")
                        context.contentResolver.openInputStream(inp.uri)?.use { ins ->
                            tempPdf.outputStream().use { outs -> ins.copyTo(outs) }
                        }
                        val pfd = ParcelFileDescriptor.open(tempPdf, ParcelFileDescriptor.MODE_READ_ONLY)
                        val renderer = PdfRenderer(pfd)
                        totalPages = renderer.pageCount
                        val safeIdx = currentPageIndex.coerceIn(0, totalPages - 1)
                        val page = renderer.openPage(safeIdx)
                        val scale = 1000f / maxOf(page.width, page.height)
                        val w = (page.width * scale).toInt()
                        val h = (page.height * scale).toInt()
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        renderer.close()
                        pfd.close()
                        tempPdf.delete()
                        pdfPageBitmap = bmp
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sign Document", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (activeSignatureId != null) {
                        IconButton(onClick = { showPad = true }) {
                            Icon(Icons.Outlined.Draw, contentDescription = "Change Signature")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            if (selectedInput == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Button(onClick = { showSourcePicker = true }) {
                        Text("Choose Document or PDF to Sign")
                    }
                }
            } else if (showPad || activeSignatureId == null) {
                // Step 1: Signature pad to pick or draw
                SignaturePad(
                    container = container,
                    onSignatureSelected = { sigId ->
                        activeSignatureId = sigId
                        showPad = false
                    }
                )
            } else {
                // Step 2: Signature Placer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Page ${currentPageIndex + 1} of $totalPages",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row {
                        IconButton(
                            onClick = { if (currentPageIndex > 0) currentPageIndex-- },
                            enabled = currentPageIndex > 0
                        ) {
                            Icon(Icons.Outlined.NavigateBefore, contentDescription = "Previous Page")
                        }
                        IconButton(
                            onClick = { if (currentPageIndex < totalPages - 1) currentPageIndex++ },
                            enabled = currentPageIndex < totalPages - 1
                        ) {
                            Icon(Icons.Outlined.NavigateNext, contentDescription = "Next Page")
                        }
                    }
                }

                Text(
                    text = "Drag or pinch to reposition and resize the signature:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Page canvas with draggable signature box
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.DarkGray.copy(alpha = 0.1f))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val containerWidth = maxWidth
                    val containerHeight = maxHeight

                    // Display page preview
                    if (selectedInput is ToolInput.AppDoc) {
                        val appDocId = (selectedInput as ToolInput.AppDoc).docId
                        val curPage = appPages.getOrNull(currentPageIndex)
                        if (curPage != null) {
                            val previewFile = container.fileStore.getPageRenderedFile(appDocId, curPage.id)
                            AsyncImage(
                                model = previewFile,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    } else if (pdfPageBitmap != null) {
                        AsyncImage(
                            model = pdfPageBitmap,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }

                    // Draggable, pinch-resizable signature overlay
                    val sigFile = container.fileStore.getSignatureFile(activeSignatureId!!)
                    val sigWidth = (containerWidth * normW).coerceAtLeast(30.dp)
                    val posX = containerWidth * normX
                    val posY = containerHeight * normY

                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    posX.toPx().roundToInt(),
                                    posY.toPx().roundToInt()
                                )
                            }
                            .width(sigWidth)
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val newW = (normW * zoom).coerceIn(0.05f, 0.90f)
                                    normW = newW
                                    val deltaNormX = pan.x / size.width
                                    val deltaNormY = pan.y / size.height
                                    normX = (normX + deltaNormX).coerceIn(0f, 1f - normW)
                                    normY = (normY + deltaNormY).coerceIn(0f, 0.95f)
                                }
                            }
                            .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.3f))
                            .padding(4.dp)
                    ) {
                        AsyncImage(
                            model = sigFile,
                            contentDescription = "Signature",
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        isProcessing = true
                        scope.launch {
                            try {
                                val stamped = withContext(Dispatchers.IO) {
                                    container.pdfToolsService.stampSignature(
                                        input = selectedInput!!,
                                        pageIndex = currentPageIndex,
                                        signatureId = activeSignatureId!!,
                                        normX = normX,
                                        normY = normY,
                                        normW = normW
                                    )
                                }

                                when (val inp = selectedInput!!) {
                                    is ToolInput.AppDoc -> {
                                        Toast.makeText(context, "Signature stamped successfully", Toast.LENGTH_SHORT).show()
                                        onNavigateToDoc(inp.docId)
                                    }
                                    is ToolInput.PdfFile -> {
                                        val baseName = inp.displayName.removeSuffix(".pdf")
                                        val savedUri = withContext(Dispatchers.IO) {
                                            container.pdfToolsService.exportToToolsFolder(stamped, baseName, "signed")
                                        }
                                        stamped.delete()
                                        if (savedUri != null) {
                                            resultUri = savedUri
                                            resultFileName = "$baseName (signed).pdf"
                                        } else {
                                            Toast.makeText(context, "Failed to save signed file", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error stamping signature: ${e.message}", Toast.LENGTH_LONG).show()
                            } finally {
                                isProcessing = false
                            }
                        }
                    },
                    enabled = !isProcessing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.height(24.dp))
                    } else {
                        Icon(Icons.Outlined.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Place Signature on Page")
                    }
                }
            }
        }

        if (showSourcePicker) {
            ToolSourcePickerDialog(
                title = "Select Document or PDF to Sign",
                container = container,
                onSelectAppDoc = { doc ->
                    selectedInput = ToolInput.AppDoc(doc.id)
                    showSourcePicker = false
                },
                onSelectPdfFile = { uri, name ->
                    selectedInput = ToolInput.PdfFile(uri, name)
                    showSourcePicker = false
                },
                onDismiss = {
                    showSourcePicker = false
                    if (selectedInput == null) onNavigateBack()
                }
            )
        }

        resultUri?.let { uri ->
            ToolResultBottomSheet(
                outputUri = uri,
                fileName = resultFileName,
                infoText = "Signature stamped onto page ${currentPageIndex + 1}",
                container = container,
                onNavigateToDoc = onNavigateToDoc,
                onDone = {
                    resultUri = null
                    onNavigateBack()
                }
            )
        }
    }
}
