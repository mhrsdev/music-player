package com.mhrsdev.musicplayer.data.scanner;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.provider.MediaStore;
import com.mhrsdev.musicplayer.data.db.DbHelper;
import com.mhrsdev.musicplayer.data.db.query.TrackQueries;
import com.mhrsdev.musicplayer.data.db.query.WaveformQueries;
import com.mhrsdev.musicplayer.data.settings.SettingsStore;
import com.mhrsdev.musicplayer.domain.model.ScanResult;
import java.util.Collections;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * Reads music from MediaStore into the {@code tracks} table: incremental (only new / changed rows are written, vanished
 * ids deleted), single transaction, low thread priority. Permission-agnostic: the caller ensures READ_MEDIA_AUDIO
 * (API 33+) or READ_EXTERNAL_STORAGE (<= 32); without it {@link #scan} returns {@link ScanResult#FAILED} and leaves the
 * library untouched. Both methods block: call them from a low-priority background thread (the scan executor). Nothing here schedules itself (no polling).
 */
public class MediaStoreScanner {
    /** Rows per transaction: short write locks, so likes / play counts / playlist edits never wait for a whole scan. */
    static final int CHUNK = 500;

    private static final String PREFS = "scanner_state";
    private static final String KEY_SCANNED_AT = "scanned_at_ms";
    private static final String KEY_GENERATION = "media_generation";
    private static final String KEY_FILTERS = "filters";

    @SuppressWarnings("deprecation") // DATA is the only column with the absolute path on API 26-28, still readable later
    private static final String[] COLUMNS = {
        MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.DISPLAY_NAME,
        MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ARTIST_ID, MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.TRACK,
        MediaStore.Audio.Media.YEAR, MediaStore.Audio.Media.DATE_ADDED, MediaStore.Audio.Media.DATE_MODIFIED,
        MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.IS_MUSIC,
        MediaStore.Audio.Media.IS_RINGTONE, MediaStore.Audio.Media.IS_NOTIFICATION, MediaStore.Audio.Media.IS_ALARM,
        MediaStore.Audio.Media.IS_PODCAST
    };
    private static final int COLUMN_PENDING = COLUMNS.length; // only selected on API 29+

    private final Context context;
    private final DbHelper helper;
    private final SettingsStore settings;
    private final SharedPreferences state;
    private final LongSupplier generation;

    public MediaStoreScanner(Context context, DbHelper helper, SettingsStore settings) {
        this(context, helper, settings, null);
    }

    /** @param generation test hook replacing the MediaStore generation (-1 = unavailable); null = the real one */
    public MediaStoreScanner(Context context, DbHelper helper, SettingsStore settings, LongSupplier generation) {
        this.context = context.getApplicationContext();
        this.helper = helper;
        this.settings = settings;
        this.state = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.generation = generation != null ? generation : this::mediaStoreGeneration;
    }

    /**
     * True when a scan is worth running: never scanned, a filter setting changed, or MediaStore changed since (its
     * generation moved). Without a generation (API < 30) it is always true: the scan itself is the cheap check.
     */
    public boolean isStale() {
        if (state.getLong(KEY_SCANNED_AT, 0) == 0) return true;
        if (!filterSignature().equals(state.getString(KEY_FILTERS, ""))) return true;
        long now = generation.getAsLong();
        return now < 0 || now != state.getLong(KEY_GENERATION, -1);
    }

    /** When the last completed scan finished (epoch ms), 0 if never. For the Settings screen. */
    public static long lastScanAtMs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_SCANNED_AT, 0);
    }

    /** Incremental scan (rows whose time, size and path match are not written). */
    public ScanResult scan() {
        return scan(false);
    }

    /** @param force also re-writes unchanged rows (manual refresh, or when MediaStore tags were edited in place) */
    public ScanResult scan(boolean force) {
        return scanInternal(force); // runs on the low-priority scan thread
    }

    private ScanResult scanInternal(boolean force) {
        long startGeneration = generation.getAsLong(); // read first: changes during the scan make the next check re-scan
        String filters = filterSignature();
        SQLiteDatabase db = helper.getWritableDatabase();
        TrackQueries tracks = new TrackQueries(db);
        IncrementalDiff diff = new IncrementalDiff(tracks.storedById());
        FolderFilter folders = new FolderFilter(orEmpty(settings.includedFolders().getValue()),
                orEmpty(settings.excludedFolders().getValue()));
        AudioFilter audioFilter = new AudioFilter(Boolean.TRUE.equals(settings.includeNonMusic().getValue()),
                minDurationMs());
        int added = 0;
        int updated = 0;
        int removed;
        int written = 0;
        db.beginTransaction();
        try (Cursor c = context.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection(),
                null, null, null)) {
            if (c == null) return ScanResult.FAILED;
            boolean hasPending = c.getColumnCount() > COLUMN_PENDING;
            while (c.moveToNext()) {
                ScannedAudio audio = read(c, hasPending);
                if (!audioFilter.accepts(audio) || !folders.accepts(audio.path())) continue;
                IncrementalDiff.Change change = diff.visit(String.valueOf(audio.id()), audio.modifiedMs(),
                        audio.sizeBytes(), audio.path());
                if (change == IncrementalDiff.Change.UNCHANGED && !force) continue;
                tracks.upsert(audio.toTrack(), audio.modifiedMs(), audio.sizeBytes(), audio.path());
                if (change == IncrementalDiff.Change.ADDED) added++;
                else if (change == IncrementalDiff.Change.UPDATED) updated++;
                if (++written % CHUNK == 0) { // commit and reopen: partial progress is safe, the diff is idempotent
                    db.setTransactionSuccessful();
                    db.endTransaction();
                    db.beginTransaction();
                }
            }
            removed = tracks.deleteByIds(diff.missingIds()); // likes / history / playlist rows stay, hidden until it returns
            new WaveformQueries(db).deleteOrphans();
            db.setTransactionSuccessful();
        } catch (SecurityException noPermission) {
            return ScanResult.FAILED;
        } finally {
            db.endTransaction();
        }
        // Only a complete scan counts, stamped with the generation from before it started.
        state.edit().putLong(KEY_SCANNED_AT, System.currentTimeMillis()).putLong(KEY_GENERATION, startGeneration)
                .putString(KEY_FILTERS, filters).apply();
        return new ScanResult(true, added, updated, removed);
    }

    private static String[] projection() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return COLUMNS;
        String[] out = java.util.Arrays.copyOf(COLUMNS, COLUMNS.length + 1);
        out[COLUMN_PENDING] = MediaStore.Audio.Media.IS_PENDING;
        return out;
    }

    private static ScannedAudio read(Cursor c, boolean hasPending) {
        boolean special = c.getInt(15) != 0 || c.getInt(16) != 0 || c.getInt(17) != 0 || c.getInt(18) != 0;
        return new ScannedAudio(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getLong(4),
                c.getString(5), c.getLong(6), c.getLong(7), c.getInt(8), c.getInt(9),
                c.getLong(10) * 1000, c.getLong(11) * 1000, c.getString(12), c.getLong(13), c.getInt(14) != 0, special,
                hasPending && c.getInt(COLUMN_PENDING) != 0);
    }

    private long minDurationMs() {
        Integer seconds = settings.minTrackSeconds().getValue();
        return 1000L * (seconds == null ? SettingsStore.DEFAULT_MIN_TRACK_SECONDS : seconds);
    }

    /** What decides which rows are library music; a change makes the next check stale. */
    private String filterSignature() {
        return settings.includeNonMusic().getValue() + "|" + minDurationMs() + "|"
                + new java.util.TreeSet<>(orEmpty(settings.includedFolders().getValue())) + "|"
                + new java.util.TreeSet<>(orEmpty(settings.excludedFolders().getValue()));
    }

    private static Set<String> orEmpty(Set<String> set) {
        return set == null ? Collections.emptySet() : set;
    }

    /** Sum of the generations of all external volumes (they only grow, so any change moves the sum); -1 when unavailable. */
    private long mediaStoreGeneration() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return -1;
        try {
            long sum = 0;
            boolean any = false;
            for (String volume : MediaStore.getExternalVolumeNames(context)) {
                sum += MediaStore.getGeneration(context, volume);
                any = true;
            }
            return any ? sum : -1;
        } catch (RuntimeException e) {
            return -1;
        }
    }
}
