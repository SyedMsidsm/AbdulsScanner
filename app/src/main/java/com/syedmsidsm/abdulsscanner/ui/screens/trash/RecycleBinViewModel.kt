package com.syedmsidsm.abdulsscanner.ui.screens.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.syedmsidsm.abdulsscanner.data.db.entity.DocumentEntity
import com.syedmsidsm.abdulsscanner.data.repo.DocumentRepository
import com.syedmsidsm.abdulsscanner.di.AppContainer
import com.syedmsidsm.abdulsscanner.domain.pdf.PdfRebuildWorker
import com.syedmsidsm.abdulsscanner.domain.safety.TrashPurgeWorker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RecycleBinViewModel(
    private val container: AppContainer
) : ViewModel() {

    val trashDocuments: StateFlow<List<DocumentEntity>> = container.database.documentDao()
        .observeTrash()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun restoreDocument(docId: String) {
        viewModelScope.launch {
            container.database.documentDao().restoreFromTrash(docId)
            // Rebuild PDF and restore mirror
            PdfRebuildWorker.enqueue(container.context, docId)
        }
    }

    fun deletePermanently(doc: DocumentEntity) {
        viewModelScope.launch {
            TrashPurgeWorker.purgeDocument(container, doc.id, doc.mirrorUri)
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            val list = container.database.documentDao().getTrashList()
            for (doc in list) {
                TrashPurgeWorker.purgeDocument(container, doc.id, doc.mirrorUri)
            }
        }
    }

    companion object {
        fun provideFactory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return RecycleBinViewModel(container) as T
                }
            }
    }
}
