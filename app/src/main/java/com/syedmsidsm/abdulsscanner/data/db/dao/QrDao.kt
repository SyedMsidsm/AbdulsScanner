package com.syedmsidsm.abdulsscanner.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.syedmsidsm.abdulsscanner.data.db.entity.QrHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QrDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: QrHistoryEntity): Long

    @Query("SELECT * FROM qr_history ORDER BY scannedAt DESC")
    fun observeAll(): Flow<List<QrHistoryEntity>>

    @Query("SELECT * FROM qr_history WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): QrHistoryEntity?

    @Query("DELETE FROM qr_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM qr_history")
    suspend fun clearAll()
}
