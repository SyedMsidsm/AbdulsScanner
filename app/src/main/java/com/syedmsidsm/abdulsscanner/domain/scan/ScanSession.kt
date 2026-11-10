package com.syedmsidsm.abdulsscanner.domain.scan

import android.content.Context
import android.util.Log
import com.syedmsidsm.abdulsscanner.data.db.entity.PageFilter
import com.syedmsidsm.abdulsscanner.domain.imaging.Point2D
import com.syedmsidsm.abdulsscanner.domain.imaging.Quad
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class ScanPage(
    val id: String = UUID.randomUUID().toString(),
    val filePath: String,
    val cropQuad: Quad? = null,
    val rotation: Int = 0,
    val filter: PageFilter = PageFilter.ENHANCED
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("filePath", filePath)
        if (cropQuad != null) {
            put("p0x", cropQuad.p0.x.toDouble())
            put("p0y", cropQuad.p0.y.toDouble())
            put("p1x", cropQuad.p1.x.toDouble())
            put("p1y", cropQuad.p1.y.toDouble())
            put("p2x", cropQuad.p2.x.toDouble())
            put("p2y", cropQuad.p2.y.toDouble())
            put("p3x", cropQuad.p3.x.toDouble())
            put("p3y", cropQuad.p3.y.toDouble())
        }
        put("rotation", rotation)
        put("filter", filter.name)
    }

    companion object {
        fun fromJson(json: JSONObject): ScanPage {
            val id = json.getString("id")
            val filePath = json.getString("filePath")
            val quad = if (json.has("p0x") && json.has("p0y") && json.has("p1x") && json.has("p1y") &&
                json.has("p2x") && json.has("p2y") && json.has("p3x") && json.has("p3y")
            ) {
                Quad(
                    p0 = Point2D(json.getDouble("p0x").toFloat(), json.getDouble("p0y").toFloat()),
                    p1 = Point2D(json.getDouble("p1x").toFloat(), json.getDouble("p1y").toFloat()),
                    p2 = Point2D(json.getDouble("p2x").toFloat(), json.getDouble("p2y").toFloat()),
                    p3 = Point2D(json.getDouble("p3x").toFloat(), json.getDouble("p3y").toFloat())
                )
            } else {
                null
            }
            val rotation = json.optInt("rotation", 0)
            val filterName = json.optString("filter", PageFilter.ENHANCED.name)
            val filter = runCatching { PageFilter.valueOf(filterName) }.getOrDefault(PageFilter.ENHANCED)
            return ScanPage(id, filePath, quad, rotation, filter)
        }
    }
}

data class ScanSession(
    val sessionId: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
    val pages: List<ScanPage> = emptyList(),
    val targetDocId: String? = null,
    val scanMode: String = "DOCS"
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("sessionId", sessionId)
        put("createdAt", createdAt)
        if (!targetDocId.isNullOrBlank()) {
            put("targetDocId", targetDocId)
        }
        put("scanMode", scanMode)
        val pagesArray = JSONArray()
        pages.forEach { pagesArray.put(it.toJson()) }
        put("pages", pagesArray)
    }

    companion object {
        private const val TAG = "ScanSession"
        private const val SESSIONS_DIR = "scan-session"
        private const val SESSION_JSON = "session.json"
        private const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000 // 7 days

        fun getSessionDir(context: Context, sessionId: String): File {
            val base = File(context.cacheDir, SESSIONS_DIR)
            val dir = File(base, sessionId)
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

        fun save(context: Context, session: ScanSession) {
            try {
                val dir = getSessionDir(context, session.sessionId)
                val jsonFile = File(dir, SESSION_JSON)
                val tmp = File(dir, "session.tmp")
                tmp.writeText(session.toJson().toString(), Charsets.UTF_8)
                tmp.renameTo(jsonFile)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to save session ${session.sessionId}", e)
            }
        }

        fun load(sessionDir: File): ScanSession? {
            return try {
                val jsonFile = File(sessionDir, SESSION_JSON)
                if (!jsonFile.exists()) return null
                val root = JSONObject(jsonFile.readText(Charsets.UTF_8))
                val sessionId = root.getString("sessionId")
                val createdAt = root.optLong("createdAt", System.currentTimeMillis())
                val targetDocId = if (root.has("targetDocId")) root.getString("targetDocId") else null
                val scanMode = root.optString("scanMode", "DOCS")
                val pages = ArrayList<ScanPage>()
                val pagesArray = root.optJSONArray("pages") ?: JSONArray()
                for (i in 0 until pagesArray.length()) {
                    pages.add(ScanPage.fromJson(pagesArray.getJSONObject(i)))
                }
                ScanSession(sessionId, createdAt, pages, targetDocId, scanMode)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to load session from $sessionDir", e)
                null
            }
        }

        fun deleteSession(context: Context, sessionId: String) {
            try {
                val dir = getSessionDir(context, sessionId)
                dir.deleteRecursively()
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to delete session $sessionId", e)
            }
        }

        /**
         * Scans cacheDir/scan-session/ for unfinished sessions.
         * Deletes any sessions older than 7 days silently.
         * Returns non-empty valid sessions.
         */
        fun findUnfinishedSessions(context: Context): List<ScanSession> {
            val base = File(context.cacheDir, SESSIONS_DIR)
            if (!base.exists() || !base.isDirectory) return emptyList()

            val now = System.currentTimeMillis()
            val result = ArrayList<ScanSession>()

            base.listFiles()?.forEach { sessionDir ->
                if (sessionDir.isDirectory) {
                    val session = load(sessionDir)
                    if (session != null) {
                        if (now - session.createdAt > MAX_AGE_MS) {
                            sessionDir.deleteRecursively()
                        } else if (session.pages.isNotEmpty()) {
                            // Verify that at least one photo file still exists
                            val hasFiles = session.pages.any { File(it.filePath).exists() }
                            if (hasFiles) {
                                result.add(session)
                            } else {
                                sessionDir.deleteRecursively()
                            }
                        } else {
                            sessionDir.deleteRecursively()
                        }
                    }
                }
            }
            return result
        }
    }
}
