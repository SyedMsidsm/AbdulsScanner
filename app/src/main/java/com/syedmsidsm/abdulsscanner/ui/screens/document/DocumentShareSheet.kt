package com.syedmsidsm.abdulsscanner.ui.screens.document

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.ViewDay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.syedmsidsm.abdulsscanner.data.db.entity.DocumentEntity
import com.syedmsidsm.abdulsscanner.data.db.entity.PageEntity
import com.syedmsidsm.abdulsscanner.data.files.FileStore
import com.syedmsidsm.abdulsscanner.domain.files.MediaStoreExporter
import com.syedmsidsm.abdulsscanner.domain.imaging.LongImageStitcher
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentShareSheet(
    doc: DocumentEntity,
    pages: List<PageEntity>,
    fileStore: FileStore,
    onDismiss: () -> Unit,
    onSharePdf: () -> Unit,
    onShareJpg: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pdfSizeText by remember { mutableStateOf("Calculating...") }
    var jpgSizeText by remember { mutableStateOf("Calculating...") }
    var hasOcrText by remember { mutableStateOf(false) }

    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }

    LaunchedEffect(doc.id, pages.size) {
        withContext(Dispatchers.IO) {
            val pdfFile = fileStore.getExportPdfFile(doc.id)
            val pdfBytes = if (pdfFile.exists()) pdfFile.length() else 0L
            pdfSizeText = formatBytes(pdfBytes)

            val renderedFiles = pages.map { fileStore.getPageRenderedFile(doc.id, it.id) }
            val jpgBytes = renderedFiles.filter { it.exists() }.sumOf { it.length() }
            jpgSizeText = formatBytes(jpgBytes)

            hasOcrText = pages.any { !it.ocrText.isNullOrBlank() }
        }
    }

    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text("Share with password") },
            text = {
                Column {
                    Text(
                        "Set a password to lock this shared PDF copy. Your saved document in the app remains unchanged.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val pass = passwordInput
                        if (pass.isBlank()) return@TextButton
                        showPasswordDialog = false
                        scope.launch(Dispatchers.IO) {
                            try {
                                val exportPdf = fileStore.getExportPdfFile(doc.id)
                                if (!exportPdf.exists()) return@launch

                                val shareDir = fileStore.shareDir
                                val safeName = "${doc.name.replace(Regex("[/\\\\:*?\"<>|]"), "_")} (Locked).pdf"
                                val lockedShareFile = File(shareDir, safeName)

                                PDDocument.load(exportPdf).use { pdDoc ->
                                    val ap = AccessPermission()
                                    val spp = StandardProtectionPolicy(pass, pass, ap)
                                    spp.encryptionKeyLength = 256
                                    spp.permissions = ap
                                    pdDoc.protect(spp)
                                    pdDoc.save(lockedShareFile)
                                }

                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    lockedShareFile
                                )
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                withContext(Dispatchers.Main) {
                                    context.startActivity(Intent.createChooser(sendIntent, "Share Locked PDF"))
                                    onDismiss()
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Error protecting PDF: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    enabled = passwordInput.isNotBlank()
                ) {
                    Text("Share")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Share",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(14.dp))

            // 1. Share PDF
            ShareOptionRow(
                icon = Icons.Outlined.Description,
                title = "Share PDF",
                subtitle = "${doc.name}.pdf · $pdfSizeText",
                onClick = {
                    onDismiss()
                    onSharePdf()
                }
            )

            // 2. Share JPG
            ShareOptionRow(
                icon = Icons.Outlined.Image,
                title = "Share JPG",
                subtitle = "${pages.size} images · $jpgSizeText",
                onClick = {
                    onDismiss()
                    onShareJpg()
                }
            )

            // 3. Share as long image
            ShareOptionRow(
                icon = Icons.Outlined.ViewDay,
                title = "Share as long image",
                subtitle = "All pages joined top to bottom · 1 image",
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val renderedFiles = pages.map { fileStore.getPageRenderedFile(doc.id, it.id) }
                            val shareDir = fileStore.shareDir
                            val safeName = "${doc.name.replace(Regex("[/\\\\:*?\"<>|]"), "_")} (Long Image).jpg"
                            val longImageFile = File(shareDir, safeName)

                            val success = LongImageStitcher.createLongImage(renderedFiles, longImageFile)
                            if (success && longImageFile.exists()) {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    longImageFile
                                )
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/jpeg"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                withContext(Dispatchers.Main) {
                                    context.startActivity(Intent.createChooser(sendIntent, "Share Long Image"))
                                    onDismiss()
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Error creating long image: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            )

            // 4. Share text
            ShareOptionRow(
                icon = Icons.Outlined.TextFields,
                title = "Share text",
                subtitle = if (hasOcrText) "Text read from the pages" else "Text read from the pages (OCR not run yet)",
                enabled = hasOcrText,
                onClick = {
                    val fullText = pages.mapNotNull { it.ocrText }
                        .filter { it.isNotBlank() }
                        .joinToString("\n\n--- Page Break ---\n\n")
                    if (fullText.isNotBlank()) {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, fullText)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share text"))
                        onDismiss()
                    }
                }
            )

            // 5. Share with password…
            ShareOptionRow(
                icon = Icons.Outlined.Lock,
                title = "Share with password…",
                subtitle = "Locked PDF; this document stays as it is",
                onClick = {
                    showPasswordDialog = true
                }
            )

            // 6. Save to Gallery
            ShareOptionRow(
                icon = Icons.Outlined.PhotoAlbum,
                title = "Save to Gallery",
                subtitle = "Saves the pages as images in Pictures/Abul's Scanner",
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val count = MediaStoreExporter.exportJpgsToPictures(
                                context = context,
                                doc = doc,
                                pages = pages,
                                fileStore = fileStore
                            )
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    "Saved $count images to Pictures/Abul's Scanner",
                                    Toast.LENGTH_LONG
                                ).show()
                                onDismiss()
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Failed to save to gallery: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ShareOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1.0f else 0.40f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
            modifier = Modifier.size(26.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
            )
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    val df = DecimalFormat("#,##0.#")
    return "${df.format(bytes / Math.pow(1024.0, digitGroups.toDouble()))} ${units[digitGroups]}"
}
