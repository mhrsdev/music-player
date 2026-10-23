package com.mhrsdev.musicplayer.data.backup;

import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.Looper;
import com.mhrsdev.musicplayer.data.db.DbHelper;
import com.mhrsdev.musicplayer.data.db.query.BackupQueries;
import com.mhrsdev.musicplayer.data.db.query.PlaylistQueries;
import com.mhrsdev.musicplayer.data.repository.ChangeNotifier;
import com.mhrsdev.musicplayer.data.settings.SettingsStore;
import com.mhrsdev.musicplayer.domain.model.BackupSummary;
import com.mhrsdev.musicplayer.domain.model.RestoreMode;
import com.mhrsdev.musicplayer.domain.repository.BackupService;
import com.mhrsdev.musicplayer.domain.repository.Callback;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;

/** {@link BackupService} on the app database and settings; all work runs on the io executor. */
public class SqliteBackupService implements BackupService {
    /** A backup of a very large library is a few MB; anything beyond this is not one of ours. */
    private static final int MAX_BYTES = 64 << 20;

    private final DbHelper helper;
    private final Executor io;
    private final ChangeNotifier notifier;
    private final SettingsStore settings;
    private final String appVersion;
    private final LongSupplier clock;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile RestorePlan pending;

    public SqliteBackupService(DbHelper helper, Executor io, ChangeNotifier notifier, SettingsStore settings,
            String appVersion, LongSupplier clock) {
        this.helper = helper;
        this.io = io;
        this.notifier = notifier;
        this.settings = settings;
        this.appVersion = appVersion;
        this.clock = clock;
    }

    @Override public void export(OutputStream out, Callback<BackupSummary> done) {
        io.execute(() -> {
            BackupSummary result;
            try (OutputStream stream = out) {
                BackupData data = read(helper.getWritableDatabase());
                stream.write(BackupJson.write(data));
                result = new BackupSummary(data.playlists().size(), data.likes().size(), data.plays().size(),
                        data.settings().size(), 0, BackupSummary.Error.NONE);
            } catch (IOException e) {
                result = BackupSummary.failed(BackupSummary.Error.IO);
            }
            deliver(done, result);
        });
    }

    @Override public void preview(InputStream in, Callback<BackupSummary> done) {
        io.execute(() -> {
            pending = null;
            BackupSummary result;
            try (InputStream stream = in) {
                pending = plan(helper.getWritableDatabase(), BackupJson.parse(readCapped(stream)));
                result = pending.summary();
            } catch (BackupFormatException e) {
                result = BackupSummary.failed(e.newerVersion ? BackupSummary.Error.NEWER_VERSION
                        : BackupSummary.Error.CORRUPT);
            } catch (IOException e) {
                result = BackupSummary.failed(BackupSummary.Error.IO);
            }
            deliver(done, result);
        });
    }

    @Override public void restore(RestoreMode mode, Callback<BackupSummary> done) {
        io.execute(() -> {
            RestorePlan plan = pending;
            pending = null;
            if (plan == null) {
                deliver(done, BackupSummary.failed(BackupSummary.Error.NO_PREVIEW));
                return;
            }
            apply(helper.getWritableDatabase(), plan, mode);
            settings.importBackup(plan.settings());
            notifier.notifyChanged(ChangeNotifier.ALL);
            deliver(done, plan.summary());
        });
    }

    /** Reads the whole stream (readNBytes needs API 33). */
    private static byte[] readCapped(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[16 << 10];
        for (int n; (n = in.read(chunk)) > 0;) {
            if (buffer.size() + n > MAX_BYTES) throw new BackupFormatException("too large", false, null);
            buffer.write(chunk, 0, n);
        }
        return buffer.toByteArray();
    }

    private void deliver(Callback<BackupSummary> done, BackupSummary result) {
        main.post(() -> done.onResult(result));
    }

    // ---- the parts worth testing without threads ----------------------------------------------------------------

    /** Everything the user created, described by {@link TrackRef}s. Entries of tracks no longer in the library are dropped. */
    BackupData read(SQLiteDatabase db) {
        BackupQueries queries = new BackupQueries(db);
        Map<String, TrackRef> refs = queries.trackRefs();
        List<BackupData.Like> likes = new ArrayList<>();
        queries.likes().forEach((id, at) -> {
            if (refs.containsKey(id)) likes.add(new BackupData.Like(refs.get(id), at));
        });
        PlaylistQueries playlistQueries = new PlaylistQueries(db);
        List<BackupData.PlaylistEntry> playlists = new ArrayList<>();
        for (BackupQueries.PlaylistRow row : queries.playlists()) {
            List<TrackRef> members = new ArrayList<>();
            for (String id : playlistQueries.orderedTrackIds(row.id())) if (refs.containsKey(id)) members.add(refs.get(id));
            playlists.add(new BackupData.PlaylistEntry(row.name(), row.description(), row.createdAt(), members));
        }
        List<BackupData.Play> plays = new ArrayList<>();
        queries.plays().forEach((id, stat) -> {
            if (refs.containsKey(id)) plays.add(new BackupData.Play(refs.get(id), stat.count(), stat.lastPlayedAt()));
        });
        return new BackupData(BackupData.FORMAT_VERSION, clock.getAsLong(), appVersion, settings.exportBackup(),
                likes, playlists, plays);
    }

    static RestorePlan plan(SQLiteDatabase db, BackupData data) {
        return RestorePlan.of(data, new TrackMatcher(new BackupQueries(db).trackRefs()));
    }

    /** One transaction: a failure leaves the old data untouched. */
    static void apply(SQLiteDatabase db, RestorePlan plan, RestoreMode mode) {
        BackupQueries queries = new BackupQueries(db);
        PlaylistQueries playlists = new PlaylistQueries(db);
        db.beginTransaction();
        try {
            if (mode == RestoreMode.REPLACE) queries.clearUserData();
            plan.likes().forEach(queries::putLike);
            plan.plays().forEach((id, stat) -> queries.putPlay(id, stat.count(), stat.lastPlayedAt()));
            for (RestorePlan.Playlist p : plan.playlists()) {
                String id = mode == RestoreMode.MERGE ? queries.playlistIdByName(p.name()) : null;
                if (id == null) id = queries.createPlaylist(p.name(), p.description(), p.createdAt());
                for (String trackId : p.trackIds()) playlists.addTrack(id, trackId);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
}
