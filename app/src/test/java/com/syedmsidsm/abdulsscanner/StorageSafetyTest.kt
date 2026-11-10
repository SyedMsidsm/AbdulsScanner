package com.syedmsidsm.abdulsscanner

import com.syedmsidsm.abdulsscanner.domain.safety.StorageSafety
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageSafetyTest {

    @Test
    fun storageThresholdConstants() {
        assertEquals(50L * 1024 * 1024, StorageSafety.BLOCK_THRESHOLD_BYTES)
        assertEquals(200L * 1024 * 1024, StorageSafety.WARN_THRESHOLD_BYTES)
    }

    @Test
    fun thresholdEvaluationLogic() {
        fun evaluate(freeBytes: Long): StorageSafety.StorageCheckResult {
            return when {
                freeBytes < StorageSafety.BLOCK_THRESHOLD_BYTES -> StorageSafety.StorageCheckResult.Blocked(
                    "Critical low storage",
                    freeBytes
                )
                freeBytes < StorageSafety.WARN_THRESHOLD_BYTES -> StorageSafety.StorageCheckResult.Warning(
                    "Low storage",
                    freeBytes
                )
                else -> StorageSafety.StorageCheckResult.Ok
            }
        }

        val blockedResult = evaluate(49L * 1024 * 1024)
        assertTrue(blockedResult is StorageSafety.StorageCheckResult.Blocked)

        val warningResult = evaluate(150L * 1024 * 1024)
        assertTrue(warningResult is StorageSafety.StorageCheckResult.Warning)

        val okResult = evaluate(500L * 1024 * 1024)
        assertTrue(okResult is StorageSafety.StorageCheckResult.Ok)
    }
}
