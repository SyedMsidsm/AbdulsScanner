package com.syedmsidsm.abdulsscanner.ui.screens.tools

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.syedmsidsm.abdulsscanner.di.AppContainer
import com.syedmsidsm.abdulsscanner.domain.office.DocxToHtml
import com.syedmsidsm.abdulsscanner.domain.office.HtmlPrinter
import com.syedmsidsm.abdulsscanner.domain.office.XlsxToHtml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolOfficeToPdfScreen(
    container: AppContainer,
    onNavigateBack: () -> Unit,
    onNavigateToDoc: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val htmlPrinter = remember { HtmlPrinter(context) }
    DisposableEffect(Unit) {
        onDispose {
            htmlPrinter.release()
        }
    }

    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    var isConverting by remember { mutableStateOf(false) }

    var showUnsupportedDialog by remember { mutableStateOf(false) }
    var unsupportedFormatName by remember { mutableStateOf("") }

    var showHelpDialog by remember { mutableStateOf(false) }
    var showPostPrintSheet by remember { mutableStateOf(false) }

    val importPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val newDocId = withContext(Dispatchers.IO) {
                        container.pdfToolsService.importPdf(uri)
                    }
                    onNavigateToDoc(newDocId)
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to import: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun handleSelectedFile(uri: Uri) {
        selectedFileUri = uri
        var fileName = "document"
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIdx >= 0 && cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIdx)
                }
            }
        } catch (e: Exception) {
            fileName = uri.lastPathSegment ?: "document"
        }
        selectedFileName = fileName

        val lowerName = fileName.lowercase(Locale.US)
        if (lowerName.endsWith(".docx")) {
            // Convert DOCX
            isConverting = true
            scope.launch {
                try {
                    val html = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            DocxToHtml.convert(stream)
                        } ?: throw IllegalStateException("Could not read document")
                    }

                    val jobName = "Abdul's Scanner - ${fileName.removeSuffix(".docx")}"
                    htmlPrinter.printHtml(
                        html = html,
                        jobName = jobName,
                        isLandscape = false,
                        onStarted = {
                            isConverting = false
                            showPostPrintSheet = true
                        },
                        onError = { err ->
                            isConverting = false
                            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                        }
                    )
                } catch (e: Exception) {
                    isConverting = false
                    Toast.makeText(context, "Conversion failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        } else if (lowerName.endsWith(".xlsx")) {
            // Convert XLSX
            isConverting = true
            scope.launch {
                try {
                    val xlsxResult = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            XlsxToHtml.convert(stream)
                        } ?: throw IllegalStateException("Could not read spreadsheet")
                    }

                    val jobName = "Abdul's Scanner - ${fileName.removeSuffix(".xlsx")}"
                    htmlPrinter.printHtml(
                        html = xlsxResult.html,
                        jobName = jobName,
                        isLandscape = xlsxResult.isLandscape,
                        onStarted = {
                            isConverting = false
                            showPostPrintSheet = true
                        },
                        onError = { err ->
                            isConverting = false
                            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                        }
                    )
                } catch (e: Exception) {
                    isConverting = false
                    Toast.makeText(context, "Conversion failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            // Unsupported format (.pptx, .doc, .xls, .ppt, .odt, etc.)
            val ext = fileName.substringAfterLast('.', "file").uppercase(Locale.US)
            unsupportedFormatName = ext
            showUnsupportedDialog = true
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            handleSelectedFile(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Word / Excel to PDF", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (selectedFileUri != null) {
                        IconButton(onClick = { showHelpDialog = true }) {
                            Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = "Doesn't look right?")
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
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isConverting) {
                CircularProgressIndicator(modifier = Modifier.size(52.dp))
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Converting $selectedFileName to PDF…",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Opening Android print dialog to save as PDF",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Icon(
                    Icons.Outlined.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Convert Office Documents on Your Phone",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Pick a Word (.docx) or Excel (.xlsx) file to convert entirely on your phone with zero internet access.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = {
                        openDocumentLauncher.launch(
                            arrayOf(
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "application/msword",
                                "application/vnd.ms-excel",
                                "application/vnd.ms-powerpoint",
                                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                                "*/*"
                            )
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(Icons.Outlined.UploadFile, contentDescription = null)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Select Word or Excel File")
                }

                if (selectedFileUri != null) {
                    Spacer(modifier = Modifier.height(20.dp))
                    OutlinedButton(
                        onClick = { showHelpDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Doesn't look right?")
                    }
                }
            }
        }

        // Unsupported Format Dialog
        if (showUnsupportedDialog) {
            AlertDialog(
                onDismissRequest = { showUnsupportedDialog = false },
                icon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text("$unsupportedFormatName format") },
                text = {
                    Text(
                        "Direct conversion is supported for .docx and .xlsx files.\n\nFor $unsupportedFormatName files, open it in Google Docs, Sheets or Slides on your phone, then use Share → Print → Save as PDF."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showUnsupportedDialog = false
                            val uri = selectedFileUri
                            if (uri != null) {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, "*/*")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Open with…"))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "No app to open this file", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) {
                        Text("Open with…")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showUnsupportedDialog = false }) {
                        Text("Dismiss")
                    }
                }
            )
        }

        // "Doesn't look right?" Dialog
        if (showHelpDialog) {
            AlertDialog(
                onDismissRequest = { showHelpDialog = false },
                icon = { Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text("Document formatting note") },
                text = {
                    Text(
                        "On-device conversion preserves headings, bold text, bullet lists, tables, and images. Dates in Excel stay as raw numbers.\n\nIf the layout doesn't look exact, open it in Google Docs, Sheets or Slides on your phone, then use Share → Print → Save as PDF."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showHelpDialog = false
                            val uri = selectedFileUri
                            if (uri != null) {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, "*/*")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Open with…"))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "No app to open this file", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) {
                        Text("Open with…")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showHelpDialog = false }) {
                        Text("Dismiss")
                    }
                }
            )
        }

        // Post-print bottom sheet
        if (showPostPrintSheet) {
            ModalBottomSheet(
                onDismissRequest = { showPostPrintSheet = false }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "PDF Generated",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Once you choose \"Save as PDF\" in the Android print screen, you can add that PDF directly into Abdul's Scanner.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            showPostPrintSheet = false
                            importPdfLauncher.launch(arrayOf("application/pdf"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Add the saved PDF to Abdul's Scanner")
                    }

                    OutlinedButton(
                        onClick = {
                            showPostPrintSheet = false
                            onNavigateBack()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Done")
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}
