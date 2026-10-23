package com.mhrsdev.musicplayer.data.db.contract;

/** Table and column names of user playlists. The id is the SQLite rowid, exposed to the app as a string. */
public final class PlaylistsContract {
    public static final String TABLE = "playlists";
    public static final String COL_ID = "id";
    public static final String COL_NAME = "name";
    public static final String COL_DESCRIPTION = "description";
    public static final String COL_CREATED_AT = "created_at";

    public static final String CREATE = "CREATE TABLE " + TABLE + " ("
            + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + COL_NAME + " TEXT NOT NULL, "
            + COL_DESCRIPTION + " TEXT, "
            + COL_CREATED_AT + " INTEGER NOT NULL)";

    public static final String[] SCHEMA = {CREATE};

    private PlaylistsContract() { }
}
