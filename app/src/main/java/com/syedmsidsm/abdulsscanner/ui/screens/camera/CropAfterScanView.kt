package com.syedmsidsm.abdulsscanner.ui.screens.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syedmsidsm.abdulsscanner.domain.imaging.BitmapIo
import com.syedmsidsm.abdulsscanner.domain.imaging.PerspectiveCrop
import com.syedmsidsm.abdulsscanner.domain.imaging.Point2D
import com.syedmsidsm.abdulsscanner.domain.imaging.Quad
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.hypot
import kotlin.math.roundToInt

enum class CropScreenMode {
    AFTER_SCAN,
    EDIT_PAGE
}

@Composable
fun CropAfterScanView(
    rawImageFile: File,
    initialQuad: Quad?,
    autoCropSuccess: Boolean = true,
    pageNumber: Int,
    totalPagesInBatch: Int,
    mode: CropScreenMode = CropScreenMode.AFTER_SCAN,
    onRetake: () -> Unit = {},
    onCancel: () -> Unit = {},
    onNextPage: (finalQuad: Quad, rotation: Int) -> Unit = { _, _ -> },
    onDone: (finalQuad: Quad, rotation: Int) -> Unit = { _, _ -> },
    onApply: (finalQuad: Quad, rotation: Int) -> Unit = { _, _ -> }
) {
    var showDiscardDialog by remember { mutableStateOf(false) }

    // Intercept back button per Section 1: Discard this photo?
    BackHandler {
        if (mode == CropScreenMode.EDIT_PAGE) {
            onCancel()
        } else {
            showDiscardDialog = true
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard this photo?") },
            text = { Text("Are you sure you want to discard this photo and return to the camera?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onRetake()
                    }
                ) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Keep editing")
                }
            }
        )
    }

    var rotation by remember { mutableIntStateOf(0) }
    val baseBitmap = remember(rawImageFile) {
        BitmapIo.decodeUpright(rawImageFile, maxLongSide = 2048)
    }

    val displayBitmap = remember(baseBitmap, rotation) {
        if (rotation % 360 == 0) {
            baseBitmap
        } else {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(baseBitmap, 0, 0, baseBitmap.width, baseBitmap.height, matrix, true)
        }
    }

    // Default to full 4 image corners (0,0 to 1,1) if no page was found, never arbitrary inset
    var quad by remember(initialQuad) {
        mutableStateOf(
            initialQuad ?: Quad(
                Point2D(0.0f, 0.0f),
                Point2D(1.0f, 0.0f),
                Point2D(1.0f, 1.0f),
                Point2D(0.0f, 1.0f)
            )
        )
    }

    var activeHandleIndex by remember { mutableStateOf<Int?>(null) }
    var activeFingerPos by remember { mutableStateOf<Offset?>(null) }
    val isValid = remember(quad) { PerspectiveCrop.isValidQuad(quad) }

    val brandGreen = Color(0xFF13A06F)
    val errorColor = Color(0xFFE53935)
    val bottomBarBg = Color(0xFF161616)

    val imageBitmap = remember(displayBitmap) { displayBitmap.asImageBitmap() }

    // Status pill state (fades out after 3 seconds)
    var showPill by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(3000)
        showPill = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Top Bar (56 dp, statusBarsPadding)
        Surface(
            color = Color.Black,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = {
                        if (mode == CropScreenMode.EDIT_PAGE) {
                            onCancel()
                        } else {
                            showDiscardDialog = true
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }

                Text(
                    text = if (mode == CropScreenMode.EDIT_PAGE) "Adjust crop" else "Page $pageNumber · adjust crop",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Image Viewport Area with Handles and Magnifier
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            val boxW = maxWidth.value
            val boxH = maxHeight.value
            val imgW = displayBitmap.width.toFloat()
            val imgH = displayBitmap.height.toFloat()

            val scale = minOf(boxW / imgW, boxH / imgH)
            val fittedW = imgW * scale
            val fittedH = imgH * scale
            val offsetX = (boxW - fittedW) / 2f
            val offsetY = (boxH - fittedH) / 2f

            val imageRect = Rect(offsetX, offsetY, offsetX + fittedW, offsetY + fittedH)

            Box(modifier = Modifier.fillMaxSize()) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(imageRect, quad) {
                            detectDragGestures(
                                onDragStart = { startOffset ->
                                    val screenP0 = Offset(imageRect.left + quad.p0.x * imageRect.width, imageRect.top + quad.p0.y * imageRect.height)
                                    val screenP1 = Offset(imageRect.left + quad.p1.x * imageRect.width, imageRect.top + quad.p1.y * imageRect.height)
                                    val screenP2 = Offset(imageRect.left + quad.p2.x * imageRect.width, imageRect.top + quad.p2.y * imageRect.height)
                                    val screenP3 = Offset(imageRect.left + quad.p3.x * imageRect.width, imageRect.top + quad.p3.y * imageRect.height)

                                    val handles = listOf(screenP0, screenP1, screenP2, screenP3)
                                    val touchTargetRadius = 36.dp.toPx()

                                    val distances = handles.map { hypot(startOffset.x - it.x, startOffset.y - it.y) }
                                    val minIdx = distances.indices.minByOrNull { distances[it] } ?: 0
                                    if (distances[minIdx] <= touchTargetRadius) {
                                        activeHandleIndex = minIdx
                                        activeFingerPos = startOffset
                                    } else {
                                        activeHandleIndex = null
                                        activeFingerPos = null
                                    }
                                },
                                onDrag = { change, _ ->
                                    val idx = activeHandleIndex ?: return@detectDragGestures
                                    change.consume()
                                    val pos = change.position
                                    activeFingerPos = pos

                                    // Clamp dragged position strictly to image rectangle
                                    val clampedX = pos.x.coerceIn(imageRect.left, imageRect.right)
                                    val clampedY = pos.y.coerceIn(imageRect.top, imageRect.bottom)

                                    val normX = ((clampedX - imageRect.left) / imageRect.width).coerceIn(0f, 1f)
                                    val normY = ((clampedY - imageRect.top) / imageRect.height).coerceIn(0f, 1f)
                                    val newPt = Point2D(normX, normY)

                                    quad = when (idx) {
                                        0 -> quad.copy(p0 = newPt)
                                        1 -> quad.copy(p1 = newPt)
                                        2 -> quad.copy(p2 = newPt)
                                        3 -> quad.copy(p3 = newPt)
                                        else -> quad
                                    }
                                },
                                onDragEnd = {
                                    activeHandleIndex = null
                                    activeFingerPos = null
                                },
                                onDragCancel = {
                                    activeHandleIndex = null
                                    activeFingerPos = null
                                }
                            )
                        }
                ) {
                    val p0 = Offset(imageRect.left + quad.p0.x * imageRect.width, imageRect.top + quad.p0.y * imageRect.height)
                    val p1 = Offset(imageRect.left + quad.p1.x * imageRect.width, imageRect.top + quad.p1.y * imageRect.height)
                    val p2 = Offset(imageRect.left + quad.p2.x * imageRect.width, imageRect.top + quad.p2.y * imageRect.height)
                    val p3 = Offset(imageRect.left + quad.p3.x * imageRect.width, imageRect.top + quad.p3.y * imageRect.height)

                    // 1. Draw base photo
                    drawImage(
                        image = imageBitmap,
                        dstOffset = IntOffset(imageRect.left.roundToInt(), imageRect.top.roundToInt()),
                        dstSize = IntSize(imageRect.width.roundToInt(), imageRect.height.roundToInt())
                    )

                    // 2. Draw Quad boundary & fill
                    val outlineColor = if (isValid) brandGreen else errorColor
                    val fillColor = if (isValid) brandGreen.copy(alpha = 0.10f) else errorColor.copy(alpha = 0.10f)

                    val quadPath = Path().apply {
                        moveTo(p0.x, p0.y)
                        lineTo(p1.x, p1.y)
                        lineTo(p2.x, p2.y)
                        lineTo(p3.x, p3.y)
                        close()
                    }
                    drawPath(quadPath, fillColor)
                    drawPath(quadPath, outlineColor, style = Stroke(width = 2.5.dp.toPx()))

                    // 3. Draw 4 Corner Handles (22 dp white circle with 3 dp green ring)
                    val handlePoints = listOf(p0, p1, p2, p3)
                    handlePoints.forEachIndexed { i, pt ->
                        val isDragging = i == activeHandleIndex
                        val radius = if (isDragging) 13.dp.toPx() else 11.dp.toPx()
                        val ringWidth = 3.dp.toPx()

                        if (isDragging) {
                            // Active handle turns green-filled
                            drawCircle(color = Color.White, radius = radius + 2.dp.toPx(), center = pt)
                            drawCircle(color = brandGreen, radius = radius, center = pt)
                        } else {
                            // Inactive handle: white fill with 3 dp green ring
                            drawCircle(color = Color.White, radius = radius, center = pt)
                            drawCircle(color = outlineColor, radius = radius, center = pt, style = Stroke(width = ringWidth))
                        }
                    }

                    // 4. Magnifier Loupe (zoomed 2.6x, 116 dp diameter, away from finger)
                    activeHandleIndex?.let { idx ->
                        val finger = activeFingerPos ?: handlePoints[idx]
                        val cornerScreen = handlePoints[idx]

                        val loupeDiameter = 116.dp.toPx()
                        val loupeRadius = loupeDiameter / 2f
                        val padding = 16.dp.toPx()

                        // Position loupe at top-left, or top-right if finger is in top-left
                        val inTopLeft = finger.x < (size.width / 2f) && finger.y < (size.height / 2f)
                        val loupeCenter = if (inTopLeft) {
                            Offset(size.width - loupeRadius - padding, loupeRadius + padding)
                        } else {
                            Offset(loupeRadius + padding, loupeRadius + padding)
                        }

                        val loupeCircle = Path().apply {
                            addOval(Rect(loupeCenter.x - loupeRadius, loupeCenter.y - loupeRadius, loupeCenter.x + loupeRadius, loupeCenter.y + loupeRadius))
                        }

                        // Loupe shadow / border backing
                        drawCircle(color = Color.Black.copy(alpha = 0.5f), radius = loupeRadius + 5.dp.toPx(), center = loupeCenter)

                        // Clip canvas and draw magnified image + lines
                        withTransform({
                            clipPath(loupeCircle)
                            translate(loupeCenter.x, loupeCenter.y)
                            scale(2.6f, 2.6f, Offset.Zero)
                            translate(-cornerScreen.x, -cornerScreen.y)
                        }) {
                            // Magnified photo
                            drawImage(
                                image = imageBitmap,
                                dstOffset = IntOffset(imageRect.left.roundToInt(), imageRect.top.roundToInt()),
                                dstSize = IntSize(imageRect.width.roundToInt(), imageRect.height.roundToInt())
                            )
                            // Magnified green corner edges
                            drawPath(quadPath, brandGreen, style = Stroke(width = 2.dp.toPx() / 2.6f))
                        }

                        // 3 dp white border around loupe
                        drawCircle(color = Color.White, radius = loupeRadius, center = loupeCenter, style = Stroke(width = 3.dp.toPx()))

                        // Thin white crosshair at loupe center
                        val crosshairLen = 14.dp.toPx()
                        drawLine(
                            color = Color.White,
                            start = Offset(loupeCenter.x - crosshairLen, loupeCenter.y),
                            end = Offset(loupeCenter.x + crosshairLen, loupeCenter.y),
                            strokeWidth = 1.5.dp.toPx()
                        )
                        drawLine(
                            color = Color.White,
                            start = Offset(loupeCenter.x, loupeCenter.y - crosshairLen),
                            end = Offset(loupeCenter.x, loupeCenter.y + crosshairLen),
                            strokeWidth = 1.5.dp.toPx()
                        )
                    }
                }
            }
        }

        // Status Pill above bottom bar
        AnimatedVisibility(
            visible = showPill,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xCC2A2A2A),
                modifier = Modifier.padding(bottom = 12.dp, start = 24.dp, end = 24.dp)
            ) {
                Text(
                    text = if (autoCropSuccess) "Page edges found automatically" else "Couldn't find the page edges. Drag the corners.",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Two-Row Bottom Bar (#161616 background, navigationBarsPadding)
        Surface(
            color = bottomBarBg,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Row 1: Retake / Rotate / Next page ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (mode == CropScreenMode.EDIT_PAGE) {
                        FilledTonalButton(
                            onClick = onCancel,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFF2A2A2A),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Cancel", maxLines = 1)
                        }
                    } else {
                        FilledTonalButton(
                            onClick = onRetake,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFF2A2A2A),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retake", maxLines = 1)
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    FilledTonalIconButton(
                        onClick = { rotation = (rotation + 90) % 360 },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color(0xFF2A2A2A),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.RotateRight, contentDescription = "Rotate")
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    if (mode == CropScreenMode.EDIT_PAGE) {
                        Button(
                            onClick = { if (isValid) onApply(quad, rotation) },
                            enabled = isValid,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = brandGreen,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Apply", fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    } else {
                        Button(
                            onClick = { if (isValid) onNextPage(quad, rotation) },
                            enabled = isValid,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = brandGreen,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Next page →", fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }

                // Row 2: Centered "Done (n pages)" (only in AFTER_SCAN mode)
                if (mode == CropScreenMode.AFTER_SCAN) {
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = { if (isValid) onDone(quad, rotation) },
                        enabled = isValid,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(
                            text = "Done ($totalPagesInBatch ${if (totalPagesInBatch == 1) "page" else "pages"})",
                            color = if (isValid) brandGreen else Color.Gray,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
