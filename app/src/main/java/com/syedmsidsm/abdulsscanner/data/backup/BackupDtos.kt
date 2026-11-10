package com.syedmsidsm.abdulsscanner.data.backup

import kotlinx.serialization.Serializable

@Serializable
data class BackupManifest(
    val format: String = FORMAT_NAME,
    val formatVersion: Int = CURRENT_VERSION,
    val appVersionCode: Long,
    val createdAt: Long,
    val documentCount: Int,
    val pageCount: Int,
    val fileCount: Int
) {
    companion object {
        const val FORMAT_NAME = "abdulsscanner-backup"
        const val CURRENT_VERSION = 1
    }
}

@Serializable
data class BackupData(
    val documents: List<BackupDocumentDto> = emptyList(),
    val pages: List<BackupPageDto> = emptyList(),
    val tags: List<BackupTagDto> = emptyList(),
    val documentTags: List<BackupDocumentTagDto> = emptyList(),
    val qrHistory: List<BackupQrDto> = emptyList(),
    val signatures: List<BackupSignatureDto> = emptyList()
)

@Serializable
data class BackupDocumentDto(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val kind: String = "DOCS",
    val pageSize: String = "AUTO",
    val isLocked: Boolean = false,
    val deletedAt: Long? = null
)

@Serializable
data class BackupPageDto(
    val id: String,
    val docId: String,
    val position: Int,
    val filter: String = "ENHANCED",
    val rotation: Int = 0,
    val cropQuad: String? = null,
    val brightness: Int = 0,
    val contrast: Int = 0,
    val overlays: String = "[]",
    val ocrText: String? = null,
    val ocrLang: String? = null,
    val ocrStatus: String = "PENDING"
)

@Serializable
data class BackupTagDto(
    val id: Long,
    val name: String,
    val color: String = "#13A06F",
    val createdAt: Long
)

@Serializable
data class BackupDocumentTagDto(
    val docId: String,
    val tagId: Long
)

@Serializable
data class BackupQrDto(
    val id: Long,
    val rawValue: String,
    val format: String = "QR_CODE",
    val valueType: String = "TEXT",
    val source: String = "CAMERA",
    val createdAt: Long
)

@Serializable
data class BackupSignatureDto(
    val id: String,
    val createdAt: Long
)
