package com.mhrsdev.musicplayer.fake

import com.mhrsdev.musicplayer.domain.model.Album
import com.mhrsdev.musicplayer.domain.model.Artist
import com.mhrsdev.musicplayer.domain.model.Playlist
import com.mhrsdev.musicplayer.domain.model.SearchFilter
import com.mhrsdev.musicplayer.domain.model.SearchResult
import com.mhrsdev.musicplayer.domain.model.Track
import com.mhrsdev.musicplayer.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeLibraryRepository : LibraryRepository {
    private val tracks = MutableStateFlow(
        SampleData.tracks.map { it.copy(isLiked = it.id in SampleData.initiallyLikedIds) },
    )
    private val playlistIds = MutableStateFlow(SampleData.seedPlaylists.associate { (p, ids) -> p.id to p })
    private val playlistTrackIds = MutableStateFlow(SampleData.seedPlaylists.associate { (p, ids) -> p.id to ids })
    private val historyIds = MutableStateFlow(SampleData.seedHistoryIds)
    private var nextPlaylist = 1

    override val allTracks: Flow<List<Track>> = tracks
    override val likedTracks: Flow<List<Track>> = tracks.map { list -> list.filter { it.isLiked } }
    override val albums: Flow<List<Album>> = MutableStateFlow(SampleData.albums)
    override val artists: Flow<List<Artist>> = MutableStateFlow(SampleData.artists)
    override val playlists: Flow<List<Playlist>> = combine(playlistIds, playlistTrackIds) { ps, ids ->
        ps.values.map { it.copy(trackCount = ids[it.id].orEmpty().size) }
    }
    override val history: Flow<List<Track>> = combine(tracks, historyIds) { all, ids ->
        val byId = all.associateBy { it.id }
        ids.mapNotNull { byId[it] }
    }
    override val recentlySaved: Flow<List<Track>> = tracks.map { l -> l.sortedByDescending { it.addedAtMs } }

    override fun album(id: String) = albums.map { l -> l.firstOrNull { it.id == id } }
    override fun artist(id: String) = artists.map { l -> l.firstOrNull { it.id == id } }
    override fun playlist(id: String) = playlists.map { l -> l.firstOrNull { it.id == id } }
    override fun albumTracks(albumId: String) =
        tracks.map { l -> l.filter { it.albumId == albumId }.sortedBy { it.trackNumber } }
    override fun artistTracks(artistId: String) =
        tracks.map { l -> l.filter { it.artistId == artistId } }
    override fun playlistTracks(playlistId: String) = combine(tracks, playlistTrackIds) { all, ids ->
        val byId = all.associateBy { it.id }
        ids[playlistId].orEmpty().mapNotNull { byId[it] }
    }

    override fun search(query: String, filter: SearchFilter): Flow<List<SearchResult>> {
        val q = query.trim()
        if (q.isEmpty()) return MutableStateFlow(emptyList())
        return combine(tracks, playlists) { ts, ps ->
            buildList {
                if (filter == SearchFilter.ALL || filter == SearchFilter.SONGS) {
                    ts.filter { it.title.contains(q, true) || it.artistName.contains(q, true) || it.albumTitle.contains(q, true) }
                        .forEach { add(SearchResult.OfTrack(it)) }
                }
                if (filter == SearchFilter.ALL || filter == SearchFilter.ALBUMS) {
                    SampleData.albums.filter { it.title.contains(q, true) || it.artistName.contains(q, true) }
                        .forEach { add(SearchResult.OfAlbum(it)) }
                }
                if (filter == SearchFilter.ALL || filter == SearchFilter.ARTISTS) {
                    SampleData.artists.filter { it.name.contains(q, true) }.forEach { add(SearchResult.OfArtist(it)) }
                }
                if (filter == SearchFilter.ALL || filter == SearchFilter.PLAYLISTS) {
                    ps.filter { it.name.contains(q, true) }.forEach { add(SearchResult.OfPlaylist(it)) }
                }
            }
        }
    }

    override suspend fun toggleLike(trackId: String) {
        tracks.update { l -> l.map { if (it.id == trackId) it.copy(isLiked = !it.isLiked) else it } }
    }

    override suspend fun recordPlay(trackId: String) {
        historyIds.update { listOf(trackId) + it.filterNot { id -> id == trackId } }
    }

    override suspend fun createPlaylist(name: String): Playlist {
        val p = Playlist(id = "playlist-new-${nextPlaylist++}", name = name, placeholderSeed = name.hashCode())
        playlistIds.update { it + (p.id to p) }
        playlistTrackIds.update { it + (p.id to emptyList()) }
        return p
    }

    override suspend fun addToPlaylist(playlistId: String, trackId: String) {
        playlistTrackIds.update { m ->
            val cur = m[playlistId] ?: return@update m
            if (trackId in cur) m else m + (playlistId to cur + trackId)
        }
    }
}
