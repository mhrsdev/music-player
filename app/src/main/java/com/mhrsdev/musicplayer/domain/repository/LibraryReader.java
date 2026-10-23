package com.mhrsdev.musicplayer.domain.repository;

import androidx.annotation.Nullable;
import com.mhrsdev.musicplayer.domain.model.Album;
import com.mhrsdev.musicplayer.domain.model.Artist;
import com.mhrsdev.musicplayer.domain.model.Playlist;
import com.mhrsdev.musicplayer.domain.model.Track;
import java.util.List;

/** Blocking one-shot reads of the library for external browsers (Android Auto, Assistant). Background thread only. */
public interface LibraryReader {
    List<Track> recentlyAdded(int limit);

    /** Tracks with at least one play, most played first. */
    List<Track> mostPlayed(int limit);

    List<Track> liked(int limit);

    List<Playlist> playlists();

    List<Album> albums();

    List<Artist> artists();

    List<Track> albumTracks(String albumId);

    List<Track> artistTracks(String artistId);

    List<Track> playlistTracks(String playlistId);

    @Nullable
    Track track(String trackId);
}
