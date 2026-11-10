package com.syedmsidsm.abdulsscanner.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.syedmsidsm.abdulsscanner.data.db.entity.SignatureEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SignatureDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(signature: SignatureEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(signature: SignatureEntity): Long

    @Query("SELECT * FROM signatures")
    suspend fun getAll(): List<SignatureEntity>

    @Query("SELECT * FROM signatures ORDER BY createdAt DESC LIMIT 5")
    fun observeSignatures(): Flow<List<SignatureEntity>>

    @Query("SELECT * FROM signatures ORDER BY createdAt DESC LIMIT 5")
    suspend fun getSignatures(): List<SignatureEntity>

    @Query("DELETE FROM signatures WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM signatures")
    suspend fun count(): Int
}
