package com.mhrsdev.musicplayer.data.lyrics

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** Turns raw bytes of a lyrics file into text: BOM aware (UTF-8/16/32), BOM-less UTF-16 heuristic, strict UTF-8, then Windows-1252. */
object LyricsTextDecoder {
    private val WINDOWS_1252: Charset = Charset.forName("windows-1252")

    fun decode(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        fun starts(vararg b: Int) = bytes.size >= b.size && b.indices.all { (bytes[it].toInt() and 0xFF) == b[it] }
        return when {
            starts(0xEF, 0xBB, 0xBF) -> String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            starts(0xFF, 0xFE, 0x00, 0x00) -> String(bytes, 4, bytes.size - 4, Charset.forName("UTF-32LE"))
            starts(0x00, 0x00, 0xFE, 0xFF) -> String(bytes, 4, bytes.size - 4, Charset.forName("UTF-32BE"))
            starts(0xFF, 0xFE) -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            starts(0xFE, 0xFF) -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            else -> {
                guessBomlessUtf16(bytes)?.let { return String(bytes, it) }
                strictUtf8(bytes) ?: String(bytes, WINDOWS_1252)
            }
        }
    }

    private fun strictUtf8(bytes: ByteArray): String? = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    } catch (_: CharacterCodingException) {
        null
    }

    /** LRC text is full of ASCII ("[00:12.50]"), so UTF-16 without BOM shows many zero bytes on one parity. */
    private fun guessBomlessUtf16(bytes: ByteArray): Charset? {
        if (bytes.size < 4) return null
        val n = minOf(bytes.size, 400) and 1.inv()
        var evenZero = 0
        var oddZero = 0
        for (i in 0 until n) if (bytes[i].toInt() == 0) if (i % 2 == 0) evenZero++ else oddZero++
        val half = n / 2
        return when {
            oddZero >= half * 0.3 && evenZero == 0 -> Charsets.UTF_16LE
            evenZero >= half * 0.3 && oddZero == 0 -> Charsets.UTF_16BE
            else -> null
        }
    }
}
