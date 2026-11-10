package com.syedmsidsm.abdulsscanner.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class QrValueType {
    URL, WIFI, TEXT, PHONE, EMAIL, SMS, CONTACT, GEO, OTHER
}

enum class QrSource {
    CAMERA, IMAGE
}

@Entity(tableName = "qr_history")
data class QrHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val rawValue: String,
    val format: String,
    val valueType: String = QrValueType.TEXT.name,
    val source: String = QrSource.CAMERA.name,
    val scannedAt: Long = System.currentTimeMillis()
)
