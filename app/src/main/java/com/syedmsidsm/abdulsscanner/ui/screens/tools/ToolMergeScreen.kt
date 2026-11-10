package com.syedmsidsm.abdulsscanner.ui.screens.tools

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.MergeType
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.syedmsidsm.abdulsscanner.di.AppContainer
import com.syedmsidsm.abdulsscanner.domain.pdf.ToolInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MergeItem(
    val input: ToolInput,
    val title: String,
    val subtitle: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolMergeScreen(
    container: AppContainer,
    onNavigateBack: () -> Unit,
    onNavigateToDoc: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val items = remember { mutableStateListOf<MergeItem>() }
    var outputName by remember { mutableStateOf("") }
    var isMerging by remember { mutableStateOf(false) }
    var showSourcePicker by remember { mutableStateOf(false) }

    var resultUri by remember { mutableStateOf<Uri?>(null) }
    var resultFileName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Merge Documents / PDFs", fontWeight = FontWeight.Bold) },
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
            OutlinedTextField(
                value = outputName,
                onValueChange = { outputName = it },
                label = { Text("Merged File Name (optional)") },
                placeholder = { Text(if (items.isNotEmpty()) items.first().title else "Merged Document") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Items to merge (${items.size}):",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                OutlinedButton(
                    onClick = { showSourcePicker = true }
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Item")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.MergeType,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.height(64.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Add at least 2 documents or PDFs to merge",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(items) { index, item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}.",
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(28.dp)
                                )
                                Icon(
                                    when (item.input) {
                                        is ToolInput.AppDoc -> Icons.Outlined.Description
                                        is ToolInput.PdfFile -> Icons.Outlined.PictureAsPdf
                                    },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        item.title,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        item.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row {
                                    if (index > 0) {
                                        IconButton(
                                            onClick = {
                                                val moved = items.removeAt(index)
                                                items.add(index - 1, moved)
                                            }
                                        ) {
                                            Icon(Icons.Outlined.ArrowUpward, contentDescription = "Move Up")
                                        }
                                    }
                                    if (index < items.size - 1) {
                                        IconButton(
                                            onClick = {
                                                val moved = items.removeAt(index)
                                                items.add(index + 1, moved)
                                            }
                                        ) {
                                            Icon(Icons.Outlined.ArrowDownward, contentDescription = "Move Down")
                                        }
                                    }
                                    IconButton(
                                        onClick = { items.removeAt(index) }
                                    ) {
                                        Icon(Icons.Outlined.Delete, contentDescription = "Remove")
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
                    if (items.size < 2) {
                        Toast.makeText(context, "Please add at least 2 items", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isMerging = true
                    val customTargetName = outputName.ifBlank { items.first().title }

                    scope.launch {
                        try {
                            val allAppDocs = items.all { it.input is ToolInput.AppDoc }
                            if (allAppDocs) {
                                val docIds: List<String> = items.mapNotNull { item ->
                                    (item.input as? ToolInput.AppDoc)?.docId
                                }
                                val newDocId = withContext(Dispatchers.IO) {
                                    container.mergeDocumentsUseCase.mergeDocuments(docIds, customTargetName)
                                }
                                onNavigateToDoc(newDocId)
                            } else {
                                val mergedFile = withContext(Dispatchers.IO) {
                                    container.pdfToolsService.merge(items.map { it.input }, customTargetName)
                                }
                                val exportedUri = withContext(Dispatchers.IO) {
                                    container.pdfToolsService.exportToToolsFolder(
                                        mergedFile,
                                        customTargetName,
                                        "merged"
                                    )
                                }
                                mergedFile.delete()
                                if (exportedUri != null) {
                                    resultUri = exportedUri
                                    resultFileName = "$customTargetName (merged).pdf"
                                } else {
                                    Toast.makeText(context, "Failed to save merged file", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Merge error: ${e.message}", Toast.LENGTH_LONG).show()
                        } finally {
                            isMerging = false
                        }
                    }
                },
                enabled = items.size >= 2 && !isMerging,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (isMerging) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.height(24.dp)
                    )
                } else {
                    Icon(Icons.Outlined.MergeType, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Merge ${items.size} Items")
                }
            }
        }

        if (showSourcePicker) {
            ToolSourcePickerDialog(
                title = "Add Item to Merge",
                container = container,
                onSelectAppDoc = { doc ->
                    items.add(
                        MergeItem(
                            input = ToolInput.AppDoc(doc.id),
                            title = doc.name,
                            subtitle = "App Document • ${doc.pageCount} pages"
                        )
                    )
                    showSourcePicker = false
                },
                onSelectPdfFile = { uri, name ->
                    items.add(
                        MergeItem(
                            input = ToolInput.PdfFile(uri, name),
                            title = name.removeSuffix(".pdf"),
                            subtitle = "External PDF file"
                        )
                    )
                    showSourcePicker = false
                },
                onDismiss = { showSourcePicker = false }
            )
        }

        resultUri?.let { uri ->
            ToolResultBottomSheet(
                outputUri = uri,
                fileName = resultFileName,
                infoText = "Successfully merged ${items.size} files into one PDF",
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
