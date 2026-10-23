package com.mhrsdev.musicplayer.data.db.query;

import static com.mhrsdev.musicplayer.data.db.contract.LikesContract.COL_LIKED_AT;
import static com.mhrsdev.musicplayer.data.db.contract.LikesContract.COL_TRACK_ID;
import static com.mhrsdev.musicplayer.data.db.contract.LikesContract.TABLE;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.HashSet;
import java.util.Set;

/** All SQL touching the {@code likes} table. Call off the main thread. Example of the query-class pattern. */
public final class LikeQueries {
    private final SQLiteDatabase db;

    public LikeQueries(SQLiteDatabase db) {
        this.db = db;
    }

    public Set<String> allTrackIds() {
        Set<String> ids = new HashSet<>();
        try (Cursor c = db.query(TABLE, new String[] {COL_TRACK_ID}, null, null, null, null, null)) {
            while (c.moveToNext()) ids.add(c.getString(0));
        }
        return ids;
    }

    /** Likes the track, or removes the like if present. Returns the new state (true = liked). */
    public boolean toggle(String trackId, long nowMs) {
        int removed = db.delete(TABLE, COL_TRACK_ID + " = ?", new String[] {trackId});
        if (removed > 0) return false;
        ContentValues values = new ContentValues();
        values.put(COL_TRACK_ID, trackId);
        values.put(COL_LIKED_AT, nowMs);
        db.insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_IGNORE);
        return true;
    }
}
