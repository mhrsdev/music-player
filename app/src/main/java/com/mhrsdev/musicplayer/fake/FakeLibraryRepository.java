package com.mhrsdev.musicplayer.fake;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import com.mhrsdev.musicplayer.domain.model.Album;
import com.mhrsdev.musicplayer.domain.model.Artist;
import com.mhrsdev.musicplayer.domain.model.Playlist;
import com.mhrsdev.musicplayer.domain.model.ScanResult;
import com.mhrsdev.musicplayer.domain.model.SearchFilter;
import com.mhrsdev.musicplayer.domain.model.SearchResult;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.domain.repository.Callback;
import com.mhrsdev.musicplayer.domain.repository.LibraryAdmin;
import com.mhrsdev.musicplayer.domain.repository.LibraryRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/** In-memory {@link LibraryRepository} seeded from {@link SampleData}. Main thread only; callbacks run inline. */
public final class FakeLibraryRepository implements LibraryRepository, LibraryAdmin {
    private final List<Track> tracks = new ArrayList<>();
    private final Map<String, Playlist> playlists = new LinkedHashMap<>();
    private final Map<String, List<String>> playlistTrackIds = new LinkedHashMap<>();
    private final List<String> historyIds = new ArrayList<>(SampleData.SEED_HISTORY_IDS);
    private int nextPlaylist = 1;
    /** Bumped on every write; every query is derived from it. */
    private final MutableLiveData<Integer> version = new MutableLiveData<>(0);

    public FakeLibraryRepository() {
        for (Track t : SampleData.TRACKS) tracks.add(t.withLiked(SampleData.INITIALLY_LIKED_IDS.contains(t.id())));
        SampleData.SEED_PLAYLISTS.forEach((p, ids) -> {
            playlists.put(p.id(), p);
            playlistTrackIds.put(p.id(), new ArrayList<>(ids));
        });
    }

    private <T> LiveData<T> derive(Supplier<T> compute) {
        return Transformations.map(version, v -> compute.get());
    }

    private void changed() {
        version.setValue(version.getValue() + 1);
    }

    private List<Track> byIds(List<String> ids) {
        Map<String, Track> byId = new LinkedHashMap<>();
        for (Track t : tracks) byId.put(t.id(), t);
        List<Track> out = new ArrayList<>();
        for (String id : ids) if (byId.containsKey(id)) out.add(byId.get(id));
        return out;
    }

    private List<Track> filterTracks(java.util.function.Predicate<Track> p) {
        List<Track> out = new ArrayList<>();
        for (Track t : tracks) if (p.test(t)) out.add(t);
        return out;
    }

    private Playlist withCount(Playlist p) {
        return p.withTrackCount(playlistTrackIds.get(p.id()).size());
    }

    @Override public LiveData<List<Track>> getAllTracks() {
        return derive(() -> new ArrayList<>(tracks));
    }

    @Override public LiveData<List<Track>> getLikedTracks() {
        return derive(() -> filterTracks(Track::liked));
    }

    @Override public LiveData<List<Album>> getAlbums() {
        return derive(() -> SampleData.ALBUMS);
    }

    @Override public LiveData<List<Artist>> getArtists() {
        return derive(() -> SampleData.ARTISTS);
    }

    @Override public LiveData<List<Playlist>> getPlaylists() {
        return derive(() -> {
            List<Playlist> out = new ArrayList<>();
            for (Playlist p : playlists.values()) out.add(withCount(p));
            return out;
        });
    }

    @Override public LiveData<List<Track>> getHistory() {
        return derive(() -> byIds(historyIds));
    }

    @Override public LiveData<List<Track>> getRecentlySaved() {
        return derive(() -> {
            List<Track> out = new ArrayList<>(tracks);
            out.sort(Comparator.comparingLong(Track::addedAtMs).reversed());
            return out;
        });
    }

    @Override public LiveData<Album> getAlbum(String id) {
        return derive(() -> {
            for (Album a : SampleData.ALBUMS) if (a.id().equals(id)) return a;
            return null;
        });
    }

    @Override public LiveData<Artist> getArtist(String id) {
        return derive(() -> {
            for (Artist a : SampleData.ARTISTS) if (a.id().equals(id)) return a;
            return null;
        });
    }

    @Override public LiveData<Playlist> getPlaylist(String id) {
        return derive(() -> playlists.containsKey(id) ? withCount(playlists.get(id)) : null);
    }

    @Override public LiveData<List<Track>> getAlbumTracks(String albumId) {
        return derive(() -> {
            List<Track> out = filterTracks(t -> t.albumId().equals(albumId));
            out.sort(Comparator.comparingInt(Track::trackNumber));
            return out;
        });
    }

    @Override public LiveData<List<Track>> getArtistTracks(String artistId) {
        return derive(() -> filterTracks(t -> t.artistId().equals(artistId)));
    }

    @Override public LiveData<List<Track>> getPlaylistTracks(String playlistId) {
        return derive(() -> byIds(playlistTrackIds.getOrDefault(playlistId, Collections.emptyList())));
    }

    @Override public LiveData<List<SearchResult>> search(String query, SearchFilter filter) {
        return derive(() -> {
            String q = query.trim().toLowerCase(Locale.ROOT);
            List<SearchResult> out = new ArrayList<>();
            if (q.isEmpty()) return out;
            boolean all = filter == SearchFilter.ALL;
            if (all || filter == SearchFilter.SONGS) {
                for (Track t : tracks) {
                    if (has(t.title(), q) || has(t.artistName(), q) || has(t.albumTitle(), q)) out.add(SearchResult.of(t));
                }
            }
            if (all || filter == SearchFilter.ALBUMS) {
                for (Album a : SampleData.ALBUMS) if (has(a.title(), q) || has(a.artistName(), q)) out.add(SearchResult.of(a));
            }
            if (all || filter == SearchFilter.ARTISTS) {
                for (Artist a : SampleData.ARTISTS) if (has(a.name(), q)) out.add(SearchResult.of(a));
            }
            if (all || filter == SearchFilter.PLAYLISTS) {
                for (Playlist p : playlists.values()) if (has(p.name(), q)) out.add(SearchResult.of(withCount(p)));
            }
            return out;
        });
    }

    private static boolean has(String haystack, String lowerNeedle) {
        return haystack.toLowerCase(Locale.ROOT).contains(lowerNeedle);
    }

    @Override public void toggleLike(String trackId) {
        for (int i = 0; i < tracks.size(); i++) {
            Track t = tracks.get(i);
            if (t.id().equals(trackId)) tracks.set(i, t.withLiked(!t.liked()));
        }
        changed();
    }

    @Override public void recordPlay(String trackId) {
        historyIds.remove(trackId);
        historyIds.add(0, trackId);
        changed();
    }

    @Override public void createPlaylist(String name, @Nullable Callback<Playlist> callback) {
        Playlist p = new Playlist("playlist-new-" + nextPlaylist++, name, 0, null, Collections.emptyList(), name.hashCode());
        playlists.put(p.id(), p);
        playlistTrackIds.put(p.id(), new ArrayList<>());
        changed();
        if (callback != null) callback.onResult(p);
    }

    @Override public void addToPlaylist(String playlistId, String trackId) {
        List<String> ids = playlistTrackIds.get(playlistId);
        if (ids == null || ids.contains(trackId)) return;
        ids.add(trackId);
        changed();
    }

    // ---- LibraryAdmin: nothing to scan in the fake; playlist edits work on the in-memory lists ----

    @Override public LiveData<Boolean> isScanning() {
        return new MutableLiveData<>(false);
    }

    @Override public void rescan(@Nullable Callback<ScanResult> callback) {
        if (callback != null) callback.onResult(new ScanResult(true, 0, 0, 0));
    }

    @Override public void scanIfStale() { }

    @Override public void renamePlaylist(String playlistId, String name) {
        Playlist p = playlists.get(playlistId);
        if (p == null || name.trim().isEmpty()) return;
        playlists.put(playlistId, new Playlist(p.id(), name.trim(), p.trackCount(), p.description(), p.artworkUris(),
                p.placeholderSeed()));
        changed();
    }

    @Override public void deletePlaylist(String playlistId) {
        if (playlists.remove(playlistId) == null) return;
        playlistTrackIds.remove(playlistId);
        changed();
    }

    @Override public void removeFromPlaylist(String playlistId, String trackId) {
        List<String> ids = playlistTrackIds.get(playlistId);
        if (ids != null && ids.remove(trackId)) changed();
    }

    @Override public void moveInPlaylist(String playlistId, int from, int to) {
        List<String> ids = playlistTrackIds.get(playlistId);
        if (ids == null || from < 0 || from >= ids.size() || to < 0 || to >= ids.size() || from == to) return;
        ids.add(to, ids.remove(from));
        changed();
    }
}
