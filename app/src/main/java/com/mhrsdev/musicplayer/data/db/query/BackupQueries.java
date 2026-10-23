package com.mhrsdev.musicplayer.data.db.query;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.Nullable;
import com.mhrsdev.musicplayer.data.backup.TrackRef;
import com.mhrsdev.musicplayer.data.db.contract.LikesContract;
import com.mhrsdev.musicplayer.data.db.contract.PlayStatsContract;
import com.mhrsdev.musicplayer.data.db.contract.PlaylistsContract;
import com.mhrsdev.musicplayer.data.db.contract.TracksContract;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** All SQL of backup / restore: whole-table reads of the user's data and the bulk writes of a restore. */
public final class BackupQueries {
    public record PlaylistRow(String id, String name, @Nullable String description, long createdAt) { }

    public record PlayRow(int count, long lastPlayedAt) { }

    private final SQLiteDatabase db;

    public BackupQueries(SQLiteDatabase db) {
        this.db = db;
    }

    /** Every library track by local id, described by what survives a device change. */
    public Map<String, TrackRef> trackRefs() {
        Map<String, TrackRef> out = new HashMap<>();
        String[] cols = {TracksContract.COL_ID, TracksContract.COL_TITLE, TracksContract.COL_ARTIST_NAME,
                TracksContract.COL_ALBUM_TITLE, TracksContract.COL_DURATION_MS, TracksContract.COL_MEDIA_URI};
        try (Cursor c = db.query(TracksContract.TABLE, cols, null, null, null, null, null)) {
            while (c.moveToNext()) {
                out.put(c.getString(0), new TrackRef(c.getString(1), c.getString(2), c.getString(3), c.getLong(4),
                        c.getString(5)));
            }
        }
        return out;
    }

    /** Track id to like time, oldest like first. */
    public Map<String, Long> likes() {
        Map<String, Long> out = new LinkedHashMap<>();
        try (Cursor c = db.query(LikesContract.TABLE, new String[] {LikesContract.COL_TRACK_ID, LikesContract.COL_LIKED_AT},
                null, null, null, null, LikesContract.COL_LIKED_AT)) {
            while (c.moveToNext()) out.put(c.getString(0), c.getLong(1));
        }
        return out;
    }

    /** Oldest playlist first, so a restore recreates them in their original order. */
    public List<PlaylistRow> playlists() {
        List<PlaylistRow> out = new ArrayList<>();
        String[] cols = {PlaylistsContract.COL_ID, PlaylistsContract.COL_NAME, PlaylistsContract.COL_DESCRIPTION,
                PlaylistsContract.COL_CREATED_AT};
        try (Cursor c = db.query(PlaylistsContract.TABLE, cols, null, null, null, null,
                PlaylistsContract.COL_CREATED_AT + ", " + PlaylistsContract.COL_ID)) {
            while (c.moveToNext()) out.add(new PlaylistRow(c.getString(0), c.getString(1), c.getString(2), c.getLong(3)));
        }
        return out;
    }

    public Map<String, PlayRow> plays() {
        Map<String, PlayRow> out = new LinkedHashMap<>();
        String[] cols = {PlayStatsContract.COL_TRACK_ID, PlayStatsContract.COL_PLAY_COUNT, PlayStatsContract.COL_LAST_PLAYED_AT};
        try (Cursor c = db.query(PlayStatsContract.TABLE, cols, null, null, null, null, PlayStatsContract.COL_LAST_PLAYED_AT)) {
            while (c.moveToNext()) out.put(c.getString(0), new PlayRow(c.getInt(1), c.getLong(2)));
        }
        return out;
    }

    /** Replace mode: empties likes, play stats and playlists (their members go by ON DELETE CASCADE). */
    public void clearUserData() {
        db.delete(LikesContract.TABLE, null, null);
        db.delete(PlayStatsContract.TABLE, null, null);
        db.delete(PlaylistsContract.TABLE, null, null);
    }

    /** Keeps an existing like (and its time). */
    public void putLike(String trackId, long likedAt) {
        ContentValues v = new ContentValues();
        v.put(LikesContract.COL_TRACK_ID, trackId);
        v.put(LikesContract.COL_LIKED_AT, likedAt);
        db.insertWithOnConflict(LikesContract.TABLE, null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }

    /** Inserts the stat, or raises an existing one to the larger count / later play. (No UPSERT on API 26.) */
    public void putPlay(String trackId, int count, long lastPlayedAt) {
        ContentValues v = new ContentValues();
        v.put(PlayStatsContract.COL_TRACK_ID, trackId);
        v.put(PlayStatsContract.COL_PLAY_COUNT, count);
        v.put(PlayStatsContract.COL_LAST_PLAYED_AT, lastPlayedAt);
        db.insertWithOnConflict(PlayStatsContract.TABLE, null, v, SQLiteDatabase.CONFLICT_IGNORE);
        db.execSQL("UPDATE " + PlayStatsContract.TABLE + " SET " + PlayStatsContract.COL_PLAY_COUNT + " = MAX("
                + PlayStatsContract.COL_PLAY_COUNT + ", ?), " + PlayStatsContract.COL_LAST_PLAYED_AT + " = MAX("
                + PlayStatsContract.COL_LAST_PLAYED_AT + ", ?) WHERE " + PlayStatsContract.COL_TRACK_ID + " = ?",
                new Object[] {count, lastPlayedAt, trackId});
    }

    /** Id of the playlist with exactly this name (oldest if several), or null. */
    @Nullable
    public String playlistIdByName(String name) {
        try (Cursor c = db.query(PlaylistsContract.TABLE, new String[] {PlaylistsContract.COL_ID},
                PlaylistsContract.COL_NAME + " = ?", new String[] {name}, null, null, PlaylistsContract.COL_ID, "1")) {
            return c.moveToFirst() ? c.getString(0) : null;
        }
    }

    /** Returns the new playlist id. */
    public String createPlaylist(String name, @Nullable String description, long createdAt) {
        ContentValues v = new ContentValues();
        v.put(PlaylistsContract.COL_NAME, name);
        v.put(PlaylistsContract.COL_DESCRIPTION, description);
        v.put(PlaylistsContract.COL_CREATED_AT, createdAt);
        return String.valueOf(db.insertOrThrow(PlaylistsContract.TABLE, null, v));
    }
}
