package com.mhrsdev.musicplayer.data.db.contract;

/**
 * Cache of network lyric lookups, one row per track. A row is valid until {@link #COL_EXPIRES_AT}; a "not found" or
 * failed lookup is stored too ({@link #COL_CONTENT} null) so the network is asked once per period.
 */
public final class LyricsCacheContract {
    public static final String TABLE = "lyrics_cache";
    public static final String COL_TRACK_ID = "track_id";
    /** Raw LRC / plain text; null = cached "no lyrics". */
    public static final String COL_CONTENT = "content";
    public static final String COL_SOURCE = "source";
    public static final String COL_EXPIRES_AT = "expires_at";

    public static final String CREATE = "CREATE TABLE " + TABLE + " ("
            + COL_TRACK_ID + " TEXT PRIMARY KEY NOT NULL, "
            + COL_CONTENT + " TEXT, "
            + COL_SOURCE + " TEXT, "
            + COL_EXPIRES_AT + " INTEGER NOT NULL)";

    public static final String[] SCHEMA = {CREATE};

    /** v2 -> v3 replaced this table's columns; it only holds re-fetchable data, so it is dropped and recreated. */
    public static final String DROP = "DROP TABLE IF EXISTS " + TABLE;

    private LyricsCacheContract() { }
}
