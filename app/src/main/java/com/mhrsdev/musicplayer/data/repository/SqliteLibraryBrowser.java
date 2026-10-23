package com.mhrsdev.musicplayer.data.repository;

import androidx.annotation.Nullable;
import com.mhrsdev.musicplayer.data.db.DbHelper;
import com.mhrsdev.musicplayer.data.db.query.AlbumQueries;
import com.mhrsdev.musicplayer.data.db.query.ArtistQueries;
import com.mhrsdev.musicplayer.data.db.query.PlaylistQueries;
import com.mhrsdev.musicplayer.data.db.query.TrackQueries;
import com.mhrsdev.musicplayer.domain.model.Album;
import com.mhrsdev.musicplayer.domain.model.Artist;
import com.mhrsdev.musicplayer.domain.model.Playlist;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.domain.repository.LibraryBrowser;
import java.util.ArrayList;
import java.util.List;

/** {@link LibraryBrowser} straight on the database; every call blocks, so callers run it on a background thread. */
public final class SqliteLibraryBrowser implements LibraryBrowser {
    private final DbHelper helper;

    public SqliteLibraryBrowser(DbHelper helper) {
        this.helper = helper;
    }

    private TrackQueries tracks() {
        return new TrackQueries(helper.getReadableDatabase());
    }

    @Override public List<Track> recentlyAdded(int limit) {
        return tracks().recentlySaved(limit);
    }

    @Override public List<Track> mostPlayed(int limit) {
        List<Track> out = new ArrayList<>();
        for (TrackQueries.TrackStat s : tracks().quickPickCandidates(limit)) if (s.plays() > 0) out.add(s.track());
        return out;
    }

    @Override public List<Track> liked(int limit) {
        List<Track> all = tracks().liked();
        return all.size() > limit ? new ArrayList<>(all.subList(0, limit)) : all;
    }

    @Override public List<Playlist> playlists() {
        return new PlaylistQueries(helper.getReadableDatabase()).all();
    }

    @Override public List<Album> albums() {
        return new AlbumQueries(helper.getReadableDatabase()).all();
    }

    @Override public List<Artist> artists() {
        return new ArtistQueries(helper.getReadableDatabase()).all();
    }

    @Override public List<Track> albumTracks(String albumId) {
        return tracks().byAlbum(albumId);
    }

    @Override public List<Track> artistTracks(String artistId) {
        return tracks().byArtist(artistId);
    }

    @Override public List<Track> playlistTracks(String playlistId) {
        return tracks().byPlaylist(playlistId);
    }

    @Nullable @Override public Track track(String trackId) {
        return tracks().byId(trackId);
    }
}
