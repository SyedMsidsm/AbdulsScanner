package com.syedmsidsm.abdulsscanner.ui.screens.document

import android.app.Activity
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    onSharePdf: (DocumentEntity) -> Unit,
    onShareJpg: (DocumentEntity) -> Unit,
    onPrint: (DocumentEntity) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val cameraEngine by settingsRepository.cameraEngine.collectAsStateWithLifecycle(initialValue = "BUILT_IN")
    val doc = uiState.document

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
            } else {
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
                        IconButton(onClick = { showTagSheet = true }) {
                            Icon(Icons.Outlined.Label, contentDescription = "Tags")
                        }
                        IconButton(onClick = { isReordering = true }) {
                            Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = "Reorder pages")
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
            // Document Screen Bottom Action Bar
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
                    // Share
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { showShareSheet = true }
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.action_share_pdf), tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.action_share_pdf), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                    }

                    // To Text
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onNavigateToOcr(doc.id) }
                    ) {
                        Icon(Icons.Outlined.TextFields, contentDescription = stringResource(R.string.to_text), tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.to_text), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                    }

                    // Sign
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            val firstPage = uiState.pages.firstOrNull()?.id ?: ""
                            onNavigateToPage(doc.id, firstPage)
                        }
                    ) {
                        Icon(Icons.Outlined.Draw, contentDescription = stringResource(R.string.sign), tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.sign), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                    }

                    // More
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

            // Page Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(uiState.pages, key = { _, page -> page.id }) { index, page ->
                    PageTile(
                        page = page,
                        pageNumber = page.position + 1,
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
                            if (!isReordering) {
                                onNavigateToPage(doc.id, page.id)
                            }
                        }
                    )
                }

                // Last tile: "+ Add"
                if (!isReordering) {
                    item {
                        AddPageTile(onClick = { showAddSheet = true })
                    }
                }
            }
        }
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

    // Share Sheet (Section 7)
    if (showShareSheet) {
        DocumentShareSheet(
            doc = doc,
            pages = uiState.pages,
            fileStore = fileStore,
            onDismiss = { showShareSheet = false },
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

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable {
                            showAddSheet = false
                            if (cameraEngine == "GOOGLE_SCANNER") {
                                launchAddScanner()
                            } else {
                                onNavigateToCamera?.invoke(doc?.id.orEmpty())
                            }
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Add, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(stringResource(R.string.scan_more_pages), style = MaterialTheme.typography.bodyLarge)
                }

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
                    Icon(Icons.Outlined.Image, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(stringResource(R.string.import_from_gallery), style = MaterialTheme.typography.bodyLarge)
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // More Sheet
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

                // Print
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

                // Page size
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

                // Book split
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

                // Watermark
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable {
                            showMoreSheet = false
                            onNavigateToWatermark?.invoke(doc.id)
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.WaterDrop, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Watermark", style = MaterialTheme.typography.bodyLarge)
                }

                // Sign
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable {
                            showMoreSheet = false
                            onNavigateToSign?.invoke(doc.id)
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Draw, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Sign document", style = MaterialTheme.typography.bodyLarge)
                }

                // Export JPGs to Pictures
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
                    Text("Export JPGs to Pictures", style = MaterialTheme.typography.bodyLarge)
                }

                // Lock / Unlock
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

                // Recycle bin
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
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    uiState.pages.forEachIndexed { idx, page ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    showPageSelectForBookSplit = false
                                    if (doc != null) {
                                        onNavigateToBookSplit(doc.id, page.id)
                                    }
                                },
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Page ${idx + 1}", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPageSelectForBookSplit = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Tag Selection Bottom Sheet
    if (showTagSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTagSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            val assignedTagIds = remember(uiState.tags) { uiState.tags.map { it.id }.toSet() }
            var currentSelection by remember { mutableStateOf(assignedTagIds) }

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
                    Text(
                        "No tags yet. Tap 'New Tag' above to create one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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

@Composable
fun PageTile(
    page: PageEntity,
    pageNumber: Int,
    isReordering: Boolean = false,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    onClick: () -> Unit
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
            .clickable(onClick = onClick)
    ) {
        if (thumbFile.exists()) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(thumbFile).crossfade(true).build(),
                contentDescription = "Page $pageNumber",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Page number badge
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.65f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = pageNumber.toString(),
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
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = stringResource(R.string.add_pages),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.add_pages),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
