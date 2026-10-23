package com.mhrsdev.musicplayer.data.db.contract;

/**
 * Cached loudness envelopes. Key = track id + bar count; a row is valid only while its {@link #COL_MODIFIED_MS}
 * equals the track's current file modification time.
 */
public final class WaveformsContract {
    public static final String TABLE = "waveforms";
    public static final String COL_TRACK_ID = "track_id";
    public static final String COL_BARS = "bars";
    public static final String COL_MODIFIED_MS = "modified_ms";
    /** One byte per bar (0..255 = loudness 0..1), see {@code WaveformMath.encode}. */
    public static final String COL_DATA = "data";

    public static final String CREATE = "CREATE TABLE " + TABLE + " ("
            + COL_TRACK_ID + " TEXT NOT NULL, "
            + COL_BARS + " INTEGER NOT NULL, "
            + COL_MODIFIED_MS + " INTEGER NOT NULL, "
            + COL_DATA + " BLOB NOT NULL, "
            + "PRIMARY KEY (" + COL_TRACK_ID + ", " + COL_BARS + "))";

    public static final String[] SCHEMA = {CREATE};

    private WaveformsContract() { }
}
