package com.syedmsidsm.abdulsscanner.ui.screens.qr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.syedmsidsm.abdulsscanner.data.db.entity.QrHistoryEntity
import com.syedmsidsm.abdulsscanner.data.repo.QrRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class QrHistoryViewModel(
    private val qrRepository: QrRepository
) : ViewModel() {

    val history: StateFlow<List<QrHistoryEntity>> = qrRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteEntry(id: Long) {
        viewModelScope.launch {
            qrRepository.deleteById(id)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            qrRepository.clearAll()
        }
    }

    companion object {
        fun provideFactory(qrRepository: QrRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return QrHistoryViewModel(qrRepository) as T
                }
            }
    }
}
