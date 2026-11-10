package com.syedmsidsm.abdulsscanner.data.repo

import com.syedmsidsm.abdulsscanner.data.db.dao.QrDao
import com.syedmsidsm.abdulsscanner.data.db.entity.QrHistoryEntity
import kotlinx.coroutines.flow.Flow

class QrRepository(private val qrDao: QrDao) {

    fun observeAll(): Flow<List<QrHistoryEntity>> = qrDao.observeAll()

    suspend fun getById(id: Long): QrHistoryEntity? = qrDao.getById(id)

    suspend fun saveQr(entry: QrHistoryEntity): Long = qrDao.insert(entry)

    suspend fun deleteById(id: Long) = qrDao.deleteById(id)

    suspend fun clearAll() = qrDao.clearAll()
}
