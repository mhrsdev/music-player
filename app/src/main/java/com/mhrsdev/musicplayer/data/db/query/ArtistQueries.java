package com.mhrsdev.musicplayer.data.db.query;

import static com.mhrsdev.musicplayer.data.db.contract.TracksContract.COL_ALBUM_ID;
import static com.mhrsdev.musicplayer.data.db.contract.TracksContract.COL_ARTIST_ID;
import static com.mhrsdev.musicplayer.data.db.contract.TracksContract.COL_ARTIST_NAME;
import static com.mhrsdev.musicplayer.data.db.contract.TracksContract.COL_ARTWORK_URI;
import static com.mhrsdev.musicplayer.data.db.contract.TracksContract.TABLE;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.mhrsdev.musicplayer.domain.model.Artist;
import java.util.ArrayList;
import java.util.List;

/** Artists are derived from the tracks table with GROUP BY artist_id. The artist image is one of its album covers. */
public final class ArtistQueries {
    private static final String SELECT = "SELECT t." + COL_ARTIST_ID + ", t." + COL_ARTIST_NAME + ", COUNT(DISTINCT t."
            + COL_ALBUM_ID + "), COUNT(*), MAX(t." + COL_ARTWORK_URI + ") FROM " + TABLE + " t";
    private static final String GROUP = " GROUP BY t." + COL_ARTIST_ID;
    private static final String BY_NAME = " ORDER BY t." + COL_ARTIST_NAME + " COLLATE LOCALIZED";

    private final SQLiteDatabase db;

    public ArtistQueries(SQLiteDatabase db) {
        this.db = db;
    }

    public List<Artist> all() {
        return run("", BY_NAME);
    }

    /** Null when no track has this artist id. */
    public Artist byId(String id) {
        List<Artist> found = run(" WHERE t." + COL_ARTIST_ID + " = ?", "", id);
        return found.isEmpty() ? null : found.get(0);
    }

    /** @param pattern from {@link LikePattern#contains} */
    public List<Artist> search(String pattern, int limit) {
        return run(" WHERE t." + COL_ARTIST_NAME + LikePattern.CLAUSE, BY_NAME + " LIMIT " + limit, pattern);
    }

    private List<Artist> run(String where, String tail, String... args) {
        List<Artist> out = new ArrayList<>();
        try (Cursor c = db.rawQuery(SELECT + where + GROUP + tail, args.length == 0 ? null : args)) {
            while (c.moveToNext()) {
                String id = c.getString(0);
                out.add(new Artist(id, c.getString(1), c.getInt(2), c.getInt(3), c.getString(4), Seeds.of(id)));
            }
        }
        return out;
    }
}
