package com.mhrsdev.musicplayer.data.db.contract;

/**
 * Ordered membership of tracks in playlists. A track appears at most once per playlist. There is deliberately no
 * foreign key to {@code tracks}: a track that disappears from the library (SD card removed) keeps its slot and is
 * simply hidden by the inner join until it is back.
 */
public final class PlaylistTracksContract {
    public static final String TABLE = "playlist_tracks";
    public static final String COL_PLAYLIST_ID = "playlist_id";
    public static final String COL_TRACK_ID = "track_id";
    public static final String COL_POSITION = "position";

    public static final String CREATE = "CREATE TABLE " + TABLE + " ("
            + COL_PLAYLIST_ID + " INTEGER NOT NULL REFERENCES " + PlaylistsContract.TABLE
            + "(" + PlaylistsContract.COL_ID + ") ON DELETE CASCADE, "
            + COL_TRACK_ID + " TEXT NOT NULL, "
            + COL_POSITION + " INTEGER NOT NULL, "
            + "PRIMARY KEY (" + COL_PLAYLIST_ID + ", " + COL_TRACK_ID + "))";

    public static final String[] SCHEMA = {
        CREATE,
        "CREATE INDEX idx_playlist_tracks_order ON " + TABLE + " (" + COL_PLAYLIST_ID + ", " + COL_POSITION + ")"
    };

    private PlaylistTracksContract() { }
}
