package com.mhrsdev.musicplayer.data.db.query;

import static com.mhrsdev.musicplayer.data.db.contract.PlayStatsContract.COL_LAST_PLAYED_AT;
import static com.mhrsdev.musicplayer.data.db.contract.PlayStatsContract.COL_PLAY_COUNT;
import static com.mhrsdev.musicplayer.data.db.contract.PlayStatsContract.COL_TRACK_ID;
import static com.mhrsdev.musicplayer.data.db.contract.PlayStatsContract.TABLE;

import android.database.sqlite.SQLiteDatabase;

/** All SQL touching {@code play_stats} (history + play counts). Reads of it are joins in the track / album queries. */
public final class PlayStatsQueries {
    private final SQLiteDatabase db;

    public PlayStatsQueries(SQLiteDatabase db) {
        this.db = db;
    }

    /**
     * Counts one play and moves the track to the top of the history. The stored time is forced strictly above every
     * existing one, so plays within the same millisecond keep their order. (No UPSERT: SQLite on API 26 is 3.18.)
     */
    public void recordPlay(String trackId, long nowMs) {
        db.beginTransaction();
        try {
            db.execSQL("INSERT OR IGNORE INTO " + TABLE + " (" + COL_TRACK_ID + ", " + COL_PLAY_COUNT + ", "
                    + COL_LAST_PLAYED_AT + ") VALUES (?, 0, ?)", new Object[] {trackId, nowMs});
            db.execSQL("UPDATE " + TABLE + " SET " + COL_PLAY_COUNT + " = " + COL_PLAY_COUNT + " + 1, "
                    + COL_LAST_PLAYED_AT + " = MAX(?, (SELECT MAX(" + COL_LAST_PLAYED_AT + ") FROM " + TABLE
                    + ") + 1) WHERE " + COL_TRACK_ID + " = ?", new Object[] {nowMs, trackId});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
}
