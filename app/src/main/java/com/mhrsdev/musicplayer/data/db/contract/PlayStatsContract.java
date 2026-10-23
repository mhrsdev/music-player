package com.mhrsdev.musicplayer.data.db.contract;

/** Play history and play counts in one row per track: history = rows ordered by last play, counts drive Home. */
public final class PlayStatsContract {
    public static final String TABLE = "play_stats";
    public static final String COL_TRACK_ID = "track_id";
    public static final String COL_PLAY_COUNT = "play_count";
    public static final String COL_LAST_PLAYED_AT = "last_played_at";

    public static final String CREATE = "CREATE TABLE " + TABLE + " ("
            + COL_TRACK_ID + " TEXT PRIMARY KEY NOT NULL, "
            + COL_PLAY_COUNT + " INTEGER NOT NULL, "
            + COL_LAST_PLAYED_AT + " INTEGER NOT NULL)";

    public static final String[] SCHEMA = {
        CREATE,
        "CREATE INDEX idx_play_stats_last ON " + TABLE + " (" + COL_LAST_PLAYED_AT + ")"
    };

    private PlayStatsContract() { }
}
