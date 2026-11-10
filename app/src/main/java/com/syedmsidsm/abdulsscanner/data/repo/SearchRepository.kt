package com.syedmsidsm.abdulsscanner.data.repo

import com.syedmsidsm.abdulsscanner.data.db.dao.SearchDao
import com.syedmsidsm.abdulsscanner.data.db.dao.SearchResultRow
import com.syedmsidsm.abdulsscanner.data.db.entity.DocumentSearchFts
import com.syedmsidsm.abdulsscanner.domain.search.SearchNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SearchRepository(private val searchDao: SearchDao) {

    suspend fun indexDocument(docId: String, name: String, pageTexts: List<String>) = withContext(Dispatchers.IO) {
        val normalizedName = SearchNormalizer.normalize(name)
        val normalizedBody = SearchNormalizer.normalize(pageTexts.joinToString("\n"))

        val fts = DocumentSearchFts(
            docId = docId,
            name = normalizedName,
            body = normalizedBody
        )
        searchDao.insertOrUpdate(fts)
    }

    suspend fun removeDocument(docId: String) = withContext(Dispatchers.IO) {
        searchDao.deleteByDocId(docId)
    }

    suspend fun search(query: String): List<SearchResultRow> = withContext(Dispatchers.IO) {
        val ftsQuery = SearchNormalizer.buildFtsQuery(query)
        if (ftsQuery.isBlank()) return@withContext emptyList()
        searchDao.search(ftsQuery)
    }
}
