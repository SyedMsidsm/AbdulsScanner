package com.syedmsidsm.abdulsscanner.ui.screens.document

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.WaterDrop
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.syedmsidsm.abdulsscanner.R
import com.syedmsidsm.abdulsscanner.data.db.entity.DocumentEntity
import com.syedmsidsm.abdulsscanner.data.db.entity.PageEntity
import com.syedmsidsm.abdulsscanner.data.files.FileStore
import com.syedmsidsm.abdulsscanner.data.settings.SettingsRepository
import com.syedmsidsm.abdulsscanner.domain.files.MediaStoreExporter
import com.syedmsidsm.abdulsscanner.domain.imaging.BitmapIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScreen(
    viewModel: DocumentViewModel,
    fileStore: FileStore,
    settingsRepository: SettingsRepository,
    onNavigateBack: () -> Unit,
    onNavigateToPage: (String, String) -> Unit,
    onNavigateToOcr: (String) -> Unit,
    onNavigateToSecurity: () -> Unit = {},
    onNavigateToBookSplit: (String, String) -> Unit = { _, _ -> },
    onNavigateToWatermark: ((String) -> Unit)? = null,
    onNavigateToSign: ((String) -> Unit)? = null,
    onNavigateToCamera: ((String) -> Unit)? = null,
    onNavigateToCollage: (() -> Unit)? = null,
    onNavigateToPassword: (() -> Unit)? = null,
    onNavigateToCompress: (() -> Unit)? = null,
    onSharePdf: (DocumentEntity) -> Unit,
    onShareJpg: (DocumentEntity) -> Unit,
    onPrint: (DocumentEntity) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val uiState by viewModel.uiState.collectAsState()
    val cameraEngine by settingsRepository.cameraEngine.collectAsStateWithLifecycle(initialValue = "BUILT_IN")
    val doc = uiState.document

    // Page-select mode states (FIXES-2 §6)
    var isSelectMode by remember { mutableStateOf(false) }
    var selectedPageIds by remember { mutableStateOf(setOf<String>()) }
    var showSelectMoreMenu by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var moveNewDocName by remember { mutableStateOf("") }
    var showCreateNewDocForMove by remember { mutableStateOf(false) }
    val otherDocuments by viewModel.getOtherDocuments().collectAsState(initial = emptyList())

    var isReordering by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var showTopOverflow by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showMoreSheet by remember { mutableStateOf(false) }
    var showTagSheet by remember { mutableStateOf(false) }
    var showNewTagDialog by remember { mutableStateOf(false) }
    var newTagName by remember { mutableStateOf("") }
    var showPageSizeDialog by remember { mutableStateOf(false) }
    var showNoPinDialog by remember { mutableStateOf(false) }
    var showPageSelectForBookSplit by remember { mutableStateOf(false) }
    val allTags by viewModel.allTags.collectAsState(initial = emptyList())

    BackHandler(enabled = isSelectMode) {
        isSelectMode = false
        selectedPageIds = emptySet()
    }

    // Add pages scanner launcher
    val addScannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val uris = scanResult?.pages?.map { it.imageUri }.orEmpty()
            if (uris.isNotEmpty()) {
                viewModel.addScannedPages(uris)
            }
        }
    }

    // Photo picker for import from gallery
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addScannedPages(uris)
        }
    }

    fun launchAddScanner() {
        val options = GmsDocumentScannerOptions.Builder()
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .setGalleryImportAllowed(true)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .build()
        val scanner = GmsDocumentScanning.getClient(options)
        val activity = context as? Activity ?: return

        scanner.getStartScanIntent(activity)
            .addOnSuccessListener { intentSender ->
                addScannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
    }

    if (doc == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (isReordering) {
                TopAppBar(
                    title = { Text("Reorder Pages", fontWeight = FontWeight.Bold) },
                    actions = {
                        TextButton(onClick = { isReordering = false }) {
                            Text("Done", fontWeight = FontWeight.Bold)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            } else if (isSelectMode) {
                // Top bar in page-select mode per FIXES-2 §6
                TopAppBar(
                    title = {
                        Text(
                            text = "${selectedPageIds.size} selected",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSelectMode = false
                            selectedPageIds = emptySet()
                        }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Exit selection")
                        }
                    },
                    actions = {
                        val allSelected = (selectedPageIds.size == uiState.pages.size && uiState.pages.isNotEmpty())
                        TextButton(
                            onClick = {
                                selectedPageIds = if (allSelected) {
                                    emptySet()
                                } else {
                                    uiState.pages.map { it.id }.toSet()
                                }
                            }
                        ) {
                            Text(
                                text = if (allSelected) "Deselect all" else "Select all",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            } else {
                // Top bar in normal mode per FIXES-2 §5: Back, doc name (tap / ✎ to rename), checkmark icon
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .clickable {
                                    renameText = doc.name
                                    showRenameDialog = true
                                }
                                .padding(4.dp)
                        ) {
                            Text(
                                text = doc.name,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    textDirection = TextDirection.Content
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = stringResource(R.string.action_rename),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        // Check/select icon (enters page-select mode per FIXES-2 §5)
                        IconButton(onClick = {
                            isSelectMode = true
                            selectedPageIds = emptySet()
                        }) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = "Select pages",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { showTagSheet = true }) {
                            Icon(Icons.Outlined.Label, contentDescription = "Tags")
                        }
                        Box {
                            IconButton(onClick = { showTopOverflow = true }) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.more))
                            }
                            DropdownMenu(
                                expanded = showTopOverflow,
                                onDismissRequest = { showTopOverflow = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Reorder pages") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Sort, null) },
                                    onClick = {
                                        showTopOverflow = false
                                        isReordering = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_recycle_bin), color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showTopOverflow = false
                                        viewModel.moveToTrash()
                                        onNavigateBack()
                                    }
                                )
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
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                if (isSelectMode) {
                    // Page-Select Mode Bottom Bar (5 actions per FIXES-2 §6: Share, Save to Gallery, Move, Delete, More)
                    val hasSelection = selectedPageIds.isNotEmpty()
                    val activeTint = MaterialTheme.colorScheme.primary
                    val disabledTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Share
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .clickable(enabled = hasSelection) {
                                    showShareSheet = true
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Outlined.Share, contentDescription = "Share", tint = if (hasSelection) activeTint else disabledTint)
                            Text("Share", fontSize = 11.sp, color = if (hasSelection) MaterialTheme.colorScheme.onSurface else disabledTint)
                        }

                        // 2. Save to Gallery
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .clickable(enabled = hasSelection) {
                                    scope.launch(Dispatchers.IO) {
                                        val count = selectedPageIds.size
                                        for (pid in selectedPageIds) {
                                            val p = uiState.pages.firstOrNull { it.id == pid } ?: continue
                                            val renderedFile = fileStore.getPageRenderedFile(doc.id, p.id)
                                            if (renderedFile.exists()) {
                                                val bmp = BitmapIo.decodeUpright(renderedFile, maxLongSide = 4000)
                                                MediaStoreExporter.exportBitmapToGallery(context, bmp, "${doc.name}_page_${p.position + 1}")
                                                bmp.recycle()
                                            }
                                        }
                                        withContext(Dispatchers.Main) {
                                            isSelectMode = false
                                            selectedPageIds = emptySet()
                                            snackbarHostState.showSnackbar("Saved $count ${if (count == 1) "page" else "pages"} to Gallery")
                                        }
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Outlined.Image, contentDescription = "Save to Gallery", tint = if (hasSelection) activeTint else disabledTint)
                            Text("Gallery", fontSize = 11.sp, color = if (hasSelection) MaterialTheme.colorScheme.onSurface else disabledTint)
                        }

                        // 3. Move
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .clickable(enabled = hasSelection) {
                                    showMoveDialog = true
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Outlined.DriveFileMove, contentDescription = "Move", tint = if (hasSelection) activeTint else disabledTint)
                            Text("Move", fontSize = 11.sp, color = if (hasSelection) MaterialTheme.colorScheme.onSurface else disabledTint)
                        }

                        // 4. Delete
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .clickable(enabled = hasSelection) {
                                    val toDelete = selectedPageIds.toList()
                                    viewModel.deletePages(toDelete) {
                                        onNavigateBack()
                                    }
                                    isSelectMode = false
                                    selectedPageIds = emptySet()
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Deleted ${toDelete.size} ${if (toDelete.size == 1) "page" else "pages"}")
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = if (hasSelection) MaterialTheme.colorScheme.error else disabledTint)
                            Text("Delete", fontSize = 11.sp, color = if (hasSelection) MaterialTheme.colorScheme.error else disabledTint)
                        }

                        // 5. More (Copy, Collage)
                        Box {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable(enabled = hasSelection) {
                                        showSelectMoreMenu = true
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Outlined.MoreHoriz, contentDescription = "More", tint = if (hasSelection) activeTint else disabledTint)
                                Text("More", fontSize = 11.sp, color = if (hasSelection) MaterialTheme.colorScheme.onSurface else disabledTint)
                            }

                            DropdownMenu(
                                expanded = showSelectMoreMenu,
                                onDismissRequest = { showSelectMoreMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Copy (duplicate in doc)") },
                                    leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                                    onClick = {
                                        showSelectMoreMenu = false
                                        viewModel.duplicatePages(selectedPageIds.toList())
                                        isSelectMode = false
                                        selectedPageIds = emptySet()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Collage from selected") },
                                    leadingIcon = { Icon(Icons.Outlined.GridView, null) },
                                    onClick = {
                                        showSelectMoreMenu = false
                                        onNavigateToCollage?.invoke()
                                        isSelectMode = false
                                        selectedPageIds = emptySet()
                                    }
                                )
                            }
                        }
                    }
                } else {
                    // Normal Document Screen Bottom Action Bar (5 actions per FIXES-2 §5: Add, Collage, Share, Edit PDF, More)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Add
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { showAddSheet = true }
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = "Add", tint = MaterialTheme.colorScheme.primary)
                            Text("Add", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                        }

                        // 2. Collage
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { onNavigateToCollage?.invoke() }
                        ) {
                            Icon(Icons.Outlined.GridView, contentDescription = "Collage", tint = MaterialTheme.colorScheme.primary)
                            Text("Collage", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                        }

                        // 3. Share
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { showShareSheet = true }
                        ) {
                            Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.action_share_pdf), tint = MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.action_share_pdf), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                        }

                        // 4. Edit PDF (opens page editor for first page)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                val firstPage = uiState.pages.firstOrNull()?.id.orEmpty()
                                if (firstPage.isNotEmpty()) {
                                    onNavigateToPage(doc.id, firstPage)
                                }
                            }
                        ) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Edit PDF", tint = MaterialTheme.colorScheme.primary)
                            Text("Edit PDF", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                        }

                        // 5. More
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { showMoreSheet = true }
                        ) {
                            Icon(Icons.Outlined.MoreHoriz, contentDescription = stringResource(R.string.more), tint = MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.more), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Updating PDF banner if in progress
            if (uiState.isUpdatingPdf) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.updating_pdf),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Page Grid (2 columns per FIXES-2 §5)
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(uiState.pages, key = { _, page -> page.id }) { index, page ->
                    PageTile(
                        page = page,
                        pageNumber = index + 1,
                        isSelectMode = isSelectMode,
                        isSelected = selectedPageIds.contains(page.id),
                        isReordering = isReordering,
                        canMoveUp = index > 0,
                        canMoveDown = index < uiState.pages.size - 1,
                        onMoveUp = {
                            if (index > 0) {
                                val newOrder = uiState.pages.map { it.id }.toMutableList()
                                val temp = newOrder[index]
                                newOrder[index] = newOrder[index - 1]
                                newOrder[index - 1] = temp
                                viewModel.reorderPages(newOrder)
                            }
                        },
                        onMoveDown = {
                            if (index < uiState.pages.size - 1) {
                                val newOrder = uiState.pages.map { it.id }.toMutableList()
                                val temp = newOrder[index]
                                newOrder[index] = newOrder[index + 1]
                                newOrder[index + 1] = temp
                                viewModel.reorderPages(newOrder)
                            }
                        },
                        onClick = {
                            if (isSelectMode) {
                                selectedPageIds = if (selectedPageIds.contains(page.id)) {
                                    selectedPageIds - page.id
                                } else {
                                    selectedPageIds + page.id
                                }
                            } else if (!isReordering) {
                                onNavigateToPage(doc.id, page.id)
                            }
                        },
                        onLongClick = {
                            if (!isSelectMode && !isReordering) {
                                isSelectMode = true
                                selectedPageIds = setOf(page.id)
                            }
                        }
                    )
                }

                // Last tile: "Tap 📷➕ to add new pages" per FIXES-2 §5
                if (!isReordering && !isSelectMode) {
                    item {
                        AddPageTile(onClick = { showAddSheet = true })
                    }
                }
            }
        }
    }

    // Move Selected Pages Dialog (FIXES-2 §6)
    if (showMoveDialog) {
        AlertDialog(
            onDismissRequest = {
                showMoveDialog = false
                showCreateNewDocForMove = false
            },
            title = { Text("Move ${selectedPageIds.size} ${if (selectedPageIds.size == 1) "page" else "pages"}") },
            text = {
                if (showCreateNewDocForMove) {
                    Column {
                        Text("Create a new document for these pages:")
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = moveNewDocName,
                            onValueChange = { moveNewDocName = it },
                            label = { Text("Document name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    Column {
                        Button(
                            onClick = {
                                moveNewDocName = "${doc.name} (Part 2)"
                                showCreateNewDocForMove = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.Add, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Move to new document")
                        }

                        val otherDocs = otherDocuments.filter { it.id != doc.id }
                        if (otherDocs.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Or select an existing document:", fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyColumn(modifier = Modifier.height(200.dp)) {
                                items(otherDocs, key = { it.id }) { targetDoc ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable {
                                                showMoveDialog = false
                                                viewModel.movePagesToDocument(selectedPageIds.toList(), targetDoc.id) {
                                                    onNavigateBack()
                                                }
                                                isSelectMode = false
                                                selectedPageIds = emptySet()
                                            }
                                            .padding(vertical = 10.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Outlined.Description, null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(targetDoc.name, fontWeight = FontWeight.Medium, maxLines = 1)
                                            Text("${targetDoc.pageCount} pages", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (showCreateNewDocForMove) {
                    Button(
                        onClick = {
                            if (moveNewDocName.isNotBlank()) {
                                showMoveDialog = false
                                showCreateNewDocForMove = false
                                viewModel.movePagesToNewDoc(selectedPageIds.toList(), moveNewDocName) {
                                    isSelectMode = false
                                    selectedPageIds = emptySet()
                                }
                            }
                        }
                    ) {
                        Text("Create & Move")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showMoveDialog = false
                        showCreateNewDocForMove = false
                    }
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // Rename Dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
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
                        if (renameText.isNotBlank()) {
                            viewModel.rename(renameText)
                        }
                        showRenameDialog = false
                    }
                ) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // Share Sheet
    if (showShareSheet) {
        val pagesToShare = if (isSelectMode && selectedPageIds.isNotEmpty()) {
            uiState.pages.filter { it.id in selectedPageIds }
        } else {
            uiState.pages
        }
        DocumentShareSheet(
            doc = doc,
            pages = pagesToShare,
            fileStore = fileStore,
            onDismiss = {
                showShareSheet = false
                if (isSelectMode) {
                    isSelectMode = false
                    selectedPageIds = emptySet()
                }
            },
            onSharePdf = { onSharePdf(doc) },
            onShareJpg = { onShareJpg(doc) }
        )
    }

    // Add Pages Sheet
    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = stringResource(R.string.add_pages),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Scan with Camera
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable {
                            showAddSheet = false
                            if (cameraEngine == "GOOGLE_SCANNER") {
                                launchAddScanner()
                            } else {
                                onNavigateToCamera?.invoke(doc.id)
                            }
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.CameraAlt, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Scan with camera", style = MaterialTheme.typography.bodyLarge)
                }

                // Import from gallery
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable {
                            showAddSheet = false
                            photoPickerLauncher.launch("image/*")
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.PhotoLibrary, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(stringResource(R.string.import_from_gallery), style = MaterialTheme.typography.bodyLarge)
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // More Sheet per FIXES-2 §5
    if (showMoreSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMoreSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = stringResource(R.string.more),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                // 1. Save PDF to...
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable {
                            showMoreSheet = false
                            onSharePdf(doc)
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Description, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Save PDF to…", style = MaterialTheme.typography.bodyLarge)
                }

                // 2. Save JPGs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable {
                            showMoreSheet = false
                            scope.launch {
                                val count = MediaStoreExporter.exportJpgsToPictures(context, doc, uiState.pages, fileStore)
                                android.widget.Toast.makeText(context, "Exported $count pages to Pictures/Abul's Scanner", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Image, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Save JPGs", style = MaterialTheme.typography.bodyLarge)
                }

                // 3. Print
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable {
                            showMoreSheet = false
                            onPrint(doc)
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Print, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(stringResource(R.string.action_print), style = MaterialTheme.typography.bodyLarge)
                }

                // 4. Password
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable {
                            showMoreSheet = false
                            onNavigateToPassword?.invoke()
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Password", style = MaterialTheme.typography.bodyLarge)
                }

                // 5. Compress
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable {
                            showMoreSheet = false
                            onNavigateToCompress?.invoke()
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Compress, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Compress", style = MaterialTheme.typography.bodyLarge)
                }

                // 6. Page size
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable {
                            showMoreSheet = false
                            showPageSizeDialog = true
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.AspectRatio, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Page size", style = MaterialTheme.typography.bodyLarge)
                        Text(doc.pageSize, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // 7. Lock / Unlock
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable {
                            showMoreSheet = false
                            scope.launch {
                                val pinHash = settingsRepository.pinHash.first()
                                if (pinHash.isNullOrBlank()) {
                                    showNoPinDialog = true
                                } else {
                                    viewModel.toggleLock()
                                }
                            }
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(if (doc.locked) "Unlock document" else "Lock document", style = MaterialTheme.typography.bodyLarge)
                }

                // 8. Book split
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable {
                            showMoreSheet = false
                            if (uiState.pages.isNotEmpty()) {
                                if (uiState.pages.size == 1) {
                                    onNavigateToBookSplit(doc.id, uiState.pages[0].id)
                                } else {
                                    showPageSelectForBookSplit = true
                                }
                            }
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Book split",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                // 9. Move to recycle bin
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable {
                            showMoreSheet = false
                            viewModel.moveToTrash()
                            onNavigateBack()
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(stringResource(R.string.action_recycle_bin), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showPageSelectForBookSplit) {
        AlertDialog(
            onDismissRequest = { showPageSelectForBookSplit = false },
            title = { Text("Select Page to Split") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text("Select which 2-page spread you want to split into separate pages:")
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(240.dp)
                    ) {
                        itemsIndexed(uiState.pages, key = { _, page -> page.id }) { index, page ->
                            PageTile(
                                page = page,
                                pageNumber = index + 1,
                                onClick = {
                                    showPageSelectForBookSplit = false
                                    onNavigateToBookSplit(doc.id, page.id)
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPageSelectForBookSplit = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // Tag Assignment Sheet
    if (showTagSheet) {
        var currentSelection by remember(uiState.tags) { mutableStateOf(uiState.tags.map { it.id }.toSet()) }

        ModalBottomSheet(
            onDismissRequest = { showTagSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Document Tags",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { showNewTagDialog = true }) {
                        Icon(Icons.Outlined.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Tag")
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                if (allTags.isEmpty()) {
                    Text("No tags created yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    allTags.forEach { tag ->
                        val isChecked = currentSelection.contains(tag.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable {
                                    currentSelection = if (isChecked) currentSelection - tag.id else currentSelection + tag.id
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    currentSelection = if (checked) currentSelection + tag.id else currentSelection - tag.id
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
                    TextButton(onClick = { showTagSheet = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            viewModel.setTags(currentSelection)
                            showTagSheet = false
                        }
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // New Tag Dialog
    if (showNewTagDialog) {
        AlertDialog(
            onDismissRequest = { showNewTagDialog = false },
            title = { Text("Create Tag") },
            text = {
                OutlinedTextField(
                    value = newTagName,
                    onValueChange = { newTagName = it },
                    label = { Text("Tag name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTagName.isNotBlank()) {
                            viewModel.createTagAndAssign(newTagName.trim())
                            newTagName = ""
                            showNewTagDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewTagDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // Page Size Dialog
    if (showPageSizeDialog) {
        AlertDialog(
            onDismissRequest = { showPageSizeDialog = false },
            title = { Text("Select Page Size") },
            text = {
                Column {
                    listOf("AUTO", "A4", "LETTER").forEach { size ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable {
                                    viewModel.setPageSize(size)
                                    showPageSizeDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (doc.pageSize == size),
                                onClick = {
                                    viewModel.setPageSize(size)
                                    showPageSizeDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(size, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPageSizeDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // No PIN Alert Dialog
    if (showNoPinDialog) {
        AlertDialog(
            onDismissRequest = { showNoPinDialog = false },
            title = { Text("Set a PIN first") },
            text = { Text("To lock documents, you need to configure an app PIN in Security Settings.") },
            confirmButton = {
                Button(
                    onClick = {
                        showNoPinDialog = false
                        onNavigateToSecurity()
                    }
                ) {
                    Text("Go to Security")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNoPinDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PageTile(
    page: PageEntity,
    pageNumber: Int,
    isSelectMode: Boolean = false,
    isSelected: Boolean = false,
    isReordering: Boolean = false,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val thumbFile = remember(page) {
        val file = File(context.filesDir, page.thumbPath)
        if (file.exists()) file else File(context.filesDir, page.renderedPath)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        if (thumbFile.exists()) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(thumbFile).crossfade(true).build(),
                contentDescription = "Page $pageNumber",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Selection Checkmark Badge (top-end)
        if (isSelectMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Page number badge "01", "02" (bottom-start) per FIXES-2 §5
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = String.format("%02d", pageNumber),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Reordering arrows overlay
        if (isReordering) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onMoveUp,
                        enabled = canMoveUp,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowBack,
                            contentDescription = "Move earlier",
                            tint = if (canMoveUp) Color.White else Color.White.copy(alpha = 0.3f)
                        )
                    }
                    IconButton(
                        onClick = onMoveDown,
                        enabled = canMoveDown,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowForward,
                            contentDescription = "Move later",
                            tint = if (canMoveDown) Color.White else Color.White.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddPageTile(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .clickable(onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = "Add pages",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Tap 📷➕ to add new pages",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
