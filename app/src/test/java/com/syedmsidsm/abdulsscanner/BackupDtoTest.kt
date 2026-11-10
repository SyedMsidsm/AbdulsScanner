package com.syedmsidsm.abdulsscanner

import com.syedmsidsm.abdulsscanner.data.backup.BackupData
import com.syedmsidsm.abdulsscanner.data.backup.BackupDocumentDto
import com.syedmsidsm.abdulsscanner.data.backup.BackupDocumentTagDto
import com.syedmsidsm.abdulsscanner.data.backup.BackupManifest
import com.syedmsidsm.abdulsscanner.data.backup.BackupPageDto
import com.syedmsidsm.abdulsscanner.data.backup.BackupQrDto
import com.syedmsidsm.abdulsscanner.data.backup.BackupSignatureDto
import com.syedmsidsm.abdulsscanner.data.backup.BackupTagDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupDtoTest {

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    @Test
    fun manifestSerializationRoundTrip() {
        val manifest = BackupManifest(
            format = "abdulsscanner-backup",
            formatVersion = 1,
            appVersionCode = 200L,
            createdAt = 1762770000000L,
            documentCount = 42,
            pageCount = 131,
            fileCount = 280
        )

        val jsonString = json.encodeToString(manifest)
        val deserialized = json.decodeFromString<BackupManifest>(jsonString)

        assertEquals(manifest, deserialized)
        assertEquals("abdulsscanner-backup", deserialized.format)
        assertEquals(1, deserialized.formatVersion)
        assertEquals(42, deserialized.documentCount)
        assertEquals(131, deserialized.pageCount)
        assertEquals(280, deserialized.fileCount)
    }

    @Test
    fun manifestValidationRejectsNewerVersion() {
        val futureJson = """
            {
              "format": "abdulsscanner-backup",
              "formatVersion": 2,
              "appVersionCode": 300,
              "createdAt": 1762770000000,
              "documentCount": 10,
              "pageCount": 20,
              "fileCount": 40
            }
        """.trimIndent()

        val manifest = json.decodeFromString<BackupManifest>(futureJson)
        assertEquals(2, manifest.formatVersion)

        // Validation rule per section 12.3: reject formatVersion > CURRENT_VERSION
        val isSupported = manifest.formatVersion <= BackupManifest.CURRENT_VERSION
        assertEquals(false, isSupported)
    }

    @Test
    fun manifestValidationRejectsInvalidFormatName() {
        val invalidJson = """
            {
              "format": "random-scanner-backup",
              "formatVersion": 1,
              "appVersionCode": 200,
              "createdAt": 1762770000000,
              "documentCount": 10,
              "pageCount": 20,
              "fileCount": 40
            }
        """.trimIndent()

        val manifest = json.decodeFromString<BackupManifest>(invalidJson)
        val isValidFormat = manifest.format == BackupManifest.FORMAT_NAME
        assertEquals(false, isValidFormat)
    }

    @Test
    fun backupDataSerializationRoundTrip() {
        val original = BackupData(
            documents = listOf(
                BackupDocumentDto(
                    id = "doc-1",
                    name = "فاتورة الكهرباء November 2026",
                    createdAt = 1762770000000L,
                    updatedAt = 1762770500000L,
                    kind = "DOCS",
                    pageSize = "A4",
                    isLocked = false,
                    deletedAt = null
                ),
                BackupDocumentDto(
                    id = "doc-2",
                    name = "Passport Copy",
                    createdAt = 1762771000000L,
                    updatedAt = 1762771500000L,
                    kind = "ID_CARD",
                    pageSize = "AUTO",
                    isLocked = true,
                    deletedAt = 1762772000000L
                )
            ),
            pages = listOf(
                BackupPageDto(
                    id = "page-1",
                    docId = "doc-1",
                    position = 0,
                    filter = "BW",
                    rotation = 90,
                    cropQuad = "[[10,10],[100,10],[100,200],[10,200]]",
                    brightness = 15,
                    contrast = -10,
                    overlays = "[{\"type\":\"WATERMARK\",\"text\":\"Draft\"}]",
                    ocrText = "شركة الكهرباء والماء\nالمبلغ المستحق: 250",
                    ocrLang = "AR",
                    ocrStatus = "DONE"
                ),
                BackupPageDto(
                    id = "page-2",
                    docId = "doc-1",
                    position = 1,
                    filter = "ENHANCED",
                    rotation = 0,
                    cropQuad = null,
                    brightness = 0,
                    contrast = 0,
                    overlays = "[]",
                    ocrText = null,
                    ocrLang = null,
                    ocrStatus = "PENDING"
                )
            ),
            tags = listOf(
                BackupTagDto(id = 1, name = "Bills", color = "#13A06F", createdAt = 1762770000000L),
                BackupTagDto(id = 2, name = "هوية", color = "#3B82F6", createdAt = 1762770000000L)
            ),
            documentTags = listOf(
                BackupDocumentTagDto(docId = "doc-1", tagId = 1),
                BackupDocumentTagDto(docId = "doc-2", tagId = 2)
            ),
            qrHistory = listOf(
                BackupQrDto(
                    id = 101L,
                    rawValue = "WIFI:T:WPA;S:HomeWifi;P:pass1234;;",
                    format = "QR_CODE",
                    valueType = "WIFI",
                    source = "CAMERA",
                    createdAt = 1762770000000L
                )
            ),
            signatures = listOf(
                BackupSignatureDto(
                    id = "sig-1",
                    createdAt = 1762770000000L
                )
            )
        )

        val jsonString = json.encodeToString(original)
        val deserialized = json.decodeFromString<BackupData>(jsonString)

        assertEquals(original, deserialized)
        assertEquals(2, deserialized.documents.size)
        assertEquals("فاتورة الكهرباء November 2026", deserialized.documents[0].name)
        assertEquals(2, deserialized.pages.size)
        assertEquals("شركة الكهرباء والماء\nالمبلغ المستحق: 250", deserialized.pages[0].ocrText)
        assertEquals(2, deserialized.tags.size)
        assertEquals("هوية", deserialized.tags[1].name)
        assertEquals(1, deserialized.qrHistory.size)
        assertEquals("WIFI", deserialized.qrHistory[0].valueType)
        assertEquals(1, deserialized.signatures.size)
        assertEquals("sig-1", deserialized.signatures[0].id)
    }
}
