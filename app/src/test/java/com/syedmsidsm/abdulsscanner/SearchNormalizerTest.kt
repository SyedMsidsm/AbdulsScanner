package com.syedmsidsm.abdulsscanner

import com.syedmsidsm.abdulsscanner.domain.search.SearchNormalizer
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchNormalizerTest {

    @Test
    fun testArabicDiacriticsRemoval() {
        // "كِتَابٌ" (with damma, kasra, tanween) -> "كتاب"
        val withTashkeel = "\u0643\u0650\u062A\u064E\u0627\u0628\u064C"
        val expected = "\u0643\u062A\u0627\u0628"
        assertEquals(expected, SearchNormalizer.normalize(withTashkeel))
    }

    @Test
    fun testAlefVariantsUnified() {
        // "أحمد" (with hamza above), "إبراهيم" (with hamza below), "آية" (with madda)
        val input = "أحمد إبراهيم آية"
        val normalized = SearchNormalizer.normalize(input)
        assertEquals("احمد ابراهيم ايه", normalized)
    }

    @Test
    fun testArabicAndPersianDigitsConvertedToAscii() {
        val arabicDigits = "١٢٣٤٥"
        val persianDigits = "۶۷۸۹۰"
        assertEquals("12345", SearchNormalizer.normalize(arabicDigits))
        assertEquals("67890", SearchNormalizer.normalize(persianDigits))
    }

    @Test
    fun testFtsQueryBuilding() {
        val raw = "فاتورة Electricity: 2026*"
        val fts = SearchNormalizer.buildFtsQuery(raw)
        assertEquals("\"فاتوره\"* \"electricity\"* \"2026\"*", fts)
    }
}
