package com.mhrsdev.musicplayer.data.waveform

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream

/**
 * Pure-Kotlin streaming WAV (RIFF) reader feeding a [WaveformBuilder]. Supports PCM 8/16/24/32-bit and
 * IEEE float 32/64-bit (also via WAVE_FORMAT_EXTENSIBLE), any channel count. Used by JVM tests and as a
 * fallback when MediaCodec cannot decode a file. Returns null for anything that is not a readable WAV.
 */
object WavDecoder {
    private const val FMT_PCM = 1
    private const val FMT_FLOAT = 3
    private const val FMT_EXTENSIBLE = 0xFFFE

    fun extract(input: InputStream, bins: Int = WaveformBuilder.DEFAULT_BINS, isActive: () -> Boolean = { true }): FloatArray? =
        try {
            read(DataInputStream(BufferedInputStream(input, 64 * 1024)), bins, isActive)
        } catch (_: EOFException) {
            null
        } catch (_: IOException) {
            null
        }

    private fun read(i: DataInputStream, bins: Int, isActive: () -> Boolean): FloatArray? {
        if (i.readTag() != "RIFF") return null
        i.readLeInt()
        if (i.readTag() != "WAVE") return null
        var format = 0
        var channels = 0
        var rate = 0
        var bits = 0
        while (true) {
            val tag = i.readTag()
            val size = i.readLeInt().toLong() and 0xFFFFFFFFL
            when (tag) {
                "fmt " -> {
                    if (size < 16) return null
                    format = i.readLeShort()
                    channels = i.readLeShort()
                    rate = i.readLeInt()
                    i.readLeInt() // byte rate
                    i.readLeShort() // block align
                    bits = i.readLeShort()
                    var rest = size - 16
                    if (format == FMT_EXTENSIBLE && rest >= 10) {
                        i.readLeShort() // cbSize
                        i.readLeShort() // valid bits
                        i.readLeInt() // channel mask
                        format = i.readLeShort() // first 2 bytes of the sub-format GUID
                        rest -= 10
                    }
                    i.skipFully(rest + (size and 1L))
                }
                "data" -> {
                    if (channels <= 0 || rate <= 0) return null
                    val bytes = bits / 8
                    val ok = (format == FMT_PCM && bytes in 1..4) || (format == FMT_FLOAT && (bytes == 4 || bytes == 8))
                    if (!ok) return null
                    val b = WaveformBuilder(rate, channels, bins)
                    // size 0 / 0xFFFFFFFF = unknown (streamed) -> read to EOF.
                    val limit = if (size == 0L || size == 0xFFFFFFFFL) Long.MAX_VALUE else size
                    decodeData(i, b, format, bytes, limit, isActive)
                    return if (b.windowsCollected == 0) null else b.build()
                }
                else -> i.skipFully(size + (size and 1L))
            }
        }
    }

    private fun decodeData(i: DataInputStream, b: WaveformBuilder, format: Int, bytes: Int, limit: Long, isActive: () -> Boolean) {
        val chunk = ByteArray(bytes * 4096)
        var carry = 0 // bytes of a split sample kept at the buffer start
        var remaining = limit
        while (remaining > 0 && isActive()) {
            val want = minOf((chunk.size - carry).toLong(), remaining).toInt()
            val got = i.read(chunk, carry, want)
            if (got <= 0) break
            remaining -= got
            val total = carry + got
            val samples = total / bytes
            for (s in 0 until samples) {
                val o = s * bytes
                val v: Float = when {
                    format == FMT_FLOAT && bytes == 4 -> java.lang.Float.intBitsToFloat(le(chunk, o, 4))
                    format == FMT_FLOAT -> java.lang.Double.longBitsToDouble(leLong(chunk, o)).toFloat()
                    bytes == 1 -> ((chunk[o].toInt() and 0xFF) - 128) / 128f
                    bytes == 2 -> le(chunk, o, 2).toShort() / 32768f
                    bytes == 3 -> ((le(chunk, o, 3) shl 8) shr 8) / 8388608f
                    else -> le(chunk, o, 4) / 2147483648f
                }
                b.addSample(v)
            }
            carry = total - samples * bytes
            for (k in 0 until carry) chunk[k] = chunk[samples * bytes + k]
        }
    }

    private fun le(a: ByteArray, o: Int, n: Int): Int {
        var v = 0
        for (k in 0 until n) v = v or ((a[o + k].toInt() and 0xFF) shl (8 * k))
        return v
    }

    private fun leLong(a: ByteArray, o: Int): Long {
        var v = 0L
        for (k in 0 until 8) v = v or ((a[o + k].toLong() and 0xFF) shl (8 * k))
        return v
    }

    private fun DataInputStream.readTag(): String = ByteArray(4).also { readFully(it) }.toString(Charsets.ISO_8859_1)
    private fun DataInputStream.readLeInt(): Int = Integer.reverseBytes(readInt())
    private fun DataInputStream.readLeShort(): Int = java.lang.Short.reverseBytes(readShort()).toInt() and 0xFFFF
    private fun DataInputStream.skipFully(n: Long) {
        var left = n
        while (left > 0) {
            val s = skip(left)
            if (s <= 0) {
                readByte()
                left--
            } else left -= s
        }
    }
}
