package com.syedmsidsm.abdulsscanner.ui.screens.tools

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
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
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.syedmsidsm.abdulsscanner.data.db.entity.DocumentKind
import com.syedmsidsm.abdulsscanner.di.AppContainer
import com.syedmsidsm.abdulsscanner.domain.imaging.BitmapIo
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolImportScreen(
    container: AppContainer,
    onNavigateBack: () -> Unit,
    onNavigateToDoc: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isProcessing by remember { mutableStateOf(false) }
    var progressStatus by remember { mutableStateOf("") }

    // Password prompt state for encrypted PDF
    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var pendingPdfUri by remember { mutableStateOf<Uri?>(null) }

    // Large PDF warning state (> 100 pages)
    var showLargePdfDialog by remember { mutableStateOf(false) }
    var pendingPageCount by remember { mutableIntStateOf(0) }

    // Image Picker Launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            isProcessing = true
            progressStatus = "Importing ${uris.size} images…"
            scope.launch {
                try {
                    val bitmaps = withContext(Dispatchers.IO) {
                        uris.mapNotNull { uri ->
                            BitmapIo.decodeFromUri(context, uri, maxLongSide = 3000)
                        }
                    }

                    if (bitmaps.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Could not load selected images", Toast.LENGTH_SHORT).show()
                            isProcessing = false
                        }
                        return@launch
                    }

                    val newDocId = withContext(Dispatchers.IO) {
                        container.saveScanUseCase.saveBitmapScan(
                            bitmaps = bitmaps,
                            kind = DocumentKind.IMPORT_IMAGES,
                            pageSize = "AUTO"
                        )
                    }

                    onNavigateToDoc(newDocId)
                } catch (e: Exception) {
                    Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    isProcessing = false
                }
            }
        }
    }

    fun processPdfImport(uri: Uri, password: String? = null) {
        isProcessing = true
        progressStatus = "Rendering PDF pages into images…"
        scope.launch {
            try {
                val newDocId = withContext(Dispatchers.IO) {
                    container.pdfToolsService.importPdf(uri, password)
                }
                onNavigateToDoc(newDocId)
            } catch (e: InvalidPasswordException) {
                pendingPdfUri = uri
                showPasswordDialog = true
            } catch (e: Exception) {
                if (e.message?.contains("password", ignoreCase = true) == true) {
                    pendingPdfUri = uri
                    showPasswordDialog = true
                } else {
                    Toast.makeText(context, "PDF import error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                isProcessing = false
            }
        }
    }

    // PDF Picker Launcher
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            // Check page count first to warn if > 100
            scope.launch {
                try {
                    val pageCount = withContext(Dispatchers.IO) {
                        val temp = container.fileStore.createTempFile("check_pages_", ".pdf")
                        context.contentResolver.openInputStream(uri)?.use { ins ->
                            temp.outputStream().use { outs -> ins.copyTo(outs) }
                        }
                        try {
                            val pfd = ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY)
                            val renderer = PdfRenderer(pfd)
                            val count = renderer.pageCount
                            renderer.close()
                            pfd.close()
                            count
                        } catch (e: Exception) {
                            -1
                        } finally {
                            temp.delete()
                        }
                    }

                    if (pageCount > 100) {
                        pendingPdfUri = uri
                        pendingPageCount = pageCount
                        showLargePdfDialog = true
                    } else {
                        processPdfImport(uri)
                    }
                } catch (e: Exception) {
                    processPdfImport(uri)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import Files", fontWeight = FontWeight.Bold) },
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Choose what to import into Abdul's Scanner:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            // Option 1: Images
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { imagePickerLauncher.launch("image/*") },
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Import Images", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Select multiple photos or images to create a new scanned document",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Option 2: PDF
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.PictureAsPdf,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Import PDF Document", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Pages become images; the app reads their text again for search.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (isProcessing) {
                Spacer(modifier = Modifier.height(24.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(progressStatus, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // Password Dialog
        if (showPasswordDialog && pendingPdfUri != null) {
            AlertDialog(
                onDismissRequest = {
                    showPasswordDialog = false
                    pendingPdfUri = null
                },
                title = { Text("Password Protected PDF") },
                text = {
                    Column {
                        Text("This PDF is encrypted. Enter password to import:")
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text("Password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val uri = pendingPdfUri!!
                        val pw = passwordInput
                        showPasswordDialog = false
                        passwordInput = ""
                        pendingPdfUri = null
                        processPdfImport(uri, pw)
                    }) {
                        Text("Unlock & Import")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showPasswordDialog = false
                        pendingPdfUri = null
                        passwordInput = ""
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Large PDF Warning Dialog (> 100 pages)
        if (showLargePdfDialog && pendingPdfUri != null) {
            AlertDialog(
                onDismissRequest = {
                    showLargePdfDialog = false
                    pendingPdfUri = null
                },
                title = { Text("Large PDF Document") },
                text = {
                    Text("This document contains $pendingPageCount pages. Rendering and indexing large documents may take a minute. Do you want to continue?")
                },
                confirmButton = {
                    Button(onClick = {
                        val uri = pendingPdfUri!!
                        showLargePdfDialog = false
                        pendingPdfUri = null
                        processPdfImport(uri)
                    }) {
                        Text("Import Anyway")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showLargePdfDialog = false
                        pendingPdfUri = null
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
