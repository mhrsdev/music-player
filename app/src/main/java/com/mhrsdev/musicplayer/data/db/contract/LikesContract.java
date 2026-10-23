package com.mhrsdev.musicplayer.data.db.contract;

/** Table and column names of liked tracks. Example of the contract pattern: one final class per table. */
public final class LikesContract {
    public static final String TABLE = "likes";
    public static final String COL_TRACK_ID = "track_id";
    public static final String COL_LIKED_AT = "liked_at";

    public static final String CREATE = "CREATE TABLE " + TABLE + " ("
            + COL_TRACK_ID + " TEXT PRIMARY KEY NOT NULL, "
            + COL_LIKED_AT + " INTEGER NOT NULL)";

    private LikesContract() { }
}
