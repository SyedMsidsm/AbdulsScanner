package com.syedmsidsm.abdulsscanner.ui.screens.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syedmsidsm.abdulsscanner.domain.imaging.BitmapIo
import com.syedmsidsm.abdulsscanner.domain.imaging.PerspectiveCrop
import com.syedmsidsm.abdulsscanner.domain.imaging.Point2D
import com.syedmsidsm.abdulsscanner.domain.imaging.Quad
import java.io.File
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun CropAfterScanView(
    rawImageFile: File,
    initialQuad: Quad?,
    pageNumber: Int,
    totalPagesInBatch: Int,
    onRetake: () -> Unit,
    onNextPage: (finalQuad: Quad, rotation: Int) -> Unit,
    onDone: (finalQuad: Quad, rotation: Int) -> Unit
) {
    var rotation by remember { mutableIntStateOf(0) }
    val baseBitmap = remember(rawImageFile) {
        BitmapIo.decodeUpright(rawImageFile, maxLongSide = 2000)
    }

    val displayBitmap = remember(baseBitmap, rotation) {
        if (rotation % 360 == 0) {
            baseBitmap
        } else {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(baseBitmap, 0, 0, baseBitmap.width, baseBitmap.height, matrix, true)
        }
    }

    var quad by remember(initialQuad) {
        mutableStateOf(
            initialQuad ?: Quad(
                Point2D(0.05f, 0.05f),
                Point2D(0.95f, 0.05f),
                Point2D(0.95f, 0.95f),
                Point2D(0.05f, 0.95f)
            )
        )
    }

    var activeHandleIndex by remember { mutableStateOf<Int?>(null) }
    val isValid = remember(quad) { PerspectiveCrop.isValidQuad(quad) }

    val brandGreen = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error

    val imageBitmap = remember(displayBitmap) { displayBitmap.asImageBitmap() }
    val imageAspect = displayBitmap.width.toFloat() / displayBitmap.height.toFloat()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar: Page {n} · adjust crop
            Surface(
                color = Color.Black,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Page $pageNumber · adjust crop",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Interactive Crop Viewport with Loupe
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                val boxWidth = maxWidth.value
                val boxHeight = maxHeight.value
                val boxAspect = boxWidth / boxHeight

                val (viewWidth, viewHeight) = if (imageAspect > boxAspect) {
                    boxWidth to (boxWidth / imageAspect)
                } else {
                    (boxHeight * imageAspect) to boxHeight
                }

                Box(
                    modifier = Modifier
                        .width(viewWidth.dp)
                        .aspectRatio(imageAspect)
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        val normX = (offset.x / size.width).coerceIn(0f, 1f)
                                        val normY = (offset.y / size.height).coerceIn(0f, 1f)
                                        val touch = Point2D(normX, normY)

                                        val distances = listOf(
                                            hypot((touch.x - quad.p0.x).toDouble(), (touch.y - quad.p0.y).toDouble()).toFloat(),
                                            hypot((touch.x - quad.p1.x).toDouble(), (touch.y - quad.p1.y).toDouble()).toFloat(),
                                            hypot((touch.x - quad.p2.x).toDouble(), (touch.y - quad.p2.y).toDouble()).toFloat(),
                                            hypot((touch.x - quad.p3.x).toDouble(), (touch.y - quad.p3.y).toDouble()).toFloat()
                                        )

                                        val minIdx = distances.indices.minByOrNull { distances[it] } ?: 0
                                        activeHandleIndex = if (distances[minIdx] < 0.20f) minIdx else null
                                    },
                                    onDrag = { change, _ ->
                                        val idx = activeHandleIndex ?: return@detectDragGestures
                                        change.consume()
                                        val normX = (change.position.x / size.width).coerceIn(0f, 1f)
                                        val normY = (change.position.y / size.height).coerceIn(0f, 1f)
                                        val pt = Point2D(normX, normY)

                                        quad = when (idx) {
                                            0 -> quad.copy(p0 = pt)
                                            1 -> quad.copy(p1 = pt)
                                            2 -> quad.copy(p2 = pt)
                                            3 -> quad.copy(p3 = pt)
                                            else -> quad
                                        }
                                    },
                                    onDragEnd = {
                                        activeHandleIndex = null
                                    },
                                    onDragCancel = {
                                        activeHandleIndex = null
                                    }
                                )
                            }
                    ) {
                        val canvasW = size.width
                        val canvasH = size.height

                        drawImage(
                            image = imageBitmap,
                            dstSize = IntSize(canvasW.roundToInt(), canvasH.roundToInt())
                        )

                        val p0 = Offset(quad.p0.x * canvasW, quad.p0.y * canvasH)
                        val p1 = Offset(quad.p1.x * canvasW, quad.p1.y * canvasH)
                        val p2 = Offset(quad.p2.x * canvasW, quad.p2.y * canvasH)
                        val p3 = Offset(quad.p3.x * canvasW, quad.p3.y * canvasH)

                        val outlineColor = if (isValid) brandGreen else errorColor

                        val path = Path().apply {
                            moveTo(p0.x, p0.y)
                            lineTo(p1.x, p1.y)
                            lineTo(p2.x, p2.y)
                            lineTo(p3.x, p3.y)
                            close()
                        }
                        drawPath(path, outlineColor, style = Stroke(width = 3.dp.toPx()))

                        val handleRadius = 14.dp.toPx()
                        val points = listOf(p0, p1, p2, p3)
                        points.forEachIndexed { i, pt ->
                            val isDragging = i == activeHandleIndex
                            drawCircle(
                                color = outlineColor,
                                radius = if (isDragging) handleRadius * 1.3f else handleRadius,
                                center = pt
                            )
                            drawCircle(
                                color = Color.White,
                                radius = (if (isDragging) handleRadius * 1.3f else handleRadius) * 0.6f,
                                center = pt
                            )
                        }

                        // Magnifier loupe
                        activeHandleIndex?.let { idx ->
                            val activePt = points[idx]
                            val loupeRadius = 45.dp.toPx()
                            val loupeCenter = Offset(
                                x = activePt.x.coerceIn(loupeRadius, canvasW - loupeRadius),
                                y = (activePt.y - 70.dp.toPx()).coerceAtLeast(loupeRadius)
                            )

                            drawCircle(
                                color = Color.White,
                                radius = loupeRadius + 3.dp.toPx(),
                                center = loupeCenter
                            )
                            drawCircle(
                                color = outlineColor,
                                radius = loupeRadius,
                                center = loupeCenter
                            )
                            drawLine(
                                color = Color.White,
                                start = Offset(loupeCenter.x - 12.dp.toPx(), loupeCenter.y),
                                end = Offset(loupeCenter.x + 12.dp.toPx(), loupeCenter.y),
                                strokeWidth = 2.dp.toPx()
                            )
                            drawLine(
                                color = Color.White,
                                start = Offset(loupeCenter.x, loupeCenter.y - 12.dp.toPx()),
                                end = Offset(loupeCenter.x, loupeCenter.y + 12.dp.toPx()),
                                strokeWidth = 2.dp.toPx()
                            )
                        }
                    }
                }
            }

            // Bottom Actions Bar (Retake · Rotate · Next page · Done)
            Surface(
                color = Color.Black,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Retake
                    OutlinedButton(
                        onClick = onRetake,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Retake", fontSize = 13.sp)
                    }

                    // Rotate
                    OutlinedButton(
                        onClick = { rotation = (rotation + 90) % 360 },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.RotateRight, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Rotate", fontSize = 13.sp)
                    }

                    // Next page
                    OutlinedButton(
                        onClick = { if (isValid) onNextPage(quad, rotation) },
                        enabled = isValid,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Next", fontSize = 13.sp)
                    }

                    // Done (n pages)
                    Button(
                        onClick = { if (isValid) onDone(quad, rotation) },
                        enabled = isValid,
                        colors = ButtonDefaults.buttonColors(containerColor = brandGreen)
                    ) {
                        Text(
                            text = "Done ($totalPagesInBatch)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
