package com.mhrsdev.musicplayer.data.db.contract;

/**
 * Lyrics cache owned by the lyrics task (it only calls {@code LyricsCacheQueries}). Negative results are stored too
 * (empty {@link #COL_CONTENT}) so the network is asked once per track.
 */
public final class LyricsCacheContract {
    public static final String TABLE = "lyrics_cache";
    public static final String COL_TRACK_ID = "track_id";
    public static final String COL_CONTENT = "content";
    public static final String COL_SYNCED = "synced";
    public static final String COL_SOURCE = "source";
    public static final String COL_FETCHED_AT = "fetched_at";

    public static final String CREATE = "CREATE TABLE " + TABLE + " ("
            + COL_TRACK_ID + " TEXT PRIMARY KEY NOT NULL, "
            + COL_CONTENT + " TEXT NOT NULL, "
            + COL_SYNCED + " INTEGER NOT NULL, "
            + COL_SOURCE + " TEXT NOT NULL, "
            + COL_FETCHED_AT + " INTEGER NOT NULL)";

    public static final String[] SCHEMA = {CREATE};

    private LyricsCacheContract() { }
}
