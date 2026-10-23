package com.mhrsdev.musicplayer.data.search

import java.text.Normalizer
import java.util.Locale

/**
 * Case-, diacritic- and script-variant-insensitive text folding used for search keys and sorting.
 *
 * Handles Latin accents (NFKD + strip combining marks), Arabic/Persian harakat and tatweel, Arabic vs Persian
 * letter variants (ي/ى -> ی, ك -> ک, ة -> ه, hamza-carrying alefs -> ا), Arabic-Indic and Persian digits -> ASCII,
 * and ZWNJ -> space. Pure Kotlin.
 */
object TextNormalizer {
    private val WHITESPACE = Regex("\\s+")

    fun normalize(input: String): String {
        if (input.isEmpty()) return input
        val decomposed = Normalizer.normalize(input, Normalizer.Form.NFKD)
        val sb = StringBuilder(decomposed.length)
        for (ch in decomposed) {
            when {
                Character.getType(ch) == Character.NON_SPACING_MARK.toInt() -> Unit // accents, harakat, dagger alef
                ch == 'ـ' || ch == '‍' || ch == '‏' || ch == '‎' -> Unit // tatweel, ZWJ, dir marks
                ch == '‌' -> sb.append(' ')
                ch == 'ي' || ch == 'ى' || ch == 'ئ' -> sb.append('ی')
                ch == 'ك' -> sb.append('ک')
                ch == 'ة' -> sb.append('ه')
                ch == 'أ' || ch == 'إ' || ch == 'آ' || ch == 'ٱ' -> sb.append('ا')
                ch == 'ؤ' -> sb.append('و')
                ch in '٠'..'٩' -> sb.append('0' + (ch - '٠'))
                ch in '۰'..'۹' -> sb.append('0' + (ch - '۰'))
                else -> sb.append(ch)
            }
        }
        return sb.toString().lowercase(Locale.ROOT).trim().replace(WHITESPACE, " ")
    }

    /** Query tokens (normalized, whitespace-split, no empties). */
    fun tokens(query: String): List<String> =
        normalize(query).split(' ').filter { it.isNotEmpty() }

    /** 0 = exact, 1 = prefix, 2 = a word starts with the token, 3 = substring, null = no match. */
    fun rank(nameKey: String, token: String): Int? = when {
        nameKey == token -> 0
        nameKey.startsWith(token) -> 1
        nameKey.contains(" $token") -> 2
        nameKey.contains(token) -> 3
        else -> null
    }
}
