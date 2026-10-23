package com.mhrsdev.musicplayer.data.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.mhrsdev.musicplayer.data.db.contract.LikesContract;
import com.mhrsdev.musicplayer.data.db.contract.LyricsCacheContract;
import com.mhrsdev.musicplayer.data.db.contract.PlayStatsContract;
import com.mhrsdev.musicplayer.data.db.contract.PlaylistTracksContract;
import com.mhrsdev.musicplayer.data.db.contract.PlaylistsContract;
import com.mhrsdev.musicplayer.data.db.contract.TracksContract;
import com.mhrsdev.musicplayer.data.db.contract.WaveformsContract;

/**
 * The app's single SQLite database (framework SQLite, no Room). Schema statements live in the
 * {@code contract} classes, queries in the {@code query} classes; this class only wires them to the lifecycle.
 * Bump {@link #VERSION} and add a migration step in {@link #onUpgrade} whenever a contract changes.
 *
 * <p>History: v1 = likes only (scaffold), v2 = library, playlists, play stats, waveform + lyrics caches,
 * v3 = lyrics cache keyed by expiry instead of fetch time.
 */
public final class DbHelper extends SQLiteOpenHelper {
    public static final String NAME = "musicplayer.db";
    public static final int VERSION = 3;

    public DbHelper(Context context) {
        super(context.getApplicationContext(), NAME, null, VERSION);
    }

    @Override public void onConfigure(SQLiteDatabase db) {
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL(LikesContract.CREATE);
        createV2Tables(db);
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) createV2Tables(db);
        if (oldVersion == 2) { // v1 databases just got the v3 shape from createV2Tables
            db.execSQL(LyricsCacheContract.DROP);
            for (String sql : LyricsCacheContract.SCHEMA) db.execSQL(sql);
        }
    }

    /** Playlists must exist before playlist_tracks (foreign key). */
    private static void createV2Tables(SQLiteDatabase db) {
        String[][] schemas = {
            TracksContract.SCHEMA, PlaylistsContract.SCHEMA, PlaylistTracksContract.SCHEMA,
            PlayStatsContract.SCHEMA, WaveformsContract.SCHEMA, LyricsCacheContract.SCHEMA
        };
        for (String[] statements : schemas) {
            for (String sql : statements) db.execSQL(sql);
        }
    }
}
