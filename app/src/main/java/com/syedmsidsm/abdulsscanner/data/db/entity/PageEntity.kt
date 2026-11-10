package com.syedmsidsm.abdulsscanner.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class PageFilter {
    ORIGINAL, ENHANCED, GRAYSCALE, BW
}

enum class OcrStatus {
    PENDING, DONE, FAILED
}

enum class OcrLanguage {
    EN, AR, BOTH
}

@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("documentId"),
        Index("position")
    ]
)
data class PageEntity(
    @PrimaryKey
    val id: String,
    val documentId: String,
    val position: Int,
    val sourcePath: String,
    val originalPath: String? = null,
    val renderedPath: String,
    val thumbPath: String,
    val widthPx: Int,
    val heightPx: Int,
    val cropQuad: String? = null,
    val rotation: Int = 0,
    val filter: String = PageFilter.ENHANCED.name,
    val brightness: Int = 0,
    val contrast: Int = 0,
    val overlays: String = "[]",
    val ocrText: String? = null,
    val ocrLang: String? = null,
    val ocrStatus: String = OcrStatus.PENDING.name,
    val createdAt: Long = System.currentTimeMillis()
)
