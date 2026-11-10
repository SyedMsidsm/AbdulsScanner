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
import com.syedmsidsm.abdulsscanner.domain.scan.MergeDocumentsUseCase
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

enum class BannerType {
    BACKUP_NEEDED,
    AUTO_BACKUP_FAILED,
    OFF_PHONE_REMINDER
}

data class HomeBanner(
    val type: BannerType,
    val message: String,
    val actionText: String
)

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
    val banner: HomeBanner? = null,
    val isSavingScan: Boolean = false
)

private data class FilterConfig(
    val sortOrder: SortOrder,
    val tagId: Long?,
    val isUngrouped: Boolean
)

private data class BackupBannerConfig(
    val lastBackupAt: Long?,
    val autoBackupFailed: Boolean,
    val lastOffPhoneReminderAt: Long?
)

private data class TagsInfo(
    val tags: List<TagWithCount>,
    val ungroupedCount: Int
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val documentRepository: DocumentRepository,
    private val tagRepository: TagRepository,
    private val searchRepository: SearchRepository,
    private val settingsRepository: SettingsRepository,
    private val saveScanUseCase: SaveScanUseCase,
    private val mergeDocumentsUseCase: MergeDocumentsUseCase
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

    private val backupConfigFlow = combine(
        settingsRepository.lastBackupAt,
        settingsRepository.autoBackupFailed,
        settingsRepository.lastOffPhoneReminderAt
    ) { lastBackup, failed, reminder ->
        BackupBannerConfig(lastBackup, failed, reminder)
    }

    private val tagsInfoFlow = combine(
        tagRepository.observeTagsWithCounts(),
        tagRepository.observeUngroupedCount()
    ) { tags, ungrouped ->
        TagsInfo(tags, ungrouped)
    }

    private val documentsFlow = filterConfig.flatMapLatest { config ->
        documentRepository.observeDocuments(config.sortOrder, config.tagId, config.isUngrouped)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        documentsFlow,
        settingsRepository.viewMode,
        tagsInfoFlow,
        filterConfig,
        backupConfigFlow
    ) { docs, viewModeStr, tagsInfo, config, backupConfig ->
        appReady.value = true
        val viewMode = try { ViewMode.valueOf(viewModeStr) } catch (_: Exception) { ViewMode.LIST }

        val now = System.currentTimeMillis()
        val sevenDays = 7L * 24 * 60 * 60 * 1000L
        val thirtyDays = 30L * 24 * 60 * 60 * 1000L

        val banner: HomeBanner? = when {
            docs.isNotEmpty() && (backupConfig.lastBackupAt == null || (now - backupConfig.lastBackupAt) > sevenDays) -> {
                val msg = if (backupConfig.lastBackupAt == null) {
                    "You haven't backed up yet"
                } else {
                    val daysAgo = ((now - backupConfig.lastBackupAt) / (24 * 60 * 60 * 1000L)).toInt().coerceAtLeast(1)
                    "Last backup $daysAgo days ago"
                }
                HomeBanner(BannerType.BACKUP_NEEDED, msg, "Back up now")
            }
            backupConfig.autoBackupFailed -> {
                HomeBanner(BannerType.AUTO_BACKUP_FAILED, "Automatic backup failed — choose the folder again", "Fix")
            }
            backupConfig.lastBackupAt != null && (backupConfig.lastOffPhoneReminderAt == null || (now - backupConfig.lastOffPhoneReminderAt) > thirtyDays) -> {
                HomeBanner(
                    BannerType.OFF_PHONE_REMINDER,
                    "Copy your newest backup off this phone (USB stick or another device) so a lost phone doesn't mean lost scans.",
                    "Got it"
                )
            }
            else -> null
        }

        HomeUiState(
            documents = docs,
            tagsWithCounts = tagsInfo.tags,
            ungroupedCount = tagsInfo.ungroupedCount,
            selectedTagId = config.tagId,
            isUngroupedSelected = config.isUngrouped,
            sortOrder = config.sortOrder,
            viewMode = viewMode,
            isSearchActive = _isSearchActive.value,
            searchQuery = _searchQuery.value,
            isSelectMode = _isSelectMode.value,
            selectedDocIds = _selectedDocIds.value,
            banner = banner,
            isSavingScan = _isSavingScan.value
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    fun dismissOffPhoneReminder() {
        viewModelScope.launch {
            settingsRepository.setLastOffPhoneReminderAt(System.currentTimeMillis())
        }
    }

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

    fun selectAll() {
        _selectedDocIds.value = uiState.value.documents.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selectedDocIds.value = emptySet()
    }

    fun moveSelectedToTrash() {
        val ids = _selectedDocIds.value.toList()
        viewModelScope.launch {
            for (id in ids) {
                documentRepository.moveToTrash(id)
            }
            toggleSelectMode()
        }
    }

    fun toggleLockSelected() {
        val selectedDocs = uiState.value.documents.filter { it.id in _selectedDocIds.value }
        val shouldLock = selectedDocs.any { !it.locked }
        viewModelScope.launch {
            for (doc in selectedDocs) {
                documentRepository.setLocked(doc.id, shouldLock)
            }
            toggleSelectMode()
        }
    }

    fun applyTagsToSelected(tagIds: Set<Long>) {
        val ids = _selectedDocIds.value.toList()
        viewModelScope.launch {
            for (docId in ids) {
                tagRepository.setTagsForDocument(docId, tagIds)
            }
            toggleSelectMode()
        }
    }

    fun mergeSelected(onMerged: (String) -> Unit) {
        val ids = _selectedDocIds.value.toList()
        if (ids.size < 2) return
        viewModelScope.launch {
            _isSavingScan.value = true
            try {
                val newDocId = mergeDocumentsUseCase.mergeDocuments(ids)
                toggleSelectMode()
                onMerged(newDocId)
            } finally {
                _isSavingScan.value = false
            }
        }
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
            saveScanUseCase: SaveScanUseCase,
            mergeDocumentsUseCase: MergeDocumentsUseCase
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(
                    documentRepository,
                    tagRepository,
                    searchRepository,
                    settingsRepository,
                    saveScanUseCase,
                    mergeDocumentsUseCase
                ) as T
            }
        }
    }
}
