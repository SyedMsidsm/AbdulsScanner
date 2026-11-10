package com.syedmsidsm.abdulsscanner.ui.screens.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.syedmsidsm.abdulsscanner.data.settings.SettingsRepository
import com.syedmsidsm.abdulsscanner.di.AppContainer
import com.syedmsidsm.abdulsscanner.domain.backup.AutoBackupWorker
import com.syedmsidsm.abdulsscanner.domain.backup.BackupReader
import com.syedmsidsm.abdulsscanner.domain.backup.BackupSummary
import com.syedmsidsm.abdulsscanner.domain.backup.BackupWriter
import com.syedmsidsm.abdulsscanner.domain.backup.RestoreResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BackupUiState(
    val lastBackupAt: Long? = null,
    val autoBackupEnabled: Boolean = false,
    val autoBackupTreeUri: String? = null,
    val autoBackupIntervalDays: Int = 7,
    val backupKeepCount: Int = 3,
    val isBackingUp: Boolean = false,
    val isRestoring: Boolean = false,
    val progressMessage: String = "",
    val progressCurrent: Int = 0,
    val progressTotal: Int = 100,
    val pendingRestoreSummary: BackupSummary? = null,
    val restoreResult: RestoreResult? = null,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

class BackupViewModel(
    private val container: AppContainer
) : ViewModel() {

    private val settings: SettingsRepository = container.settingsRepository
    private val backupWriter: BackupWriter = container.backupWriter
    private val backupReader: BackupReader = container.backupReader

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settings.lastBackupAt.collect { at ->
                _uiState.value = _uiState.value.copy(lastBackupAt = at)
            }
        }
        viewModelScope.launch {
            settings.autoBackupEnabled.collect { enabled ->
                _uiState.value = _uiState.value.copy(autoBackupEnabled = enabled)
            }
        }
        viewModelScope.launch {
            settings.autoBackupTreeUri.collect { uri ->
                _uiState.value = _uiState.value.copy(autoBackupTreeUri = uri)
            }
        }
        viewModelScope.launch {
            settings.autoBackupIntervalDays.collect { days ->
                _uiState.value = _uiState.value.copy(autoBackupIntervalDays = days)
            }
        }
        viewModelScope.launch {
            settings.backupKeepCount.collect { keep ->
                _uiState.value = _uiState.value.copy(backupKeepCount = keep)
            }
        }
    }

    fun generateBackupFileName(): String {
        return BackupWriter.generateBackupFileName()
    }

    fun startManualBackup(destinationUri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isBackingUp = true,
                progressMessage = "Preparing backup...",
                progressCurrent = 0,
                progressTotal = 100,
                errorMessage = null
            )
            try {
                val bytesCopied = backupWriter.writeManualBackupToUri(destinationUri) { cur, tot, msg ->
                    _uiState.value = _uiState.value.copy(
                        progressCurrent = cur,
                        progressTotal = tot,
                        progressMessage = msg
                    )
                }
                val mb = String.format(Locale.ENGLISH, "%.1f MB", bytesCopied / (1024.0 * 1024.0))
                _uiState.value = _uiState.value.copy(
                    isBackingUp = false,
                    statusMessage = "Backup completed successfully ($mb)."
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isBackingUp = false,
                    errorMessage = "Backup failed: ${e.message}"
                )
            }
        }
    }

    fun inspectBackupForRestore(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isRestoring = true,
                progressMessage = "Inspecting backup file...",
                errorMessage = null
            )
            try {
                val summary = backupReader.inspectBackup(uri)
                _uiState.value = _uiState.value.copy(
                    isRestoring = false,
                    pendingRestoreSummary = summary
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRestoring = false,
                    errorMessage = e.message ?: "Failed to read backup file"
                )
            }
        }
    }

    fun confirmRestore() {
        val summary = _uiState.value.pendingRestoreSummary ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isRestoring = true,
                pendingRestoreSummary = null,
                progressMessage = "Restoring documents...",
                progressCurrent = 0,
                progressTotal = 100
            )
            try {
                val result = backupReader.restoreBackup(summary) { cur, tot, msg ->
                    _uiState.value = _uiState.value.copy(
                        progressCurrent = cur,
                        progressTotal = tot,
                        progressMessage = msg
                    )
                }
                _uiState.value = _uiState.value.copy(
                    isRestoring = false,
                    restoreResult = result
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRestoring = false,
                    errorMessage = "Restore failed: ${e.message}"
                )
            }
        }
    }

    fun dismissRestoreDialog() {
        _uiState.value.pendingRestoreSummary?.tempFile?.delete()
        _uiState.value = _uiState.value.copy(
            pendingRestoreSummary = null,
            restoreResult = null
        )
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.setAutoBackupEnabled(enabled)
            if (enabled) {
                AutoBackupWorker.schedule(container.context, _uiState.value.autoBackupIntervalDays)
            } else {
                AutoBackupWorker.cancel(container.context)
            }
        }
    }

    fun setAutoBackupTreeUri(uri: String?) {
        viewModelScope.launch {
            settings.setAutoBackupTreeUri(uri)
        }
    }

    fun setAutoBackupIntervalDays(days: Int) {
        viewModelScope.launch {
            settings.setAutoBackupIntervalDays(days)
            if (_uiState.value.autoBackupEnabled) {
                AutoBackupWorker.schedule(container.context, days)
            }
        }
    }

    fun forceAutoBackupNow() {
        val uriStr = _uiState.value.autoBackupTreeUri
        if (uriStr.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please choose a backup folder first.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isBackingUp = true,
                progressMessage = "Running auto-backup...",
                progressCurrent = 0,
                progressTotal = 100,
                errorMessage = null
            )
            try {
                val treeUri = Uri.parse(uriStr)
                val docFile = backupWriter.writeAutoBackupToTree(
                    treeUri = treeUri,
                    keepCount = _uiState.value.backupKeepCount
                ) { cur, tot, msg ->
                    _uiState.value = _uiState.value.copy(
                        progressCurrent = cur,
                        progressTotal = tot,
                        progressMessage = msg
                    )
                }
                _uiState.value = _uiState.value.copy(
                    isBackingUp = false,
                    statusMessage = "Auto-backup saved: ${docFile.name}"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isBackingUp = false,
                    errorMessage = "Auto-backup error: ${e.message}"
                )
            }
        }
    }

    fun clearStatusMessage() {
        _uiState.value = _uiState.value.copy(statusMessage = null, errorMessage = null)
    }

    companion object {
        fun provideFactory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return BackupViewModel(container) as T
                }
            }
    }
}
