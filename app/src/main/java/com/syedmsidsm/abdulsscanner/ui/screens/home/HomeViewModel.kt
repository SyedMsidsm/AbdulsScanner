package com.syedmsidsm.abdulsscanner.ui.screens.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.syedmsidsm.abdulsscanner.data.db.dao.TagWithCount
import com.syedmsidsm.abdulsscanner.data.db.entity.DocumentEntity
import com.syedmsidsm.abdulsscanner.data.repo.DocumentRepository
import com.syedmsidsm.abdulsscanner.data.repo.SearchRepository
import com.syedmsidsm.abdulsscanner.data.repo.TagRepository
import com.syedmsidsm.abdulsscanner.data.settings.SettingsRepository
import com.syedmsidsm.abdulsscanner.data.settings.SortOrder
import com.syedmsidsm.abdulsscanner.data.settings.ViewMode
import com.syedmsidsm.abdulsscanner.domain.scan.SaveScanUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val documents: List<DocumentEntity> = emptyList(),
    val tagsWithCounts: List<TagWithCount> = emptyList(),
    val ungroupedCount: Int = 0,
    val selectedTagId: Long? = null,
    val isUngroupedSelected: Boolean = false,
    val sortOrder: SortOrder = SortOrder.UPDATED,
    val viewMode: ViewMode = ViewMode.LIST,
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<DocumentEntity> = emptyList(),
    val searchSnippets: Map<String, String> = emptyMap(),
    val isSelectMode: Boolean = false,
    val selectedDocIds: Set<String> = emptySet(),
    val bannerMessage: String? = null,
    val bannerActionText: String? = null,
    val isSavingScan: Boolean = false
)

private data class FilterConfig(
    val sortOrder: SortOrder,
    val tagId: Long?,
    val isUngrouped: Boolean
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val documentRepository: DocumentRepository,
    private val tagRepository: TagRepository,
    private val searchRepository: SearchRepository,
    private val settingsRepository: SettingsRepository,
    private val saveScanUseCase: SaveScanUseCase
) : ViewModel() {

    private val _selectedTagId = MutableStateFlow<Long?>(null)
    private val _isUngroupedSelected = MutableStateFlow(false)
    private val _isSearchActive = MutableStateFlow(false)
    private val _searchQuery = MutableStateFlow("")
    private val _isSelectMode = MutableStateFlow(false)
    private val _selectedDocIds = MutableStateFlow<Set<String>>(emptySet())
    private val _isSavingScan = MutableStateFlow(false)

    val appReady = MutableStateFlow(false)

    private val filterConfig = combine(
        settingsRepository.sortOrder,
        _selectedTagId,
        _isUngroupedSelected
    ) { sortStr, tagId, ungrouped ->
        val sort = try { SortOrder.valueOf(sortStr) } catch (_: Exception) { SortOrder.UPDATED }
        FilterConfig(sort, tagId, ungrouped)
    }

    private val documentsFlow = filterConfig.flatMapLatest { config ->
        documentRepository.observeDocuments(config.sortOrder, config.tagId, config.isUngrouped)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        documentsFlow,
        settingsRepository.viewMode,
        tagRepository.observeTagsWithCounts(),
        tagRepository.observeUngroupedCount(),
        filterConfig
    ) { docs, viewModeStr, tags, ungroupedCount, config ->
        appReady.value = true
        val viewMode = try { ViewMode.valueOf(viewModeStr) } catch (_: Exception) { ViewMode.LIST }

        HomeUiState(
            documents = docs,
            tagsWithCounts = tags,
            ungroupedCount = ungroupedCount,
            selectedTagId = config.tagId,
            isUngroupedSelected = config.isUngrouped,
            sortOrder = config.sortOrder,
            viewMode = viewMode,
            isSearchActive = _isSearchActive.value,
            searchQuery = _searchQuery.value,
            isSelectMode = _isSelectMode.value,
            selectedDocIds = _selectedDocIds.value,
            isSavingScan = _isSavingScan.value
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    fun selectTag(tagId: Long?) {
        _isUngroupedSelected.value = false
        _selectedTagId.value = tagId
    }

    fun selectUngrouped() {
        _selectedTagId.value = null
        _isUngroupedSelected.value = true
    }

    fun selectAllDocs() {
        _selectedTagId.value = null
        _isUngroupedSelected.value = false
    }

    fun setSortOrder(order: SortOrder) {
        viewModelScope.launch {
            settingsRepository.setSortOrder(order.name)
        }
    }

    fun toggleViewMode() {
        viewModelScope.launch {
            val current = uiState.value.viewMode
            val next = if (current == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST
            settingsRepository.setViewMode(next.name)
        }
    }

    fun setSearchActive(active: Boolean) {
        _isSearchActive.value = active
        if (!active) _searchQuery.value = ""
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun toggleSelectMode() {
        _isSelectMode.value = !_isSelectMode.value
        _selectedDocIds.value = emptySet()
    }

    fun toggleDocSelection(docId: String) {
        val current = _selectedDocIds.value.toMutableSet()
        if (current.contains(docId)) current.remove(docId) else current.add(docId)
        _selectedDocIds.value = current
    }

    fun renameDocument(docId: String, newName: String) {
        viewModelScope.launch {
            documentRepository.rename(docId, newName)
        }
    }

    fun moveToTrash(docId: String) {
        viewModelScope.launch {
            documentRepository.moveToTrash(docId)
        }
    }

    fun onScanned(uris: List<Uri>, onSaved: (String) -> Unit) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isSavingScan.value = true
            try {
                val docId = saveScanUseCase.saveScan(imageUris = uris)
                onSaved(docId)
            } finally {
                _isSavingScan.value = false
            }
        }
    }

    companion object {
        fun provideFactory(
            documentRepository: DocumentRepository,
            tagRepository: TagRepository,
            searchRepository: SearchRepository,
            settingsRepository: SettingsRepository,
            saveScanUseCase: SaveScanUseCase
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(
                    documentRepository,
                    tagRepository,
                    searchRepository,
                    settingsRepository,
                    saveScanUseCase
                ) as T
            }
        }
    }
}
