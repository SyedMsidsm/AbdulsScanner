package com.syedmsidsm.abdulsscanner.ui.screens.labs

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syedmsidsm.abdulsscanner.R
import com.syedmsidsm.abdulsscanner.data.settings.ArabicOcrEngine
import com.syedmsidsm.abdulsscanner.data.settings.SettingsRepository
import com.syedmsidsm.abdulsscanner.domain.imaging.BitmapIo
import com.syedmsidsm.abdulsscanner.domain.ocr.MlKitLatinEngine
import com.syedmsidsm.abdulsscanner.domain.ocr.PaddleEngine
import com.syedmsidsm.abdulsscanner.domain.ocr.TesseractEngine
import com.syedmsidsm.abdulsscanner.ui.theme.CardShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class OcrTestResult(
    val engineName: String,
    val text: String,
    val timeMs: Long,
    val rating: Int = 0
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArabicOcrLabsScreen(
    tesseractEngine: TesseractEngine,
    paddleEngine: PaddleEngine,
    mlKitLatinEngine: MlKitLatinEngine,
    settingsRepository: SettingsRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentEngine by settingsRepository.arabicEngine.collectAsState(initial = ArabicOcrEngine.TESSERACT.name)

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isRunningTest by remember { mutableStateOf(false) }
    var tesseractResult by remember { mutableStateOf<OcrTestResult?>(null) }
    var paddleResult by remember { mutableStateOf<OcrTestResult?>(null) }
    var tesseractRating by remember { mutableIntStateOf(4) }
    var paddleRating by remember { mutableIntStateOf(4) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            scope.launch {
                isRunningTest = true
                try {
                    val bitmap = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use {
                            BitmapFactory.decodeStream(it)
                        }
                    }
                    if (bitmap != null) {
                        // 1. Run Tesseract ara+eng
                        val startTess = System.currentTimeMillis()
                        val tessText = tesseractEngine.recognize(bitmap)
                        val endTess = System.currentTimeMillis()
                        tesseractResult = OcrTestResult(
                            engineName = "Tesseract",
                            text = tessText,
                            timeMs = endTess - startTess
                        )

                        // 2. Run PaddleOCR
                        val startPaddle = System.currentTimeMillis()
                        val paddleText = paddleEngine.recognize(bitmap)
                        val endPaddle = System.currentTimeMillis()
                        paddleResult = OcrTestResult(
                            engineName = "PaddleOCR",
                            text = paddleText,
                            timeMs = endPaddle - startPaddle
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isRunningTest = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.labs_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.labs_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Pick sample button
            Button(
                onClick = { photoPickerLauncher.launch("image/*") },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small
            ) {
                Icon(Icons.Outlined.Image, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Select Arabic Document to Test")
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isRunningTest) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Testing engines on page…")
                    }
                }
            }

            // Results Section
            if (tesseractResult != null) {
                ResultCard(
                    title = "Tesseract (ara+eng)",
                    result = tesseractResult!!,
                    rating = tesseractRating,
                    onRatingChanged = { tesseractRating = it }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (paddleResult != null) {
                ResultCard(
                    title = "PaddleOCR",
                    result = paddleResult!!,
                    rating = paddleRating,
                    onRatingChanged = { paddleRating = it }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Selection Controls
            Text(
                text = "Active Engine: $currentEngine",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            settingsRepository.setArabicEngine(ArabicOcrEngine.TESSERACT.name)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (currentEngine == ArabicOcrEngine.TESSERACT.name) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(stringResource(R.string.labs_use_tesseract))
                }

                Button(
                    onClick = {
                        scope.launch {
                            settingsRepository.setArabicEngine(ArabicOcrEngine.PADDLE.name)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (currentEngine == ArabicOcrEngine.PADDLE.name) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(stringResource(R.string.labs_use_paddle))
                }
            }
        }
    }
}

@Composable
fun ResultCard(
    title: String,
    result: OcrTestResult,
    rating: Int,
    onRatingChanged: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${result.timeMs} ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp)
            ) {
                Text(
                    text = if (result.text.isNotBlank()) result.text else "(No text recognized)",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        textDirection = TextDirection.Content
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 1-5 Star Rating Row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Quality: ", style = MaterialTheme.typography.labelMedium)
                for (star in 1..5) {
                    Icon(
                        imageVector = if (star <= rating) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        contentDescription = "Star $star",
                        tint = if (star <= rating) Color(0xFFFFB800) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { onRatingChanged(star) }
                    )
                }
            }
        }
    }
}
