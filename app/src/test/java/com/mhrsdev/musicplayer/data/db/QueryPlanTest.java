package com.mhrsdev.musicplayer.data.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteDatabase;
import com.mhrsdev.musicplayer.data.db.query.AlbumQueries;
import com.mhrsdev.musicplayer.data.db.query.ArtistQueries;
import com.mhrsdev.musicplayer.data.db.query.LikePattern;
import com.mhrsdev.musicplayer.data.db.query.PlayStatsQueries;
import com.mhrsdev.musicplayer.data.db.query.PlaylistQueries;
import com.mhrsdev.musicplayer.data.db.query.TrackQueries;
import com.mhrsdev.musicplayer.domain.model.Track;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * Runs the hot queries against a 3000-track library and asks SQLite how it executes them (EXPLAIN QUERY PLAN), via
 * a cursor factory that records the exact SQL the query classes send. Fails when a query would build an automatic
 * (temporary) index, which means a real index is missing; prints every plan for review.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class QueryPlanTest {
    private final List<String> recorded = new ArrayList<>();
    private SQLiteDatabase db;
    private File file;

    @Before public void open() {
        Context context = RuntimeEnvironment.getApplication();
        file = context.getDatabasePath("plans.db");
        file.getParentFile().mkdirs();
        file.delete();
        db = SQLiteDatabase.openOrCreateDatabase(file, (database, driver, editTable, query) -> {
            recorded.add(query.toString());
            return new SQLiteCursor(driver, editTable, query);
        });
        new DbHelper(context).onCreate(db);
        seed();
        db.execSQL("ANALYZE"); // the planner needs statistics, like a device that has run a while
    }

    @After public void close() {
        db.close();
        file.delete();
    }

    private void seed() {
        TrackQueries tracks = new TrackQueries(db);
        PlayStatsQueries stats = new PlayStatsQueries(db);
        PlaylistQueries playlists = new PlaylistQueries(db);
        String playlist = playlists.create("Mix", 1L);
        db.beginTransaction();
        for (int i = 0; i < 3000; i++) {
            tracks.upsert(new Track("t" + i, "Title " + i, "ar" + i % 150, "Artist " + i % 150, "al" + i % 300,
                    "Album " + i % 300, 200_000, "content://art/" + i % 300, 0, "content://media/" + i,
                    false, false, 2000 + i % 25, i % 15, 1_700_000_000_000L + i * 1000L), 1L);
            if (i % 10 == 0) stats.recordPlay("t" + i, 1_700_000_000_000L + i);
            if (i % 30 == 0) playlists.addTrack(playlist, "t" + i);
            if (i % 20 == 0) db.execSQL("INSERT INTO likes (track_id, liked_at) VALUES ('t" + i + "', " + i + ")");
        }
        db.setTransactionSuccessful();
        db.endTransaction();
        recorded.clear();
    }

    private String plan(String sql) {
        int args = sql.length() - sql.replace("?", "").length();
        StringBuilder out = new StringBuilder();
        String[] dummy = new String[args];
        java.util.Arrays.fill(dummy, "x");
        try (Cursor c = db.rawQuery("EXPLAIN QUERY PLAN " + sql, args == 0 ? null : dummy)) {
            while (c.moveToNext()) out.append("    ").append(c.getString(3)).append('\n');
        }
        return out.toString();
    }

    /** Runs {@code query}, returns the plan of every SELECT it sent. */
    private String run(String name, Runnable query) {
        recorded.clear();
        query.run();
        StringBuilder all = new StringBuilder();
        for (String sql : new ArrayList<>(recorded)) {
            if (!sql.contains("SELECT")) continue;
            all.append(plan(sql.substring(sql.indexOf("SELECT"))));
        }
        System.out.println("PLAN " + name + "\n" + all);
        assertFalse(name + " builds an automatic index (missing index?)\n" + all, all.toString().contains("AUTOMATIC"));
        return all.toString();
    }

    @Test public void databaseIsInWalMode() {
        Context context = RuntimeEnvironment.getApplication();
        DbHelper helper = new DbHelper(context);
        try (Cursor c = helper.getWritableDatabase().rawQuery("PRAGMA journal_mode", null)) {
            c.moveToFirst();
            assertEquals("wal", c.getString(0).toLowerCase());
        } finally {
            helper.close();
        }
    }

    @Test public void hotQueriesUseIndices() {
        TrackQueries t = new TrackQueries(db);
        String pattern = LikePattern.contains("title 12");
        run("allTracks", t::all);
        run("search LIKE", () -> t.search(pattern, 50));
        run("liked", t::liked);
        run("history", () -> t.history(200));
        run("recentlySaved", () -> t.recentlySaved(100));
        String album = run("tracksByAlbum", () -> t.byAlbum("al7"));
        String artist = run("tracksByArtist", () -> t.byArtist("ar7"));
        String playlist = run("playlistTracks", () -> t.byPlaylist("1"));
        String picks = run("quickPicks (home)", () -> t.quickPickCandidates(60));
        assertFalse("home quick picks must not scan the library\n" + picks, picks.contains("SCAN t"));
        run("album stats (home)", () -> new AlbumQueries(db).stats());
        run("albums", () -> new AlbumQueries(db).all());
        run("artists", () -> new ArtistQueries(db).all());
        run("playlists", () -> new PlaylistQueries(db).all());
        assertTrue(album, album.contains("idx_tracks_album"));
        assertTrue(artist, artist.contains("idx_tracks_artist"));
        assertTrue(playlist, playlist.contains("idx_playlist_tracks_order") || playlist.contains("autoindex"));
    }
}
