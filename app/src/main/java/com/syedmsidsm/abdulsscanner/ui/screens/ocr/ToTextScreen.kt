package com.syedmsidsm.abdulsscanner.ui.screens.ocr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syedmsidsm.abdulsscanner.R
import com.syedmsidsm.abdulsscanner.data.db.entity.OcrLanguage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToTextScreen(
    viewModel: ToTextViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()

    var showLangDropdown by remember { mutableStateOf(false) }

    fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Recognized Text", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
    }

    fun shareText(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Share text"))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.document?.name ?: "To Text",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Language selector dropdown
                    Box {
                        TextButton(onClick = { showLangDropdown = true }) {
                            Text(uiState.selectedLanguage.name)
                        }
                        DropdownMenu(
                            expanded = showLangDropdown,
                            onDismissRequest = { showLangDropdown = false }
                        ) {
                            OcrLanguage.values().forEach { lang ->
                                DropdownMenuItem(
                                    text = { Text(lang.name) },
                                    onClick = {
                                        showLangDropdown = false
                                        viewModel.setLanguage(lang)
                                    }
                                )
                            }
                        }
                    }

                    // Re-read icon
                    IconButton(
                        onClick = { viewModel.runOcr() },
                        enabled = !uiState.isRunning
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Re-read")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Copy
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable { copyToClipboard(uiState.fullText) }
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Outlined.ContentCopy, null, tint = MaterialTheme.colorScheme.primary)
                        Text("Copy", fontSize = 12.sp)
                    }

                    // Share
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable { shareText(uiState.fullText) }
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Outlined.Share, null, tint = MaterialTheme.colorScheme.primary)
                        Text("Share", fontSize = 12.sp)
                    }

                    // Save to Doc
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable {
                                viewModel.saveToDocument()
                                Toast.makeText(context, "Saved to document", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (uiState.isSavedSuccess) Icons.Outlined.Check else Icons.Outlined.Save,
                            contentDescription = null,
                            tint = if (uiState.isSavedSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Text(if (uiState.isSavedSuccess) "Saved" else "Save to doc", fontSize = 12.sp)
                    }

                    // Save as TXT
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable {
                                scope.launch {
                                    val uri = viewModel.exportTxtFile(context)
                                    if (uri != null) {
                                        Toast.makeText(context, "Saved to Documents/Abdul's Scanner", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "Failed to save TXT", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Outlined.FileDownload, null, tint = MaterialTheme.colorScheme.primary)
                        Text("Save as TXT", fontSize = 12.sp)
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Running progress banner
            if (uiState.isRunning && uiState.progress != null) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Reading page ${uiState.progress?.first} of ${uiState.progress?.second}...",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            TextButton(onClick = { viewModel.cancelOcr() }) {
                                Text(stringResource(R.string.action_cancel))
                            }
                        }
                        val fraction = (uiState.progress?.first?.toFloat() ?: 0f) / (uiState.progress?.second?.toFloat() ?: 1f)
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth().height(4.dp)
                        )
                    }
                }
            }

            // Editable text view
            OutlinedTextField(
                value = uiState.fullText,
                onValueChange = { viewModel.onTextChanged(it) },
                placeholder = {
                    Text(if (uiState.isRunning) "Recognizing text..." else "No text recognized yet. Tap 'Re-read' above.")
                },
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    textDirection = TextDirection.Content
                )
            )
        }
    }
}
