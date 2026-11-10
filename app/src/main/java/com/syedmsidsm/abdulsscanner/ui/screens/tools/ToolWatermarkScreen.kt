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
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syedmsidsm.abdulsscanner.di.AppContainer
import com.syedmsidsm.abdulsscanner.domain.imaging.OverlayCompositor
import com.syedmsidsm.abdulsscanner.domain.imaging.PageOverlay
import com.syedmsidsm.abdulsscanner.domain.pdf.ToolInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolWatermarkScreen(
    docIdArg: String? = null,
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

    var watermarkText by remember { mutableStateOf("Confidential") }
    var sizePct by remember { mutableIntStateOf(6) } // S=4, M=6, L=9
    var opacity by remember { mutableFloatStateOf(25f) } // 10..60
    var angle by remember { mutableIntStateOf(45) } // 0, 30, 45
    var colorHex by remember { mutableStateOf("#808080") } // Grey, Red, Blue
    var isTiled by remember { mutableStateOf(false) }

    var isProcessing by remember { mutableStateOf(false) }
    var resultUri by remember { mutableStateOf<Uri?>(null) }
    var resultFileName by remember { mutableStateOf("") }

    val colors = listOf(
        Pair("Grey", "#808080"),
        Pair("Red", "#E53935"),
        Pair("Blue", "#1E88E5")
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Watermark", fontWeight = FontWeight.Bold) },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (selectedInput == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Button(onClick = { showSourcePicker = true }) {
                        Text("Choose Document or PDF to Watermark")
                    }
                }
            } else {
                // Watermark Text input
                OutlinedTextField(
                    value = watermarkText,
                    onValueChange = { watermarkText = it },
                    label = { Text("Watermark Text") },
                    placeholder = { Text("e.g. Confidential, DRAFT, أو نص عربي") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Preview Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        val previewColor = try {
                            val parsed = android.graphics.Color.parseColor(colorHex)
                            Color(parsed).copy(alpha = opacity / 100f)
                        } catch (e: Exception) {
                            Color.Gray.copy(alpha = 0.25f)
                        }

                        Text(
                            text = watermarkText.ifBlank { "Watermark Preview" },
                            fontSize = (sizePct * 3.5).sp,
                            fontWeight = FontWeight.Bold,
                            color = previewColor,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.rotate(-angle.toFloat())
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Size selector: S / M / L
                Text("Text Size:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    listOf(Pair("Small (4%)", 4), Pair("Medium (6%)", 6), Pair("Large (9%)", 9)).forEach { (label, sz) ->
                        FilterChip(
                            selected = sizePct == sz,
                            onClick = { sizePct = sz },
                            label = { Text(label) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Opacity Slider
                Text(
                    text = "Opacity: ${opacity.toInt()}%",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Slider(
                    value = opacity,
                    onValueChange = { opacity = it },
                    valueRange = 10f..60f,
                    steps = 10
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Angle selector: 0 / 30 / 45
                Text("Rotation Angle:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    listOf(0, 30, 45).forEach { ang ->
                        FilterChip(
                            selected = angle == ang,
                            onClick = { angle = ang },
                            label = { Text("$ang°") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Color picker
                Text("Color:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    colors.forEach { (name, hex) ->
                        val isSelected = colorHex == hex
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { colorHex = hex }
                                .padding(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(Color(android.graphics.Color.parseColor(hex)), CircleShape)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                                        shape = CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(name, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tiled toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isTiled = !isTiled }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Tiled Watermark", fontWeight = FontWeight.SemiBold)
                        Text("Repeat watermark across the entire page", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = isTiled, onCheckedChange = { isTiled = it })
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action buttons
                Button(
                    onClick = {
                        if (watermarkText.isBlank()) {
                            Toast.makeText(context, "Please enter watermark text", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isProcessing = true
                        scope.launch {
                            try {
                                val watermark = PageOverlay.Watermark(
                                    text = watermarkText,
                                    sizePct = sizePct,
                                    opacity = opacity.toInt(),
                                    angle = angle,
                                    color = colorHex,
                                    tiled = isTiled
                                )

                                when (val inp = selectedInput!!) {
                                    is ToolInput.AppDoc -> {
                                        withContext(Dispatchers.IO) {
                                            container.pdfToolsService.applyWatermark(inp, watermark)
                                        }
                                        Toast.makeText(context, "Watermark applied to all pages", Toast.LENGTH_SHORT).show()
                                        onNavigateToDoc(inp.docId)
                                    }
                                    is ToolInput.PdfFile -> {
                                        val stampedFile = withContext(Dispatchers.IO) {
                                            container.pdfToolsService.applyWatermark(inp, watermark)
                                        }
                                        val baseName = inp.displayName.removeSuffix(".pdf")
                                        val savedUri = withContext(Dispatchers.IO) {
                                            container.pdfToolsService.exportToToolsFolder(stampedFile, baseName, "watermarked")
                                        }
                                        stampedFile.delete()

                                        if (savedUri != null) {
                                            resultUri = savedUri
                                            resultFileName = "$baseName (watermarked).pdf"
                                        } else {
                                            Toast.makeText(context, "Failed to save file", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Watermark error: ${e.message}", Toast.LENGTH_LONG).show()
                            } finally {
                                isProcessing = false
                            }
                        }
                    },
                    enabled = watermarkText.isNotBlank() && !isProcessing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.height(24.dp))
                    } else {
                        Icon(Icons.Outlined.WaterDrop, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Apply Watermark")
                    }
                }

                // If working on an AppDoc, also allow "Remove watermark from all pages"
                if (selectedInput is ToolInput.AppDoc) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            val docId = (selectedInput as ToolInput.AppDoc).docId
                            isProcessing = true
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        val pages = container.database.pageDao().getByDocumentId(docId)
                                        for (p in pages) {
                                            val existing = OverlayCompositor.parseOverlays(p.overlays).toMutableList()
                                            existing.removeAll { it is PageOverlay.Watermark }
                                            val json = OverlayCompositor.serializeOverlays(existing)
                                            container.database.pageDao().update(p.copy(overlays = json))
                                            container.pageRenderer.renderPage(p.copy(overlays = json))
                                        }
                                        val doc = container.database.documentDao().getById(docId)!!
                                        val updatedPages = container.database.pageDao().getByDocumentId(docId)
                                        container.pdfBuilder.buildPdf(doc, updatedPages)
                                    }
                                    Toast.makeText(context, "Watermarks removed from all pages", Toast.LENGTH_SHORT).show()
                                    onNavigateToDoc(docId)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                } finally {
                                    isProcessing = false
                                }
                            }
                        },
                        enabled = !isProcessing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Remove Watermark from All Pages")
                    }
                }
            }
        }

        if (showSourcePicker) {
            ToolSourcePickerDialog(
                title = "Select Document or PDF for Watermark",
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
                infoText = "Watermark applied to every page",
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
