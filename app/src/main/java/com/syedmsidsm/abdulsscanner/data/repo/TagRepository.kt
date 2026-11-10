package com.syedmsidsm.abdulsscanner.data.repo

import com.syedmsidsm.abdulsscanner.data.db.dao.TagDao
import com.syedmsidsm.abdulsscanner.data.db.dao.TagWithCount
import com.syedmsidsm.abdulsscanner.data.db.entity.DocumentTagCrossRef
import com.syedmsidsm.abdulsscanner.data.db.entity.TagEntity
import kotlinx.coroutines.flow.Flow

class TagRepository(private val tagDao: TagDao) {

    fun observeTagsWithCounts(): Flow<List<TagWithCount>> = tagDao.observeTagsWithCounts()
    fun observeUngroupedCount(): Flow<Int> = tagDao.observeUngroupedCount()
    fun observeAllTags(): Flow<List<TagEntity>> = tagDao.observeAll()
    suspend fun getAllTags(): List<TagEntity> = tagDao.getAll()

    suspend fun createTag(name: String): Long {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return -1
        val existing = tagDao.getByName(trimmed)
        if (existing != null) return existing.id
        return tagDao.insert(TagEntity(name = trimmed))
    }

    suspend fun renameTag(id: Long, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isNotEmpty()) {
            tagDao.rename(id, trimmed)
        }
    }

    suspend fun deleteTag(id: Long) {
        tagDao.deleteById(id)
    }

    fun observeTagsForDocument(documentId: String): Flow<List<TagEntity>> =
        tagDao.observeTagsForDocument(documentId)

    suspend fun getTagsForDocument(documentId: String): List<TagEntity> =
        tagDao.getTagsForDocument(documentId)

    suspend fun setTagsForDocument(documentId: String, tagIds: Set<Long>) {
        tagDao.clearTagsForDocument(documentId)
        for (tagId in tagIds) {
            tagDao.addTagToDocument(DocumentTagCrossRef(documentId, tagId))
        }
    }
}
