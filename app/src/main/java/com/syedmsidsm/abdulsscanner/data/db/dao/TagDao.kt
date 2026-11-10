package com.syedmsidsm.abdulsscanner.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.syedmsidsm.abdulsscanner.data.db.entity.DocumentTagCrossRef
import com.syedmsidsm.abdulsscanner.data.db.entity.TagEntity
import kotlinx.coroutines.flow.Flow

data class TagWithCount(
    val id: Long,
    val name: String,
    val docCount: Int
)

@Dao
interface TagDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity): Long

    @Query("SELECT * FROM tags WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getByName(name: String): TagEntity?

    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAll(): List<TagEntity>

    @Query("""
        SELECT t.id, t.name, COUNT(d.id) AS docCount
        FROM tags t
        LEFT JOIN document_tags dt ON t.id = dt.tagId
        LEFT JOIN documents d ON dt.documentId = d.id AND d.deletedAt IS NULL
        GROUP BY t.id, t.name
        ORDER BY t.name COLLATE NOCASE ASC
    """)
    fun observeTagsWithCounts(): Flow<List<TagWithCount>>

    @Query("""
        SELECT COUNT(d.id) FROM documents d
        WHERE d.deletedAt IS NULL
        AND d.id NOT IN (SELECT DISTINCT documentId FROM document_tags)
    """)
    fun observeUngroupedCount(): Flow<Int>

    @Query("UPDATE tags SET name = :newName WHERE id = :id")
    suspend fun rename(id: Long, newName: String)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTagToDocument(crossRef: DocumentTagCrossRef)

    @Query("DELETE FROM document_tags WHERE documentId = :documentId AND tagId = :tagId")
    suspend fun removeTagFromDocument(documentId: String, tagId: Long)

    @Query("DELETE FROM document_tags WHERE documentId = :documentId")
    suspend fun clearTagsForDocument(documentId: String)

    @Query("""
        SELECT t.* FROM tags t
        INNER JOIN document_tags dt ON t.id = dt.tagId
        WHERE dt.documentId = :documentId
        ORDER BY t.name COLLATE NOCASE ASC
    """)
    fun observeTagsForDocument(documentId: String): Flow<List<TagEntity>>

    @Query("""
        SELECT t.* FROM tags t
        INNER JOIN document_tags dt ON t.id = dt.tagId
        WHERE dt.documentId = :documentId
        ORDER BY t.name COLLATE NOCASE ASC
    """)
    suspend fun getTagsForDocument(documentId: String): List<TagEntity>
}
