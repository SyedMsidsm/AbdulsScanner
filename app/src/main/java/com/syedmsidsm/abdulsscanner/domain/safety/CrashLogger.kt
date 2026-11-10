package com.syedmsidsm.abdulsscanner.domain.safety

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local-only crash logger for Abul's Scanner.
 *
 * Saves uncaught exceptions to filesDir/crash/last-crash.txt, rotating up to 3 crash reports.
 * Allows the user to view, copy, or share the crash report directly from Settings -> About.
 * No data is ever sent to any remote server (strictly offline).
 */
object CrashLogger {

    private const val TAG = "CrashLogger"
    private const val CRASH_DIR = "crash"
    private const val FILE_CURRENT = "last-crash.txt"
    private const val FILE_PREV_1 = "last-crash-1.txt"
    private const val FILE_PREV_2 = "last-crash-2.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                recordCrash(appContext, thread, throwable)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to record local crash", t)
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun recordCrash(context: Context, thread: Thread, throwable: Throwable) {
        val dir = File(context.filesDir, CRASH_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }

        // Rotate existing crash reports (keep last 3)
        val file2 = File(dir, FILE_PREV_2)
        val file1 = File(dir, FILE_PREV_1)
        val fileCurrent = File(dir, FILE_CURRENT)

        if (file2.exists()) file2.delete()
        if (file1.exists()) file1.renameTo(file2)
        if (fileCurrent.exists()) fileCurrent.renameTo(file1)

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val stackTrace = throwable.stackTraceToString()

        val (verName, verCode) = runCatching {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
            (pInfo.versionName ?: "1.0.0") to code
        }.getOrDefault("1.0.0" to 800L)

        val report = buildString {
            appendLine("=== Abul's Scanner Crash Report ===")
            appendLine("Time: $timestamp")
            appendLine("App Version: $verName ($verCode)")
            appendLine("Android Version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.PRODUCT})")
            appendLine("Thread: ${thread.name} (id: ${thread.id})")
            appendLine("----------------------------------------")
            appendLine("Exception:")
            appendLine(stackTrace)
            appendLine("========================================")
        }

        val tmpFile = File(dir, "crash-${System.currentTimeMillis()}.tmp")
        tmpFile.writeText(report, Charsets.UTF_8)
        tmpFile.renameTo(fileCurrent)
    }

    fun getLastCrashReport(context: Context): String? {
        val file = File(context.filesDir, "$CRASH_DIR/$FILE_CURRENT")
        return if (file.exists() && file.length() > 0) {
            runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
        } else {
            null
        }
    }

    fun copyLastCrashReport(context: Context): Boolean {
        val report = getLastCrashReport(context) ?: return false
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return false
        val clip = ClipData.newPlainText("Abul's Scanner Crash Report", report)
        clipboard.setPrimaryClip(clip)
        return true
    }

    fun shareLastCrashReport(context: Context) {
        val report = getLastCrashReport(context) ?: "No crashes recorded."
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Abul's Scanner Crash Report")
            putExtra(Intent.EXTRA_TEXT, report)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(sendIntent, "Share crash report").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
