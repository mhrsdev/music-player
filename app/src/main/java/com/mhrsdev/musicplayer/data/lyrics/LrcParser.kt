package com.mhrsdev.musicplayer.data.lyrics

import com.mhrsdev.musicplayer.domain.model.LyricLine
import com.mhrsdev.musicplayer.domain.model.Lyrics

/** Result of parsing LRC text. [metadata] holds tags such as `ar`, `ti`, `al` (lower-cased keys). */
data class ParsedLrc(
    val lines: List<LyricLine>,
    val isSynced: Boolean,
    val metadata: Map<String, String>,
    val offsetMs: Long,
)

/**
 * Tolerant LRC parser.
 *
 * Supported: `[mm:ss.xx]`, `[mm:ss:xx]`, `[mm:ss]`, `[mm:ss.xxx]`, `[h:mm:ss.xx]`, several timestamps per line,
 * `[offset:+/-ms]` (positive = lyrics appear earlier, per LRC convention), metadata tags (`ar ti al by length ...`),
 * enhanced word-level `<mm:ss.xx>` tags (stripped; the public [Lyrics] model has no word timings), unsynced text
 * fallback, any Unicode digits in timestamps (Persian/Arabic-Indic), LF/CRLF/CR, invisible bidi marks and BOM
 * before tags. Non-tag text lines inside a synced file are ignored (they cannot be placed on the timeline).
 */
object LrcParser {
    private val WORD_TAG = Regex("""<\s*\d+\s*:\s*\d+(?:\s*[.:,]\s*\d+)?\s*>""")
    private val MULTI_SPACE = Regex("""[ \t]{2,}""")
    private val SHORT_TS = Regex("""^(\d+):(\d{1,2})(?:[.:,](\d{1,3}))?$""")
    private val LONG_TS = Regex("""^(\d+):(\d{1,2}):(\d{1,2})[.,](\d{1,3})$""")
    private val META_KEY = Regex("""^([A-Za-z#][A-Za-z0-9_#-]*)\s*:(.*)$""", RegexOption.DOT_MATCHES_ALL)

    fun parse(raw: String): ParsedLrc? {
        val synced = ArrayList<LyricLine>()
        val plain = ArrayList<String>()
        val meta = LinkedHashMap<String, String>()

        for (rawLine in raw.split("\r\n", "\n", "\r")) {
            var i = skipInvisible(rawLine, 0)
            if (i >= rawLine.length) {
                plain += ""
                continue
            }
            val start = i
            val times = ArrayList<Long>()
            var isMeta = false
            while (i < rawLine.length && rawLine[i] == '[') {
                val close = rawLine.indexOf(']', i)
                if (close < 0) break
                val inner = rawLine.substring(i + 1, close)
                val ts = parseTimestamp(inner)
                if (ts != null) {
                    times += ts
                    i = skipSpaces(rawLine, close + 1)
                    continue
                }
                if (times.isEmpty()) {
                    val m = META_KEY.matchEntire(inner)
                    if (m != null) {
                        meta[m.groupValues[1].lowercase()] = m.groupValues[2].trim()
                        isMeta = true
                    }
                }
                break
            }
            if (isMeta) continue
            if (times.isEmpty()) {
                plain += stripWordTags(rawLine.substring(start)).trim()
            } else {
                val text = stripWordTags(rawLine.substring(i)).trim()
                for (t in times) synced += LyricLine(t, text)
            }
        }

        val offset = meta["offset"]?.trim()?.removePrefix("+")?.toLongOrNull() ?: 0L

        if (synced.isNotEmpty()) {
            val shifted = synced.map { LyricLine((it.timeMs - offset).coerceAtLeast(0L), it.text) }
                .sortedBy { it.timeMs } // stable: equal timestamps (e.g. translations) keep file order
            val lines = collapseBlanks(shifted) { it.text.isEmpty() }
            if (lines.none { it.text.isNotEmpty() }) return null
            val allSameTime = lines.size > 1 && lines.all { it.timeMs == lines[0].timeMs }
            if (!allSameTime) return ParsedLrc(lines, true, meta, offset)
            // Degenerate "[00:00.00]" on every line: treat as unsynced text.
            return unsynced(lines.map { it.text }, meta, offset)
        }
        return unsynced(plain, meta, offset)
    }

    private fun unsynced(texts: List<String>, meta: Map<String, String>, offset: Long): ParsedLrc? {
        val lines = collapseBlanks(texts) { it.isEmpty() }
            .dropWhile { it.isEmpty() }.dropLastWhile { it.isEmpty() }
        if (lines.isEmpty()) return null
        return ParsedLrc(lines.map { LyricLine(LyricLine.UNSYNCED, it) }, false, meta, offset)
    }

    /** Collapses runs of blank entries into one. */
    private fun <T> collapseBlanks(items: List<T>, isBlank: (T) -> Boolean): List<T> {
        val out = ArrayList<T>(items.size)
        for (item in items) {
            if (isBlank(item) && out.isNotEmpty() && isBlank(out.last())) continue
            out += item
        }
        return out
    }

    fun toLyrics(trackId: String, raw: String, source: String?): Lyrics? {
        val p = parse(raw) ?: return null
        return Lyrics(trackId, p.lines, p.isSynced, source)
    }

    /** Parses the inside of a `[...]` tag, returns milliseconds or null if it is not a timestamp. */
    internal fun parseTimestamp(inner: String): Long? {
        val s = normalizeDigits(inner.trim())
        LONG_TS.matchEntire(s)?.let { m ->
            val h = m.groupValues[1].toLongOrNull() ?: return null
            val mi = m.groupValues[2].toLong()
            val sec = m.groupValues[3].toLong()
            return ((h * 60 + mi) * 60 + sec) * 1000 + fractionMs(m.groupValues[4])
        }
        SHORT_TS.matchEntire(s)?.let { m ->
            val min = m.groupValues[1].toLongOrNull() ?: return null
            val sec = m.groupValues[2].toLong()
            val frac = m.groupValues[3]
            return (min * 60 + sec) * 1000 + if (frac.isEmpty()) 0 else fractionMs(frac)
        }
        return null
    }

    /** "5" -> 500, "50" -> 500, "05" -> 50, "500" -> 500. */
    private fun fractionMs(frac: String): Long = frac.padEnd(3, '0').take(3).toLong()

    private fun normalizeDigits(s: String): String {
        if (s.all { it.code < 128 }) return s
        val sb = StringBuilder(s.length)
        for (c in s) {
            val d = if (c.code < 128) -1 else Character.digit(c, 10)
            sb.append(
                when {
                    d >= 0 -> '0' + d
                    c == '٫' -> '.' // Arabic decimal separator
                    c == '：' -> ':' // fullwidth colon
                    else -> c
                },
            )
        }
        return sb.toString()
    }

    private fun stripWordTags(s: String): String =
        if (s.indexOf('<') < 0) s else MULTI_SPACE.replace(WORD_TAG.replace(s, ""), " ")

    private fun isInvisible(c: Char) =
        c == '﻿' || c == '‎' || c == '‏' || c in '‪'..'‮' || c in '⁦'..'⁩' || c == '​'

    private fun skipInvisible(s: String, from: Int): Int {
        var i = from
        while (i < s.length && (s[i].isWhitespace() || isInvisible(s[i]))) i++
        return i
    }

    private fun skipSpaces(s: String, from: Int): Int {
        var i = from
        while (i < s.length && (s[i] == ' ' || s[i] == '\t')) i++
        return i
    }
}
