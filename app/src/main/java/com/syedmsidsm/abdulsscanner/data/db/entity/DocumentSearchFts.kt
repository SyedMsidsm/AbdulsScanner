package com.syedmsidsm.abdulsscanner.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

@Fts4(tokenizer = "unicode61", notIndexed = ["doc_id"])
@Entity(tableName = "document_search")
data class DocumentSearchFts(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "rowid")
    val rowId: Int = 0,
    @ColumnInfo(name = "doc_id")
    val docId: String,
    val name: String,
    val body: String
)
