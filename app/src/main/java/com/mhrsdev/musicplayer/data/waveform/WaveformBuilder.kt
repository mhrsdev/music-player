package com.mhrsdev.musicplayer.data.waveform

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Pure-Kotlin core: turns a stream of interleaved PCM samples (any channel count, already scaled to -1..1)
 * into a normalized loudness profile of [bins] values in 0f..1f.
 *
 * Pipeline: per-10ms-window mean power (channels averaged in the power domain) -> equal split into [bins]
 * -> RMS -> dB -> normalize against the 95th percentile (and a [RANGE_DB] floor below it) -> light smoothing.
 * Memory is O(duration): one float per 10 ms (about 120 KB for a 5 minute song).
 */
class WaveformBuilder(
    val sampleRate: Int,
    val channels: Int,
    val bins: Int = DEFAULT_BINS,
) {
    init {
        require(sampleRate > 0) { "sampleRate" }
        require(channels > 0) { "channels" }
        require(bins > 0) { "bins" }
    }

    private val windowFrames = max(1, sampleRate / WINDOWS_PER_SECOND)
    private var windows = FloatArray(4096)
    private var windowCount = 0
    private var windowPower = 0.0
    private var windowFilled = 0
    private var framePower = 0f
    private var channelIndex = 0

    /** Number of completed 10 ms windows consumed so far. */
    val windowsCollected: Int get() = windowCount

    /** Feeds one sample (one channel of one frame), expected in -1..1. Out-of-range values are clipped, NaN -> 0. */
    fun addSample(sample: Float) {
        val s = if (sample.isNaN()) 0f else if (sample > 1f) 1f else if (sample < -1f) -1f else sample
        framePower += s * s
        if (++channelIndex == channels) {
            windowPower += framePower / channels
            framePower = 0f
            channelIndex = 0
            if (++windowFilled == windowFrames) flushWindow()
        }
    }

    /** Feeds [count] interleaved float samples. */
    fun addFloats(data: FloatArray, count: Int = data.size) {
        for (i in 0 until count) addSample(data[i])
    }

    /** Feeds [count] interleaved signed 16-bit samples. */
    fun addShorts(data: ShortArray, count: Int = data.size) {
        for (i in 0 until count) addSample(data[i] / 32768f)
    }

    private fun flushWindow() {
        if (windowCount == windows.size) windows = windows.copyOf(windows.size * 2)
        windows[windowCount++] = (windowPower / windowFilled).toFloat()
        windowPower = 0.0
        windowFilled = 0
    }

    /** Produces the profile. Silence or no data -> all zeros ([bins] values). The builder may keep being fed afterwards. */
    fun build(): FloatArray {
        val count = windowCount + if (windowFilled > 0) 1 else 0
        if (count == 0) return FloatArray(bins)
        val power = windows.copyOf(count)
        if (windowFilled > 0) power[count - 1] = (windowPower / windowFilled).toFloat()
        return normalize(binPower(power, bins))
    }

    companion object {
        const val DEFAULT_BINS = 256
        const val WINDOWS_PER_SECOND = 100
        const val PERCENTILE = 0.95f

        /** Dynamic range shown below the percentile reference level. */
        const val RANGE_DB = 32f

        /** If the percentile level is below this (about -70 dBFS RMS) the whole track is treated as silent. */
        const val SILENCE_DB = -70f

        private const val EPS = 1e-9f

        /** Averages window powers into [bins] equal slices and returns each slice's RMS. Fewer windows than bins repeats windows. */
        internal fun binPower(power: FloatArray, bins: Int): FloatArray {
            val n = power.size
            val out = FloatArray(bins)
            for (i in 0 until bins) {
                val s = ((i.toLong() * n) / bins).toInt().coerceIn(0, n - 1)
                val e = max(s + 1, (((i + 1).toLong() * n) / bins).toInt()).coerceAtMost(n)
                var sum = 0.0
                for (k in s until e) sum += power[k]
                out[i] = sqrt((sum / (e - s)).toFloat())
            }
            return out
        }

        /** RMS values (0..1 linear) -> dB -> percentile-relative 0..1 -> smoothed. */
        internal fun normalize(rms: FloatArray): FloatArray {
            val n = rms.size
            val db = FloatArray(n) { 20f * log10(max(rms[it], EPS)) }
            val sorted = db.copyOf().also { it.sort() }
            val hi = sorted[((n - 1) * PERCENTILE).toInt().coerceIn(0, n - 1)]
            if (hi < SILENCE_DB) return FloatArray(n)
            val lo = hi - RANGE_DB
            val v = FloatArray(n) { ((db[it] - lo) / RANGE_DB).coerceIn(0f, 1f) }
            return smooth(v)
        }

        /** [1 2 1]/4 kernel with edge clamping. */
        internal fun smooth(v: FloatArray): FloatArray {
            if (v.size < 3) return v
            val out = FloatArray(v.size)
            for (i in v.indices) {
                val a = v[max(i - 1, 0)]
                val c = v[min(i + 1, v.size - 1)]
                out[i] = (a + 2f * v[i] + c) / 4f
            }
            return out
        }

        /** Resamples a profile to [bars] values: mean-pooling when shrinking, linear interpolation when growing. */
        fun resample(src: FloatArray, bars: Int): FloatArray {
            require(bars > 0) { "bars" }
            if (src.isEmpty()) return FloatArray(bars)
            if (src.size == bars) return src.copyOf()
            val out = FloatArray(bars)
            if (bars < src.size) {
                for (i in 0 until bars) {
                    val s = ((i.toLong() * src.size) / bars).toInt()
                    val e = max(s + 1, (((i + 1).toLong() * src.size) / bars).toInt()).coerceAtMost(src.size)
                    var sum = 0f
                    for (k in s until e) sum += src[k]
                    out[i] = sum / (e - s)
                }
            } else {
                for (i in 0 until bars) {
                    val pos = (i + 0.5f) * src.size / bars - 0.5f
                    val i0 = if (pos < 0f) 0 else pos.toInt().coerceAtMost(src.size - 1)
                    val i1 = min(i0 + 1, src.size - 1)
                    val f = (pos - i0).coerceIn(0f, 1f)
                    out[i] = src[i0] * (1f - f) + src[i1] * f
                }
            }
            for (i in out.indices) out[i] = abs(out[i]).coerceAtMost(1f)
            return out
        }
    }
}
