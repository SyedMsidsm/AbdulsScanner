package com.syedmsidsm.abdulsscanner.ui.screens.tools

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.view.MotionEvent
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.syedmsidsm.abdulsscanner.data.db.entity.SignatureEntity
import com.syedmsidsm.abdulsscanner.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.util.UUID

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SignaturePad(
    container: AppContainer,
    onSignatureSelected: (signatureId: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val savedSignatures by container.signatureRepository.observeSignatures().collectAsState(initial = emptyList())

    // 0 = Black, 1 = Blue
    var inkColorIndex by remember { mutableStateOf(0) }
    val inkColorHex = if (inkColorIndex == 0) "#000000" else "#1E88E5"
    val composeInkColor = if (inkColorIndex == 0) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color(0xFF1E88E5)

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // Native Path for smooth Bézier drawing and exporting
    val currentPath = remember { Path() }
    val pathPoints = remember { mutableStateListOf<Offset>() }
    var redrawTrigger by remember { mutableStateOf(0) }
    var hasStrokes by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Saved signatures row
        if (savedSignatures.isNotEmpty()) {
            Text(
                text = "Saved Signatures (${savedSignatures.size}/5):",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(savedSignatures) { sig ->
                    val file = container.fileStore.getSignatureFile(sig.id)
                    Card(
                        modifier = Modifier
                            .width(130.dp)
                            .height(65.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                            .clickable { onSignatureSelected(sig.id) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = file,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(6.dp)
                            )
                            IconButton(
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        container.signatureRepository.deleteSignature(sig.id)
                                    }
                                },
                                modifier = Modifier
                                    .size(24.dp)
                                    .align(Alignment.TopEnd)
                            ) {
                                Icon(
                                    Icons.Outlined.Clear,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Draw new signature section
        Text(
            text = "Draw Signature:",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Ink color chips
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Ink Color:", style = MaterialTheme.typography.bodyMedium)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { inkColorIndex = 0 }
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(androidx.compose.ui.graphics.Color.Black, CircleShape)
                        .border(
                            if (inkColorIndex == 0) 2.dp else 1.dp,
                            if (inkColorIndex == 0) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.LightGray,
                            CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Black", fontWeight = if (inkColorIndex == 0) FontWeight.Bold else FontWeight.Normal)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { inkColorIndex = 1 }
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(androidx.compose.ui.graphics.Color(0xFF1E88E5), CircleShape)
                        .border(
                            if (inkColorIndex == 1) 2.dp else 1.dp,
                            if (inkColorIndex == 1) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.LightGray,
                            CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Blue", fontWeight = if (inkColorIndex == 1) FontWeight.Bold else FontWeight.Normal)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Canvas aspect 2.5:1
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2.5f)
                .clip(RoundedCornerShape(10.dp))
                .background(androidx.compose.ui.graphics.Color.White)
                .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                .onSizeChanged { canvasSize = it }
        ) {
            var lastX = 0f
            var lastY = 0f

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInteropFilter { event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                currentPath.moveTo(event.x, event.y)
                                lastX = event.x
                                lastY = event.y
                                pathPoints.add(Offset(event.x, event.y))
                                hasStrokes = true
                                redrawTrigger++
                                true
                            }
                            MotionEvent.ACTION_MOVE -> {
                                for (i in 0 until event.historySize) {
                                    val hx = event.getHistoricalX(i)
                                    val hy = event.getHistoricalY(i)
                                    currentPath.quadTo(lastX, lastY, (hx + lastX) / 2f, (hy + lastY) / 2f)
                                    lastX = hx
                                    lastY = hy
                                    pathPoints.add(Offset(hx, hy))
                                }
                                currentPath.quadTo(lastX, lastY, (event.x + lastX) / 2f, (event.y + lastY) / 2f)
                                lastX = event.x
                                lastY = event.y
                                pathPoints.add(Offset(event.x, event.y))
                                redrawTrigger++
                                true
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                currentPath.lineTo(event.x, event.y)
                                pathPoints.add(Offset(event.x, event.y))
                                redrawTrigger++
                                true
                            }
                            else -> false
                        }
                    }
            ) {
                // Read trigger to redraw on each stroke move
                if (redrawTrigger >= 0) {
                    drawPath(
                        path = currentPath.asComposePath(),
                        color = composeInkColor,
                        style = Stroke(width = 6.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
                    )
                }
            }

            if (!hasStrokes) {
                Text(
                    text = "Sign here with finger",
                    style = MaterialTheme.typography.bodyMedium,
                    color = androidx.compose.ui.graphics.Color.LightGray,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(
                onClick = {
                    currentPath.reset()
                    pathPoints.clear()
                    hasStrokes = false
                    redrawTrigger++
                },
                enabled = hasStrokes
            ) {
                Icon(Icons.Outlined.Clear, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Clear")
            }

            Button(
                onClick = {
                    if (!hasStrokes || canvasSize.width <= 0 || canvasSize.height <= 0) {
                        Toast.makeText(context, "Please sign first", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    scope.launch {
                        try {
                            val sigId = UUID.randomUUID().toString()
                            val trimmedFile = withContext(Dispatchers.IO) {
                                // Render path to bitmap
                                val fullBmp = Bitmap.createBitmap(canvasSize.width, canvasSize.height, Bitmap.Config.ARGB_8888)
                                val canvas = android.graphics.Canvas(fullBmp)
                                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                    color = if (inkColorIndex == 0) Color.BLACK else Color.parseColor("#1E88E5")
                                    strokeWidth = 6f * context.resources.displayMetrics.density
                                    style = Paint.Style.STROKE
                                    strokeCap = Paint.Cap.ROUND
                                    strokeJoin = Paint.Join.ROUND
                                }
                                canvas.drawPath(currentPath, paint)

                                // Find ink bounding box
                                var minX = fullBmp.width
                                var minY = fullBmp.height
                                var maxX = 0
                                var maxY = 0

                                val pixels = IntArray(fullBmp.width * fullBmp.height)
                                fullBmp.getPixels(pixels, 0, fullBmp.width, 0, 0, fullBmp.width, fullBmp.height)

                                for (y in 0 until fullBmp.height) {
                                    for (x in 0 until fullBmp.width) {
                                        val pixel = pixels[y * fullBmp.width + x]
                                        if (Color.alpha(pixel) > 10) {
                                            if (x < minX) minX = x
                                            if (x > maxX) maxX = x
                                            if (y < minY) minY = y
                                            if (y > maxY) maxY = y
                                        }
                                    }
                                }

                                if (maxX < minX || maxY < minY) {
                                    minX = 0; minY = 0; maxX = fullBmp.width - 1; maxY = fullBmp.height - 1
                                }

                                // 16 px padding
                                val pad = 16
                                val cropX = (minX - pad).coerceAtLeast(0)
                                val cropY = (minY - pad).coerceAtLeast(0)
                                val cropW = ((maxX - minX) + 2 * pad).coerceAtMost(fullBmp.width - cropX).coerceAtLeast(10)
                                val cropH = ((maxY - minY) + 2 * pad).coerceAtMost(fullBmp.height - cropY).coerceAtLeast(10)

                                val trimmedBmp = Bitmap.createBitmap(fullBmp, cropX, cropY, cropW, cropH)
                                fullBmp.recycle()

                                val sigFile = container.fileStore.getSignatureFile(sigId)
                                FileOutputStream(sigFile).use { out ->
                                    trimmedBmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                                }
                                trimmedBmp.recycle()

                                container.signatureRepository.saveSignature(
                                    SignatureEntity(
                                        id = sigId,
                                        path = sigFile.absolutePath,
                                        createdAt = System.currentTimeMillis()
                                    )
                                )
                                sigId
                            }
                            onSignatureSelected(trimmedFile)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error saving signature: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = hasStrokes
            ) {
                Icon(Icons.Outlined.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Use Signature")
            }
        }
    }
}
