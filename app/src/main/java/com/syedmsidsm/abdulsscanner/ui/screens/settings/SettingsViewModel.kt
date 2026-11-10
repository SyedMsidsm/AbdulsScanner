package com.syedmsidsm.abdulsscanner.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.syedmsidsm.abdulsscanner.data.files.FileStore
import com.syedmsidsm.abdulsscanner.data.settings.ArabicOcrEngine
import com.syedmsidsm.abdulsscanner.data.settings.BookDirection
import com.syedmsidsm.abdulsscanner.data.settings.SettingsRepository
import com.syedmsidsm.abdulsscanner.di.AppContainer
import com.syedmsidsm.abdulsscanner.domain.ocr.OcrWorker
import com.syedmsidsm.abdulsscanner.domain.safety.StorageSafety
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

data class SettingsUiState(
    val theme: String = "DARK",
    val defaultFilter: String = "ENHANCED",
    val hdMode: Boolean = false,
    val pageSize: String = "AUTO",
    val namePattern: String = "Scan yyyy-MM-dd HH:mm",
    val mirrorEnabled: Boolean = true,
    val ocrSearchLanguage: String = "BOTH",
    val qrSaveHistory: Boolean = true,
    val cameraEngine: String = "BUILT_IN",
    val cameraFlash: String = "AUTO",
    val cameraPhotoSize: String? = null,
    val cameraGrid: Boolean = false,
    val autoShutter: Boolean = true,
    val autoCrop: Boolean = true,
    val cropAfterEachScan: Boolean = true,
    val muteShutter: Boolean = false,
    val bookDirection: String = "LTR",
    val docCount: Int = 0,
    val pageCount: Int = 0,
    val appStorageBytes: Long = 0L,
    val freeDeviceBytes: Long = 0L,
    val tempCacheCleared: Boolean = false,
    val reindexQueued: Boolean = false
)

class SettingsViewModel(
    private val container: AppContainer
) : ViewModel() {

    private val settings: SettingsRepository = container.settingsRepository
    private val fileStore: FileStore = container.fileStore

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
        refreshStorageStats()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            settings.theme.collect { _uiState.value = _uiState.value.copy(theme = it) }
        }
        viewModelScope.launch {
            settings.defaultFilter.collect { _uiState.value = _uiState.value.copy(defaultFilter = it) }
        }
        viewModelScope.launch {
            settings.hdMode.collect { _uiState.value = _uiState.value.copy(hdMode = it) }
        }
        viewModelScope.launch {
            settings.pageSize.collect { _uiState.value = _uiState.value.copy(pageSize = it) }
        }
        viewModelScope.launch {
            settings.namePattern.collect { _uiState.value = _uiState.value.copy(namePattern = it) }
        }
        viewModelScope.launch {
            settings.mirrorEnabled.collect { _uiState.value = _uiState.value.copy(mirrorEnabled = it) }
        }
        viewModelScope.launch {
            settings.ocrSearchLanguage.collect { _uiState.value = _uiState.value.copy(ocrSearchLanguage = it) }
        }
        viewModelScope.launch {
            settings.qrSaveHistory.collect { _uiState.value = _uiState.value.copy(qrSaveHistory = it) }
        }
        viewModelScope.launch {
            settings.cameraEngine.collect { _uiState.value = _uiState.value.copy(cameraEngine = it) }
        }
        viewModelScope.launch {
            settings.cameraFlash.collect { _uiState.value = _uiState.value.copy(cameraFlash = it) }
        }
        viewModelScope.launch {
            settings.cameraPhotoSize.collect { _uiState.value = _uiState.value.copy(cameraPhotoSize = it) }
        }
        viewModelScope.launch {
            settings.cameraGrid.collect { _uiState.value = _uiState.value.copy(cameraGrid = it) }
        }
        viewModelScope.launch {
            settings.autoShutter.collect { _uiState.value = _uiState.value.copy(autoShutter = it) }
        }
        viewModelScope.launch {
            settings.autoCrop.collect { _uiState.value = _uiState.value.copy(autoCrop = it) }
        }
        viewModelScope.launch {
            settings.cropAfterEachScan.collect { _uiState.value = _uiState.value.copy(cropAfterEachScan = it) }
        }
        viewModelScope.launch {
            settings.muteShutter.collect { _uiState.value = _uiState.value.copy(muteShutter = it) }
        }
        viewModelScope.launch {
            settings.bookDirection.collect { _uiState.value = _uiState.value.copy(bookDirection = it) }
        }
    }

    fun refreshStorageStats() {
        viewModelScope.launch {
            val docs = container.database.documentDao().getActiveCount()
            val pages = container.database.pageDao().getAllPages().size
            val appBytes = withContext(Dispatchers.IO) { fileStore.getTotalStorageBytes() }
            val freeBytes = withContext(Dispatchers.IO) { StorageSafety.getFreeBytes(container.context) }

            _uiState.value = _uiState.value.copy(
                docCount = docs,
                pageCount = pages,
                appStorageBytes = appBytes,
                freeDeviceBytes = freeBytes
            )
        }
    }

    fun clearTempCache() {
        viewModelScope.launch(Dispatchers.IO) {
            fileStore.clearTmpAndShare()
            refreshStorageStats()
            _uiState.value = _uiState.value.copy(tempCacheCleared = true)
        }
    }

    fun setTheme(theme: String) {
        viewModelScope.launch { settings.setTheme(theme) }
    }

    fun setDefaultFilter(filter: String) {
        viewModelScope.launch { settings.setDefaultFilter(filter) }
    }

    fun setHdMode(enabled: Boolean) {
        viewModelScope.launch { settings.setHdMode(enabled) }
    }

    fun setPageSize(size: String) {
        viewModelScope.launch { settings.setPageSize(size) }
    }

    fun setNamePattern(pattern: String) {
        viewModelScope.launch { settings.setNamePattern(pattern) }
    }

    fun setMirrorEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setMirrorEnabled(enabled) }
    }

    fun setOcrSearchLanguage(lang: String) {
        viewModelScope.launch { settings.setOcrSearchLanguage(lang) }
    }

    fun setQrSaveHistory(enabled: Boolean) {
        viewModelScope.launch { settings.setQrSaveHistory(enabled) }
    }

    fun setCameraEngine(engine: String) {
        viewModelScope.launch { settings.setCameraEngine(engine) }
    }

    fun setCameraFlash(flash: String) {
        viewModelScope.launch { settings.setCameraFlash(flash) }
    }

    fun setCameraPhotoSize(size: String?) {
        viewModelScope.launch { settings.setCameraPhotoSize(size) }
    }

    fun setCameraGrid(enabled: Boolean) {
        viewModelScope.launch { settings.setCameraGrid(enabled) }
    }

    fun setAutoShutter(enabled: Boolean) {
        viewModelScope.launch { settings.setAutoShutter(enabled) }
    }

    fun setAutoCrop(enabled: Boolean) {
        viewModelScope.launch { settings.setAutoCrop(enabled) }
    }

    fun setCropAfterEachScan(enabled: Boolean) {
        viewModelScope.launch { settings.setCropAfterEachScan(enabled) }
    }

    fun setMuteShutter(enabled: Boolean) {
        viewModelScope.launch { settings.setMuteShutter(enabled) }
    }

    fun setBookDirection(direction: String) {
        viewModelScope.launch { settings.setBookDirection(direction) }
    }

    fun reReadPages() {
        OcrWorker.enqueueAll(container.context)
        _uiState.value = _uiState.value.copy(reindexQueued = true)
    }

    companion object {
        fun provideFactory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SettingsViewModel(container) as T
                }
            }
    }
}
