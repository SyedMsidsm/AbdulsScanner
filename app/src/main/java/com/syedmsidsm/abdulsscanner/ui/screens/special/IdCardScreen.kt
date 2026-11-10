package com.syedmsidsm.abdulsscanner.ui.screens.special

import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.syedmsidsm.abdulsscanner.R
import com.syedmsidsm.abdulsscanner.data.db.entity.DocumentKind
import com.syedmsidsm.abdulsscanner.domain.imaging.BitmapIo
import com.syedmsidsm.abdulsscanner.domain.scan.SaveScanUseCase
import com.syedmsidsm.abdulsscanner.domain.scan.special.IdCardLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdCardScreen(
    saveScanUseCase: SaveScanUseCase,
    onNavigateBack: () -> Unit,
    onNavigateToDocument: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var frontBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var backBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var compositeBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var isProcessing by remember { mutableStateOf(false) }
    var showSingleSideDialog by remember { mutableStateOf(false) }
    var pendingFrontUri by remember { mutableStateOf<Uri?>(null) }

    val singleBackScannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val backUri = scanResult?.pages?.firstOrNull()?.imageUri
            val fUri = pendingFrontUri

            if (fUri != null && backUri != null) {
                isProcessing = true
                scope.launch(Dispatchers.IO) {
                    try {
                        val front = BitmapIo.decodeFromUri(context, fUri, maxLongSide = 2400)
                        val back = BitmapIo.decodeFromUri(context, backUri, maxLongSide = 2400)
                        val composite = IdCardLayout.composeIdCard(front, back)

                        withContext(Dispatchers.Main) {
                            frontBitmap = front
                            backBitmap = back
                            compositeBitmap = composite
                            isProcessing = false
                            showSingleSideDialog = false
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            isProcessing = false
                            Toast.makeText(context, "Error processing ID card: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    val primaryScannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val pages = scanResult?.pages?.map { it.imageUri } ?: emptyList()

            if (pages.size >= 2) {
                // Both front and back scanned
                isProcessing = true
                scope.launch(Dispatchers.IO) {
                    try {
                        val front = BitmapIo.decodeFromUri(context, pages[0], maxLongSide = 2400)
                        val back = BitmapIo.decodeFromUri(context, pages[1], maxLongSide = 2400)
                        val composite = IdCardLayout.composeIdCard(front, back)

                        withContext(Dispatchers.Main) {
                            frontBitmap = front
                            backBitmap = back
                            compositeBitmap = composite
                            isProcessing = false
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            isProcessing = false
                            Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else if (pages.size == 1) {
                // Only 1 side scanned
                pendingFrontUri = pages[0]
                showSingleSideDialog = true
            }
        }
    }

    fun startInitialScan() {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(2)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()

        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(context as Activity)
            .addOnSuccessListener { intentSender ->
                primaryScannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Scanner unavailable: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    fun scanBackSide() {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(1)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()

        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(context as Activity)
            .addOnSuccessListener { intentSender ->
                singleBackScannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Scanner unavailable: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    fun saveFrontOnly() {
        val fUri = pendingFrontUri ?: return
        showSingleSideDialog = false
        isProcessing = true
        scope.launch(Dispatchers.IO) {
            try {
                val front = BitmapIo.decodeFromUri(context, fUri, maxLongSide = 2400)
                val composite = IdCardLayout.composeIdCard(front, null)
                withContext(Dispatchers.Main) {
                    frontBitmap = front
                    backBitmap = null
                    compositeBitmap = composite
                    isProcessing = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isProcessing = false
                    Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tool_id_card), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val composite = compositeBitmap

            if (composite == null) {
                // Intro Screen
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Badge,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(54.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Scan ID Card",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Scan the front, then the back.\nBoth sides go on one A4 page at real physical size (85.6 × 54 mm).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )

                    Spacer(modifier = Modifier.height(36.dp))

                    Button(
                        onClick = { startInitialScan() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Outlined.CameraAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Scanning", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                // Preview Screen with Composed Card on A4 Canvas
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(2480f / 3508f)
                            .clip(RoundedCornerShape(8.dp)),
                        shadowElevation = 4.dp
                    ) {
                        Image(
                            bitmap = composite.asImageBitmap(),
                            contentDescription = "ID Card Preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                compositeBitmap = null
                                frontBitmap = null
                                backBitmap = null
                                startInitialScan()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Outlined.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retake")
                        }

                        Button(
                            onClick = {
                                isProcessing = true
                                scope.launch {
                                    try {
                                        val docId = saveScanUseCase.saveBitmapScan(
                                            bitmaps = listOf(composite),
                                            kind = DocumentKind.ID_CARD,
                                            pageSize = "A4",
                                            autoTagIdCard = true
                                        )
                                        withContext(Dispatchers.Main) {
                                            isProcessing = false
                                            onNavigateToDocument(docId)
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            isProcessing = false
                                            Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Outlined.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save to Documents")
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

            if (showSingleSideDialog) {
                AlertDialog(
                    onDismissRequest = { showSingleSideDialog = false },
                    title = { Text("Only one side scanned") },
                    text = { Text("Would you like to scan the back side now or save the front side only?") },
                    confirmButton = {
                        Button(onClick = { scanBackSide() }) {
                            Text("Scan Back Side")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { saveFrontOnly() }) {
                            Text("Save Front Only")
                        }
                    }
                )
            }
        }
    }
}
