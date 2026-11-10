package com.syedmsidsm.abdulsscanner.ui.screens.camera

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.syedmsidsm.abdulsscanner.data.db.entity.PageFilter
import com.syedmsidsm.abdulsscanner.domain.imaging.Quad
import com.syedmsidsm.abdulsscanner.domain.scan.ScanPage
import com.syedmsidsm.abdulsscanner.domain.scan.ScanSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/**
 * Shared ViewModel scoped to the scan_flow navigation graph.
 * Survives navigation across Camera, Crop, and Review screens, orientation changes,
 * and maintains crash-proof state via local session.json.
 */
class ScanSessionViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val _session = MutableStateFlow(ScanSession())
    val session: StateFlow<ScanSession> = _session.asStateFlow()

    val pages: List<ScanPage> get() = _session.value.pages
    val pageCount: Int get() = _session.value.pages.size

    init {
        val savedId = savedStateHandle.get<String>("sessionId")
        if (savedId != null) {
            val dir = ScanSession.getSessionDir(getApplication(), savedId)
            val loaded = ScanSession.load(dir)
            if (loaded != null) {
                _session.value = loaded
            }
        }
    }

    fun initSession(targetDocId: Long? = null, existingSessionId: String? = null) {
        if (existingSessionId != null) {
            val dir = ScanSession.getSessionDir(getApplication(), existingSessionId)
            val loaded = ScanSession.load(dir)
            if (loaded != null) {
                _session.value = loaded
                savedStateHandle["sessionId"] = loaded.sessionId
                return
            }
        }

        // If current session already matches and has pages, retain it
        if (_session.value.pages.isNotEmpty() && _session.value.targetDocId == targetDocId) {
            return
        }

        // Check if there is an active unfinished session on disk to resume
        val unfinished = ScanSession.findUnfinishedSessions(getApplication())
        if (unfinished.isNotEmpty() && (targetDocId == null || unfinished.first().targetDocId == targetDocId)) {
            _session.value = unfinished.first()
            savedStateHandle["sessionId"] = _session.value.sessionId
            return
        }

        val newSession = ScanSession(
            sessionId = UUID.randomUUID().toString(),
            targetDocId = targetDocId
        )
        _session.value = newSession
        savedStateHandle["sessionId"] = newSession.sessionId
        persist()
    }

    fun loadExistingSession(sessionToLoad: ScanSession) {
        _session.value = sessionToLoad
        savedStateHandle["sessionId"] = sessionToLoad.sessionId
        persist()
    }

    fun getPage(pageId: String): ScanPage? {
        return _session.value.pages.firstOrNull { it.id == pageId }
    }

    fun addPage(
        filePath: String,
        cropQuad: Quad? = null,
        rotation: Int = 0,
        filter: PageFilter = PageFilter.ENHANCED
    ): ScanPage {
        val page = ScanPage(
            id = UUID.randomUUID().toString(),
            filePath = filePath,
            cropQuad = cropQuad,
            rotation = rotation,
            filter = filter
        )
        _session.update { current ->
            current.copy(pages = current.pages + page)
        }
        persist()
        return page
    }

    fun updatePage(
        pageId: String,
        cropQuad: Quad? = null,
        rotation: Int? = null,
        filter: PageFilter? = null
    ) {
        _session.update { current ->
            current.copy(
                pages = current.pages.map { p ->
                    if (p.id == pageId) {
                        p.copy(
                            cropQuad = cropQuad ?: p.cropQuad,
                            rotation = rotation ?: p.rotation,
                            filter = filter ?: p.filter
                        )
                    } else {
                        p
                    }
                }
            )
        }
        persist()
    }

    fun replacePagePhoto(pageId: String, newFilePath: String, newCropQuad: Quad?) {
        _session.update { current ->
            current.copy(
                pages = current.pages.map { p ->
                    if (p.id == pageId) {
                        // Delete previous photo file if it was a session-managed temp file
                        runCatching { File(p.filePath).delete() }
                        p.copy(
                            filePath = newFilePath,
                            cropQuad = newCropQuad,
                            rotation = 0
                        )
                    } else {
                        p
                    }
                }
            )
        }
        persist()
    }

    fun removePage(pageId: String): ScanPage? {
        val pageToRemove = getPage(pageId)
        _session.update { current ->
            current.copy(pages = current.pages.filter { it.id != pageId })
        }
        persist()
        return pageToRemove
    }

    fun reorderPages(fromIndex: Int, toIndex: Int) {
        _session.update { current ->
            val list = current.pages.toMutableList()
            if (fromIndex in list.indices && toIndex in list.indices) {
                val item = list.removeAt(fromIndex)
                list.add(toIndex, item)
            }
            current.copy(pages = list)
        }
        persist()
    }

    fun setFilterForAllPages(filter: PageFilter) {
        _session.update { current ->
            current.copy(pages = current.pages.map { it.copy(filter = filter) })
        }
        persist()
    }

    fun rotatePage(pageId: String, deltaDegrees: Int = 90) {
        _session.update { current ->
            current.copy(
                pages = current.pages.map { p ->
                    if (p.id == pageId) {
                        p.copy(rotation = (p.rotation + deltaDegrees) % 360)
                    } else {
                        p
                    }
                }
            )
        }
        persist()
    }

    fun discardSession() {
        val currentId = _session.value.sessionId
        ScanSession.deleteSession(getApplication(), currentId)
        _session.value = ScanSession()
        savedStateHandle.remove<String>("sessionId")
    }

    private fun persist() {
        viewModelScope.launch {
            ScanSession.save(getApplication(), _session.value)
        }
    }
}
