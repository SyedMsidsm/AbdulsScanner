package com.syedmsidsm.abdulsscanner.ui.screens.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
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

private fun Quad.rotate90Cw(): Quad {
    fun r(p: Point2D) = Point2D((1f - p.y).coerceIn(0f, 1f), p.x.coerceIn(0f, 1f))
    return Quad(p0 = r(p3), p1 = r(p0), p2 = r(p1), p3 = r(p2))
}

private fun Quad.rotate90Ccw(): Quad {
    fun r(p: Point2D) = Point2D(p.y.coerceIn(0f, 1f), (1f - p.x).coerceIn(0f, 1f))
    return Quad(p0 = r(p1), p1 = r(p2), p2 = r(p3), p3 = r(p0))
}

private fun Quad.rotateStepsCw(steps: Int): Quad {
    var q = this
    repeat(((steps % 4) + 4) % 4) { q = q.rotate90Cw() }
    return q
}

private fun Quad.toUnrotatedSpace(rotationDegrees: Int): Quad {
    val steps = (((rotationDegrees % 360) + 360) % 360) / 90
    var q = this
    repeat(steps) { q = q.rotate90Ccw() }
    return q
}

@Composable
fun CropAfterScanView(
    rawImageFile: File,
    initialQuad: Quad?,
    initialRotation: Int = 0,
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

    var rotation by remember(initialRotation) { mutableIntStateOf(initialRotation) }
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

    val baseDetectedQuad = remember(initialQuad, baseBitmap) {
        if (initialQuad != null) {
            initialQuad
        } else {
            try {
                val small = BitmapIo.downscaleBitmap(baseBitmap, maxLongSide = 1000)
                val rgbaMat = org.opencv.core.Mat()
                org.opencv.android.Utils.bitmapToMat(small, rgbaMat)
                val bgrMat = org.opencv.core.Mat()
                org.opencv.imgproc.Imgproc.cvtColor(rgbaMat, bgrMat, org.opencv.imgproc.Imgproc.COLOR_RGBA2BGR)
                val best = com.syedmsidsm.abdulsscanner.domain.imaging.PageDetector.detectPageBestEffortQuad(bgrMat)
                rgbaMat.release()
                bgrMat.release()
                if (small != baseBitmap) small.recycle()
                best
            } catch (_: Throwable) {
                Quad(
                    p0 = Point2D(0.08f, 0.08f),
                    p1 = Point2D(0.92f, 0.08f),
                    p2 = Point2D(0.92f, 0.92f),
                    p3 = Point2D(0.08f, 0.92f)
                )
            }
        }
    }
    var isAutoCropOn by remember { mutableStateOf(autoCropSuccess && initialQuad != null) }

    var quad by remember(initialQuad, initialRotation) {
        mutableStateOf(
            (if (autoCropSuccess && initialQuad != null) initialQuad else Quad.fullQuad()).rotateStepsCw(initialRotation / 90)
        )
    }

    var activeHandleIndex by remember { mutableStateOf<Int?>(null) }
    var activeFingerPos by remember { mutableStateOf<Offset?>(null) }
    var dragStartPos by remember { mutableStateOf<Offset?>(null) }
    var dragStartQuad by remember { mutableStateOf<Quad?>(null) }

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
        // Top Bar (56 dp, statusBarsPadding, title "Adjust crop" per FIXES-2 §4)
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
                    text = "Adjust crop",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )

                // Rotate button in top-right
                FilledTonalIconButton(
                    onClick = {
                        rotation = (rotation + 90) % 360
                        quad = quad.rotate90Cw()
                    },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Color(0xFF2A2A2A),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .size(40.dp)
                        .align(Alignment.CenterEnd)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.RotateRight,
                        contentDescription = "Rotate",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Image Viewport Area with 8 Handles and Magnifier
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            val boxW = constraints.maxWidth.toFloat()
            val boxH = constraints.maxHeight.toFloat()
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
                        .pointerInput(imageRect) {
                            detectDragGestures(
                                onDragStart = { startOffset ->
                                    val screenP0 = Offset(imageRect.left + quad.p0.x * imageRect.width, imageRect.top + quad.p0.y * imageRect.height)
                                    val screenP1 = Offset(imageRect.left + quad.p1.x * imageRect.width, imageRect.top + quad.p1.y * imageRect.height)
                                    val screenP2 = Offset(imageRect.left + quad.p2.x * imageRect.width, imageRect.top + quad.p2.y * imageRect.height)
                                    val screenP3 = Offset(imageRect.left + quad.p3.x * imageRect.width, imageRect.top + quad.p3.y * imageRect.height)

                                    val screenM0 = Offset((screenP0.x + screenP1.x) / 2f, (screenP0.y + screenP1.y) / 2f)
                                    val screenM1 = Offset((screenP1.x + screenP2.x) / 2f, (screenP1.y + screenP2.y) / 2f)
                                    val screenM2 = Offset((screenP2.x + screenP3.x) / 2f, (screenP2.y + screenP3.y) / 2f)
                                    val screenM3 = Offset((screenP3.x + screenP0.x) / 2f, (screenP3.y + screenP0.y) / 2f)

                                    val handles = listOf(screenP0, screenP1, screenP2, screenP3, screenM0, screenM1, screenM2, screenM3)
                                    val touchTargetRadius = 48.dp.toPx()

                                    val distances = handles.map { hypot(startOffset.x - it.x, startOffset.y - it.y) }
                                    val minIdx = distances.indices.minByOrNull { distances[it] } ?: 0
                                    if (distances[minIdx] <= touchTargetRadius) {
                                        activeHandleIndex = minIdx
                                        activeFingerPos = handles[minIdx]
                                        dragStartPos = startOffset
                                        dragStartQuad = quad
                                    } else {
                                        activeHandleIndex = null
                                        activeFingerPos = null
                                        dragStartPos = null
                                        dragStartQuad = null
                                    }
                                },
                                onDrag = { change, _ ->
                                    val idx = activeHandleIndex ?: return@detectDragGestures
                                    val startPos = dragStartPos ?: return@detectDragGestures
                                    val baseQuad = dragStartQuad ?: return@detectDragGestures
                                    change.consume()
                                    val pos = change.position
                                    isAutoCropOn = false

                                    val dx = (pos.x - startPos.x) / imageRect.width
                                    val dy = (pos.y - startPos.y) / imageRect.height

                                    if (idx in 0..3) {
                                        // Smooth delta-based corner drag (no initial jump on grab)
                                        val updatedQuad = when (idx) {
                                            0 -> {
                                                val nx = (baseQuad.p0.x + dx).coerceIn(0f, 1f)
                                                val ny = (baseQuad.p0.y + dy).coerceIn(0f, 1f)
                                                baseQuad.copy(p0 = Point2D(nx, ny))
                                            }
                                            1 -> {
                                                val nx = (baseQuad.p1.x + dx).coerceIn(0f, 1f)
                                                val ny = (baseQuad.p1.y + dy).coerceIn(0f, 1f)
                                                baseQuad.copy(p1 = Point2D(nx, ny))
                                            }
                                            2 -> {
                                                val nx = (baseQuad.p2.x + dx).coerceIn(0f, 1f)
                                                val ny = (baseQuad.p2.y + dy).coerceIn(0f, 1f)
                                                baseQuad.copy(p2 = Point2D(nx, ny))
                                            }
                                            3 -> {
                                                val nx = (baseQuad.p3.x + dx).coerceIn(0f, 1f)
                                                val ny = (baseQuad.p3.y + dy).coerceIn(0f, 1f)
                                                baseQuad.copy(p3 = Point2D(nx, ny))
                                            }
                                            else -> baseQuad
                                        }
                                        quad = updatedQuad
                                        val activePt = when (idx) {
                                            0 -> updatedQuad.p0
                                            1 -> updatedQuad.p1
                                            2 -> updatedQuad.p2
                                            else -> updatedQuad.p3
                                        }
                                        activeFingerPos = Offset(
                                            imageRect.left + activePt.x * imageRect.width,
                                            imageRect.top + activePt.y * imageRect.height
                                        )
                                    } else {
                                        // Mid-edge drag: moves that entire edge parallel
                                        val updatedQuad = when (idx) {
                                            4 -> { // Top edge: p0 and p1
                                                val p0x = (baseQuad.p0.x + dx).coerceIn(0f, 1f)
                                                val p0y = (baseQuad.p0.y + dy).coerceIn(0f, 1f)
                                                val p1x = (baseQuad.p1.x + dx).coerceIn(0f, 1f)
                                                val p1y = (baseQuad.p1.y + dy).coerceIn(0f, 1f)
                                                baseQuad.copy(p0 = Point2D(p0x, p0y), p1 = Point2D(p1x, p1y))
                                            }
                                            5 -> { // Right edge: p1 and p2
                                                val p1x = (baseQuad.p1.x + dx).coerceIn(0f, 1f)
                                                val p1y = (baseQuad.p1.y + dy).coerceIn(0f, 1f)
                                                val p2x = (baseQuad.p2.x + dx).coerceIn(0f, 1f)
                                                val p2y = (baseQuad.p2.y + dy).coerceIn(0f, 1f)
                                                baseQuad.copy(p1 = Point2D(p1x, p1y), p2 = Point2D(p2x, p2y))
                                            }
                                            6 -> { // Bottom edge: p2 and p3
                                                val p2x = (baseQuad.p2.x + dx).coerceIn(0f, 1f)
                                                val p2y = (baseQuad.p2.y + dy).coerceIn(0f, 1f)
                                                val p3x = (baseQuad.p3.x + dx).coerceIn(0f, 1f)
                                                val p3y = (baseQuad.p3.y + dy).coerceIn(0f, 1f)
                                                baseQuad.copy(p2 = Point2D(p2x, p2y), p3 = Point2D(p3x, p3y))
                                            }
                                            7 -> { // Left edge: p3 and p0
                                                val p3x = (baseQuad.p3.x + dx).coerceIn(0f, 1f)
                                                val p3y = (baseQuad.p3.y + dy).coerceIn(0f, 1f)
                                                val p0x = (baseQuad.p0.x + dx).coerceIn(0f, 1f)
                                                val p0y = (baseQuad.p0.y + dy).coerceIn(0f, 1f)
                                                baseQuad.copy(p3 = Point2D(p3x, p3y), p0 = Point2D(p0x, p0y))
                                            }
                                            else -> baseQuad
                                        }
                                        quad = updatedQuad
                                        activeFingerPos = pos
                                    }
                                },
                                onDragEnd = {
                                    activeHandleIndex = null
                                    activeFingerPos = null
                                    dragStartPos = null
                                    dragStartQuad = null
                                },
                                onDragCancel = {
                                    activeHandleIndex = null
                                    activeFingerPos = null
                                    dragStartPos = null
                                    dragStartQuad = null
                                }
                            )
                        }
                ) {
                    val p0 = Offset(imageRect.left + quad.p0.x * imageRect.width, imageRect.top + quad.p0.y * imageRect.height)
                    val p1 = Offset(imageRect.left + quad.p1.x * imageRect.width, imageRect.top + quad.p1.y * imageRect.height)
                    val p2 = Offset(imageRect.left + quad.p2.x * imageRect.width, imageRect.top + quad.p2.y * imageRect.height)
                    val p3 = Offset(imageRect.left + quad.p3.x * imageRect.width, imageRect.top + quad.p3.y * imageRect.height)

                    val m0 = Offset((p0.x + p1.x) / 2f, (p0.y + p1.y) / 2f)
                    val m1 = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
                    val m2 = Offset((p2.x + p3.x) / 2f, (p2.y + p3.y) / 2f)
                    val m3 = Offset((p3.x + p0.x) / 2f, (p3.y + p0.y) / 2f)

                    val allHandles = listOf(p0, p1, p2, p3, m0, m1, m2, m3)

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

                    // 3. Draw 8 Handles (4 Corner handles + 4 Mid-edge handles)
                    // Corner handles: 22 dp white circle with 3 dp green ring (or green filled if active)
                    for (i in 0..3) {
                        val pt = allHandles[i]
                        val isDragging = (i == activeHandleIndex)
                        val radius = if (isDragging) 13.dp.toPx() else 11.dp.toPx()
                        val ringWidth = 3.dp.toPx()

                        if (isDragging) {
                            drawCircle(color = Color.White, radius = radius + 2.dp.toPx(), center = pt)
                            drawCircle(color = brandGreen, radius = radius, center = pt)
                        } else {
                            drawCircle(color = Color.White, radius = radius, center = pt)
                            drawCircle(color = outlineColor, radius = radius, center = pt, style = Stroke(width = ringWidth))
                        }
                    }

                    // Mid-edge handles: 16 dp white circle with 2.5 dp green ring
                    for (i in 4..7) {
                        val pt = allHandles[i]
                        val isDragging = (i == activeHandleIndex)
                        val radius = if (isDragging) 10.dp.toPx() else 8.dp.toPx()
                        val ringWidth = 2.5.dp.toPx()

                        if (isDragging) {
                            drawCircle(color = Color.White, radius = radius + 2.dp.toPx(), center = pt)
                            drawCircle(color = brandGreen, radius = radius, center = pt)
                        } else {
                            drawCircle(color = Color.White, radius = radius, center = pt)
                            drawCircle(color = outlineColor, radius = radius, center = pt, style = Stroke(width = ringWidth))
                        }
                    }

                    // 4. Magnifier Loupe (zoomed 2.6x, 116 dp diameter, away from finger)
                    activeHandleIndex?.let { idx ->
                        val targetPt = allHandles[idx]
                        val finger = activeFingerPos ?: targetPt

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
                            translate(-targetPt.x, -targetPt.y)
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

        // Helper line under image per FIXES-2 §4
        Text(
            text = "Drag the dots to the page corners, or adjust later with the crop tool",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        )

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
                modifier = Modifier.padding(bottom = 6.dp, start = 24.dp, end = 24.dp)
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

        // Action row: single line per FIXES-2 §4 (Auto Crop toggle left, Spacer, Retake text button, Continue filled green)
        Surface(
            color = bottomBarBg,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Auto Crop toggle on left: radio circle + text
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            val next = !isAutoCropOn
                            isAutoCropOn = next
                            quad = if (next) baseDetectedQuad.rotateStepsCw(rotation / 90) else Quad.fullQuad()
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .border(2.dp, if (isAutoCropOn) brandGreen else Color.Gray, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isAutoCropOn) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(brandGreen, CircleShape)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Auto Crop",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                if (mode == CropScreenMode.EDIT_PAGE) {
                    TextButton(
                        onClick = onCancel,
                        modifier = Modifier.height(44.dp)
                    ) {
                        Text("Cancel", color = Color.White, fontSize = 15.sp)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            if (isValid) {
                                val unrotatedQuad = quad.toUnrotatedSpace(rotation)
                                onApply(unrotatedQuad, rotation)
                            }
                        },
                        enabled = isValid,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = brandGreen,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Text("Apply", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                } else {
                    TextButton(
                        onClick = onRetake,
                        modifier = Modifier.height(44.dp)
                    ) {
                        Text("Retake", color = Color.White, fontSize = 15.sp)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            if (isValid) {
                                val unrotatedQuad = quad.toUnrotatedSpace(rotation)
                                onDone(unrotatedQuad, rotation)
                            }
                        },
                        enabled = isValid,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = brandGreen,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Text("Continue", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}
