package com.mhrsdev.musicplayer.data.db.query;

import static com.mhrsdev.musicplayer.data.db.contract.LyricsCacheContract.COL_CONTENT;
import static com.mhrsdev.musicplayer.data.db.contract.LyricsCacheContract.COL_EXPIRES_AT;
import static com.mhrsdev.musicplayer.data.db.contract.LyricsCacheContract.COL_SOURCE;
import static com.mhrsdev.musicplayer.data.db.contract.LyricsCacheContract.COL_TRACK_ID;
import static com.mhrsdev.musicplayer.data.db.contract.LyricsCacheContract.TABLE;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.Nullable;

/** All SQL touching {@code lyrics_cache}. Call off the main thread. */
public final class LyricsCacheQueries {
    /** @param content raw LRC / plain text; null = cached "no lyrics" */
    public record Entry(@Nullable String content, @Nullable String source, long expiresAtMs) { }

    private final SQLiteDatabase db;

    public LyricsCacheQueries(SQLiteDatabase db) {
        this.db = db;
    }

    /** Null when nothing is cached for the track (expired rows are returned too: the caller judges expiry). */
    @Nullable public Entry get(String trackId) {
        try (Cursor c = db.query(TABLE, new String[] {COL_CONTENT, COL_SOURCE, COL_EXPIRES_AT},
                COL_TRACK_ID + " = ?", new String[] {trackId}, null, null, null)) {
            return c.moveToFirst() ? new Entry(c.getString(0), c.getString(1), c.getLong(2)) : null;
        }
    }

    public void put(String trackId, Entry entry) {
        ContentValues v = new ContentValues();
        v.put(COL_TRACK_ID, trackId);
        v.put(COL_CONTENT, entry.content());
        v.put(COL_SOURCE, entry.source());
        v.put(COL_EXPIRES_AT, entry.expiresAtMs());
        db.insertWithOnConflict(TABLE, null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void delete(String trackId) {
        db.delete(TABLE, COL_TRACK_ID + " = ?", new String[] {trackId});
    }

    /** Drops every row that expired at or before {@code nowMs}. */
    public void deleteExpired(long nowMs) {
        db.delete(TABLE, COL_EXPIRES_AT + " <= ?", new String[] {String.valueOf(nowMs)});
    }
}
