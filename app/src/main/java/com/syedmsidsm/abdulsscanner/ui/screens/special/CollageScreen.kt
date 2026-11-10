package com.syedmsidsm.abdulsscanner.ui.screens.special

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syedmsidsm.abdulsscanner.R
import com.syedmsidsm.abdulsscanner.data.db.entity.DocumentKind
import com.syedmsidsm.abdulsscanner.domain.imaging.BitmapIo
import com.syedmsidsm.abdulsscanner.domain.scan.SaveScanUseCase
import com.syedmsidsm.abdulsscanner.domain.scan.special.CollageLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollageScreen(
    saveScanUseCase: SaveScanUseCase,
    onNavigateBack: () -> Unit,
    onNavigateToDocument: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedBitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var selectedTemplate by remember { mutableStateOf(CollageLayout.CollageTemplate.TWO_ROWS) }
    var composedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    fun updateComposite(bitmaps: List<Bitmap>, template: CollageLayout.CollageTemplate) {
        if (bitmaps.isEmpty()) {
            composedBitmap = null
            return
        }
        isProcessing = true
        scope.launch(Dispatchers.Default) {
            val result = CollageLayout.composeCollage(bitmaps, template)
            withContext(Dispatchers.Main) {
                composedBitmap = result
                isProcessing = false
            }
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            isProcessing = true
            scope.launch(Dispatchers.IO) {
                try {
                    val loaded = uris.take(4).map { uri: Uri ->
                        BitmapIo.decodeFromUri(context, uri, maxLongSide = 1800)
                    }
                    val combined = (selectedBitmaps + loaded).take(4)
                    val defaultTemplate = when (combined.size) {
                        2 -> CollageLayout.CollageTemplate.TWO_ROWS
                        3 -> CollageLayout.CollageTemplate.ONE_LARGE_TWO_SMALL
                        4 -> CollageLayout.CollageTemplate.GRID_2X2
                        else -> CollageLayout.CollageTemplate.TWO_ROWS
                    }
                    withContext(Dispatchers.Main) {
                        selectedBitmaps = combined
                        selectedTemplate = defaultTemplate
                        updateComposite(combined, defaultTemplate)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isProcessing = false
                        Toast.makeText(context, "Failed to load images: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val availableTemplates = when (selectedBitmaps.size) {
        2 -> listOf(CollageLayout.CollageTemplate.TWO_ROWS, CollageLayout.CollageTemplate.TWO_COLUMNS)
        3 -> listOf(CollageLayout.CollageTemplate.THREE_ROWS, CollageLayout.CollageTemplate.ONE_LARGE_TWO_SMALL)
        4 -> listOf(CollageLayout.CollageTemplate.GRID_2X2, CollageLayout.CollageTemplate.FOUR_ROWS)
        else -> emptyList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tool_collage), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (selectedBitmaps.isNotEmpty()) {
                        IconButton(onClick = {
                            selectedBitmaps = emptyList()
                            composedBitmap = null
                        }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (selectedBitmaps.isEmpty()) {
                // Empty state: pick 2-4 images
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
                            imageVector = Icons.Outlined.GridOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(54.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Create Document Collage",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Select 2 to 4 photos or scanned pages.\nCombine them onto a single clean A4 document page with rows, columns, or grids.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )

                    Spacer(modifier = Modifier.height(36.dp))

                    Button(
                        onClick = { photoPicker.launch("image/*") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Outlined.Image, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pick 2–4 Images", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                // Interactive Collage Preview & Controls
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Template selector row
                    if (availableTemplates.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (tmpl in availableTemplates) {
                                FilterChip(
                                    selected = selectedTemplate == tmpl,
                                    onClick = {
                                        selectedTemplate = tmpl
                                        updateComposite(selectedBitmaps, tmpl)
                                    },
                                    label = { Text(tmpl.title) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // A4 Canvas Preview
                    val composite = composedBitmap
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(2480f / 3508f)
                            .clip(RoundedCornerShape(8.dp)),
                        shadowElevation = 4.dp
                    ) {
                        if (composite != null) {
                            Image(
                                bitmap = composite.asImageBitmap(),
                                contentDescription = "Collage Preview",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Selected images thumbnail strip
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(selectedBitmaps.size) { idx ->
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                            ) {
                                Image(
                                    bitmap = selectedBitmaps[idx].asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }

                        if (selectedBitmaps.size < 4) {
                            item {
                                OutlinedButton(
                                    onClick = { photoPicker.launch("image/*") },
                                    modifier = Modifier.size(56.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(Icons.Outlined.Add, contentDescription = "Add image")
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Save Button
                    Button(
                        onClick = {
                            val comp = composedBitmap ?: return@Button
                            isProcessing = true
                            scope.launch {
                                try {
                                    val docId = saveScanUseCase.saveBitmapScan(
                                        bitmaps = listOf(comp),
                                        kind = DocumentKind.COLLAGE,
                                        pageSize = "A4"
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
                        enabled = selectedBitmaps.size in 2..4 && composedBitmap != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Outlined.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save as A4 Document")
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
        }
    }
}
