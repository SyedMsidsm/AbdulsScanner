package com.syedmsidsm.abdulsscanner.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),      // Chips, small controls
    medium = RoundedCornerShape(12.dp),    // Cards, dialogs
    large = RoundedCornerShape(16.dp),     // Bottom sheets top corners
    extraLarge = RoundedCornerShape(24.dp)
)

val CardShape = RoundedCornerShape(12.dp)
val ChipShape = RoundedCornerShape(8.dp)
val BottomSheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
val CameraFabShape = RoundedCornerShape(32.dp)
