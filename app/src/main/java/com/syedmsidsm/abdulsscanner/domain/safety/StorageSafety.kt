package com.syedmsidsm.abdulsscanner.domain.safety

import android.content.Context
import android.os.StatFs

object StorageSafety {
    const val BLOCK_THRESHOLD_BYTES = 50L * 1024 * 1024 // 50 MB
    const val WARN_THRESHOLD_BYTES = 200L * 1024 * 1024 // 200 MB

    sealed interface StorageCheckResult {
        object Ok : StorageCheckResult
        data class Warning(val message: String, val freeBytes: Long) : StorageCheckResult
        data class Blocked(val message: String, val freeBytes: Long) : StorageCheckResult
    }

    fun checkStorage(context: Context): StorageCheckResult {
        return try {
            val stat = StatFs(context.filesDir.absolutePath)
            val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
            when {
                freeBytes < BLOCK_THRESHOLD_BYTES -> StorageCheckResult.Blocked(
                    message = "Storage is almost completely full (less than 50 MB free). Please free up space on your device to continue.",
                    freeBytes = freeBytes
                )
                freeBytes < WARN_THRESHOLD_BYTES -> StorageCheckResult.Warning(
                    message = "Device storage is low (less than 200 MB free).",
                    freeBytes = freeBytes
                )
                else -> StorageCheckResult.Ok
            }
        } catch (_: Exception) {
            StorageCheckResult.Ok
        }
    }

    fun getFreeBytes(context: Context): Long {
        return try {
            val stat = StatFs(context.filesDir.absolutePath)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (_: Exception) {
            -1L
        }
    }
}
