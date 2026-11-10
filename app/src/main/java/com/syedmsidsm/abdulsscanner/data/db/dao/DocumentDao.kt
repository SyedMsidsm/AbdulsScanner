package com.syedmsidsm.abdulsscanner.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.syedmsidsm.abdulsscanner.data.db.entity.DocumentEntity
import com.syedmsidsm.abdulsscanner.data.db.entity.PageEntity
import kotlinx.coroutines.flow.Flow

data class DocumentWithPages(
    val document: DocumentEntity,
    val pages: List<PageEntity>
)

@Dao
interface DocumentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(document: DocumentEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(document: DocumentEntity): Long

    @Update
    suspend fun update(document: DocumentEntity)

    @Query("SELECT * FROM documents")
    suspend fun getAllDocuments(): List<DocumentEntity>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DocumentEntity?

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<DocumentEntity?>

    // All active docs ordered by updatedAt DESC
    @Query("SELECT * FROM documents WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeAllByUpdatedDesc(): Flow<List<DocumentEntity>>

    // All active docs ordered by createdAt DESC
    @Query("SELECT * FROM documents WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeAllByCreatedDesc(): Flow<List<DocumentEntity>>

    // All active docs ordered by name ASC
    @Query("SELECT * FROM documents WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE ASC")
    fun observeAllByNameAsc(): Flow<List<DocumentEntity>>

    // Active docs filtered by Tag
    @Query("""
        SELECT d.* FROM documents d
        INNER JOIN document_tags dt ON d.id = dt.documentId
        WHERE d.deletedAt IS NULL AND dt.tagId = :tagId
        ORDER BY d.updatedAt DESC
    """)
    fun observeByTagUpdatedDesc(tagId: Long): Flow<List<DocumentEntity>>

    // Active docs that are ungrouped (have no tags)
    @Query("""
        SELECT d.* FROM documents d
        WHERE d.deletedAt IS NULL
        AND d.id NOT IN (SELECT DISTINCT documentId FROM document_tags)
        ORDER BY d.updatedAt DESC
    """)
    fun observeUngroupedUpdatedDesc(): Flow<List<DocumentEntity>>

    // Trash list (deletedAt IS NOT NULL)
    @Query("SELECT * FROM documents WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrash(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    suspend fun getTrashList(): List<DocumentEntity>

    // Soft delete
    @Query("UPDATE documents SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun moveToTrash(id: String, deletedAt: Long = System.currentTimeMillis())

    // Restore from trash
    @Query("UPDATE documents SET deletedAt = NULL WHERE id = :id")
    suspend fun restoreFromTrash(id: String)

    // Hard delete
    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deletePermanently(id: String)

    @Query("DELETE FROM documents WHERE deletedAt IS NOT NULL")
    suspend fun emptyTrash()

    // Query for purge older than cutoff
    @Query("SELECT * FROM documents WHERE deletedAt IS NOT NULL AND deletedAt < :cutoffMillis")
    suspend fun getDeletedBefore(cutoffMillis: Long): List<DocumentEntity>

    @Query("UPDATE documents SET name = :newName, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: String, newName: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE documents SET locked = :locked, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setLocked(id: String, locked: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE documents SET pageSize = :pageSize, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setPageSize(id: String, pageSize: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE documents SET mirrorUri = :mirrorUri WHERE id = :id")
    suspend fun updateMirrorUri(id: String, mirrorUri: String?)

    @Query("UPDATE documents SET pdfBuiltAt = :builtAt WHERE id = :id")
    suspend fun updatePdfBuiltAt(id: String, builtAt: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM documents WHERE deletedAt IS NULL")
    fun observeActiveCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM documents WHERE deletedAt IS NULL")
    suspend fun getActiveCount(): Int

    @Query("SELECT MAX(updatedAt) FROM documents WHERE deletedAt IS NULL")
    suspend fun getMaxUpdatedAt(): Long?
}
