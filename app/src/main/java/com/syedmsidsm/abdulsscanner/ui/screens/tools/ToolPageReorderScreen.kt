package com.syedmsidsm.abdulsscanner.ui.screens.tools

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Reorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.syedmsidsm.abdulsscanner.di.AppContainer
import com.syedmsidsm.abdulsscanner.domain.pdf.ToolInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PdfPageItem(
    val originalPageIndex: Int,
    val bitmap: Bitmap
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolPageReorderScreen(
    container: AppContainer,
    onNavigateBack: () -> Unit,
    onNavigateToDoc: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showSourcePicker by remember { mutableStateOf(true) }
    var selectedPdfFile by remember { mutableStateOf<ToolInput.PdfFile?>(null) }
    val pagesList = remember { mutableStateListOf<PdfPageItem>() }
    var isLoadingPages by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    var resultUri by remember { mutableStateOf<Uri?>(null) }
    var resultFileName by remember { mutableStateOf("") }

    LaunchedEffect(selectedPdfFile) {
        val fileInput = selectedPdfFile ?: return@LaunchedEffect
        isLoadingPages = true
        pagesList.clear()

        withContext(Dispatchers.IO) {
            try {
                val tempPdf = container.fileStore.createTempFile("reorder_load_", ".pdf")
                context.contentResolver.openInputStream(fileInput.uri)?.use { ins ->
                    tempPdf.outputStream().use { outs -> ins.copyTo(outs) }
                }
                val pfd = ParcelFileDescriptor.open(tempPdf, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)

                val loaded = mutableListOf<PdfPageItem>()
                for (i in 0 until renderer.pageCount) {
                    val p = renderer.openPage(i)
                    val scale = 300f / maxOf(p.width, p.height)
                    val w = (p.width * scale).toInt().coerceAtLeast(50)
                    val h = (p.height * scale).toInt().coerceAtLeast(50)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    p.close()
                    loaded.add(PdfPageItem(originalPageIndex = i, bitmap = bmp))
                }
                renderer.close()
                pfd.close()
                tempPdf.delete()

                withContext(Dispatchers.Main) {
                    pagesList.addAll(loaded)
                }
            } catch (e: Exception) {
                // ignore
            } finally {
                isLoadingPages = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reorder PDF Pages", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
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
            if (selectedPdfFile == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Button(onClick = { showSourcePicker = true }) {
                        Text("Choose PDF to Reorder")
                    }
                }
            } else if (isLoadingPages) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Loading PDF pages…")
                    }
                }
            } else {
                Text(
                    text = "${selectedPdfFile!!.displayName} (${pagesList.size} pages remaining)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Tap ✕ to remove pages, or use arrows to rearrange pages:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(pagesList) { index, item ->
                        Card(
                            modifier = Modifier
                                .aspectRatio(0.75f)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                AsyncImage(
                                    model = item.bitmap,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // Delete button
                                IconButton(
                                    onClick = {
                                        if (pagesList.size > 1) {
                                            pagesList.removeAt(index)
                                        } else {
                                            Toast.makeText(context, "A PDF must have at least 1 page", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(28.dp)
                                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f), CircleShape)
                                ) {
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = "Remove page",
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                // Move buttons row at bottom
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f), RoundedCornerShape(topStart = 6.dp))
                                ) {
                                    if (index > 0) {
                                        IconButton(
                                            onClick = {
                                                val p = pagesList.removeAt(index)
                                                pagesList.add(index - 1, p)
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Outlined.ArrowUpward, contentDescription = "Move earlier", modifier = Modifier.size(14.dp))
                                        }
                                    }
                                    if (index < pagesList.size - 1) {
                                        IconButton(
                                            onClick = {
                                                val p = pagesList.removeAt(index)
                                                pagesList.add(index + 1, p)
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Outlined.ArrowDownward, contentDescription = "Move later", modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        isSaving = true
                        scope.launch {
                            try {
                                val order = pagesList.map { it.originalPageIndex }
                                val reordered = withContext(Dispatchers.IO) {
                                    container.pdfToolsService.reorderExternalPdf(selectedPdfFile!!, order)
                                }
                                val baseName = selectedPdfFile!!.displayName.removeSuffix(".pdf")
                                val savedUri = withContext(Dispatchers.IO) {
                                    container.pdfToolsService.exportToToolsFolder(reordered, baseName, "reordered")
                                }
                                reordered.delete()

                                if (savedUri != null) {
                                    resultUri = savedUri
                                    resultFileName = "$baseName (reordered).pdf"
                                } else {
                                    Toast.makeText(context, "Failed to save reordered PDF", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Reorder error: ${e.message}", Toast.LENGTH_LONG).show()
                            } finally {
                                isSaving = false
                            }
                        }
                    },
                    enabled = pagesList.isNotEmpty() && !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.height(24.dp))
                    } else {
                        Icon(Icons.Outlined.Reorder, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Reordered PDF")
                    }
                }
            }
        }

        if (showSourcePicker) {
            ToolSourcePickerDialog(
                title = "Select PDF or Document to Reorder",
                container = container,
                onSelectAppDoc = { doc ->
                    showSourcePicker = false
                    // Redirects to document viewer which has native reorder mode
                    onNavigateToDoc(doc.id)
                },
                onSelectPdfFile = { uri, name ->
                    selectedPdfFile = ToolInput.PdfFile(uri, name)
                    showSourcePicker = false
                },
                onDismiss = {
                    showSourcePicker = false
                    if (selectedPdfFile == null) onNavigateBack()
                }
            )
        }

        resultUri?.let { uri ->
            ToolResultBottomSheet(
                outputUri = uri,
                fileName = resultFileName,
                infoText = "Pages reordered (${pagesList.size} pages)",
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
