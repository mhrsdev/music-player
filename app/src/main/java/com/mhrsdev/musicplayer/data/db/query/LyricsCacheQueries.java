package com.mhrsdev.musicplayer.data.db.query;

import static com.mhrsdev.musicplayer.data.db.contract.LyricsCacheContract.COL_CONTENT;
import static com.mhrsdev.musicplayer.data.db.contract.LyricsCacheContract.COL_FETCHED_AT;
import static com.mhrsdev.musicplayer.data.db.contract.LyricsCacheContract.COL_SOURCE;
import static com.mhrsdev.musicplayer.data.db.contract.LyricsCacheContract.COL_SYNCED;
import static com.mhrsdev.musicplayer.data.db.contract.LyricsCacheContract.COL_TRACK_ID;
import static com.mhrsdev.musicplayer.data.db.contract.LyricsCacheContract.TABLE;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * Entry point of the lyrics task into the database. Call off the main thread. Get an instance via
 * {@code DataModule.lyricsCache()}. To cache a "no lyrics" answer, store an empty {@code content} (e.g. source "none").
 */
public final class LyricsCacheQueries {
    /** One cached lyrics answer. {@code content} is the raw LRC / plain text; {@code synced} = has timestamps. */
    public record Entry(String content, boolean synced, String source, long fetchedAtMs) {
        public boolean isNegative() {
            return content.isEmpty();
        }
    }

    private final SQLiteDatabase db;

    public LyricsCacheQueries(SQLiteDatabase db) {
        this.db = db;
    }

    /** Null when nothing is cached for the track. */
    public Entry get(String trackId) {
        try (Cursor c = db.query(TABLE, new String[] {COL_CONTENT, COL_SYNCED, COL_SOURCE, COL_FETCHED_AT},
                COL_TRACK_ID + " = ?", new String[] {trackId}, null, null, null)) {
            if (!c.moveToFirst()) return null;
            return new Entry(c.getString(0), c.getInt(1) != 0, c.getString(2), c.getLong(3));
        }
    }

    public void put(String trackId, Entry entry) {
        ContentValues v = new ContentValues();
        v.put(COL_TRACK_ID, trackId);
        v.put(COL_CONTENT, entry.content());
        v.put(COL_SYNCED, entry.synced() ? 1 : 0);
        v.put(COL_SOURCE, entry.source());
        v.put(COL_FETCHED_AT, entry.fetchedAtMs());
        db.insertWithOnConflict(TABLE, null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void delete(String trackId) {
        db.delete(TABLE, COL_TRACK_ID + " = ?", new String[] {trackId});
    }
}
