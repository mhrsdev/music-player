package com.mhrsdev.musicplayer.data.scanner;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Process;
import android.provider.MediaStore;
import com.mhrsdev.musicplayer.data.db.DbHelper;
import com.mhrsdev.musicplayer.data.db.query.TrackQueries;
import com.mhrsdev.musicplayer.data.db.query.WaveformQueries;
import com.mhrsdev.musicplayer.data.settings.SettingsStore;
import com.mhrsdev.musicplayer.domain.model.ScanResult;
import java.util.Collections;
import java.util.Set;

/**
 * Reads music from MediaStore into the {@code tracks} table: incremental (only new / modified rows are written, vanished
 * ids deleted), single transaction, low thread priority. Permission-agnostic: the caller ensures READ_MEDIA_AUDIO
 * (API 33+) or READ_EXTERNAL_STORAGE (<= 32); without it {@link #scan()} returns {@link ScanResult#FAILED} and leaves the
 * library untouched. Both methods block: call them from the io executor. Nothing here schedules itself (no polling).
 */
public final class MediaStoreScanner {
    /** Files shorter than this are ringtones, notifications, voice notes ... */
    static final long MIN_DURATION_MS = 30_000;
    /** Before API 30 there is no MediaStore generation counter, so re-scan at app start if the last scan is this old. */
    private static final long STALE_AFTER_MS = 6L * 60 * 60 * 1000;

    private static final String PREFS = "scanner_state";
    private static final String KEY_SCANNED_AT = "scanned_at_ms";
    private static final String KEY_GENERATION = "media_generation";

    @SuppressWarnings("deprecation") // DATA is the only column with the absolute path on API 26-28, still readable later
    private static final String[] PROJECTION = {
        MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.DISPLAY_NAME,
        MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ARTIST_ID, MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.TRACK,
        MediaStore.Audio.Media.YEAR, MediaStore.Audio.Media.DATE_ADDED, MediaStore.Audio.Media.DATE_MODIFIED,
        MediaStore.Audio.Media.DATA
    };
    private static final String SELECTION = MediaStore.Audio.Media.IS_MUSIC + " != 0 AND "
            + MediaStore.Audio.Media.DURATION + " >= ?";

    private final Context context;
    private final DbHelper helper;
    private final SettingsStore settings;
    private final SharedPreferences state;

    public MediaStoreScanner(Context context, DbHelper helper, SettingsStore settings) {
        this.context = context.getApplicationContext();
        this.helper = helper;
        this.settings = settings;
        this.state = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** True when a scan is worth running at app start: never scanned, or MediaStore changed since. */
    public boolean isStale() {
        long lastScan = state.getLong(KEY_SCANNED_AT, 0);
        if (lastScan == 0) return true;
        long generation = currentGeneration();
        if (generation >= 0) return generation != state.getLong(KEY_GENERATION, -1);
        return System.currentTimeMillis() - lastScan > STALE_AFTER_MS;
    }

    public ScanResult scan() {
        int oldPriority = Process.getThreadPriority(Process.myTid());
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
        try {
            return scanInternal();
        } finally {
            Process.setThreadPriority(oldPriority);
        }
    }

    private ScanResult scanInternal() {
        long generation = currentGeneration(); // read first: changes during the scan make the next start re-scan
        SQLiteDatabase db = helper.getWritableDatabase();
        TrackQueries tracks = new TrackQueries(db);
        IncrementalDiff diff = new IncrementalDiff(tracks.modifiedById());
        FolderFilter folders = new FolderFilter(orEmpty(settings.includedFolders().getValue()),
                orEmpty(settings.excludedFolders().getValue()));
        int added = 0;
        int updated = 0;
        int removed;
        db.beginTransaction();
        try (Cursor c = context.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, PROJECTION,
                SELECTION, new String[] {String.valueOf(MIN_DURATION_MS)}, null)) {
            if (c == null) return ScanResult.FAILED;
            while (c.moveToNext()) {
                ScannedAudio audio = read(c);
                if (!folders.accepts(audio.path())) continue;
                IncrementalDiff.Change change = diff.visit(String.valueOf(audio.id()), audio.modifiedMs());
                if (change == IncrementalDiff.Change.UNCHANGED) continue;
                tracks.upsert(audio.toTrack(), audio.modifiedMs());
                if (change == IncrementalDiff.Change.ADDED) added++; else updated++;
            }
            removed = tracks.deleteByIds(diff.missingIds());
            new WaveformQueries(db).deleteOrphans();
            db.setTransactionSuccessful();
        } catch (SecurityException noPermission) {
            return ScanResult.FAILED;
        } finally {
            db.endTransaction();
        }
        state.edit().putLong(KEY_SCANNED_AT, System.currentTimeMillis()).putLong(KEY_GENERATION, generation).apply();
        return new ScanResult(true, added, updated, removed);
    }

    private static ScannedAudio read(Cursor c) {
        return new ScannedAudio(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getLong(4),
                c.getString(5), c.getLong(6), c.getLong(7), c.getInt(8), c.getInt(9),
                c.getLong(10) * 1000, c.getLong(11) * 1000, c.getString(12));
    }

    private static Set<String> orEmpty(Set<String> set) {
        return set == null ? Collections.emptySet() : set;
    }

    /** MediaStore generation of the external volume (API 30+), or -1 when unavailable. */
    private long currentGeneration() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return -1;
        try {
            return MediaStore.getGeneration(context, MediaStore.VOLUME_EXTERNAL);
        } catch (RuntimeException e) {
            return -1;
        }
    }
}
