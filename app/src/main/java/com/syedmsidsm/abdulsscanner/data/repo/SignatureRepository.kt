package com.syedmsidsm.abdulsscanner.data.repo

import com.syedmsidsm.abdulsscanner.data.db.dao.SignatureDao
import com.syedmsidsm.abdulsscanner.data.db.entity.SignatureEntity
import com.syedmsidsm.abdulsscanner.data.files.FileStore
import kotlinx.coroutines.flow.Flow

class SignatureRepository(
    private val signatureDao: SignatureDao,
    private val fileStore: FileStore
) {

    fun observeSignatures(): Flow<List<SignatureEntity>> = signatureDao.observeSignatures()

    suspend fun getSignatures(): List<SignatureEntity> = signatureDao.getSignatures()

    suspend fun saveSignature(signature: SignatureEntity) {
        // Enforce max 5 signatures: delete oldest if >= 5
        val existing = signatureDao.getSignatures()
        if (existing.size >= 5) {
            val toDelete = existing.last()
            deleteSignature(toDelete.id)
        }
        signatureDao.insert(signature)
    }

    suspend fun deleteSignature(id: String) {
        val file = fileStore.getSignatureFile(id)
        file.delete()
        signatureDao.deleteById(id)
    }
}
