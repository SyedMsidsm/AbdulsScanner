package com.syedmsidsm.abdulsscanner.ui.screens.settings

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToBackup: () -> Unit,
    onNavigateToTrash: () -> Unit,
    onNavigateToLabs: () -> Unit,
    onNavigateToSecurity: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    var showFilterDropdown by remember { mutableStateOf(false) }
    var showPageSizeDropdown by remember { mutableStateOf(false) }
    var showScannerDropdown by remember { mutableStateOf(false) }
    var showFlashDropdown by remember { mutableStateOf(false) }
    var showBookDirDropdown by remember { mutableStateOf(false) }
    var showThemeDropdown by remember { mutableStateOf(false) }
    var showOcrLangDropdown by remember { mutableStateOf(false) }
    var showReReadConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Backup & Safety
            SettingsSectionHeader(title = "Backup & Safety")
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    SettingsRow(
                        icon = Icons.Outlined.Backup,
                        title = "Backup & Restore",
                        subtitle = "Manual archive, automatic backup, restore",
                        onClick = onNavigateToBackup
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsRow(
                        icon = Icons.Outlined.Delete,
                        title = "Recycle Bin",
                        subtitle = "Deleted documents are kept for 30 days",
                        onClick = onNavigateToTrash
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsRow(
                        icon = Icons.Outlined.Lock,
                        title = "Security & Privacy",
                        subtitle = "App PIN, fingerprint unlock",
                        onClick = onNavigateToSecurity
                    )
                }
            }

            // Section 2: Storage
            SettingsSectionHeader(title = "Storage")
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Storage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "App Storage",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            val mb = String.format(Locale.ENGLISH, "%.1f MB", uiState.appStorageBytes / (1024.0 * 1024.0))
                            Text(
                                text = "${uiState.docCount} docs · ${uiState.pageCount} pages · $mb used",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    val freeGb = String.format(Locale.ENGLISH, "%.1f GB", uiState.freeDeviceBytes / (1024.0 * 1024.0 * 1024.0))
                    Text(
                        text = "Device free space: $freeGb available",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { viewModel.clearTempCache() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CleaningServices,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (uiState.tempCacheCleared) "Cache Cleared" else "Clear Temporary Cache")
                    }
                }
            }

            // Section 3: Scanning
            SettingsSectionHeader(title = "Scanning")
            val isBuiltIn = uiState.cameraEngine == "BUILT_IN"
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // 1. Scanner engine
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Scanner", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (isBuiltIn) "Built-in camera (CameraX + OpenCV)" else "Google scanner (ML Kit)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box {
                            TextButton(onClick = { showScannerDropdown = true }) {
                                Text(if (isBuiltIn) "Built-in ▾" else "Google ▾")
                            }
                            DropdownMenu(
                                expanded = showScannerDropdown,
                                onDismissRequest = { showScannerDropdown = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Built-in camera") },
                                    onClick = {
                                        viewModel.setCameraEngine("BUILT_IN")
                                        showScannerDropdown = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Google scanner") },
                                    onClick = {
                                        viewModel.setCameraEngine("GOOGLE_SCANNER")
                                        showScannerDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // 2. Default filter
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val filterLabel = when (uiState.defaultFilter) {
                            "NO_SHADOW" -> "No shadow (BETA)"
                            "ORIGINAL" -> "Original"
                            "LIGHTEN" -> "Lighten"
                            "ENHANCED" -> "Magic Color"
                            "GRAYSCALE" -> "Grayscale"
                            "BW" -> "B&W"
                            "ECO" -> "Eco"
                            else -> uiState.defaultFilter
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Default filter", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Applied to new scans by default",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box {
                            TextButton(onClick = { showFilterDropdown = true }) {
                                Text("$filterLabel ▾")
                            }
                            DropdownMenu(
                                expanded = showFilterDropdown,
                                onDismissRequest = { showFilterDropdown = false }
                            ) {
                                listOf(
                                    "NO_SHADOW" to "No shadow (BETA)",
                                    "ORIGINAL" to "Original",
                                    "LIGHTEN" to "Lighten",
                                    "ENHANCED" to "Magic Color",
                                    "GRAYSCALE" to "Grayscale",
                                    "BW" to "B&W",
                                    "ECO" to "Eco"
                                ).forEach { (key, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            viewModel.setDefaultFilter(key)
                                            showFilterDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // 3. Photo size
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Photo size", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (!isBuiltIn) "Built-in camera only" else (uiState.cameraPhotoSize ?: "Largest (4:3)"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // 4. Flash
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val flashLabel = when (uiState.cameraFlash) {
                            "AUTO" -> "Flash Auto"
                            "ON" -> "Flash On"
                            "OFF" -> "Flash Off"
                            "TORCH" -> "Torch"
                            else -> uiState.cameraFlash
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Flash", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (!isBuiltIn) "Built-in camera only" else flashLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isBuiltIn) {
                            Box {
                                TextButton(onClick = { showFlashDropdown = true }) {
                                    Text("$flashLabel ▾")
                                }
                                DropdownMenu(
                                    expanded = showFlashDropdown,
                                    onDismissRequest = { showFlashDropdown = false }
                                ) {
                                    listOf(
                                        "AUTO" to "Flash Auto",
                                        "ON" to "Flash On",
                                        "OFF" to "Flash Off",
                                        "TORCH" to "Torch"
                                    ).forEach { (mode, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                viewModel.setCameraFlash(mode)
                                                showFlashDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // 5. Grid lines
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Grid lines", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (!isBuiltIn) "Built-in camera only" else "3x3 alignment grid over preview",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.cameraGrid,
                            enabled = isBuiltIn,
                            onCheckedChange = { viewModel.setCameraGrid(it) }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // 6. Auto shutter
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto shutter", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (!isBuiltIn) "Built-in camera only" else "Capture automatically when page is steady",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.autoShutter,
                            enabled = isBuiltIn,
                            onCheckedChange = { viewModel.setAutoShutter(it) }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // 7. Auto crop images
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto crop images", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (!isBuiltIn) "Built-in camera only" else "Crop to detected page outline",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.autoCrop,
                            enabled = isBuiltIn,
                            onCheckedChange = { viewModel.setAutoCrop(it) }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // 8. Crop images after each scan
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Crop images after each scan", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (!isBuiltIn) "Built-in camera only" else "Open crop editor immediately after each capture",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.cropAfterEachScan,
                            enabled = isBuiltIn,
                            onCheckedChange = { viewModel.setCropAfterEachScan(it) }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // 9. Mute mode
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Mute mode", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (!isBuiltIn) "Built-in camera only" else "Mute shutter sound on capture",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.muteShutter,
                            enabled = isBuiltIn,
                            onCheckedChange = { viewModel.setMuteShutter(it) }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // 10. Book page order
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Book page order", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (uiState.bookDirection == "LTR") "Left-to-Right (English/Latin)" else "Right-to-Left (Arabic/Hebrew)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box {
                            TextButton(onClick = { showBookDirDropdown = true }) {
                                Text(if (uiState.bookDirection == "LTR") "LTR ▾" else "RTL ▾")
                            }
                            DropdownMenu(
                                expanded = showBookDirDropdown,
                                onDismissRequest = { showBookDirDropdown = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Left-to-Right (LTR)") },
                                    onClick = {
                                        viewModel.setBookDirection("LTR")
                                        showBookDirDropdown = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Right-to-Left (RTL)") },
                                    onClick = {
                                        viewModel.setBookDirection("RTL")
                                        showBookDirDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // 11. HD scans (Google scanner and imports)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("HD scans", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Google scanner and imports",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.hdMode,
                            onCheckedChange = { viewModel.setHdMode(it) }
                        )
                    }
                }
            }

            // Section 4: PDF & Mirroring
            SettingsSectionHeader(title = "PDF & Mirroring")
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Page size
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Page size", style = MaterialTheme.typography.bodyLarge)
                        Box {
                            TextButton(onClick = { showPageSizeDropdown = true }) {
                                Text("${uiState.pageSize} ▾")
                            }
                            DropdownMenu(
                                expanded = showPageSizeDropdown,
                                onDismissRequest = { showPageSizeDropdown = false }
                            ) {
                                listOf("AUTO", "A4", "LETTER").forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text(s) },
                                        onClick = {
                                            viewModel.setPageSize(s)
                                            showPageSizeDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Mirror to Documents folder", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Keeps copies in Documents/Abdul's Scanner/ for easy access via file manager or PC USB",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.mirrorEnabled,
                            onCheckedChange = { viewModel.setMirrorEnabled(it) }
                        )
                    }
                }
            }

            // Section: Text Reading & Search
            SettingsSectionHeader(title = "Text Reading")
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Language for search indexing", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Choose language for background search reading",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box {
                            TextButton(onClick = { showOcrLangDropdown = true }) {
                                val label = when (uiState.ocrSearchLanguage) {
                                    "EN" -> "English"
                                    "AR" -> "Arabic"
                                    else -> "Both"
                                }
                                Text("$label ▾")
                            }
                            DropdownMenu(
                                expanded = showOcrLangDropdown,
                                onDismissRequest = { showOcrLangDropdown = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Both (English + Arabic)") },
                                    onClick = {
                                        viewModel.setOcrSearchLanguage("BOTH")
                                        showOcrLangDropdown = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("English") },
                                    onClick = {
                                        viewModel.setOcrSearchLanguage("EN")
                                        showOcrLangDropdown = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Arabic") },
                                    onClick = {
                                        viewModel.setOcrSearchLanguage("AR")
                                        showOcrLangDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    OutlinedButton(
                        onClick = { showReReadConfirmDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (uiState.reindexQueued) "Re-reading queued" else "Re-read all pages")
                    }
                }
            }

            // Section: QR Code
            SettingsSectionHeader(title = "QR Code")
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Save scan history", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Record scanned QR codes in history list",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.qrSaveHistory,
                        onCheckedChange = { viewModel.setQrSaveHistory(it) }
                    )
                }
            }

            // Section 5: Appearance
            SettingsSectionHeader(title = "Appearance")
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Theme", style = MaterialTheme.typography.bodyLarge)
                    Box {
                        TextButton(onClick = { showThemeDropdown = true }) {
                            Text("${uiState.theme} ▾")
                        }
                        DropdownMenu(
                            expanded = showThemeDropdown,
                            onDismissRequest = { showThemeDropdown = false }
                        ) {
                            listOf("DARK", "LIGHT", "SYSTEM").forEach { th ->
                                DropdownMenuItem(
                                    text = { Text(th) },
                                    onClick = {
                                        viewModel.setTheme(th)
                                        showThemeDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Section 6: Labs
            SettingsSectionHeader(title = "Labs")
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                SettingsRow(
                    icon = Icons.Outlined.Science,
                    title = "Arabic OCR comparison",
                    subtitle = "Benchmark ML Kit, Tesseract, and PaddleOCR",
                    onClick = onNavigateToLabs
                )
            }

            // Section 7: About
            SettingsSectionHeader(title = "About")
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Abdul's Scanner",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Version 1.0.0 · Offline, ad-free personal document scanner",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        if (showReReadConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showReReadConfirmDialog = false },
                title = { Text("Re-read all pages") },
                text = {
                    Text("This will re-read ${uiState.pageCount} pages in the background. It will pause while your phone is in use and only run when the battery is not low.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.reReadPages()
                            showReReadConfirmDialog = false
                        }
                    ) {
                        Text("Re-read")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showReReadConfirmDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
