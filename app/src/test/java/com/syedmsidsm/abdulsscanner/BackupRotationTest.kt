package com.syedmsidsm.abdulsscanner

import com.syedmsidsm.abdulsscanner.domain.backup.BackupWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class BackupRotationTest {

    @Test
    fun testGenerateBackupFileNamePrefix() {
        val name = BackupWriter.generateBackupFileName(Date())
        assertTrue("Expected new prefix AbulsScanner-backup- but got: $name", name.startsWith("AbulsScanner-backup-"))
        assertTrue("Expected .zip suffix but got: $name", name.endsWith(".zip"))
    }

    @Test
    fun testDualPrefixFilterAndRotation() {
        val files = listOf(
            "AbdulsScanner-backup-20261101-1000.zip",
            "AbdulsScanner-backup-20261105-1400.zip",
            "AbulsScanner-backup-20261108-0900.zip",
            "AbulsScanner-backup-20261110-1200.zip",
            "other-document.zip",
            "unrelated.txt"
        )

        // Filter matching both prefixes
        val validBackups = files.filter { name ->
            (name.startsWith("AbulsScanner-backup-") || name.startsWith("AbdulsScanner-backup-")) && name.endsWith(".zip")
        }
        assertEquals(4, validBackups.size)

        // Sort by date suffix descending (newest first)
        val sorted = validBackups.sortedByDescending { name ->
            name.removePrefix("AbulsScanner-backup-")
                .removePrefix("AbdulsScanner-backup-")
        }

        assertEquals("AbulsScanner-backup-20261110-1200.zip", sorted[0])
        assertEquals("AbulsScanner-backup-20261108-0900.zip", sorted[1])
        assertEquals("AbdulsScanner-backup-20261105-1400.zip", sorted[2])
        assertEquals("AbdulsScanner-backup-20261101-1000.zip", sorted[3])

        // Keep last 3, delete the rest
        val keepCount = 3
        val toDelete = sorted.drop(keepCount)
        assertEquals(1, toDelete.size)
        assertEquals("AbdulsScanner-backup-20261101-1000.zip", toDelete[0])
    }
}
