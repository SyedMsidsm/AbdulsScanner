package com.syedmsidsm.abdulsscanner.ui.screens.home

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallMerge
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.common.api.ApiException
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.syedmsidsm.abdulsscanner.R
import com.syedmsidsm.abdulsscanner.data.db.entity.DocumentEntity
import com.syedmsidsm.abdulsscanner.data.settings.SortOrder
import com.syedmsidsm.abdulsscanner.data.settings.ViewMode
import com.syedmsidsm.abdulsscanner.ui.components.DocCard
import com.syedmsidsm.abdulsscanner.ui.nav.AppBottomBar
import com.syedmsidsm.abdulsscanner.ui.nav.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    isSessionUnlocked: Boolean = false,
    onNavigateToDocument: (String) -> Unit,
    onNavigateToLock: (String) -> Unit = {},
    onNavigateToTools: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToBackup: () -> Unit = {},
    onNavigateToTags: () -> Unit,
    onSharePdf: (DocumentEntity) -> Unit,
    onShareMultiplePdfs: (List<DocumentEntity>) -> Unit = {},
    onNavigateToCamera: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val uiState by viewModel.uiState.collectAsState()
    val cameraEngine by viewModel.cameraEngine.collectAsStateWithLifecycle(initialValue = "BUILT_IN")

    var docToRename by remember { mutableStateOf<DocumentEntity?>(null) }
    var renameText by remember { mutableStateOf("") }
    var showTagDropdown by remember { mutableStateOf(false) }
    var showSortDropdown by remember { mutableStateOf(false) }
    var showSelectTagSheet by remember { mutableStateOf(false) }
    var chosenTagIdsForSelection by remember { mutableStateOf(setOf<Long>()) }
    var scannerErrorMsg by remember { mutableStateOf<String?>(null) }

    // Unfinished Session Recovery (Section 1.1 & fix-05-recover-session.png)
    var unfinishedSession by remember { mutableStateOf<com.syedmsidsm.abdulsscanner.domain.scan.ScanSession?>(null) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val sessions = com.syedmsidsm.abdulsscanner.domain.scan.ScanSession.findUnfinishedSessions(context)
            if (sessions.isNotEmpty()) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    unfinishedSession = sessions.first()
                }
            }
        }
    }

    if (unfinishedSession != null) {
        val sess = unfinishedSession!!
        AlertDialog(
            onDismissRequest = { unfinishedSession = null },
            title = { Text("Unfinished scan found", fontWeight = FontWeight.Bold) },
            text = {
                Text("You have ${sess.pages.size} scanned ${if (sess.pages.size == 1) "page" else "pages"} that weren't saved because the app was closed.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        unfinishedSession = null
                        onNavigateToCamera("DOCS")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Continue scanning")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        com.syedmsidsm.abdulsscanner.domain.scan.ScanSession.deleteSession(context, sess.sessionId)
                        unfinishedSession = null
                    }
                ) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }

    // Scanner Activity Result Launcher
    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val uris = scanResult?.pages?.map { it.imageUri }.orEmpty()
            if (uris.isNotEmpty()) {
                viewModel.onScanned(uris) { newDocId ->
                    onNavigateToDocument(newDocId)
                }
            }
        }
    }

    fun launchScanner() {
        val options = GmsDocumentScannerOptions.Builder()
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .setGalleryImportAllowed(true)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .build()

        val scanner = GmsDocumentScanning.getClient(options)
        val activity = context as? Activity ?: return

        scanner.getStartScanIntent(activity)
            .addOnSuccessListener { intentSender ->
                scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { e ->
                scannerErrorMsg = context.getString(R.string.scanner_unavailable)
            }
    }

    fun startScan() {
        if (cameraEngine == "GOOGLE_SCANNER") {
            launchScanner()
        } else {
            onNavigateToCamera("DOCS")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (uiState.isSelectMode) {
                TopAppBar(
                    title = {
                        Text(
                            text = "${uiState.selectedDocIds.size} selected",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.toggleSelectMode() }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Exit select mode")
                        }
                    },
                    actions = {
                        val allSelected = uiState.selectedDocIds.size == uiState.documents.size && uiState.documents.isNotEmpty()
                        TextButton(
                            onClick = {
                                if (allSelected) viewModel.clearSelection() else viewModel.selectAll()
                            }
                        ) {
                            Text(if (allSelected) "Deselect all" else "Select all")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        if (uiState.isSearchActive) {
                            OutlinedTextField(
                                value = uiState.searchQuery,
                                onValueChange = { viewModel.onSearchQueryChanged(it) },
                                placeholder = { Text(stringResource(R.string.action_search)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.app_name),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    navigationIcon = {
                        if (uiState.isSearchActive) {
                            IconButton(onClick = { viewModel.setSearchActive(false) }) {
                                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_cancel))
                            }
                        } else {
                            IconButton(onClick = onNavigateToSettings) {
                                Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.action_settings))
                            }
                        }
                    },
                    actions = {
                        if (!uiState.isSearchActive) {
                            IconButton(onClick = { viewModel.setSearchActive(true) }) {
                                Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.action_search))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        },
        bottomBar = {
            if (uiState.isSelectMode) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val hasSelection = uiState.selectedDocIds.isNotEmpty()
                        val canMerge = uiState.selectedDocIds.size >= 2

                        // Tag
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .clickable(enabled = hasSelection) {
                                    chosenTagIdsForSelection = emptySet()
                                    showSelectTagSheet = true
                                }
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Label,
                                contentDescription = "Tag",
                                tint = if (hasSelection) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            )
                            Text(
                                "Tag",
                                fontSize = 12.sp,
                                color = if (hasSelection) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            )
                        }

                        // Share
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .clickable(enabled = hasSelection) {
                                    val selectedDocs = uiState.documents.filter { it.id in uiState.selectedDocIds }
                                    if (selectedDocs.size == 1) {
                                        onSharePdf(selectedDocs.first())
                                    } else if (selectedDocs.size > 1) {
                                        onShareMultiplePdfs(selectedDocs)
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Share,
                                contentDescription = "Share",
                                tint = if (hasSelection) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            )
                            Text(
                                "Share",
                                fontSize = 12.sp,
                                color = if (hasSelection) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            )
                        }

                        // Merge
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .clickable(enabled = canMerge) {
                                    viewModel.mergeSelected { newDocId ->
                                        onNavigateToDocument(newDocId)
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Outlined.CallMerge,
                                contentDescription = "Merge",
                                tint = if (canMerge) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            )
                            Text(
                                "Merge",
                                fontSize = 12.sp,
                                color = if (canMerge) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            )
                        }

                        // Lock
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .clickable(enabled = hasSelection) { viewModel.toggleLockSelected() }
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Lock,
                                contentDescription = "Lock",
                                tint = if (hasSelection) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            )
                            Text(
                                "Lock",
                                fontSize = 12.sp,
                                color = if (hasSelection) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            )
                        }

                        // Delete
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .clickable(enabled = hasSelection) {
                                    viewModel.moveSelectedToTrash()
                                }
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "Delete",
                                tint = if (hasSelection) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            )
                            Text(
                                "Delete",
                                fontSize = 12.sp,
                                color = if (hasSelection) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            )
                        }
                    }
                }
            } else {
                AppBottomBar(
                    currentRoute = Routes.HOME,
                    onNavigateToDocs = {},
                    onNavigateToTools = onNavigateToTools,
                    onCameraClick = { startScan() }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Banner area (Section 10.1 & 12.5)
                uiState.banner?.let { banner ->
                    androidx.compose.material3.Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Outlined.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = banner.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(
                                onClick = {
                                    when (banner.type) {
                                        BannerType.BACKUP_NEEDED, BannerType.AUTO_BACKUP_FAILED -> {
                                            onNavigateToBackup()
                                        }
                                        BannerType.OFF_PHONE_REMINDER -> {
                                            viewModel.dismissOffPhoneReminder()
                                        }
                                    }
                                }
                            ) {
                                Text(
                                    text = banner.actionText,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // Filter and sort bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tag selector dropdown button
                    Box {
                        val currentLabel = when {
                            uiState.isUngroupedSelected -> "${stringResource(R.string.ungrouped)} (${uiState.ungroupedCount})"
                            uiState.selectedTagId != null -> {
                                val tag = uiState.tagsWithCounts.find { it.id == uiState.selectedTagId }
                                "${tag?.name ?: ""} (${tag?.docCount ?: 0})"
                            }
                            else -> "${stringResource(R.string.all_docs)} (${uiState.documents.size})"
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .clickable { showTagDropdown = true }
                                .padding(vertical = 4.dp, horizontal = 8.dp)
                        ) {
                            Text(
                                text = currentLabel,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(
                                imageVector = Icons.Outlined.ArrowDropDown,
                                contentDescription = null
                            )
                        }

                        DropdownMenu(
                            expanded = showTagDropdown,
                            onDismissRequest = { showTagDropdown = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("${stringResource(R.string.all_docs)} (${uiState.documents.size})") },
                                onClick = {
                                    viewModel.selectAllDocs()
                                    showTagDropdown = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("${stringResource(R.string.ungrouped)} (${uiState.ungroupedCount})") },
                                onClick = {
                                    viewModel.selectUngrouped()
                                    showTagDropdown = false
                                }
                            )
                            uiState.tagsWithCounts.forEach { tagWithCount ->
                                DropdownMenuItem(
                                    text = { Text("${tagWithCount.name} (${tagWithCount.docCount})") },
                                    onClick = {
                                        viewModel.selectTag(tagWithCount.id)
                                        showTagDropdown = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.manage_tags), color = MaterialTheme.colorScheme.primary) },
                                onClick = {
                                    showTagDropdown = false
                                    onNavigateToTags()
                                }
                            )
                        }
                    }

                    // Sort, List/Grid, Select mode icons
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            IconButton(onClick = { showSortDropdown = true }) {
                                Icon(Icons.Outlined.Sort, contentDescription = stringResource(R.string.sort_last_modified))
                            }
                            DropdownMenu(
                                expanded = showSortDropdown,
                                onDismissRequest = { showSortDropdown = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.sort_last_modified)) },
                                    onClick = {
                                        viewModel.setSortOrder(SortOrder.UPDATED)
                                        showSortDropdown = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.sort_date_created)) },
                                    onClick = {
                                        viewModel.setSortOrder(SortOrder.CREATED)
                                        showSortDropdown = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.sort_name)) },
                                    onClick = {
                                        viewModel.setSortOrder(SortOrder.NAME)
                                        showSortDropdown = false
                                    }
                                )
                            }
                        }

                        IconButton(onClick = { viewModel.toggleViewMode() }) {
                            Icon(
                                imageVector = if (uiState.viewMode == ViewMode.LIST) Icons.Outlined.GridView else Icons.Outlined.ViewList,
                                contentDescription = null
                            )
                        }

                        IconButton(onClick = { viewModel.toggleSelectMode() }) {
                            Icon(
                                imageVector = Icons.Outlined.Checklist,
                                contentDescription = stringResource(R.string.action_select),
                                tint = if (uiState.isSelectMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Documents List / Grid / Empty State
                if (uiState.documents.isEmpty()) {
                    // Empty state (Section 10.1: page illustration, "You don't have any documents yet", button)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 72.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            if (uiState.isSearchActive && uiState.searchQuery.isNotBlank()) {
                                Text(
                                    text = "No matching documents",
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No results found for \"${uiState.searchQuery}\"",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.home_empty_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                OutlinedButton(
                                    onClick = { startScan() },
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(stringResource(R.string.home_empty_scan_button))
                                }
                            }
                        }
                    }
                } else {
                    if (uiState.viewMode == ViewMode.LIST) {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(uiState.documents, key = { it.id }) { doc ->
                                DocCard(
                                    document = doc,
                                    isGrid = false,
                                    isSelectMode = uiState.isSelectMode,
                                    isSelected = uiState.selectedDocIds.contains(doc.id),
                                    snippet = uiState.searchSnippets[doc.id],
                                    onClick = {
                                        if (uiState.isSelectMode) {
                                            viewModel.toggleDocSelection(doc.id)
                                        } else {
                                            if (doc.locked && !isSessionUnlocked) {
                                                onNavigateToLock(doc.id)
                                            } else {
                                                onNavigateToDocument(doc.id)
                                            }
                                        }
                                    },
                                    onLongClick = {
                                        if (!uiState.isSelectMode) {
                                            viewModel.toggleSelectMode()
                                            viewModel.toggleDocSelection(doc.id)
                                        }
                                    },
                                    onRename = {
                                        docToRename = doc
                                        renameText = doc.name
                                    },
                                    onSharePdf = { onSharePdf(doc) },
                                    onMoveToTrash = { viewModel.moveToTrash(doc.id) }
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(uiState.documents, key = { it.id }) { doc ->
                                DocCard(
                                    document = doc,
                                    isGrid = true,
                                    isSelectMode = uiState.isSelectMode,
                                    isSelected = uiState.selectedDocIds.contains(doc.id),
                                    snippet = uiState.searchSnippets[doc.id],
                                    onClick = {
                                        if (uiState.isSelectMode) {
                                            viewModel.toggleDocSelection(doc.id)
                                        } else {
                                            if (doc.locked && !isSessionUnlocked) {
                                                onNavigateToLock(doc.id)
                                            } else {
                                                onNavigateToDocument(doc.id)
                                            }
                                        }
                                    },
                                    onLongClick = {
                                        if (!uiState.isSelectMode) {
                                            viewModel.toggleSelectMode()
                                            viewModel.toggleDocSelection(doc.id)
                                        }
                                    },
                                    onRename = {
                                        docToRename = doc
                                        renameText = doc.name
                                    },
                                    onSharePdf = { onSharePdf(doc) },
                                    onMoveToTrash = { viewModel.moveToTrash(doc.id) }
                                )
                            }
                        }
                    }
                }
            }

            // Saving progress overlay
            if (uiState.isSavingScan) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }

    // Rename Dialog
    if (docToRename != null) {
        AlertDialog(
            onDismissRequest = { docToRename = null },
            title = { Text(stringResource(R.string.action_rename)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val doc = docToRename
                        if (doc != null && renameText.isNotBlank()) {
                            viewModel.renameDocument(doc.id, renameText)
                        }
                        docToRename = null
                    }
                ) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { docToRename = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // Scanner error dialog
    if (scannerErrorMsg != null) {
        AlertDialog(
            onDismissRequest = { scannerErrorMsg = null },
            title = { Text(stringResource(R.string.app_name)) },
            text = { Text(scannerErrorMsg!!) },
            confirmButton = {
                TextButton(onClick = {
                    scannerErrorMsg = null
                    launchScanner()
                }) {
                    Text(stringResource(R.string.action_retry))
                }
            },
            dismissButton = {
                TextButton(onClick = { scannerErrorMsg = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // Apply Tags Modal Bottom Sheet
    if (showSelectTagSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSelectTagSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "Apply Tags",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (uiState.tagsWithCounts.isEmpty()) {
                    Text(
                        "No tags yet. Go to Manage Tags to create some.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    uiState.tagsWithCounts.forEach { tag ->
                        val isChecked = chosenTagIdsForSelection.contains(tag.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable {
                                    chosenTagIdsForSelection = if (isChecked) {
                                        chosenTagIdsForSelection - tag.id
                                    } else {
                                        chosenTagIdsForSelection + tag.id
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    chosenTagIdsForSelection = if (checked) {
                                        chosenTagIdsForSelection + tag.id
                                    } else {
                                        chosenTagIdsForSelection - tag.id
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(tag.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showSelectTagSheet = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            viewModel.applyTagsToSelected(chosenTagIdsForSelection)
                            showSelectTagSheet = false
                        }
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
