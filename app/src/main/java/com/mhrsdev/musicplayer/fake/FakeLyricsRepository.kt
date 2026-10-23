package com.mhrsdev.musicplayer.fake

import com.mhrsdev.musicplayer.domain.model.Lyrics
import com.mhrsdev.musicplayer.domain.model.Track
import com.mhrsdev.musicplayer.domain.repository.LyricsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeLyricsRepository : LyricsRepository {
    override fun lyricsFor(track: Track): Flow<Lyrics?> = flow { emit(SampleData.lyricsFor(track.id)) }
}
