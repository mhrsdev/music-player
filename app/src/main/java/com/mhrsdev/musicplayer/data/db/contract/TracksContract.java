package com.mhrsdev.musicplayer.data.db.contract;

/**
 * Table and column names of the scanned track library. Albums and artists are not tables: they are derived with
 * {@code GROUP BY} over this table (see {@code AlbumQueries} / {@code ArtistQueries}).
 * {@link #COL_TRACK_NUMBER} stores MediaStore's raw value (disc * 1000 + track) so ordering respects discs.
 */
public final class TracksContract {
    public static final String TABLE = "tracks";
    public static final String COL_ID = "id";
    public static final String COL_TITLE = "title";
    public static final String COL_ARTIST_ID = "artist_id";
    public static final String COL_ARTIST_NAME = "artist_name";
    public static final String COL_ALBUM_ID = "album_id";
    public static final String COL_ALBUM_TITLE = "album_title";
    public static final String COL_DURATION_MS = "duration_ms";
    public static final String COL_ARTWORK_URI = "artwork_uri";
    public static final String COL_MEDIA_URI = "media_uri";
    public static final String COL_YEAR = "year";
    public static final String COL_TRACK_NUMBER = "track_number";
    public static final String COL_ADDED_AT_MS = "added_at_ms";
    /** File modification time (ms); the scanner re-reads a row only when this changes, waveform cache keys on it. */
    public static final String COL_MODIFIED_MS = "modified_ms";

    public static final String CREATE = "CREATE TABLE " + TABLE + " ("
            + COL_ID + " TEXT PRIMARY KEY NOT NULL, "
            + COL_TITLE + " TEXT NOT NULL, "
            + COL_ARTIST_ID + " TEXT NOT NULL, "
            + COL_ARTIST_NAME + " TEXT NOT NULL, "
            + COL_ALBUM_ID + " TEXT NOT NULL, "
            + COL_ALBUM_TITLE + " TEXT NOT NULL, "
            + COL_DURATION_MS + " INTEGER NOT NULL, "
            + COL_ARTWORK_URI + " TEXT, "
            + COL_MEDIA_URI + " TEXT NOT NULL, "
            + COL_YEAR + " INTEGER NOT NULL DEFAULT 0, "
            + COL_TRACK_NUMBER + " INTEGER NOT NULL DEFAULT 0, "
            + COL_ADDED_AT_MS + " INTEGER NOT NULL, "
            + COL_MODIFIED_MS + " INTEGER NOT NULL)";

    /** Table plus the indices the album / artist / recently-added queries use. */
    public static final String[] SCHEMA = {
        CREATE,
        "CREATE INDEX idx_tracks_album ON " + TABLE + " (" + COL_ALBUM_ID + ")",
        "CREATE INDEX idx_tracks_artist ON " + TABLE + " (" + COL_ARTIST_ID + ")",
        "CREATE INDEX idx_tracks_added ON " + TABLE + " (" + COL_ADDED_AT_MS + ")"
    };

    private TracksContract() { }
}
