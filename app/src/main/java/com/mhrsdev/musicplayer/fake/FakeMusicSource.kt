package com.mhrsdev.musicplayer.fake

import com.mhrsdev.musicplayer.domain.model.Track
import com.mhrsdev.musicplayer.domain.repository.MusicSource

/** In-memory catalog source: searches [SampleData]; has no playable URIs. */
class FakeMusicSource : MusicSource {
    override val id = "fake"
    override val displayName = "Sample catalog"
    override suspend fun search(query: String, limit: Int): List<Track> =
        SampleData.tracks.filter { it.title.contains(query, true) || it.artistName.contains(query, true) }.take(limit)
    override suspend fun resolvePlayableUri(track: Track): String? = null
}
