package com.mhrsdev.musicplayer.data.db.query;

import static com.mhrsdev.musicplayer.data.db.contract.WaveformsContract.COL_BARS;
import static com.mhrsdev.musicplayer.data.db.contract.WaveformsContract.COL_DATA;
import static com.mhrsdev.musicplayer.data.db.contract.WaveformsContract.COL_MODIFIED_MS;
import static com.mhrsdev.musicplayer.data.db.contract.WaveformsContract.COL_TRACK_ID;
import static com.mhrsdev.musicplayer.data.db.contract.WaveformsContract.TABLE;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.mhrsdev.musicplayer.data.db.contract.TracksContract;

/** All SQL touching the waveform cache. Validity is checked against {@code tracks.modified_ms} inside SQL. */
public final class WaveformQueries {
    private final SQLiteDatabase db;

    public WaveformQueries(SQLiteDatabase db) {
        this.db = db;
    }

    /** The cached blob, or null if absent or the file changed since it was stored. */
    public byte[] get(String trackId, int bars) {
        String sql = "SELECT w." + COL_DATA + " FROM " + TABLE + " w JOIN " + TracksContract.TABLE + " t ON t."
                + TracksContract.COL_ID + " = w." + COL_TRACK_ID + " AND t." + TracksContract.COL_MODIFIED_MS + " = w."
                + COL_MODIFIED_MS + " WHERE w." + COL_TRACK_ID + " = ? AND w." + COL_BARS + " = ?";
        try (Cursor c = db.rawQuery(sql, new String[] {trackId, String.valueOf(bars)})) {
            return c.moveToFirst() ? c.getBlob(0) : null;
        }
    }

    /** Stores the blob stamped with the track's current modified time and drops this track's stale entries. */
    public void put(String trackId, int bars, byte[] data) {
        db.beginTransaction();
        try {
            db.execSQL("INSERT OR REPLACE INTO " + TABLE + " (" + COL_TRACK_ID + ", " + COL_BARS + ", "
                    + COL_MODIFIED_MS + ", " + COL_DATA + ") SELECT ?, ?, " + TracksContract.COL_MODIFIED_MS + ", ? FROM "
                    + TracksContract.TABLE + " WHERE " + TracksContract.COL_ID + " = ?",
                    new Object[] {trackId, bars, data, trackId});
            db.execSQL("DELETE FROM " + TABLE + " WHERE " + COL_TRACK_ID + " = ? AND " + COL_MODIFIED_MS
                    + " != (SELECT " + TracksContract.COL_MODIFIED_MS + " FROM " + TracksContract.TABLE + " WHERE "
                    + TracksContract.COL_ID + " = ?)", new Object[] {trackId, trackId});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** Removes entries of tracks that no longer exist (called after a scan). */
    public void deleteOrphans() {
        db.execSQL("DELETE FROM " + TABLE + " WHERE " + COL_TRACK_ID + " NOT IN (SELECT " + TracksContract.COL_ID
                + " FROM " + TracksContract.TABLE + ")");
    }
}
