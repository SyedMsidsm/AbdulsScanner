package com.syedmsidsm.abdulsscanner.ui.screens.document

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.syedmsidsm.abdulsscanner.data.db.entity.DocumentEntity
import com.syedmsidsm.abdulsscanner.data.db.entity.PageEntity
import com.syedmsidsm.abdulsscanner.data.db.entity.TagEntity
import com.syedmsidsm.abdulsscanner.data.repo.DocumentRepository
import com.syedmsidsm.abdulsscanner.data.repo.TagRepository
import com.syedmsidsm.abdulsscanner.domain.scan.SaveScanUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DocumentUiState(
    val document: DocumentEntity? = null,
    val pages: List<PageEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val isUpdatingPdf: Boolean = false,
    val isAddingPages: Boolean = false
)

class DocumentViewModel(
    private val docId: String,
    private val documentRepository: DocumentRepository,
    private val tagRepository: TagRepository,
    private val saveScanUseCase: SaveScanUseCase
) : ViewModel() {

    val allTags = tagRepository.observeAllTags()
    private val _isUpdatingPdf = MutableStateFlow(false)
    private val _isAddingPages = MutableStateFlow(false)

    val uiState: StateFlow<DocumentUiState> = combine(
        documentRepository.observeDocumentById(docId),
        documentRepository.observePages(docId),
        tagRepository.observeTagsForDocument(docId),
        _isUpdatingPdf,
        _isAddingPages
    ) { doc, pages, tags, updatingPdf, addingPages ->
        DocumentUiState(
            document = doc,
            pages = pages,
            tags = tags,
            isUpdatingPdf = updatingPdf,
            isAddingPages = addingPages
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DocumentUiState())

    fun reorderPages(pageIdsInOrder: List<String>) {
        viewModelScope.launch {
            _isUpdatingPdf.value = true
            try {
                documentRepository.reorderPages(docId, pageIdsInOrder)
            } finally {
                _isUpdatingPdf.value = false
            }
        }
    }

    fun setTags(tagIds: Set<Long>) {
        viewModelScope.launch {
            tagRepository.setTagsForDocument(docId, tagIds)
        }
    }

    fun createTagAndAssign(name: String) {
        viewModelScope.launch {
            val newTagId = tagRepository.createTag(name)
            if (newTagId > 0) {
                val currentTagIds = uiState.value.tags.map { it.id }.toSet()
                tagRepository.setTagsForDocument(docId, currentTagIds + newTagId)
            }
        }
    }

    fun setPageSize(pageSize: String) {
        viewModelScope.launch {
            _isUpdatingPdf.value = true
            try {
                documentRepository.setPageSize(docId, pageSize)
            } finally {
                _isUpdatingPdf.value = false
            }
        }
    }

    fun toggleLock() {
        val currentDoc = uiState.value.document ?: return
        viewModelScope.launch {
            documentRepository.setLocked(docId, !currentDoc.locked)
        }
    }

    fun rename(newName: String) {
        viewModelScope.launch {
            _isUpdatingPdf.value = true
            try {
                documentRepository.rename(docId, newName)
            } finally {
                _isUpdatingPdf.value = false
            }
        }
    }

    fun moveToTrash() {
        viewModelScope.launch {
            documentRepository.moveToTrash(docId)
        }
    }

    fun deletePage(pageId: String) {
        viewModelScope.launch {
            _isUpdatingPdf.value = true
            try {
                documentRepository.deletePage(docId, pageId)
            } finally {
                _isUpdatingPdf.value = false
            }
        }
    }

    fun deletePages(pageIds: List<String>, onAllDeleted: () -> Unit) {
        viewModelScope.launch {
            _isUpdatingPdf.value = true
            try {
                val wholeDocMoved = documentRepository.deletePages(docId, pageIds)
                if (wholeDocMoved) {
                    onAllDeleted()
                }
            } finally {
                _isUpdatingPdf.value = false
            }
        }
    }

    fun duplicatePages(pageIds: List<String>) {
        viewModelScope.launch {
            _isUpdatingPdf.value = true
            try {
                documentRepository.duplicatePages(docId, pageIds)
            } finally {
                _isUpdatingPdf.value = false
            }
        }
    }

    fun movePagesToDocument(pageIds: List<String>, targetDocId: String, onSourceEmpty: () -> Unit) {
        viewModelScope.launch {
            _isUpdatingPdf.value = true
            try {
                documentRepository.movePagesToDocument(docId, pageIds, targetDocId)
                val remaining = documentRepository.getPages(docId)
                if (remaining.isEmpty()) {
                    onSourceEmpty()
                }
            } finally {
                _isUpdatingPdf.value = false
            }
        }
    }

    fun movePagesToNewDoc(pageIds: List<String>, newDocName: String, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            _isUpdatingPdf.value = true
            try {
                val newDocId = documentRepository.movePagesToNewDoc(docId, pageIds, newDocName)
                onComplete(newDocId)
            } finally {
                _isUpdatingPdf.value = false
            }
        }
    }

    fun getOtherDocuments(): kotlinx.coroutines.flow.Flow<List<DocumentEntity>> {
        return documentRepository.observeDocuments(com.syedmsidsm.abdulsscanner.data.settings.SortOrder.UPDATED)
    }

    fun addScannedPages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isAddingPages.value = true
            try {
                saveScanUseCase.saveScan(imageUris = uris, targetDocId = docId)
            } finally {
                _isAddingPages.value = false
            }
        }
    }

    companion object {
        fun provideFactory(
            docId: String,
            documentRepository: DocumentRepository,
            tagRepository: TagRepository,
            saveScanUseCase: SaveScanUseCase
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DocumentViewModel(
                    docId,
                    documentRepository,
                    tagRepository,
                    saveScanUseCase
                ) as T
            }
        }
    }
}
