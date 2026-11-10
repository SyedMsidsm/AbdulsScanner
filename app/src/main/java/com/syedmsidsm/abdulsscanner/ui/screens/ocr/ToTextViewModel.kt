package com.syedmsidsm.abdulsscanner.ui.screens.ocr

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.syedmsidsm.abdulsscanner.data.db.AppDatabase
import com.syedmsidsm.abdulsscanner.data.db.entity.DocumentEntity
import com.syedmsidsm.abdulsscanner.data.db.entity.OcrLanguage
import com.syedmsidsm.abdulsscanner.data.db.entity.OcrStatus
import com.syedmsidsm.abdulsscanner.data.db.entity.PageEntity
import com.syedmsidsm.abdulsscanner.data.files.FileStore
import com.syedmsidsm.abdulsscanner.data.files.MirrorStore
import com.syedmsidsm.abdulsscanner.data.repo.SearchRepository
import com.syedmsidsm.abdulsscanner.data.settings.SettingsRepository
import com.syedmsidsm.abdulsscanner.domain.ocr.OcrService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class ToTextUiState(
    val document: DocumentEntity? = null,
    val pages: List<PageEntity> = emptyList(),
    val selectedLanguage: OcrLanguage = OcrLanguage.BOTH,
    val isRunning: Boolean = false,
    val progress: Pair<Int, Int>? = null, // current to total
    val fullText: String = "",
    val isSavedSuccess: Boolean = false,
    val exportedTxtUri: Uri? = null
)

class ToTextViewModel(
    private val docId: String,
    private val pageIdFilter: List<String>,
    private val database: AppDatabase,
    private val fileStore: FileStore,
    private val ocrService: OcrService,
    private val searchRepository: SearchRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ToTextUiState())
    val uiState: StateFlow<ToTextUiState> = _uiState.asStateFlow()

    private var ocrJob: Job? = null

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val doc = database.documentDao().getById(docId)
            val allPages = database.pageDao().getByDocumentId(docId)
            val targetPages = if (pageIdFilter.isNotEmpty()) {
                allPages.filter { it.id in pageIdFilter }
            } else {
                allPages
            }

            val defaultLangStr = settingsRepository.ocrSearchLanguage.first()
            val defaultLang = try {
                OcrLanguage.valueOf(defaultLangStr)
            } catch (_: Exception) {
                OcrLanguage.BOTH
            }

            // Check if pages already have OCR text
            val existingTexts = targetPages.mapNotNull { it.ocrText }
            val combinedExisting = existingTexts.joinToString("\n\n")

            _uiState.value = _uiState.value.copy(
                document = doc,
                pages = targetPages,
                selectedLanguage = defaultLang,
                fullText = combinedExisting
            )

            // If empty, auto-trigger OCR
            if (combinedExisting.isBlank() && targetPages.isNotEmpty()) {
                runOcr(defaultLang)
            }
        }
    }

    fun setLanguage(language: OcrLanguage) {
        _uiState.value = _uiState.value.copy(selectedLanguage = language)
        runOcr(language)
    }

    fun onTextChanged(newText: String) {
        _uiState.value = _uiState.value.copy(fullText = newText, isSavedSuccess = false)
    }

    fun runOcr(language: OcrLanguage = _uiState.value.selectedLanguage) {
        ocrJob?.cancel()
        val pages = _uiState.value.pages
        if (pages.isEmpty()) return

        ocrJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isRunning = true,
                progress = Pair(0, pages.size),
                selectedLanguage = language
            )

            val results = mutableListOf<String>()

            for ((idx, page) in pages.withIndex()) {
                _uiState.value = _uiState.value.copy(progress = Pair(idx + 1, pages.size))

                val renderedFile = fileStore.getPageRenderedFile(docId, page.id)
                if (renderedFile.exists()) {
                    val bitmap = withContext(Dispatchers.IO) {
                        BitmapFactory.decodeFile(renderedFile.absolutePath)
                    }
                    if (bitmap != null) {
                        try {
                            val text = ocrService.recognize(bitmap, language)
                            results.add(text)
                            // Update page in DB
                            database.pageDao().updateOcr(
                                pageId = page.id,
                                text = text,
                                lang = language.name,
                                status = OcrStatus.DONE.name
                            )
                        } catch (e: Exception) {
                            database.pageDao().updateOcr(
                                pageId = page.id,
                                text = null,
                                lang = language.name,
                                status = OcrStatus.FAILED.name
                            )
                        }
                    }
                }
            }

            val combined = results.joinToString("\n\n")
            _uiState.value = _uiState.value.copy(
                isRunning = false,
                progress = null,
                fullText = combined
            )

            // Index in FTS
            val doc = _uiState.value.document
            if (doc != null) {
                searchRepository.indexDocument(docId, doc.name, results)
            }
        }
    }

    fun cancelOcr() {
        ocrJob?.cancel()
        _uiState.value = _uiState.value.copy(isRunning = false, progress = null)
    }

    fun saveToDocument() {
        viewModelScope.launch {
            val text = _uiState.value.fullText
            val pages = _uiState.value.pages
            val doc = _uiState.value.document ?: return@launch

            if (pages.size == 1) {
                database.pageDao().updateOcr(
                    pageId = pages.first().id,
                    text = text,
                    lang = _uiState.value.selectedLanguage.name,
                    status = OcrStatus.DONE.name
                )
            } else {
                // Split lines roughly or apply to first page
                database.pageDao().updateOcr(
                    pageId = pages.first().id,
                    text = text,
                    lang = _uiState.value.selectedLanguage.name,
                    status = OcrStatus.DONE.name
                )
            }

            // Update search index
            searchRepository.indexDocument(docId, doc.name, listOf(text))
            _uiState.value = _uiState.value.copy(isSavedSuccess = true)
        }
    }

    suspend fun exportTxtFile(context: Context): Uri? = withContext(Dispatchers.IO) {
        val doc = _uiState.value.document ?: return@withContext null
        val safeName = MirrorStore.sanitizeFileName(doc.name)
        val text = _uiState.value.fullText

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$safeName.txt")
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/Abdul's Scanner")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(text.toByteArray(Charsets.UTF_8))
                }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                return@withContext uri
            }
        } else {
            @Suppress("DEPRECATION")
            val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val targetDir = File(docsDir, "Abdul's Scanner").apply { mkdirs() }
            val targetFile = File(targetDir, "$safeName.txt")
            FileOutputStream(targetFile).use { it.write(text.toByteArray(Charsets.UTF_8)) }
            return@withContext Uri.fromFile(targetFile)
        }
        null
    }

    companion object {
        fun provideFactory(
            docId: String,
            pageIds: List<String>,
            database: AppDatabase,
            fileStore: FileStore,
            ocrService: OcrService,
            searchRepository: SearchRepository,
            settingsRepository: SettingsRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ToTextViewModel(
                    docId = docId,
                    pageIdFilter = pageIds,
                    database = database,
                    fileStore = fileStore,
                    ocrService = ocrService,
                    searchRepository = searchRepository,
                    settingsRepository = settingsRepository
                ) as T
            }
        }
    }
}
