package com.mhrsdev.musicplayer.data.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.mhrsdev.musicplayer.data.db.contract.LikesContract;
import com.mhrsdev.musicplayer.data.db.contract.LyricsCacheContract;
import com.mhrsdev.musicplayer.data.db.contract.PlayEventsContract;
import com.mhrsdev.musicplayer.data.db.contract.PlayStatsContract;
import com.mhrsdev.musicplayer.data.db.contract.PlaylistTracksContract;
import com.mhrsdev.musicplayer.data.db.contract.PlaylistsContract;
import com.mhrsdev.musicplayer.data.db.contract.TracksContract;
import com.mhrsdev.musicplayer.data.db.contract.WaveformsContract;
import com.mhrsdev.musicplayer.data.db.query.PlayEventQueries;
import com.mhrsdev.musicplayer.data.db.query.TrackQueries;
import com.mhrsdev.musicplayer.domain.model.Track;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** v0.2.0 shipped DB v3; the single v3 -> v4 step must add every new column / table and keep likes + history. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class DbMigrationTest {

    /** The tracks table exactly as v3 created it (no size_bytes / path / genre). */
    private static final String V3_TRACKS = """
            CREATE TABLE tracks (id TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL, artist_id TEXT NOT NULL,
            artist_name TEXT NOT NULL, album_id TEXT NOT NULL, album_title TEXT NOT NULL, duration_ms INTEGER NOT NULL,
            artwork_uri TEXT, media_uri TEXT NOT NULL, year INTEGER NOT NULL DEFAULT 0,
            track_number INTEGER NOT NULL DEFAULT 0, added_at_ms INTEGER NOT NULL, modified_ms INTEGER NOT NULL)""";

    @Test public void v3UpgradesToV4KeepingLikesAndHistory() {
        Context ctx = RuntimeEnvironment.getApplication();
        ctx.deleteDatabase(DbHelper.NAME);
        try (SQLiteDatabase v3 = SQLiteDatabase.openOrCreateDatabase(ctx.getDatabasePath(DbHelper.NAME), null)) {
            v3.execSQL(LikesContract.CREATE);
            v3.execSQL(V3_TRACKS);
            String[][] unchanged = {PlaylistsContract.SCHEMA, PlaylistTracksContract.SCHEMA, PlayStatsContract.SCHEMA,
                WaveformsContract.SCHEMA, LyricsCacheContract.SCHEMA};
            for (String[] schema : unchanged) for (String sql : schema) v3.execSQL(sql);
            v3.execSQL("""
                    INSERT INTO tracks (id, title, artist_id, artist_name, album_id, album_title, duration_ms,
                    media_uri, added_at_ms, modified_ms) VALUES ('7', 'Song', 'a', 'Artist', 'b', 'Album', 1000,
                    'content://media/external/audio/media/7', 5, 6000)""");
            v3.execSQL("INSERT INTO likes (" + LikesContract.COL_TRACK_ID + ", " + LikesContract.COL_LIKED_AT
                    + ") VALUES ('7', 9)");
            v3.execSQL("INSERT INTO play_stats VALUES ('7', 4, 1234)");
            v3.setVersion(3);
        }

        SQLiteDatabase db = new DbHelper(ctx).getWritableDatabase();
        assertEquals(4, db.getVersion());
        Set<String> cols = new HashSet<>();
        try (Cursor c = db.rawQuery("PRAGMA table_info(" + TracksContract.TABLE + ")", null)) {
            while (c.moveToNext()) cols.add(c.getString(1));
        }
        assertTrue(cols.toString(), cols.containsAll(Set.of(TracksContract.COL_SIZE_BYTES, TracksContract.COL_PATH,
                TracksContract.COL_GENRE)));
        TrackQueries tracks = new TrackQueries(db);
        assertTrue("migrated rows wait for one re-read", tracks.needsRescan());
        List<Track> liked = tracks.liked();
        assertEquals(1, liked.size());
        assertEquals(List.of("7"), tracks.history(10).stream().map(Track::id).toList());
        assertEquals(4, tracks.feedTracks().get(0).plays());

        // play_events exists and is written by a play; a scan write clears the stale mark
        new com.mhrsdev.musicplayer.data.db.query.PlayStatsQueries(db).recordPlay("7", 2000);
        assertEquals(1, new PlayEventQueries(db).since(0, 10).size());
        tracks.upsert(liked.get(0), 6000, 123, "/m/Song.mp3", "Pop");
        assertTrue(!tracks.needsRescan());
        assertEquals("Pop", tracks.feedTracks().get(0).genre());
        try (Cursor c = db.rawQuery("SELECT name FROM sqlite_master WHERE name = ?",
                new String[] {PlayEventsContract.TABLE})) {
            assertTrue(c.moveToFirst());
        }
        db.close();
    }

    @Test public void freshInstallHasTheSameShape() {
        Context ctx = RuntimeEnvironment.getApplication();
        ctx.deleteDatabase(DbHelper.NAME);
        SQLiteDatabase db = new DbHelper(ctx).getWritableDatabase();
        Set<String> cols = new HashSet<>();
        try (Cursor c = db.rawQuery("PRAGMA table_info(" + TracksContract.TABLE + ")", null)) {
            while (c.moveToNext()) cols.add(c.getString(1));
        }
        assertTrue(cols.containsAll(Set.of(TracksContract.COL_SIZE_BYTES, TracksContract.COL_PATH, TracksContract.COL_GENRE)));
        assertEquals(0, new PlayEventQueries(db).since(0, 10).size());
        db.close();
    }
}
