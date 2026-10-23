package com.mhrsdev.musicplayer.fake

import com.mhrsdev.musicplayer.domain.model.Track
import com.mhrsdev.musicplayer.domain.repository.WaveformRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Deterministic, plausible loudness curves for UI work (T2). Same track id + bar count -> same curve.
 *
 * The curve is built like a pop song: a quiet intro that ramps up, verses at medium level, louder
 * choruses, an optional breakdown, and a fade-out; plus beat-level texture so bars look alive.
 * Emits `null` first (as the real extractor will while decoding), then the profile after [latencyMs].
 */
class FakeWaveformRepository(private val latencyMs: Long = 450L) : WaveformRepository {

    override fun waveformFor(track: Track, bars: Int): Flow<FloatArray?> = flow {
        emit(null)
        if (latencyMs > 0) delay(latencyMs)
        emit(generate(track.id, bars))
    }

    companion object {
        /** Pure function so previews/tests can get a curve synchronously. */
        fun generate(trackId: String, bars: Int = 128): FloatArray {
            require(bars > 0) { "bars must be > 0" }
            val rnd = Random(trackId.hashCode())
            // Section plan over normalized time 0..1: level per section.
            val introEnd = 0.05f + rnd.nextFloat() * 0.06f
            val outroStart = 0.88f + rnd.nextFloat() * 0.06f
            val sections = buildList {
                var t = introEnd
                var chorus = false
                while (t < outroStart) {
                    val len = 0.09f + rnd.nextFloat() * 0.12f
                    val level = when {
                        chorus -> 0.82f + rnd.nextFloat() * 0.16f
                        rnd.nextFloat() < 0.18f -> 0.32f + rnd.nextFloat() * 0.12f // breakdown
                        else -> 0.55f + rnd.nextFloat() * 0.14f
                    }
                    add(Triple(t, min(outroStart, t + len), level))
                    t += len
                    chorus = !chorus
                }
            }
            val beatPhase = rnd.nextFloat() * 2f * PI.toFloat()
            val beatsPerBar = 0.9f + rnd.nextFloat() * 0.8f
            val noise = FloatArray(bars) { rnd.nextFloat() }
            val out = FloatArray(bars)
            for (i in 0 until bars) {
                val x = (i + 0.5f) / bars
                var level = sections.firstOrNull { x >= it.first && x < it.second }?.third ?: 0.5f
                if (x < introEnd) level = 0.18f + 0.4f * (x / introEnd)
                if (x >= outroStart) level *= max(0.08f, 1f - (x - outroStart) / (1f - outroStart))
                // Smoothed noise (3-tap) + a slow "beat" ripple.
                val n = (noise[max(0, i - 1)] + 2f * noise[i] + noise[min(bars - 1, i + 1)]) / 4f
                val beat = 0.5f + 0.5f * sin(beatPhase + i * beatsPerBar)
                out[i] = level * (0.72f + 0.2f * n + 0.08f * beat)
            }
            // Soften section edges a little so transitions read as musical, not stepped.
            val smoothed = FloatArray(bars) { i ->
                (out[max(0, i - 1)] * 0.25f + out[i] * 0.5f + out[min(bars - 1, i + 1)] * 0.25f)
            }
            val peak = smoothed.maxOf { abs(it) }.takeIf { it > 0f } ?: 1f
            return FloatArray(bars) { (smoothed[it] / peak).coerceIn(0f, 1f) }
        }
    }
}
