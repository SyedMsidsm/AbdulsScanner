package com.syedmsidsm.abdulsscanner.ui.screens.tools

import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.syedmsidsm.abdulsscanner.domain.pdf.CompressionPreset
import com.syedmsidsm.abdulsscanner.domain.pdf.ToolInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolCompressScreen(
    container: AppContainer,
    onNavigateBack: () -> Unit,
    onNavigateToDoc: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showSourcePicker by remember { mutableStateOf(true) }
    var selectedInput by remember { mutableStateOf<ToolInput?>(null) }
    var selectedPreset by remember { mutableStateOf(CompressionPreset.MEDIUM) }
    var isProcessing by remember { mutableStateOf(false) }

    var resultUri by remember { mutableStateOf<Uri?>(null) }
    var resultFileName by remember { mutableStateOf("") }
    var resultInfoText by remember { mutableStateOf<String?>(null) }

    fun formatFileSize(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return String.format(Locale.US, "%.2f MB", mb)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compress PDF", fontWeight = FontWeight.Bold) },
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
                        Text("Choose Document or PDF to Compress")
                    }
                }
            } else {
                val inputName = when (val inp = selectedInput!!) {
                    is ToolInput.AppDoc -> "App Document"
                    is ToolInput.PdfFile -> inp.displayName
                }

                Text(
                    text = "Compressing: $inputName",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Select Compression Preset:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))

                val presets = listOf(
                    Triple(CompressionPreset.SMALL, "Small (High Compression)", "Max 1240 px, 50% quality • Best for email and messaging"),
                    Triple(CompressionPreset.MEDIUM, "Medium (Balanced)", "Max 1754 px, 65% quality • Good balance of quality and size"),
                    Triple(CompressionPreset.LARGE, "Large (High Quality)", "Max 2480 px, 80% quality • Retains high visual detail")
                )

                presets.forEach { (preset, title, desc) ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { selectedPreset = preset },
                        color = if (selectedPreset == preset)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedPreset == preset,
                                onClick = { selectedPreset = preset }
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(title, fontWeight = FontWeight.SemiBold)
                                Text(
                                    desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = {
                        isProcessing = true
                        scope.launch {
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    container.pdfToolsService.compressPdf(selectedInput!!, selectedPreset)
                                }

                                val baseName = container.pdfToolsService.getDocDisplayName(selectedInput!!)

                                if (!result.isSmaller) {
                                    Toast.makeText(context, "This PDF is already compact (${formatFileSize(result.beforeSizeBytes)})", Toast.LENGTH_LONG).show()
                                    result.outputFile.delete()
                                    onNavigateBack()
                                    return@launch
                                }

                                val savedUri = withContext(Dispatchers.IO) {
                                    container.pdfToolsService.exportToToolsFolder(
                                        result.outputFile,
                                        baseName,
                                        "compressed"
                                    )
                                }
                                result.outputFile.delete()

                                if (savedUri != null) {
                                    resultUri = savedUri
                                    resultFileName = "$baseName (compressed).pdf"
                                    resultInfoText = "${formatFileSize(result.beforeSizeBytes)} → ${formatFileSize(result.afterSizeBytes)}"
                                } else {
                                    Toast.makeText(context, "Failed to save compressed file", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Compression error: ${e.message}", Toast.LENGTH_LONG).show()
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
                        Icon(Icons.Outlined.Compress, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Compress Now")
                    }
                }
            }
        }

        if (showSourcePicker) {
            ToolSourcePickerDialog(
                title = "Select PDF to Compress",
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
