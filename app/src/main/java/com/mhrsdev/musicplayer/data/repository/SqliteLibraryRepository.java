package com.mhrsdev.musicplayer.data.repository;

import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.mhrsdev.musicplayer.data.db.DbHelper;
import com.mhrsdev.musicplayer.data.db.query.AlbumQueries;
import com.mhrsdev.musicplayer.data.db.query.ArtistQueries;
import com.mhrsdev.musicplayer.data.db.query.LikePattern;
import com.mhrsdev.musicplayer.data.db.query.LikeQueries;
import com.mhrsdev.musicplayer.data.db.query.PlayStatsQueries;
import com.mhrsdev.musicplayer.data.db.query.PlaylistQueries;
import com.mhrsdev.musicplayer.data.db.query.TrackQueries;
import com.mhrsdev.musicplayer.data.scanner.MediaStoreScanner;
import com.mhrsdev.musicplayer.domain.model.ScanResult;
import com.mhrsdev.musicplayer.domain.model.Album;
import com.mhrsdev.musicplayer.domain.model.Artist;
import com.mhrsdev.musicplayer.domain.model.Playlist;
import com.mhrsdev.musicplayer.domain.model.SearchFilter;
import com.mhrsdev.musicplayer.domain.model.SearchResult;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.domain.repository.Callback;
import com.mhrsdev.musicplayer.domain.repository.LibraryAdmin;
import com.mhrsdev.musicplayer.domain.repository.LibraryRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * {@link LibraryRepository} on framework SQLite. Reads run on the io executor and are delivered as LiveData that reload
 * only while observed ({@link DbLiveData}); writes run on the io executor and notify through {@link ChangeNotifier}.
 * Also the {@link LibraryAdmin}: library scanning and the remaining playlist edits (rename, delete, remove, reorder).
 */
public final class SqliteLibraryRepository implements LibraryRepository, LibraryAdmin {
    private static final int HISTORY_LIMIT = 200;
    private static final int RECENT_LIMIT = 100;
    private static final int SEARCH_LIMIT = 50;
    private static final String DEFAULT_PLAYLIST_NAME = "New playlist";

    private final DbHelper helper;
    private final Executor io;
    private final ChangeNotifier notifier;
    private final MediaStoreScanner scanner;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean scanRunning = new AtomicBoolean();
    private final MutableLiveData<Boolean> scanning = new MutableLiveData<>(false);

    private final LiveData<List<Track>> allTracks;
    private final LiveData<List<Track>> likedTracks;
    private final LiveData<List<Album>> albums;
    private final LiveData<List<Artist>> artists;
    private final LiveData<List<Playlist>> playlists;
    private final LiveData<List<Track>> history;
    private final LiveData<List<Track>> recentlySaved;

    public SqliteLibraryRepository(DbHelper helper, Executor io, ChangeNotifier notifier, MediaStoreScanner scanner) {
        this.helper = helper;
        this.io = io;
        this.notifier = notifier;
        this.scanner = scanner;
        int tracksAndLikes = ChangeNotifier.TRACKS | ChangeNotifier.LIKES;
        allTracks = live(tracksAndLikes, db -> new TrackQueries(db).all());
        likedTracks = live(tracksAndLikes, db -> new TrackQueries(db).liked());
        albums = live(ChangeNotifier.TRACKS, db -> new AlbumQueries(db).all());
        artists = live(ChangeNotifier.TRACKS, db -> new ArtistQueries(db).all());
        playlists = live(ChangeNotifier.TRACKS | ChangeNotifier.PLAYLISTS, db -> new PlaylistQueries(db).all());
        history = live(tracksAndLikes | ChangeNotifier.HISTORY, db -> new TrackQueries(db).history(HISTORY_LIMIT));
        recentlySaved = live(tracksAndLikes, db -> new TrackQueries(db).recentlySaved(RECENT_LIMIT));
    }

    private <T> LiveData<T> live(int topics, Function<SQLiteDatabase, T> query) {
        return new DbLiveData<>(io, notifier, topics, () -> query.apply(helper.getWritableDatabase()));
    }

    // ---- reads --------------------------------------------------------------------------------------------------

    @Override public LiveData<List<Track>> getAllTracks() {
        return allTracks;
    }

    @Override public LiveData<List<Track>> getLikedTracks() {
        return likedTracks;
    }

    @Override public LiveData<List<Album>> getAlbums() {
        return albums;
    }

    @Override public LiveData<List<Artist>> getArtists() {
        return artists;
    }

    @Override public LiveData<List<Playlist>> getPlaylists() {
        return playlists;
    }

    @Override public LiveData<List<Track>> getHistory() {
        return history;
    }

    @Override public LiveData<List<Track>> getRecentlySaved() {
        return recentlySaved;
    }

    @Override public LiveData<Album> getAlbum(String id) {
        return live(ChangeNotifier.TRACKS, db -> new AlbumQueries(db).byId(id));
    }

    @Override public LiveData<Artist> getArtist(String id) {
        return live(ChangeNotifier.TRACKS, db -> new ArtistQueries(db).byId(id));
    }

    @Override public LiveData<Playlist> getPlaylist(String id) {
        return live(ChangeNotifier.TRACKS | ChangeNotifier.PLAYLISTS, db -> new PlaylistQueries(db).byId(id));
    }

    @Override public LiveData<List<Track>> getAlbumTracks(String albumId) {
        return live(ChangeNotifier.TRACKS | ChangeNotifier.LIKES, db -> new TrackQueries(db).byAlbum(albumId));
    }

    @Override public LiveData<List<Track>> getArtistTracks(String artistId) {
        return live(ChangeNotifier.TRACKS | ChangeNotifier.LIKES, db -> new TrackQueries(db).byArtist(artistId));
    }

    @Override public LiveData<List<Track>> getPlaylistTracks(String playlistId) {
        return live(ChangeNotifier.ALL & ~ChangeNotifier.HISTORY, db -> new TrackQueries(db).byPlaylist(playlistId));
    }

    @Override public LiveData<List<SearchResult>> search(String query, SearchFilter filter) {
        String pattern = LikePattern.contains(query);
        return live(ChangeNotifier.ALL & ~ChangeNotifier.HISTORY, db -> pattern == null
                ? Collections.<SearchResult>emptyList() : searchNow(db, pattern, filter));
    }

    private static List<SearchResult> searchNow(SQLiteDatabase db, String pattern, SearchFilter filter) {
        boolean all = filter == SearchFilter.ALL;
        List<SearchResult> out = new ArrayList<>();
        if (all || filter == SearchFilter.SONGS) {
            for (Track t : new TrackQueries(db).search(pattern, SEARCH_LIMIT)) out.add(SearchResult.of(t));
        }
        if (all || filter == SearchFilter.ALBUMS) {
            for (Album a : new AlbumQueries(db).search(pattern, SEARCH_LIMIT)) out.add(SearchResult.of(a));
        }
        if (all || filter == SearchFilter.ARTISTS) {
            for (Artist a : new ArtistQueries(db).search(pattern, SEARCH_LIMIT)) out.add(SearchResult.of(a));
        }
        if (all || filter == SearchFilter.PLAYLISTS) {
            for (Playlist p : new PlaylistQueries(db).search(pattern, SEARCH_LIMIT)) out.add(SearchResult.of(p));
        }
        return out;
    }

    // ---- writes -------------------------------------------------------------------------------------------------

    @Override public void toggleLike(String trackId) {
        write(ChangeNotifier.LIKES, db -> new LikeQueries(db).toggle(trackId, System.currentTimeMillis()));
    }

    @Override public void recordPlay(String trackId) {
        write(ChangeNotifier.HISTORY, db -> new PlayStatsQueries(db).recordPlay(trackId, System.currentTimeMillis()));
    }

    @Override public void createPlaylist(String name, @Nullable Callback<Playlist> callback) {
        String clean = name.trim().isEmpty() ? DEFAULT_PLAYLIST_NAME : name.trim();
        io.execute(() -> {
            PlaylistQueries queries = new PlaylistQueries(helper.getWritableDatabase());
            Playlist created = queries.byId(queries.create(clean, System.currentTimeMillis()));
            notifier.notifyChanged(ChangeNotifier.PLAYLISTS);
            if (callback != null) main.post(() -> callback.onResult(created));
        });
    }

    @Override public void addToPlaylist(String playlistId, String trackId) {
        write(ChangeNotifier.PLAYLISTS, db -> new PlaylistQueries(db).addTrack(playlistId, trackId));
    }

    @Override public void renamePlaylist(String playlistId, String name) {
        if (name.trim().isEmpty()) return;
        write(ChangeNotifier.PLAYLISTS, db -> new PlaylistQueries(db).rename(playlistId, name.trim()));
    }

    @Override public void deletePlaylist(String playlistId) {
        write(ChangeNotifier.PLAYLISTS, db -> new PlaylistQueries(db).delete(playlistId));
    }

    @Override public void removeFromPlaylist(String playlistId, String trackId) {
        write(ChangeNotifier.PLAYLISTS, db -> new PlaylistQueries(db).removeTrack(playlistId, trackId));
    }

    /** Moves the member at index {@code from} to index {@code to} (indices into the playlist's current order). */
    @Override public void moveInPlaylist(String playlistId, int from, int to) {
        write(ChangeNotifier.PLAYLISTS, db -> {
            PlaylistQueries queries = new PlaylistQueries(db);
            List<String> ids = queries.orderedTrackIds(playlistId);
            if (from < 0 || from >= ids.size() || to < 0 || to >= ids.size() || from == to) return;
            ids.add(to, ids.remove(from));
            db.beginTransaction();
            try {
                queries.setOrder(playlistId, ids);
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        });
    }

    private void write(int topic, java.util.function.Consumer<SQLiteDatabase> action) {
        io.execute(() -> {
            action.accept(helper.getWritableDatabase());
            notifier.notifyChanged(topic);
        });
    }

    // ---- scanning -----------------------------------------------------------------------------------------------

    /** True while a scan runs (for a progress indicator). */
    @Override public LiveData<Boolean> isScanning() {
        return scanning;
    }

    /** App-start hook: scans only if MediaStore changed since the last scan (or never scanned). Needs the permission. */
    @Override public void scanIfStale() {
        io.execute(() -> {
            if (scanner.isStale()) scanNow(null);
        });
    }

    /** Manual rescan. {@code callback} (may be null) gets the result on the main thread; FAILED if one is already running. */
    @Override public void rescan(@Nullable Callback<ScanResult> callback) {
        io.execute(() -> scanNow(callback));
    }

    private void scanNow(@Nullable Callback<ScanResult> callback) {
        ScanResult result = ScanResult.FAILED;
        if (scanRunning.compareAndSet(false, true)) {
            scanning.postValue(true);
            try {
                result = scanner.scan();
                if (result.changed()) notifier.notifyChanged(ChangeNotifier.TRACKS);
            } finally {
                scanRunning.set(false);
                scanning.postValue(false);
            }
        }
        if (callback != null) {
            ScanResult delivered = result;
            main.post(() -> callback.onResult(delivered));
        }
    }
}
