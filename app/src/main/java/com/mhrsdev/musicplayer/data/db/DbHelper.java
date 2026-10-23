package com.mhrsdev.musicplayer.data.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.mhrsdev.musicplayer.data.db.contract.LikesContract;

/**
 * The app's single SQLite database (framework SQLite, no Room). Schema statements live in the
 * {@code contract} classes, queries in the {@code query} classes; this class only wires them to the lifecycle.
 * Bump {@link #VERSION} and add a migration step in {@link #onUpgrade} whenever a contract changes.
 */
public final class DbHelper extends SQLiteOpenHelper {
    public static final String NAME = "musicplayer.db";
    public static final int VERSION = 1;

    public DbHelper(Context context) {
        super(context.getApplicationContext(), NAME, null, VERSION);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL(LikesContract.CREATE);
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Version 1 is the first schema; migrations go here as "if (oldVersion < N) { ... }" blocks.
    }
}
