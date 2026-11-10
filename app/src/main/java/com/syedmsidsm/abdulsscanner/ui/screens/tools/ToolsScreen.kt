package com.syedmsidsm.abdulsscanner.ui.screens.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MergeType
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Reorder
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Web
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syedmsidsm.abdulsscanner.R
import com.syedmsidsm.abdulsscanner.ui.nav.AppBottomBar
import com.syedmsidsm.abdulsscanner.ui.nav.Routes
import com.syedmsidsm.abdulsscanner.ui.theme.CardShape

data class ToolItem(
    val titleRes: Int,
    val icon: ImageVector,
    val phaseLabel: String? = null,
    val route: String? = null,
    val action: (() -> Unit)? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    onNavigateToDocs: () -> Unit,
    onCameraClick: () -> Unit,
    onNavigateToRoute: (String) -> Unit
) {
    val scanTools = listOf(
        ToolItem(R.string.tool_id_card, Icons.Outlined.Badge, phaseLabel = "Phase 5", route = Routes.ID_CARD),
        ToolItem(R.string.tool_scan_docs, Icons.Outlined.CameraAlt, action = onCameraClick),
        ToolItem(R.string.tool_book, Icons.Outlined.MenuBook, phaseLabel = "Phase 5"),
        ToolItem(R.string.tool_qr_code, Icons.Outlined.QrCodeScanner, phaseLabel = "Phase 4", route = Routes.QR),
        ToolItem(R.string.tool_to_text, Icons.Outlined.TextFields, phaseLabel = "Phase 4")
    )

    val processTools = listOf(
        ToolItem(R.string.tool_import_files, Icons.Outlined.UploadFile, phaseLabel = "Phase 6", route = Routes.TOOL_IMPORT),
        ToolItem(R.string.tool_collage, Icons.Outlined.GridOn, phaseLabel = "Phase 5", route = Routes.COLLAGE),
        ToolItem(R.string.tool_watermark, Icons.Outlined.WaterDrop, phaseLabel = "Phase 6", route = Routes.TOOL_WATERMARK),
        ToolItem(R.string.tool_merge, Icons.Outlined.MergeType, phaseLabel = "Phase 6", route = Routes.TOOL_MERGE),
        ToolItem(R.string.tool_compression, Icons.Outlined.Compress, phaseLabel = "Phase 6", route = Routes.TOOL_COMPRESS),
        ToolItem(R.string.tool_office_to_pdf, Icons.Outlined.Description, phaseLabel = "Phase 7", route = Routes.TOOL_OFFICE),
        ToolItem(R.string.tool_web_to_pdf, Icons.Outlined.Web, phaseLabel = "Phase 6", route = Routes.TOOL_WEB)
    )

    val pdfTools = listOf(
        ToolItem(R.string.tool_signature, Icons.Outlined.Draw, phaseLabel = "Phase 6", route = Routes.TOOL_SIGNATURE),
        ToolItem(R.string.tool_password, Icons.Outlined.Lock, phaseLabel = "Phase 6", route = Routes.TOOL_PASSWORD),
        ToolItem(R.string.tool_page_reorder, Icons.Outlined.Reorder, phaseLabel = "Phase 6", route = Routes.TOOL_REORDER),
        ToolItem(R.string.tool_split_pdf, Icons.Outlined.PictureAsPdf, phaseLabel = "Phase 6", route = Routes.TOOL_SPLIT),
        ToolItem(R.string.tool_pdf_to_jpg, Icons.Outlined.Image, phaseLabel = "Phase 6", route = Routes.TOOL_PDF_TO_JPG)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.nav_tools),
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            AppBottomBar(
                currentRoute = Routes.TOOLS,
                onNavigateToDocs = onNavigateToDocs,
                onNavigateToTools = {},
                onCameraClick = onCameraClick
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Scan Section
            item(span = { GridItemSpan(3) }) {
                SectionHeader(stringResource(R.string.section_scan))
            }
            items(scanTools.size) { idx ->
                ToolCard(scanTools[idx], onNavigateToRoute)
            }

            // Process Files Section
            item(span = { GridItemSpan(3) }) {
                Spacer(modifier = Modifier.height(8.dp))
                SectionHeader(stringResource(R.string.section_process))
            }
            items(processTools.size) { idx ->
                ToolCard(processTools[idx], onNavigateToRoute)
            }

            // PDF Tools Section
            item(span = { GridItemSpan(3) }) {
                Spacer(modifier = Modifier.height(8.dp))
                SectionHeader(stringResource(R.string.section_pdf_tools))
            }
            items(pdfTools.size) { idx ->
                ToolCard(pdfTools[idx], onNavigateToRoute)
            }

            item(span = { GridItemSpan(3) }) {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
fun ToolCard(item: ToolItem, onNavigateToRoute: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.95f)
            .clip(CardShape)
            .clickable {
                if (item.action != null) {
                    item.action.invoke()
                } else if (item.route != null && item.phaseLabel == null) {
                    onNavigateToRoute(item.route)
                }
            },
        shape = CardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(6.dp)
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = stringResource(item.titleRes),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(item.titleRes),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    lineHeight = 15.sp
                )
                if (item.phaseLabel != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.phaseLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}
