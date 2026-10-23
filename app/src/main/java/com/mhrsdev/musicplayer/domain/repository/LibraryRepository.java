package com.mhrsdev.musicplayer.domain.repository;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import com.mhrsdev.musicplayer.domain.model.Album;
import com.mhrsdev.musicplayer.domain.model.Artist;
import com.mhrsdev.musicplayer.domain.model.Playlist;
import com.mhrsdev.musicplayer.domain.model.SearchFilter;
import com.mhrsdev.musicplayer.domain.model.SearchResult;
import com.mhrsdev.musicplayer.domain.model.Track;
import java.util.List;

/**
 * The user's library. All {@code LiveData} are delivered on the main thread and update when the underlying data
 * changes (like, playlist edit, rescan). Writes are fire-and-forget; observers see the result.
 */
public interface LibraryRepository {

    LiveData<List<Track>> getAllTracks();

    LiveData<List<Track>> getLikedTracks();

    LiveData<List<Album>> getAlbums();

    LiveData<List<Artist>> getArtists();

    LiveData<List<Playlist>> getPlaylists();

    /** Most recently played first. */
    LiveData<List<Track>> getHistory();

    /** Most recently added first. */
    LiveData<List<Track>> getRecentlySaved();

    /** Value is null when the id is unknown. */
    LiveData<Album> getAlbum(String id);

    LiveData<Artist> getArtist(String id);

    LiveData<Playlist> getPlaylist(String id);

    LiveData<List<Track>> getAlbumTracks(String albumId);

    LiveData<List<Track>> getArtistTracks(String artistId);

    LiveData<List<Track>> getPlaylistTracks(String playlistId);

    /** Empty list for a blank query. Result order: tracks, albums, artists, playlists. */
    LiveData<List<SearchResult>> search(String query, SearchFilter filter);

    void toggleLike(String trackId);

    /** Moves the track to the top of the history. */
    void recordPlay(String trackId);

    /** Creates an empty playlist; {@code callback} (may be null) receives it on the main thread. */
    void createPlaylist(String name, @Nullable Callback<Playlist> callback);

    void addToPlaylist(String playlistId, String trackId);
}
