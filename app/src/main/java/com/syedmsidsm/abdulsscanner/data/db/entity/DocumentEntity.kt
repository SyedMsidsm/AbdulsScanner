package com.syedmsidsm.abdulsscanner.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class DocumentKind {
    SCAN, ID_CARD, BOOK, COLLAGE, IMPORT_IMAGES, IMPORT_PDF
}

enum class PageSizeSetting {
    AUTO, A4, LETTER
}

@Entity(
    tableName = "documents",
    indices = [
        Index("deletedAt"),
        Index("updatedAt"),
        Index("name")
    ]
)
data class DocumentEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val kind: String = DocumentKind.SCAN.name,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val pageCount: Int = 0,
    val coverPageId: String? = null,
    val pageSize: String = PageSizeSetting.AUTO.name,
    val locked: Boolean = false,
    val deletedAt: Long? = null,
    val pdfBuiltAt: Long? = null,
    val mirrorUri: String? = null
)
