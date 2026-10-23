package com.mhrsdev.musicplayer.data.db.query;

import static com.mhrsdev.musicplayer.data.db.contract.PlayEventsContract.COL_PLAYED_AT;
import static com.mhrsdev.musicplayer.data.db.contract.PlayEventsContract.COL_TRACK_ID;
import static com.mhrsdev.musicplayer.data.db.contract.PlayEventsContract.KEEP_MS;
import static com.mhrsdev.musicplayer.data.db.contract.PlayEventsContract.TABLE;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.List;

/** All SQL touching {@code play_events}. Callers run inside {@link PlayStatsQueries#recordPlay}'s transaction. */
public class PlayEventQueries {
    /** One counted play. */
    public record PlayEvent(String trackId, long playedAtMs) { }

    private final SQLiteDatabase db;

    public PlayEventQueries(SQLiteDatabase db) {
        this.db = db;
    }

    /** Appends one play and drops rows older than the retention window (index range delete, cheap). */
    public void insert(String trackId, long nowMs) {
        db.execSQL("INSERT INTO " + TABLE + " (" + COL_TRACK_ID + ", " + COL_PLAYED_AT + ") VALUES (?, ?)",
                new Object[] {trackId, nowMs});
        db.execSQL("DELETE FROM " + TABLE + " WHERE " + COL_PLAYED_AT + " < ?", new Object[] {nowMs - KEEP_MS});
    }

    /** Plays since {@code fromMs}, newest first, at most {@code limit} rows. */
    public List<PlayEvent> since(long fromMs, int limit) {
        List<PlayEvent> out = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT " + COL_TRACK_ID + ", " + COL_PLAYED_AT + " FROM " + TABLE + " WHERE "
                + COL_PLAYED_AT + " >= ? ORDER BY " + COL_PLAYED_AT + " DESC LIMIT " + limit,
                new String[] {String.valueOf(fromMs)})) {
            while (c.moveToNext()) out.add(new PlayEvent(c.getString(0), c.getLong(1)));
        }
        return out;
    }
}
