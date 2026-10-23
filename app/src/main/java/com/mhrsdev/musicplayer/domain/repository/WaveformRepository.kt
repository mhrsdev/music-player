package com.mhrsdev.musicplayer.domain.repository

import com.mhrsdev.musicplayer.domain.model.Track
import kotlinx.coroutines.flow.Flow

/** Normalized loudness profile (values 0f..1f) for a track, [bars] samples evenly spread over its duration. Emits null while unavailable/being computed. */
interface WaveformRepository {
    fun waveformFor(track: Track, bars: Int = 128): Flow<FloatArray?>
}
