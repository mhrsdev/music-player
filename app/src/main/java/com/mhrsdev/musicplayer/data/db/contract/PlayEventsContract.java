package com.mhrsdev.musicplayer.data.db.contract;

/**
 * One row per counted play (DB v4): the timestamped history behind "Late night", "Morning", "On this day" and the
 * monthly stats. Bounded: rows older than {@link #KEEP_MS} are pruned on every insert. No foreign key on purpose
 * (a rescan may drop and re-add a track); reads join on {@code tracks}.
 */
public class PlayEventsContract {
    public static final String TABLE = "play_events";
    public static final String COL_TRACK_ID = "track_id";
    public static final String COL_PLAYED_AT = "played_at";

    /** About 13 months: enough for "a year ago" plus a margin. */
    public static final long KEEP_MS = 400L * 24 * 60 * 60 * 1000;

    public static final String CREATE = "CREATE TABLE " + TABLE + " ("
            + COL_TRACK_ID + " TEXT NOT NULL, "
            + COL_PLAYED_AT + " INTEGER NOT NULL)";

    public static final String[] SCHEMA = {
        CREATE,
        "CREATE INDEX idx_play_events_at ON " + TABLE + " (" + COL_PLAYED_AT + ")"
    };

    private PlayEventsContract() { }
}
