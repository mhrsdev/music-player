package com.mhrsdev.musicplayer.data.waveform

import com.mhrsdev.musicplayer.domain.model.Track
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Synchronous (call from a background dispatcher for the disk implementation) store of stored-resolution profiles. */
interface WaveformCache {
    fun get(key: String): FloatArray?
    fun put(key: String, profile: FloatArray)
}

/** Cache key: track id + media uri + duration + stored resolution (a changed file/duration invalidates the entry). */
object WaveformKey {
    fun of(track: Track, resolution: Int = WaveformBuilder.DEFAULT_BINS): String {
        val raw = "${track.id}|${track.mediaUri}|${track.durationMs}|$resolution"
        return MessageDigest.getInstance("SHA-1").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

/** Small thread-safe LRU. Values are copied in/out so callers cannot corrupt cached data. */
class MemoryWaveformCache(private val maxEntries: Int = 64) : WaveformCache {
    private val map = object : LinkedHashMap<String, FloatArray>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FloatArray>?) = size > maxEntries
    }

    @Synchronized override fun get(key: String): FloatArray? = map[key]?.copyOf()
    @Synchronized override fun put(key: String, profile: FloatArray) {
        map[key] = profile.copyOf()
    }

    @Synchronized fun size(): Int = map.size
}

/**
 * One tiny file per track: magic, version, count, then one unsigned byte per bar (quantized 0..255, ~0.4% error).
 * Writes are atomic (temp file + rename); corrupt or truncated files are deleted and read as a miss.
 * Oldest files (by last-modified) are pruned beyond [maxFiles].
 */
class DiskWaveformCache(private val dir: File, private val maxFiles: Int = 3000) : WaveformCache {
    private var writesSincePrune = 0

    override fun get(key: String): FloatArray? {
        val f = File(dir, "$key.wf")
        if (!f.isFile) return null
        return try {
            DataInputStream(f.inputStream().buffered()).use { i ->
                if (i.readInt() != MAGIC || i.readInt() != VERSION) throw IOException("header")
                val n = i.readInt()
                if (n !in 1..MAX_BARS) throw IOException("count")
                val bytes = ByteArray(n)
                i.readFully(bytes)
                if (i.read() != -1) throw IOException("trailing")
                FloatArray(n) { (bytes[it].toInt() and 0xFF) / 255f }
            }.also { f.setLastModified(System.currentTimeMillis()) }
        } catch (_: IOException) {
            f.delete()
            null
        }
    }

    override fun put(key: String, profile: FloatArray) {
        if (profile.isEmpty() || profile.size > MAX_BARS) return
        try {
            dir.mkdirs()
            val tmp = File(dir, "$key.tmp")
            DataOutputStream(tmp.outputStream().buffered()).use { o ->
                o.writeInt(MAGIC)
                o.writeInt(VERSION)
                o.writeInt(profile.size)
                for (v in profile) o.writeByte((v.coerceIn(0f, 1f) * 255f + 0.5f).toInt())
            }
            val dst = File(dir, "$key.wf")
            if (!tmp.renameTo(dst)) {
                dst.delete()
                if (!tmp.renameTo(dst)) tmp.delete()
            }
            if (++writesSincePrune >= PRUNE_EVERY) prune()
        } catch (_: IOException) {
            // Cache is best-effort.
        }
    }

    @Synchronized
    fun prune() {
        writesSincePrune = 0
        val files = dir.listFiles { f -> f.name.endsWith(".wf") } ?: return
        if (files.size <= maxFiles) return
        files.sortedBy { it.lastModified() }.take(files.size - maxFiles).forEach { it.delete() }
    }

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private companion object {
        const val MAGIC = 0x57465231 // "WFR1"
        const val VERSION = 1
        const val MAX_BARS = 4096
        const val PRUNE_EVERY = 50
    }
}
