package com.syedmsidsm.abdulsscanner.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "signatures")
data class SignatureEntity(
    @PrimaryKey
    val id: String,
    val path: String,
    val createdAt: Long = System.currentTimeMillis()
)
