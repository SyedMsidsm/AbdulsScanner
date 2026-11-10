package com.syedmsidsm.abdulsscanner.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.syedmsidsm.abdulsscanner.data.db.entity.PageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(page: PageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(pages: List<PageEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPagesIgnore(pages: List<PageEntity>)

    @Query("SELECT * FROM pages")
    suspend fun getAllPages(): List<PageEntity>

    @Update
    suspend fun update(page: PageEntity)

    @Update
    suspend fun updateAll(pages: List<PageEntity>)

    @Query("SELECT * FROM pages WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PageEntity?

    @Query("SELECT * FROM pages WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<PageEntity?>

    @Query("SELECT * FROM pages WHERE documentId = :documentId ORDER BY position ASC")
    suspend fun getByDocumentId(documentId: String): List<PageEntity>

    @Query("SELECT * FROM pages WHERE documentId = :documentId ORDER BY position ASC")
    fun observeByDocumentId(documentId: String): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE ocrStatus = 'PENDING' AND documentId = :documentId ORDER BY position ASC")
    suspend fun getPendingOcrPages(documentId: String): List<PageEntity>

    @Query("SELECT * FROM pages WHERE ocrStatus = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getAllPendingOcrPages(): List<PageEntity>

    @Query("UPDATE pages SET ocrText = :text, ocrLang = :lang, ocrStatus = :status WHERE id = :pageId")
    suspend fun updateOcr(pageId: String, text: String?, lang: String?, status: String)

    @Query("UPDATE pages SET ocrStatus = 'PENDING'")
    suspend fun markAllOcrPending()

    @Query("UPDATE pages SET position = :newPosition WHERE id = :pageId")
    suspend fun updatePosition(pageId: String, newPosition: Int)

    @Query("DELETE FROM pages WHERE id = :pageId")
    suspend fun deleteById(pageId: String)

    @Query("DELETE FROM pages WHERE documentId = :documentId")
    suspend fun deleteByDocumentId(documentId: String)

    @Query("SELECT COUNT(*) FROM pages WHERE documentId = :documentId")
    suspend fun countByDocumentId(documentId: String): Int
}
