package com.mhrsdev.musicplayer.domain.repository

import com.mhrsdev.musicplayer.domain.model.Album
import com.mhrsdev.musicplayer.domain.model.Artist
import com.mhrsdev.musicplayer.domain.model.HomeSection
import com.mhrsdev.musicplayer.domain.model.Lyrics
import com.mhrsdev.musicplayer.domain.model.PlaybackState
import com.mhrsdev.musicplayer.domain.model.Playlist
import com.mhrsdev.musicplayer.domain.model.SearchFilter
import com.mhrsdev.musicplayer.domain.model.SearchResult
import com.mhrsdev.musicplayer.domain.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Domain-facing wrapper over the playback engine. All calls are main-safe and non-blocking. */
interface PlayerController {
    val state: StateFlow<PlaybackState>

    /** Replace the queue with [tracks] and start playing at [startIndex]. */
    fun play(tracks: List<Track>, startIndex: Int = 0, playingFrom: String? = null)
    /** Resume playback of the current queue. */
    fun resume()
    fun pause()
    fun togglePlayPause()
    fun seekTo(positionMs: Long)
    fun next()
    fun previous()
    fun skipTo(index: Int)

    fun toggleShuffle()
    /** OFF -> ALL -> ONE -> OFF */
    fun cycleRepeat()

    fun moveQueueItem(from: Int, to: Int)
    fun removeFromQueue(index: Int)
    /** Insert at [index] (null = append). Also used to undo a removal. */
    fun addToQueue(track: Track, index: Int? = null)
    /** Insert right after the current item. */
    fun playNext(track: Track)
    /** Shuffles the upcoming items (current stays); sets [PlaybackState.canUndoShuffle]. */
    fun shuffleQueue()
    fun undoShuffleQueue()

    fun setSleepTimer(minutes: Int)
    fun setSleepTimerEndOfTrack()
    fun cancelSleepTimer()
}

interface LibraryRepository {
    val allTracks: Flow<List<Track>>
    val likedTracks: Flow<List<Track>>
    val albums: Flow<List<Album>>
    val artists: Flow<List<Artist>>
    val playlists: Flow<List<Playlist>>
    /** Most recently played first. */
    val history: Flow<List<Track>>
    /** Most recently added first. */
    val recentlySaved: Flow<List<Track>>

    fun album(id: String): Flow<Album?>
    fun artist(id: String): Flow<Artist?>
    fun playlist(id: String): Flow<Playlist?>
    fun albumTracks(albumId: String): Flow<List<Track>>
    fun artistTracks(artistId: String): Flow<List<Track>>
    fun playlistTracks(playlistId: String): Flow<List<Track>>

    fun search(query: String, filter: SearchFilter = SearchFilter.ALL): Flow<List<SearchResult>>

    suspend fun toggleLike(trackId: String)
    suspend fun recordPlay(trackId: String)
    suspend fun createPlaylist(name: String): Playlist
    suspend fun addToPlaylist(playlistId: String, trackId: String)
}

interface LyricsRepository {
    /** Emits null while unknown/not found; emits Lyrics once resolved (synced or plain). */
    fun lyricsFor(track: Track): Flow<Lyrics?>
}

interface HomeFeedSource {
    fun sections(): Flow<List<HomeSection>>
}

/** Pluggable catalog provider (local library now; a legal online provider later). */
interface MusicSource {
    val id: String
    val displayName: String
    suspend fun search(query: String, limit: Int = 30): List<Track>
    /** Resolve a playable URI for [track], or null if it can't be played from this source. */
    suspend fun resolvePlayableUri(track: Track): String?
}
