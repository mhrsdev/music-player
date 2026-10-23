package com.mhrsdev.musicplayer.data.db.query;

import static com.mhrsdev.musicplayer.data.db.contract.TracksContract.COL_ADDED_AT_MS;
import static com.mhrsdev.musicplayer.data.db.contract.TracksContract.COL_ALBUM_ID;
import static com.mhrsdev.musicplayer.data.db.contract.TracksContract.COL_ALBUM_TITLE;
import static com.mhrsdev.musicplayer.data.db.contract.TracksContract.COL_ARTIST_ID;
import static com.mhrsdev.musicplayer.data.db.contract.TracksContract.COL_ARTIST_NAME;
import static com.mhrsdev.musicplayer.data.db.contract.TracksContract.COL_ARTWORK_URI;
import static com.mhrsdev.musicplayer.data.db.contract.TracksContract.COL_ID;
import static com.mhrsdev.musicplayer.data.db.contract.TracksContract.COL_YEAR;
import static com.mhrsdev.musicplayer.data.db.contract.TracksContract.TABLE;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.mhrsdev.musicplayer.data.db.contract.PlayStatsContract;
import com.mhrsdev.musicplayer.domain.model.Album;
import java.util.ArrayList;
import java.util.List;

/** Albums are derived from the tracks table with GROUP BY album_id (no album table to keep in sync). */
public final class AlbumQueries {
    /** An album with listening / freshness numbers, input of the Home ranking. */
    public record AlbumStat(Album album, long newestAddedMs, int plays, long lastPlayedMs) { }

    private static final String COLUMNS = "t." + COL_ALBUM_ID + ", t." + COL_ALBUM_TITLE + ", t." + COL_ARTIST_ID
            + ", t." + COL_ARTIST_NAME + ", MAX(t." + COL_YEAR + "), COUNT(*), MAX(t." + COL_ARTWORK_URI + ")";
    private static final String GROUP = " GROUP BY t." + COL_ALBUM_ID;
    private static final String BY_TITLE = " ORDER BY t." + COL_ALBUM_TITLE + " COLLATE LOCALIZED";

    private final SQLiteDatabase db;

    public AlbumQueries(SQLiteDatabase db) {
        this.db = db;
    }

    public List<Album> all() {
        return run("", BY_TITLE);
    }

    /** Null when no track has this album id. */
    public Album byId(String id) {
        List<Album> found = run(" WHERE t." + COL_ALBUM_ID + " = ?", "", id);
        return found.isEmpty() ? null : found.get(0);
    }

    /** @param pattern from {@link LikePattern#contains}; matches album title or artist name. */
    public List<Album> search(String pattern, int limit) {
        return run(" WHERE t." + COL_ALBUM_TITLE + LikePattern.CLAUSE + " OR t." + COL_ARTIST_NAME + LikePattern.CLAUSE,
                BY_TITLE + " LIMIT " + limit, pattern, pattern);
    }

    public List<AlbumStat> stats() {
        String sql = "SELECT " + COLUMNS + ", MAX(t." + COL_ADDED_AT_MS + "), COALESCE(SUM(ps."
                + PlayStatsContract.COL_PLAY_COUNT + "), 0), COALESCE(MAX(ps." + PlayStatsContract.COL_LAST_PLAYED_AT
                + "), 0) FROM " + TABLE + " t LEFT JOIN " + PlayStatsContract.TABLE + " ps ON ps."
                + PlayStatsContract.COL_TRACK_ID + " = t." + COL_ID + GROUP + BY_TITLE;
        List<AlbumStat> out = new ArrayList<>();
        try (Cursor c = db.rawQuery(sql, null)) {
            while (c.moveToNext()) out.add(new AlbumStat(read(c), c.getLong(7), c.getInt(8), c.getLong(9)));
        }
        return out;
    }

    private List<Album> run(String where, String tail, String... args) {
        List<Album> out = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT " + COLUMNS + " FROM " + TABLE + " t" + where + GROUP + tail,
                args.length == 0 ? null : args)) {
            while (c.moveToNext()) out.add(read(c));
        }
        return out;
    }

    private static Album read(Cursor c) {
        String id = c.getString(0);
        return new Album(id, c.getString(1), c.getString(2), c.getString(3), c.getInt(4), c.getInt(5),
                c.getString(6), Seeds.of(id));
    }
}
