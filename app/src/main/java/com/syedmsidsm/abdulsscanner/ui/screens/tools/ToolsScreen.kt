package com.syedmsidsm.abdulsscanner.ui.screens.tools

import android.app.Activity
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MergeType
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Reorder
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Web
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syedmsidsm.abdulsscanner.R
import com.syedmsidsm.abdulsscanner.data.db.entity.DocumentEntity
import com.syedmsidsm.abdulsscanner.data.db.entity.QrSource
import com.syedmsidsm.abdulsscanner.di.AppContainer
import com.syedmsidsm.abdulsscanner.domain.qr.QrScannerHelper
import com.syedmsidsm.abdulsscanner.ui.nav.AppBottomBar
import com.syedmsidsm.abdulsscanner.ui.nav.Routes
import com.syedmsidsm.abdulsscanner.ui.theme.CardShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ToolItem(
    val titleRes: Int,
    val icon: ImageVector,
    val phaseLabel: String? = null,
    val route: String? = null,
    val action: (() -> Unit)? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    container: AppContainer,
    onNavigateToDocs: () -> Unit,
    onCameraClick: () -> Unit,
    onNavigateToRoute: (String) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    var showQrDialog by remember { mutableStateOf(false) }
    var showBookPrompt by remember { mutableStateOf(false) }
    var showDocPickerForOcr by remember { mutableStateOf(false) }
    var activeDocs by remember { mutableStateOf<List<DocumentEntity>>(emptyList()) }

    val bookScannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val pages = scanResult?.pages?.map { it.imageUri } ?: emptyList()
            if (pages.isNotEmpty()) {
                container.pendingBookScanUris = pages
                onNavigateToRoute(Routes.book())
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val stream = context.contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(stream)
                    stream?.close()
                    if (bitmap != null) {
                        val codes = QrScannerHelper.scanFromBitmap(bitmap)
                        if (codes.isNotEmpty()) {
                            val code = codes.first()
                            val id = QrScannerHelper.saveScannedCode(
                                raw = code,
                                source = QrSource.IMAGE,
                                qrRepository = container.qrRepository,
                                settingsRepository = container.settingsRepository
                            )
                            withContext(Dispatchers.Main) {
                                onNavigateToRoute(Routes.qrResult(id, if (id == 0L) code else ""))
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "No QR code found in selected image", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to read image: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val scanTools = listOf(
        ToolItem(R.string.tool_id_card, Icons.Outlined.Badge, action = { onNavigateToRoute(Routes.camera("ID_CARD")) }),
        ToolItem(R.string.tool_scan_docs, Icons.Outlined.CameraAlt, action = onCameraClick),
        ToolItem(R.string.tool_book, Icons.Outlined.MenuBook, action = { onNavigateToRoute(Routes.camera("BOOK")) }),
        ToolItem(R.string.tool_qr_code, Icons.Outlined.QrCodeScanner, action = { showQrDialog = true }),
        ToolItem(R.string.tool_to_text, Icons.Outlined.TextFields, action = {
            scope.launch {
                activeDocs = container.database.documentDao().getActiveDocuments()
                showDocPickerForOcr = true
            }
        })
    )

    val processTools = listOf(
        ToolItem(R.string.tool_import_files, Icons.Outlined.UploadFile, route = Routes.TOOL_IMPORT),
        ToolItem(R.string.tool_collage, Icons.Outlined.GridOn, route = Routes.COLLAGE),
        ToolItem(R.string.tool_watermark, Icons.Outlined.WaterDrop, route = Routes.TOOL_WATERMARK),
        ToolItem(R.string.tool_merge, Icons.Outlined.MergeType, route = Routes.TOOL_MERGE),
        ToolItem(R.string.tool_compression, Icons.Outlined.Compress, route = Routes.TOOL_COMPRESS),
        ToolItem(R.string.tool_office_to_pdf, Icons.Outlined.Description, route = Routes.TOOL_OFFICE),
        ToolItem(R.string.tool_web_to_pdf, Icons.Outlined.Web, route = Routes.TOOL_WEB)
    )

    val pdfTools = listOf(
        ToolItem(R.string.tool_signature, Icons.Outlined.Draw, route = Routes.TOOL_SIGNATURE),
        ToolItem(R.string.tool_password, Icons.Outlined.Lock, route = Routes.TOOL_PASSWORD),
        ToolItem(R.string.tool_page_reorder, Icons.Outlined.Reorder, route = Routes.TOOL_REORDER),
        ToolItem(R.string.tool_split_pdf, Icons.Outlined.PictureAsPdf, route = Routes.TOOL_SPLIT),
        ToolItem(R.string.tool_pdf_to_jpg, Icons.Outlined.Image, route = Routes.TOOL_PDF_TO_JPG)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.nav_tools),
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            AppBottomBar(
                currentRoute = Routes.TOOLS,
                onNavigateToDocs = onNavigateToDocs,
                onNavigateToTools = {},
                onCameraClick = onCameraClick
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Scan Section
            item(span = { GridItemSpan(3) }) {
                SectionHeader(stringResource(R.string.section_scan))
            }
            items(scanTools.size) { idx ->
                ToolCard(scanTools[idx], onNavigateToRoute)
            }

            // Process Files Section
            item(span = { GridItemSpan(3) }) {
                Spacer(modifier = Modifier.height(8.dp))
                SectionHeader(stringResource(R.string.section_process))
            }
            items(processTools.size) { idx ->
                ToolCard(processTools[idx], onNavigateToRoute)
            }

            // PDF Tools Section
            item(span = { GridItemSpan(3) }) {
                Spacer(modifier = Modifier.height(8.dp))
                SectionHeader(stringResource(R.string.section_pdf_tools))
            }
            items(pdfTools.size) { idx ->
                ToolCard(pdfTools[idx], onNavigateToRoute)
            }

            item(span = { GridItemSpan(3) }) {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        if (showQrDialog) {
            ModalBottomSheet(
                onDismissRequest = { showQrDialog = false }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.tool_qr_code),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                showQrDialog = false
                                onNavigateToRoute(Routes.camera("QR_CODE"))
                            },
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.QrCodeScanner, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("Scan with Camera", fontWeight = FontWeight.SemiBold)
                                Text("Live QR & barcode scanner", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                showQrDialog = false
                                imagePicker.launch("image/*")
                            },
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("Scan from Photo", fontWeight = FontWeight.SemiBold)
                                Text("Detect QR code from an existing photo or image", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                showQrDialog = false
                                onNavigateToRoute(Routes.QR_HISTORY)
                            },
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("Scan History", fontWeight = FontWeight.SemiBold)
                                Text("View past scanned barcodes and QR codes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        if (showDocPickerForOcr) {
            ModalBottomSheet(
                onDismissRequest = { showDocPickerForOcr = false }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = "Select Document for Text Reading",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (activeDocs.isEmpty()) {
                        Text(
                            text = "No documents found. Scan a document first.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                showDocPickerForOcr = false
                                onCameraClick()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Scan Document")
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(activeDocs) { doc ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            showDocPickerForOcr = false
                                            onNavigateToRoute(Routes.ocr(doc.id))
                                        },
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Outlined.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(doc.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("${doc.pageCount} pages", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        if (showBookPrompt) {
            AlertDialog(
                onDismissRequest = { showBookPrompt = false },
                title = { Text(stringResource(R.string.tool_book)) },
                text = { Text("Lay the book open and flat. Scan both pages together; you'll split them next.") },
                confirmButton = {
                    Button(onClick = {
                        showBookPrompt = false
                        val options = GmsDocumentScannerOptions.Builder()
                            .setGalleryImportAllowed(true)
                            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                            .build()
                        if (activity != null) {
                            GmsDocumentScanning.getClient(options)
                                .getStartScanIntent(activity)
                                .addOnSuccessListener { intentSender ->
                                    bookScannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(context, "Scanner unavailable: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        }
                    }) {
                        Text("Start Scanning")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBookPrompt = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
fun ToolCard(item: ToolItem, onNavigateToRoute: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.95f)
            .clip(CardShape)
            .clickable {
                if (item.action != null) {
                    item.action.invoke()
                } else if (item.route != null && item.phaseLabel == null) {
                    onNavigateToRoute(item.route)
                }
            },
        shape = CardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(6.dp)
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = stringResource(item.titleRes),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(item.titleRes),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    lineHeight = 15.sp
                )
                if (item.phaseLabel != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.phaseLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}
