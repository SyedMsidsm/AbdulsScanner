package com.syedmsidsm.abdulsscanner.ui.screens.tools

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Image
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.syedmsidsm.abdulsscanner.di.AppContainer
import com.syedmsidsm.abdulsscanner.domain.pdf.ToolInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolPdfToJpgScreen(
    container: AppContainer,
    onNavigateBack: () -> Unit,
    onNavigateToDoc: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showSourcePicker by remember { mutableStateOf(true) }
    var selectedInput by remember { mutableStateOf<ToolInput?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var exportedCount by remember { mutableIntStateOf(0) }
    var targetFolderName by remember { mutableStateOf("") }
    var isCompleted by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PDF to JPG Images", fontWeight = FontWeight.Bold) },
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
                        Text("Choose Document or PDF to Convert")
                    }
                }
            } else if (isCompleted) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Converted $exportedCount Pages to JPG",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Saved in your gallery under:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Pictures/Abdul's Scanner/$targetFolderName/",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onNavigateBack,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Done")
                    }
                }
            } else {
                val inputName = when (val inp = selectedInput!!) {
                    is ToolInput.AppDoc -> "App Document"
                    is ToolInput.PdfFile -> inp.displayName
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Selected:", style = MaterialTheme.typography.labelMedium)
                        Text(inputName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Every page will be converted into high-resolution JPG images (200 DPI) and saved to your device's Pictures gallery.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = {
                        isProcessing = true
                        scope.launch {
                            try {
                                val name = container.pdfToolsService.getDocDisplayName(selectedInput!!)
                                targetFolderName = name
                                val count = withContext(Dispatchers.IO) {
                                    container.pdfToolsService.pdfToJpg(selectedInput!!)
                                }
                                exportedCount = count
                                isCompleted = true
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error converting: ${e.message}", Toast.LENGTH_LONG).show()
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
                        Icon(Icons.Outlined.Image, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Convert to JPG")
                    }
                }
            }
        }

        if (showSourcePicker) {
            ToolSourcePickerDialog(
                title = "Select Document or PDF for JPG",
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
    }
}
