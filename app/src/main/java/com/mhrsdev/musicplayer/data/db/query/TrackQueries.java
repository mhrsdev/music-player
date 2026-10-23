package com.mhrsdev.musicplayer.data.db.query;

import static com.mhrsdev.musicplayer.data.db.contract.TracksContract.COL_ADDED_AT_MS;
import static com.mhrsdev.musicplayer.data.db.contract.TracksContract.COL_ALBUM_ID;
import static com.mhrsdev.musicplayer.data.db.contract.TracksContract.COL_ALBUM_TITLE;
import static com.mhrsdev.musicplayer.data.db.contract.TracksContract.COL_ARTIST_ID;
import static com.mhrsdev.musicplayer.data.db.contract.TracksContract.COL_ARTIST_NAME;
import static com.mhrsdev.musicplayer.data.db.contract.TracksContract.COL_ARTWORK_URI;
import static com.mhrsdev.musicplayer.data.db.contract.TracksContract.COL_DURATION_MS;
import static com.mhrsdev.musicplayer.data.db.contract.TracksContract.COL_ID;
import static com.mhrsdev.musicplayer.data.db.contract.TracksContract.COL_MEDIA_URI;
import static com.mhrsdev.musicplayer.data.db.contract.TracksContract.COL_MODIFIED_MS;
import static com.mhrsdev.musicplayer.data.db.contract.TracksContract.COL_PATH;
import static com.mhrsdev.musicplayer.data.db.contract.TracksContract.COL_SIZE_BYTES;
import static com.mhrsdev.musicplayer.data.db.contract.TracksContract.COL_TITLE;
import static com.mhrsdev.musicplayer.data.db.contract.TracksContract.COL_TRACK_NUMBER;
import static com.mhrsdev.musicplayer.data.db.contract.TracksContract.COL_YEAR;
import static com.mhrsdev.musicplayer.data.db.contract.TracksContract.TABLE;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.mhrsdev.musicplayer.data.db.contract.LikesContract;
import com.mhrsdev.musicplayer.data.db.contract.PlayStatsContract;
import com.mhrsdev.musicplayer.data.db.contract.PlaylistTracksContract;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.domain.model.YearCount;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** All SQL touching the {@code tracks} table (plus the likes / play_stats / playlist joins that decorate a Track). */
public final class TrackQueries {
    /** A track with its listening numbers, input of the Home "Quick picks" ranking. */
    public record TrackStat(Track track, int plays, long lastPlayedMs) { }

    private static final int CHUNK = 500; // stays below SQLite's 999 bound-variable limit
    private static final String BY_TITLE = " ORDER BY t." + COL_TITLE + " COLLATE LOCALIZED";

    /** Columns 0..12 map to a {@link Track}; the liked flag comes from a LEFT JOIN on likes. */
    private static final String COLUMNS = "t." + COL_ID + ", t." + COL_TITLE + ", t." + COL_ARTIST_ID
            + ", t." + COL_ARTIST_NAME + ", t." + COL_ALBUM_ID + ", t." + COL_ALBUM_TITLE
            + ", t." + COL_DURATION_MS + ", t." + COL_ARTWORK_URI + ", t." + COL_MEDIA_URI + ", t." + COL_YEAR
            + ", t." + COL_TRACK_NUMBER + ", t." + COL_ADDED_AT_MS
            + ", l." + LikesContract.COL_TRACK_ID + " IS NOT NULL";
    private static final String FROM = " FROM " + TABLE + " t LEFT JOIN " + LikesContract.TABLE + " l ON l."
            + LikesContract.COL_TRACK_ID + " = t." + COL_ID;
    private static final String SELECT = "SELECT " + COLUMNS + FROM;

    private static final String JOIN_STATS = " LEFT JOIN " + PlayStatsContract.TABLE + " ps ON ps."
            + PlayStatsContract.COL_TRACK_ID + " = t." + COL_ID;

    private final SQLiteDatabase db;

    public TrackQueries(SQLiteDatabase db) {
        this.db = db;
    }

    public List<Track> all() {
        return run(BY_TITLE);
    }

    public List<Track> liked() {
        return run(" WHERE l." + LikesContract.COL_TRACK_ID + " IS NOT NULL ORDER BY l."
                + LikesContract.COL_LIKED_AT + " DESC");
    }

    /** Most recently played first. */
    public List<Track> history(int limit) {
        return run(" JOIN " + PlayStatsContract.TABLE + " ps ON ps." + PlayStatsContract.COL_TRACK_ID + " = t." + COL_ID
                + " ORDER BY ps." + PlayStatsContract.COL_LAST_PLAYED_AT + " DESC LIMIT " + limit);
    }

    public List<Track> recentlySaved(int limit) {
        return run(" ORDER BY t." + COL_ADDED_AT_MS + " DESC LIMIT " + limit);
    }

    public List<Track> byAlbum(String albumId) {
        return run(" WHERE t." + COL_ALBUM_ID + " = ? ORDER BY t." + COL_TRACK_NUMBER + ", t." + COL_TITLE
                + " COLLATE LOCALIZED", albumId);
    }

    public List<Track> byArtist(String artistId) {
        return run(" WHERE t." + COL_ARTIST_ID + " = ? ORDER BY t." + COL_ALBUM_TITLE + " COLLATE LOCALIZED, t."
                + COL_TRACK_NUMBER, artistId);
    }

    public List<Track> byPlaylist(String playlistId) {
        return run(" JOIN " + PlaylistTracksContract.TABLE + " pt ON pt." + PlaylistTracksContract.COL_TRACK_ID
                + " = t." + COL_ID + " WHERE pt." + PlaylistTracksContract.COL_PLAYLIST_ID + " = ? ORDER BY pt."
                + PlaylistTracksContract.COL_POSITION, playlistId);
    }

    /** Played tracks, most plays first, ties broken by the most recent play. */
    public List<Track> mostPlayed(int limit) {
        return run(" JOIN " + PlayStatsContract.TABLE + " ps ON ps." + PlayStatsContract.COL_TRACK_ID + " = t." + COL_ID
                + " WHERE ps." + PlayStatsContract.COL_PLAY_COUNT + " > 0 ORDER BY ps." + PlayStatsContract.COL_PLAY_COUNT
                + " DESC, ps." + PlayStatsContract.COL_LAST_PLAYED_AT + " DESC LIMIT " + limit);
    }

    /** Tracks without a recorded play, newest added first. */
    public List<Track> neverPlayed(int limit) {
        return run(JOIN_STATS + " WHERE ps." + PlayStatsContract.COL_TRACK_ID + " IS NULL OR ps."
                + PlayStatsContract.COL_PLAY_COUNT + " = 0 ORDER BY t." + COL_ADDED_AT_MS + " DESC LIMIT " + limit);
    }

    public List<Track> byYear(int year) {
        return run(" WHERE t." + COL_YEAR + " = ? ORDER BY t." + COL_ALBUM_TITLE + " COLLATE LOCALIZED, t."
                + COL_TRACK_NUMBER, String.valueOf(year));
    }

    /** Known release years with their track counts, newest first. */
    public List<YearCount> years() {
        List<YearCount> out = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT " + COL_YEAR + ", COUNT(*) FROM " + TABLE + " WHERE " + COL_YEAR
                + " > 0 GROUP BY " + COL_YEAR + " ORDER BY " + COL_YEAR + " DESC", null)) {
            while (c.moveToNext()) out.add(new YearCount(c.getInt(0), c.getInt(1)));
        }
        return out;
    }

    /** One track by id, or null. */
    public Track byId(String id) {
        List<Track> found = run(" WHERE t." + COL_ID + " = ?", id);
        return found.isEmpty() ? null : found.get(0);
    }

    /** @param pattern from {@link LikePattern#contains} */
    public List<Track> search(String pattern, int limit) {
        return run(" WHERE t." + COL_TITLE + LikePattern.CLAUSE + " OR t." + COL_ARTIST_NAME + LikePattern.CLAUSE
                + " OR t." + COL_ALBUM_TITLE + LikePattern.CLAUSE + BY_TITLE + " LIMIT " + limit,
                pattern, pattern, pattern);
    }

    /** Played or liked tracks, most played first, at most {@code limit}. */
    public List<TrackStat> quickPickCandidates(int limit) {
        String sql = "SELECT " + COLUMNS + ", COALESCE(ps." + PlayStatsContract.COL_PLAY_COUNT + ", 0), COALESCE(ps."
                + PlayStatsContract.COL_LAST_PLAYED_AT + ", 0)" + FROM + JOIN_STATS
                // Candidates first (a few hundred rows), then one primary-key lookup each: no scan of the whole library.
                + " WHERE t." + COL_ID + " IN (SELECT " + PlayStatsContract.COL_TRACK_ID + " FROM " + PlayStatsContract.TABLE
                + " WHERE " + PlayStatsContract.COL_PLAY_COUNT + " > 0 UNION SELECT " + LikesContract.COL_TRACK_ID
                + " FROM " + LikesContract.TABLE + ") ORDER BY ps." + PlayStatsContract.COL_PLAY_COUNT + " DESC LIMIT " + limit;
        List<TrackStat> out = new ArrayList<>();
        try (Cursor c = db.rawQuery(sql, null)) {
            Map<String, String> shared = new HashMap<>();
            while (c.moveToNext()) out.add(new TrackStat(read(c, shared), c.getInt(13), c.getLong(14)));
        }
        return out;
    }

    /** What the scanner stored about a file; any difference from MediaStore's current row means "re-read it". */
    public record Stored(long modifiedMs, long sizeBytes, String path) { }

    /** Track id to {@link Stored}, what the incremental scan compares against. */
    public Map<String, Stored> storedById() {
        Map<String, Stored> out = new HashMap<>();
        try (Cursor c = db.query(TABLE, new String[] {COL_ID, COL_MODIFIED_MS, COL_SIZE_BYTES, COL_PATH}, null, null,
                null, null, null)) {
            while (c.moveToNext()) out.put(c.getString(0), new Stored(c.getLong(1), c.getLong(2), c.getString(3)));
        }
        return out;
    }

    /** Inserts or replaces the row of {@code track}. The liked flag is ignored (likes live in their own table). */
    public void upsert(Track track, long modifiedMs) {
        upsert(track, modifiedMs, 0, null);
    }

    public void upsert(Track track, long modifiedMs, long sizeBytes, String path) {
        ContentValues v = new ContentValues();
        v.put(COL_ID, track.id());
        v.put(COL_TITLE, track.title());
        v.put(COL_ARTIST_ID, track.artistId());
        v.put(COL_ARTIST_NAME, track.artistName());
        v.put(COL_ALBUM_ID, track.albumId());
        v.put(COL_ALBUM_TITLE, track.albumTitle());
        v.put(COL_DURATION_MS, track.durationMs());
        v.put(COL_ARTWORK_URI, track.artworkUri());
        v.put(COL_MEDIA_URI, track.mediaUri());
        v.put(COL_YEAR, track.year());
        v.put(COL_TRACK_NUMBER, track.trackNumber());
        v.put(COL_ADDED_AT_MS, track.addedAtMs());
        v.put(COL_MODIFIED_MS, modifiedMs);
        v.put(COL_SIZE_BYTES, sizeBytes);
        v.put(COL_PATH, path);
        db.insertWithOnConflict(TABLE, null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    /** Deletes the given track ids in chunks; returns how many rows went away. */
    public int deleteByIds(Collection<String> ids) {
        List<String> all = new ArrayList<>(ids);
        int deleted = 0;
        for (int from = 0; from < all.size(); from += CHUNK) {
            List<String> part = all.subList(from, Math.min(all.size(), from + CHUNK));
            String marks = String.join(",", java.util.Collections.nCopies(part.size(), "?"));
            deleted += db.delete(TABLE, COL_ID + " IN (" + marks + ")", part.toArray(new String[0]));
        }
        return deleted;
    }

    private List<Track> run(String tail, String... args) {
        List<Track> out = new ArrayList<>();
        Map<String, String> shared = new HashMap<>();
        try (Cursor c = db.rawQuery(SELECT + tail, args.length == 0 ? null : args)) {
            while (c.moveToNext()) out.add(read(c, shared));
        }
        return out;
    }

    /**
     * {@code shared} makes the artist / album / artwork strings of one result list a single instance each: a 10k-track
     * library holds a few hundred of them instead of 50k copies (about 2 MB less heap while the list is alive).
     */
    private static Track read(Cursor c, Map<String, String> shared) {
        String albumId = shared(shared, c.getString(4));
        return new Track(c.getString(0), c.getString(1), shared(shared, c.getString(2)), shared(shared, c.getString(3)),
                albumId, shared(shared, c.getString(5)), c.getLong(6), shared(shared, c.getString(7)), Seeds.of(albumId),
                c.getString(8), false, c.getInt(12) != 0, c.getInt(9), c.getInt(10) % 1000, c.getLong(11));
    }

    private static String shared(Map<String, String> shared, String s) {
        if (s == null) return null;
        String known = shared.putIfAbsent(s, s);
        return known == null ? s : known;
    }
}
