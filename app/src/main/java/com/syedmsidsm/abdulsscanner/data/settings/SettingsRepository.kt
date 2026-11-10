package com.syedmsidsm.abdulsscanner.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class SortOrder {
    UPDATED, CREATED, NAME
}

enum class ViewMode {
    LIST, GRID
}

enum class ArabicOcrEngine {
    TESSERACT, PADDLE
}

enum class BookDirection {
    LTR, RTL
}

enum class CameraEngine {
    GOOGLE_SCANNER, BUILT_IN
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val DEFAULT_FILTER = stringPreferencesKey("default_filter")
        val HD_MODE = booleanPreferencesKey("hd_mode")
        val PAGE_SIZE = stringPreferencesKey("page_size")
        val NAME_PATTERN = stringPreferencesKey("name_pattern")
        val MIRROR_ENABLED = booleanPreferencesKey("mirror_enabled")
        val OCR_SEARCH_LANGUAGE = stringPreferencesKey("ocr_search_language")
        val OCR_LAST_LANGUAGE = stringPreferencesKey("ocr_last_language")
        val ARABIC_ENGINE = stringPreferencesKey("arabic_engine")
        val BOOK_DIRECTION = stringPreferencesKey("book_direction")
        val SORT_ORDER = stringPreferencesKey("sort_order")
        val VIEW_MODE = stringPreferencesKey("view_mode")
        val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        val AUTO_BACKUP_TREE_URI = stringPreferencesKey("auto_backup_tree_uri")
        val AUTO_BACKUP_INTERVAL_DAYS = intPreferencesKey("auto_backup_interval_days")
        val BACKUP_KEEP_COUNT = intPreferencesKey("backup_keep_count")
        val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
        val LAST_OFF_PHONE_REMINDER_AT = longPreferencesKey("last_off_phone_reminder_at")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val QR_SAVE_HISTORY = booleanPreferencesKey("qr_save_history")
        val CAMERA_ENGINE = stringPreferencesKey("camera_engine")
    }

    val theme: Flow<String> = context.dataStore.data.map { it[Keys.THEME] ?: "DARK" }
    val defaultFilter: Flow<String> = context.dataStore.data.map { it[Keys.DEFAULT_FILTER] ?: "ENHANCED" }
    val hdMode: Flow<Boolean> = context.dataStore.data.map { it[Keys.HD_MODE] ?: false }
    val pageSize: Flow<String> = context.dataStore.data.map { it[Keys.PAGE_SIZE] ?: "AUTO" }
    val namePattern: Flow<String> = context.dataStore.data.map { it[Keys.NAME_PATTERN] ?: "Scan yyyy-MM-dd HH:mm" }
    val mirrorEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.MIRROR_ENABLED] ?: true }
    val ocrSearchLanguage: Flow<String> = context.dataStore.data.map { it[Keys.OCR_SEARCH_LANGUAGE] ?: "BOTH" }
    val ocrLastLanguage: Flow<String> = context.dataStore.data.map { it[Keys.OCR_LAST_LANGUAGE] ?: "EN" }
    val arabicEngine: Flow<String> = context.dataStore.data.map { it[Keys.ARABIC_ENGINE] ?: ArabicOcrEngine.TESSERACT.name }
    val bookDirection: Flow<String> = context.dataStore.data.map { it[Keys.BOOK_DIRECTION] ?: BookDirection.LTR.name }
    val sortOrder: Flow<String> = context.dataStore.data.map { it[Keys.SORT_ORDER] ?: SortOrder.UPDATED.name }
    val viewMode: Flow<String> = context.dataStore.data.map { it[Keys.VIEW_MODE] ?: ViewMode.LIST.name }
    val autoBackupEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.AUTO_BACKUP_ENABLED] ?: false }
    val autoBackupTreeUri: Flow<String?> = context.dataStore.data.map { it[Keys.AUTO_BACKUP_TREE_URI] }
    val autoBackupIntervalDays: Flow<Int> = context.dataStore.data.map { it[Keys.AUTO_BACKUP_INTERVAL_DAYS] ?: 7 }
    val backupKeepCount: Flow<Int> = context.dataStore.data.map { it[Keys.BACKUP_KEEP_COUNT] ?: 3 }
    val lastBackupAt: Flow<Long?> = context.dataStore.data.map { it[Keys.LAST_BACKUP_AT] }
    val lastOffPhoneReminderAt: Flow<Long?> = context.dataStore.data.map { it[Keys.LAST_OFF_PHONE_REMINDER_AT] }
    val pinHash: Flow<String?> = context.dataStore.data.map { it[Keys.PIN_HASH] }
    val pinSalt: Flow<String?> = context.dataStore.data.map { it[Keys.PIN_SALT] }
    val biometricEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.BIOMETRIC_ENABLED] ?: false }
    val qrSaveHistory: Flow<Boolean> = context.dataStore.data.map { it[Keys.QR_SAVE_HISTORY] ?: true }
    val cameraEngine: Flow<String> = context.dataStore.data.map { it[Keys.CAMERA_ENGINE] ?: CameraEngine.GOOGLE_SCANNER.name }

    suspend fun setTheme(theme: String) = context.dataStore.edit { it[Keys.THEME] = theme }
    suspend fun setDefaultFilter(filter: String) = context.dataStore.edit { it[Keys.DEFAULT_FILTER] = filter }
    suspend fun setHdMode(enabled: Boolean) = context.dataStore.edit { it[Keys.HD_MODE] = enabled }
    suspend fun setPageSize(size: String) = context.dataStore.edit { it[Keys.PAGE_SIZE] = size }
    suspend fun setNamePattern(pattern: String) = context.dataStore.edit { it[Keys.NAME_PATTERN] = pattern }
    suspend fun setMirrorEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.MIRROR_ENABLED] = enabled }
    suspend fun setOcrSearchLanguage(lang: String) = context.dataStore.edit { it[Keys.OCR_SEARCH_LANGUAGE] = lang }
    suspend fun setOcrLastLanguage(lang: String) = context.dataStore.edit { it[Keys.OCR_LAST_LANGUAGE] = lang }
    suspend fun setArabicEngine(engine: String) = context.dataStore.edit { it[Keys.ARABIC_ENGINE] = engine }
    suspend fun setBookDirection(dir: String) = context.dataStore.edit { it[Keys.BOOK_DIRECTION] = dir }
    suspend fun setSortOrder(order: String) = context.dataStore.edit { it[Keys.SORT_ORDER] = order }
    suspend fun setViewMode(mode: String) = context.dataStore.edit { it[Keys.VIEW_MODE] = mode }
    suspend fun setAutoBackupEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.AUTO_BACKUP_ENABLED] = enabled }
    suspend fun setAutoBackupTreeUri(uri: String?) = context.dataStore.edit {
        if (uri != null) it[Keys.AUTO_BACKUP_TREE_URI] = uri else it.remove(Keys.AUTO_BACKUP_TREE_URI)
    }
    suspend fun setAutoBackupIntervalDays(days: Int) = context.dataStore.edit { it[Keys.AUTO_BACKUP_INTERVAL_DAYS] = days }
    suspend fun setBackupKeepCount(count: Int) = context.dataStore.edit { it[Keys.BACKUP_KEEP_COUNT] = count }
    suspend fun setLastBackupAt(timestamp: Long) = context.dataStore.edit { it[Keys.LAST_BACKUP_AT] = timestamp }
    suspend fun setLastOffPhoneReminderAt(timestamp: Long) = context.dataStore.edit { it[Keys.LAST_OFF_PHONE_REMINDER_AT] = timestamp }
    suspend fun setPin(hash: String, salt: String) = context.dataStore.edit {
        it[Keys.PIN_HASH] = hash
        it[Keys.PIN_SALT] = salt
    }
    suspend fun clearPin() = context.dataStore.edit {
        it.remove(Keys.PIN_HASH)
        it.remove(Keys.PIN_SALT)
        it[Keys.BIOMETRIC_ENABLED] = false
    }
    suspend fun setBiometricEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.BIOMETRIC_ENABLED] = enabled }
    suspend fun setQrSaveHistory(enabled: Boolean) = context.dataStore.edit { it[Keys.QR_SAVE_HISTORY] = enabled }
    suspend fun setCameraEngine(engine: String) = context.dataStore.edit { it[Keys.CAMERA_ENGINE] = engine }
}
