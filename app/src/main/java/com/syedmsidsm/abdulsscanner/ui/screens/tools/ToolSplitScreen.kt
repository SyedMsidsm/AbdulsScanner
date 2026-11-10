package com.syedmsidsm.abdulsscanner.ui.screens.tools

import android.net.Uri
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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.syedmsidsm.abdulsscanner.data.db.entity.DocumentEntity
import com.syedmsidsm.abdulsscanner.data.db.entity.PageEntity
import com.syedmsidsm.abdulsscanner.di.AppContainer
import com.syedmsidsm.abdulsscanner.domain.pdf.ToolInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolSplitScreen(
    container: AppContainer,
    onNavigateBack: () -> Unit,
    onNavigateToDoc: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showSourcePicker by remember { mutableStateOf(true) }
    var selectedInput by remember { mutableStateOf<ToolInput?>(null) }
    var selectedDocEntity by remember { mutableStateOf<DocumentEntity?>(null) }
    var appDocPages by remember { mutableStateOf<List<PageEntity>>(emptyList()) }
    val selectedPageIds = remember { mutableStateListOf<String>() }
    var removeFromOriginal by remember { mutableStateOf(false) }

    var rangesText by remember { mutableStateOf("1-3, 5") }
    var splitEveryPage by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }

    var resultUri by remember { mutableStateOf<Uri?>(null) }
    var resultFileName by remember { mutableStateOf("") }
    var resultInfoText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedDocEntity) {
        val doc = selectedDocEntity
        if (doc != null) {
            withContext(Dispatchers.IO) {
                appDocPages = container.database.pageDao().getByDocumentId(doc.id).sortedBy { it.position }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Split / Extract Pages", fontWeight = FontWeight.Bold) },
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
            if (selectedInput == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Button(onClick = { showSourcePicker = true }) {
                        Text("Choose Document or PDF to Split")
                    }
                }
            } else {
                when (val input = selectedInput!!) {
                    is ToolInput.AppDoc -> {
                        // Extract pages from App Document
                        Text(
                            text = "Extract from \"${selectedDocEntity?.name ?: "Document"}\"",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Select pages to extract into a new document (${selectedPageIds.size} selected):",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(appDocPages) { page ->
                                val isSelected = selectedPageIds.contains(page.id)
                                val previewFile = container.fileStore.getThumbFile(page.documentId, page.id)

                                Card(
                                    modifier = Modifier
                                        .aspectRatio(0.75f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            if (isSelected) selectedPageIds.remove(page.id)
                                            else selectedPageIds.add(page.id)
                                        },
                                    elevation = CardDefaults.cardElevation(2.dp)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        AsyncImage(
                                            model = previewFile,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "p. ${page.position + 1}",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }

                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(6.dp)
                                                    .size(24.dp)
                                                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Outlined.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { removeFromOriginal = !removeFromOriginal }
                        ) {
                            Checkbox(
                                checked = removeFromOriginal,
                                onCheckedChange = { removeFromOriginal = it }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Remove extracted pages from original document", style = MaterialTheme.typography.bodyMedium)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                if (selectedPageIds.isEmpty()) {
                                    Toast.makeText(context, "Select at least 1 page", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                isProcessing = true
                                scope.launch {
                                    try {
                                        val newDocId = withContext(Dispatchers.IO) {
                                            val doc = selectedDocEntity!!
                                            val pagesToCopy = appDocPages.filter { selectedPageIds.contains(it.id) }
                                            val newDoc = doc.copy(
                                                id = java.util.UUID.randomUUID().toString(),
                                                name = "${doc.name} (Extracted)",
                                                pageCount = pagesToCopy.size,
                                                createdAt = System.currentTimeMillis(),
                                                updatedAt = System.currentTimeMillis()
                                            )
                                            container.database.documentDao().insert(newDoc)

                                            val newPages = pagesToCopy.mapIndexed { idx, p ->
                                                val newPageId = java.util.UUID.randomUUID().toString()
                                                val origSrc = container.fileStore.getOrigFile(doc.id, p.id)
                                                val procSrc = container.fileStore.getRenderedFile(doc.id, p.id)
                                                val prevSrc = container.fileStore.getThumbFile(doc.id, p.id)

                                                if (origSrc.exists()) origSrc.copyTo(container.fileStore.getOrigFile(newDoc.id, newPageId), overwrite = true)
                                                if (procSrc.exists()) procSrc.copyTo(container.fileStore.getRenderedFile(newDoc.id, newPageId), overwrite = true)
                                                if (prevSrc.exists()) prevSrc.copyTo(container.fileStore.getThumbFile(newDoc.id, newPageId), overwrite = true)

                                                p.copy(
                                                    id = newPageId,
                                                    documentId = newDoc.id,
                                                    position = idx
                                                )
                                            }
                                            container.database.pageDao().insertAll(newPages)
                                            container.pdfBuilder.buildPdf(newDoc, newPages)

                                            if (removeFromOriginal) {
                                                for (id in selectedPageIds) {
                                                    container.database.pageDao().deleteById(id)
                                                    container.fileStore.deletePageFiles(doc.id, id)
                                                }
                                                val remainingPages = container.database.pageDao().getByDocumentId(doc.id)
                                                    .sortedBy { it.position }
                                                    .mapIndexed { i, p -> p.copy(position = i) }
                                                container.database.pageDao().updateAll(remainingPages)
                                                container.database.documentDao().update(doc.copy(pageCount = remainingPages.size))
                                                container.pdfBuilder.buildPdf(doc.copy(pageCount = remainingPages.size), remainingPages)
                                            }

                                            newDoc.id
                                        }
                                        onNavigateToDoc(newDocId)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                    } finally {
                                        isProcessing = false
                                    }
                                }
                            },
                            enabled = selectedPageIds.isNotEmpty() && !isProcessing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.height(24.dp))
                            } else {
                                Text("Extract ${selectedPageIds.size} Pages")
                            }
                        }
                    }

                    is ToolInput.PdfFile -> {
                        // Split external PDF
                        Text(
                            text = "Split \"${input.displayName}\"",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = !splitEveryPage,
                                onClick = { splitEveryPage = false },
                                label = { Text("Custom Ranges") }
                            )
                            FilterChip(
                                selected = splitEveryPage,
                                onClick = { splitEveryPage = true },
                                label = { Text("Every page separately") }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (!splitEveryPage) {
                            OutlinedTextField(
                                value = rangesText,
                                onValueChange = { rangesText = it },
                                label = { Text("Page Ranges") },
                                placeholder = { Text("e.g. 1-3, 5, 8-10") },
                                supportingText = { Text("Comma-separated page numbers or ranges (1-based)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                text = "Every single page of the PDF will be saved as an individual PDF file.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        Button(
                            onClick = {
                                isProcessing = true
                                scope.launch {
                                    try {
                                        val rangesArg = if (splitEveryPage) "all" else rangesText
                                        val splitFiles = withContext(Dispatchers.IO) {
                                            container.pdfToolsService.splitRanges(input, rangesArg)
                                        }

                                        val baseName = input.displayName.removeSuffix(".pdf")
                                        var firstUri: Uri? = null

                                        withContext(Dispatchers.IO) {
                                            for ((idx, file) in splitFiles.withIndex()) {
                                                val suffix = if (splitEveryPage) "page_${idx + 1}" else "part_${idx + 1}"
                                                val uri = container.pdfToolsService.exportToToolsFolder(file, baseName, suffix)
                                                if (idx == 0) firstUri = uri
                                                file.delete()
                                            }
                                        }

                                        if (firstUri != null) {
                                            resultUri = firstUri
                                            resultFileName = "$baseName (split).pdf"
                                            resultInfoText = "Generated ${splitFiles.size} PDF files in Tools folder"
                                        } else {
                                            Toast.makeText(context, "Failed to export split files", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Split failed: ${e.message}", Toast.LENGTH_LONG).show()
                                    } finally {
                                        isProcessing = false
                                    }
                                }
                            },
                            enabled = !isProcessing && (splitEveryPage || rangesText.isNotBlank()),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.height(24.dp))
                            } else {
                                Text("Split PDF")
                            }
                        }
                    }
                }
            }
        }

        if (showSourcePicker) {
            ToolSourcePickerDialog(
                title = "Select PDF or Document to Split",
                container = container,
                onSelectAppDoc = { doc ->
                    selectedDocEntity = doc
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
                infoText = resultInfoText,
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
