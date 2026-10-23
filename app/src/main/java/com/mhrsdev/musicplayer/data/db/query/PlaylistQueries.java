package com.mhrsdev.musicplayer.data.db.query;

import static com.mhrsdev.musicplayer.data.db.contract.PlaylistsContract.COL_CREATED_AT;
import static com.mhrsdev.musicplayer.data.db.contract.PlaylistsContract.COL_DESCRIPTION;
import static com.mhrsdev.musicplayer.data.db.contract.PlaylistsContract.COL_ID;
import static com.mhrsdev.musicplayer.data.db.contract.PlaylistsContract.COL_NAME;
import static com.mhrsdev.musicplayer.data.db.contract.PlaylistsContract.TABLE;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.mhrsdev.musicplayer.data.db.contract.PlaylistTracksContract;
import com.mhrsdev.musicplayer.data.db.contract.TracksContract;
import com.mhrsdev.musicplayer.domain.model.Playlist;
import java.util.ArrayList;
import java.util.List;

/**
 * All SQL touching playlists and their ordered members. Playlist ids are rowids passed around as strings; SQLite
 * converts them for the INTEGER columns, an unknown / non-numeric id simply matches nothing.
 */
public final class PlaylistQueries {
    private static final int MOSAIC = 4;
    private static final String PT = PlaylistTracksContract.TABLE;
    private static final String PT_PLAYLIST = PlaylistTracksContract.COL_PLAYLIST_ID;
    private static final String PT_TRACK = PlaylistTracksContract.COL_TRACK_ID;
    private static final String PT_POSITION = PlaylistTracksContract.COL_POSITION;

    /** Track count only counts members still present in the library (inner join on tracks). */
    private static final String SELECT = "SELECT p." + COL_ID + ", p." + COL_NAME + ", p." + COL_DESCRIPTION
            + ", COUNT(t." + TracksContract.COL_ID + ") FROM " + TABLE + " p LEFT JOIN " + PT + " pt ON pt."
            + PT_PLAYLIST + " = p." + COL_ID + " LEFT JOIN " + TracksContract.TABLE + " t ON t."
            + TracksContract.COL_ID + " = pt." + PT_TRACK;
    private static final String GROUP = " GROUP BY p." + COL_ID;

    private final SQLiteDatabase db;

    public PlaylistQueries(SQLiteDatabase db) {
        this.db = db;
    }

    /** Newest playlist first. */
    public List<Playlist> all() {
        return run("", " ORDER BY p." + COL_CREATED_AT + " DESC, p." + COL_ID + " DESC");
    }

    /** Null when the id is unknown. */
    public Playlist byId(String id) {
        List<Playlist> found = run(" WHERE p." + COL_ID + " = ?", "", id);
        return found.isEmpty() ? null : found.get(0);
    }

    /** @param pattern from {@link LikePattern#contains} */
    public List<Playlist> search(String pattern, int limit) {
        return run(" WHERE p." + COL_NAME + LikePattern.CLAUSE, " ORDER BY p." + COL_NAME + " COLLATE LOCALIZED LIMIT "
                + limit, pattern);
    }

    /** Returns the new playlist id. */
    public String create(String name, long nowMs) {
        ContentValues v = new ContentValues();
        v.put(COL_NAME, name);
        v.put(COL_CREATED_AT, nowMs);
        return String.valueOf(db.insertOrThrow(TABLE, null, v));
    }

    public void rename(String id, String name) {
        ContentValues v = new ContentValues();
        v.put(COL_NAME, name);
        db.update(TABLE, v, COL_ID + " = ?", new String[] {id});
    }

    /** Members go with it (ON DELETE CASCADE). */
    public void delete(String id) {
        db.delete(TABLE, COL_ID + " = ?", new String[] {id});
    }

    /** Appends the track; no-op if it is already in the playlist or the playlist does not exist. */
    public void addTrack(String playlistId, String trackId) {
        db.execSQL("INSERT OR IGNORE INTO " + PT + " (" + PT_PLAYLIST + ", " + PT_TRACK + ", " + PT_POSITION
                + ") SELECT ?, ?, next FROM (SELECT COALESCE(MAX(" + PT_POSITION + ") + 1, 0) AS next FROM " + PT
                + " WHERE " + PT_PLAYLIST + " = ?) WHERE EXISTS (SELECT 1 FROM " + TABLE + " WHERE " + COL_ID + " = ?)",
                new Object[] {playlistId, trackId, playlistId, playlistId});
    }

    public void removeTrack(String playlistId, String trackId) {
        db.delete(PT, PT_PLAYLIST + " = ? AND " + PT_TRACK + " = ?", new String[] {playlistId, trackId});
    }

    /** Member track ids in playlist order (including ones currently missing from the library). */
    public List<String> orderedTrackIds(String playlistId) {
        List<String> ids = new ArrayList<>();
        try (Cursor c = db.query(PT, new String[] {PT_TRACK}, PT_PLAYLIST + " = ?", new String[] {playlistId}, null,
                null, PT_POSITION)) {
            while (c.moveToNext()) ids.add(c.getString(0));
        }
        return ids;
    }

    /** Rewrites positions to match {@code orderedTrackIds}. Run inside a transaction. */
    public void setOrder(String playlistId, List<String> orderedTrackIds) {
        for (int i = 0; i < orderedTrackIds.size(); i++) {
            db.execSQL("UPDATE " + PT + " SET " + PT_POSITION + " = ? WHERE " + PT_PLAYLIST + " = ? AND " + PT_TRACK
                    + " = ?", new Object[] {i, playlistId, orderedTrackIds.get(i)});
        }
    }

    private List<Playlist> run(String where, String tail, String... args) {
        List<Playlist> out = new ArrayList<>();
        try (Cursor c = db.rawQuery(SELECT + where + GROUP + tail, args.length == 0 ? null : args)) {
            while (c.moveToNext()) {
                String id = c.getString(0);
                out.add(new Playlist(id, c.getString(1), c.getInt(3), c.getString(2), mosaic(id), Seeds.of(id)));
            }
        }
        return out;
    }

    /** Up to four distinct cover URIs of the first members. */
    private List<String> mosaic(String playlistId) {
        List<String> uris = new ArrayList<>(MOSAIC);
        String sql = "SELECT t." + TracksContract.COL_ARTWORK_URI + " FROM " + PT + " pt JOIN " + TracksContract.TABLE
                + " t ON t." + TracksContract.COL_ID + " = pt." + PT_TRACK + " WHERE pt." + PT_PLAYLIST
                + " = ? AND t." + TracksContract.COL_ARTWORK_URI + " IS NOT NULL GROUP BY t."
                + TracksContract.COL_ARTWORK_URI + " ORDER BY MIN(pt." + PT_POSITION + ") LIMIT " + MOSAIC;
        try (Cursor c = db.rawQuery(sql, new String[] {playlistId})) {
            while (c.moveToNext()) uris.add(c.getString(0));
        }
        return uris;
    }
}
