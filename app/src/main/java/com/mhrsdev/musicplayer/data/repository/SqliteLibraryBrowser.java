package com.mhrsdev.musicplayer.data.repository;

import android.content.ContentResolver;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import com.mhrsdev.musicplayer.data.db.DbHelper;
import com.mhrsdev.musicplayer.data.db.query.PlayStatsQueries;
import com.mhrsdev.musicplayer.data.db.query.PlaylistQueries;
import com.mhrsdev.musicplayer.data.db.query.TrackQueries;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.domain.model.TrackDetails;
import com.mhrsdev.musicplayer.domain.model.YearCount;
import com.mhrsdev.musicplayer.domain.repository.Callback;
import com.mhrsdev.musicplayer.domain.repository.LibraryBrowser;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * {@link LibraryBrowser} on framework SQLite (same db, io executor and {@link ChangeNotifier} as
 * {@link SqliteLibraryRepository}). File facts for the details sheet come from MediaStore, read on demand only.
 */
public class SqliteLibraryBrowser implements LibraryBrowser {
    private static final int SMART_LIMIT = 200;

    private final DbHelper helper;
    private final Executor io;
    private final ChangeNotifier notifier;
    @Nullable private final ContentResolver resolver;
    private final Handler main = new Handler(Looper.getMainLooper());

    private final LiveData<List<Track>> mostPlayed;
    private final LiveData<List<Track>> neverPlayed;
    private final LiveData<List<YearCount>> years;
    private final LiveData<Map<String, Integer>> playCounts;

    /** @param resolver null in tests (details then carry no file facts) */
    public SqliteLibraryBrowser(DbHelper helper, Executor io, ChangeNotifier notifier, @Nullable ContentResolver resolver) {
        this.helper = helper;
        this.io = io;
        this.notifier = notifier;
        this.resolver = resolver;
        int stats = ChangeNotifier.TRACKS | ChangeNotifier.LIKES | ChangeNotifier.HISTORY;
        mostPlayed = live(stats, db -> new TrackQueries(db).mostPlayed(SMART_LIMIT));
        neverPlayed = live(stats, db -> new TrackQueries(db).neverPlayed(SMART_LIMIT));
        years = live(ChangeNotifier.TRACKS, db -> new TrackQueries(db).years());
        playCounts = live(ChangeNotifier.HISTORY, db -> new PlayStatsQueries(db).playCounts());
    }

    private <T> LiveData<T> live(int topics, Function<SQLiteDatabase, T> query) {
        return new DbLiveData<>(io, notifier, topics, () -> query.apply(helper.getWritableDatabase()));
    }

    @Override public LiveData<List<Track>> getMostPlayed() { return mostPlayed; }

    @Override public LiveData<List<Track>> getNeverPlayed() { return neverPlayed; }

    @Override public LiveData<List<YearCount>> getYears() { return years; }

    @Override public LiveData<List<Track>> getYearTracks(int year) {
        return live(ChangeNotifier.TRACKS | ChangeNotifier.LIKES, db -> new TrackQueries(db).byYear(year));
    }

    @Override public LiveData<Map<String, Integer>> getPlayCounts() { return playCounts; }

    @Override public void addAllToPlaylist(String playlistId, List<String> trackIds, @Nullable Callback<Integer> callback) {
        List<String> ids = new ArrayList<>(trackIds);
        io.execute(() -> {
            SQLiteDatabase db = helper.getWritableDatabase();
            int added;
            db.beginTransaction();
            try {
                added = new PlaylistQueries(db).addTracks(playlistId, ids);
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            if (added > 0) notifier.notifyChanged(ChangeNotifier.PLAYLISTS);
            if (callback != null) main.post(() -> callback.onResult(added));
        });
    }

    @Override public void restoreToPlaylist(String playlistId, String trackId, int index) {
        io.execute(() -> {
            SQLiteDatabase db = helper.getWritableDatabase();
            PlaylistQueries queries = new PlaylistQueries(db);
            db.beginTransaction();
            try {
                List<String> ids = queries.orderedTrackIds(playlistId);
                if (!ids.contains(trackId)) {
                    queries.addTrack(playlistId, trackId);
                    ids.add(Math.max(0, Math.min(index, ids.size())), trackId);
                    queries.setOrder(playlistId, ids);
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            notifier.notifyChanged(ChangeNotifier.PLAYLISTS);
        });
    }

    @Override public void loadDetails(Track track, Callback<TrackDetails> callback) {
        io.execute(() -> {
            long[] stats = new PlayStatsQueries(helper.getWritableDatabase()).statsOf(track.id());
            TrackDetails details = withFileFacts(track, (int) stats[0], stats[1]);
            main.post(() -> callback.onResult(details));
        });
    }

    private TrackDetails withFileFacts(Track track, int plays, long lastPlayed) {
        String path = null;
        String mime = null;
        long size = 0;
        int kbps = 0;
        if (resolver != null && track.mediaUri() != null) {
            boolean hasBitrate = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
            String[] projection = hasBitrate
                    ? new String[] {MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.SIZE,
                            MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.BITRATE}
                    : new String[] {MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.SIZE,
                            MediaStore.MediaColumns.MIME_TYPE};
            try (Cursor c = resolver.query(Uri.parse(track.mediaUri()), projection, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    path = c.getString(0);
                    size = c.getLong(1);
                    mime = c.getString(2);
                    if (hasBitrate) kbps = (int) (c.getLong(3) / 1000);
                }
            } catch (RuntimeException unreadable) {
                // file gone or permission revoked: show the library facts only
            }
        }
        return new TrackDetails(track, plays, lastPlayed, path, size, mime, kbps);
    }

    @Override public void forgetTracks(Collection<String> trackIds) {
        List<String> ids = new ArrayList<>(trackIds);
        io.execute(() -> {
            if (new TrackQueries(helper.getWritableDatabase()).deleteByIds(ids) > 0) {
                notifier.notifyChanged(ChangeNotifier.TRACKS);
            }
        });
    }
}
