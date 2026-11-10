package com.syedmsidsm.abdulsscanner.domain.search

object SearchNormalizer {

    private val TASHKEEL_REGEX = Regex("[\\u064B-\\u0652\\u0670\\u0640]")
    private val PUNCTUATION_STRIP_REGEX = Regex("[\"*():^\\-]")

    fun normalize(text: String): String {
        if (text.isEmpty()) return text

        var result = text.lowercase()

        // Remove Arabic tashkeel (diacritics) and tatweel
        result = result.replace(TASHKEEL_REGEX, "")

        // Normalize Arabic letters
        val sb = StringBuilder(result.length)
        for (ch in result) {
            when (ch) {
                // Alef variants -> bare alef (ا)
                '\u0622', '\u0623', '\u0625', '\u0671' -> sb.append('\u0627')
                // Alif Maqsurah -> Yaa (ي)
                '\u0649' -> sb.append('\u064A')
                // Taa Marbuta -> Haa (ه)
                '\u0629' -> sb.append('\u0647')
                // Waw with Hamza -> Waw (و)
                '\u0624' -> sb.append('\u0648')
                // Yaa with Hamza -> Yaa (ي)
                '\u0626' -> sb.append('\u064A')

                // Arabic-Indic digits ٠-٩ -> 0-9
                in '\u0660'..'\u0669' -> sb.append(('0'.code + (ch.code - 0x0660)).toChar())

                // Eastern Arabic-Indic / Persian digits ۰-۹ -> 0-9
                in '\u06F0'..'\u06F9' -> sb.append(('0'.code + (ch.code - 0x06F0)).toChar())

                else -> sb.append(ch)
            }
        }

        return sb.toString()
    }

    /**
     * Builds FTS MATCH query: splits on whitespace, strips punctuation, wraps each token in "token"*, joins with space (AND).
     */
    fun buildFtsQuery(rawQuery: String): String {
        val normalized = normalize(rawQuery)
        val tokens = normalized.split(Regex("\\s+"))
            .map { it.replace(PUNCTUATION_STRIP_REGEX, "").trim() }
            .filter { it.isNotEmpty() }

        if (tokens.isEmpty()) return ""

        return tokens.joinToString(" ") { "\"$it\"*" }
    }
}
