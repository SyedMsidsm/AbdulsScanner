package com.syedmsidsm.abdulsscanner.ui.screens.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.syedmsidsm.abdulsscanner.data.db.dao.TagWithCount
import com.syedmsidsm.abdulsscanner.data.db.entity.TagEntity
import com.syedmsidsm.abdulsscanner.data.repo.TagRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TagsViewModel(
    private val tagRepository: TagRepository
) : ViewModel() {

    val tagsWithCounts: StateFlow<List<TagWithCount>> = tagRepository.observeTagsWithCounts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun createTag(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            tagRepository.createTag(trimmed)
        }
    }

    fun renameTag(id: Long, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            tagRepository.renameTag(id, trimmed)
        }
    }

    fun deleteTag(id: Long) {
        viewModelScope.launch {
            tagRepository.deleteTag(id)
        }
    }

    companion object {
        fun provideFactory(tagRepository: TagRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return TagsViewModel(tagRepository) as T
                }
            }
    }
}
