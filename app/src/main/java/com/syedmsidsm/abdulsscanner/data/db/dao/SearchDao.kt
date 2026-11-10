package com.syedmsidsm.abdulsscanner.data.db.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.syedmsidsm.abdulsscanner.data.db.entity.DocumentSearchFts

data class SearchResultRow(
    @ColumnInfo(name = "docId")
    val docId: String,
    @ColumnInfo(name = "snippet")
    val snippet: String
)

@Dao
interface SearchDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(fts: DocumentSearchFts)

    @Query("DELETE FROM document_search WHERE doc_id = :docId")
    suspend fun deleteByDocId(docId: String)

    @Query("""
        SELECT doc_id AS docId, snippet(document_search, '[', ']', '…', -1, 12) AS snippet
        FROM document_search
        WHERE document_search MATCH :ftsQuery
    """)
    suspend fun search(ftsQuery: String): List<SearchResultRow>
}
