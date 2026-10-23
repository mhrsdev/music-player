package com.mhrsdev.musicplayer.data.db;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import com.mhrsdev.musicplayer.data.db.query.AlbumQueries;
import com.mhrsdev.musicplayer.data.db.query.ArtistQueries;
import com.mhrsdev.musicplayer.data.db.query.LikePattern;
import com.mhrsdev.musicplayer.data.db.query.LikeQueries;
import com.mhrsdev.musicplayer.data.db.query.LyricsCacheQueries;
import com.mhrsdev.musicplayer.data.db.query.PlayStatsQueries;
import com.mhrsdev.musicplayer.data.db.query.PlaylistQueries;
import com.mhrsdev.musicplayer.data.db.query.TrackQueries;
import com.mhrsdev.musicplayer.data.db.query.WaveformQueries;
import com.mhrsdev.musicplayer.domain.model.Album;
import com.mhrsdev.musicplayer.domain.model.Playlist;
import com.mhrsdev.musicplayer.domain.model.Track;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Runs every query class against a real (Robolectric) SQLite database. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class DbQueriesTest {
    private DbHelper helper;
    private SQLiteDatabase db;
    private TrackQueries tracks;

    @Before public void open() {
        Context context = RuntimeEnvironment.getApplication();
        context.deleteDatabase(DbHelper.NAME);
        helper = new DbHelper(context);
        db = helper.getWritableDatabase();
        tracks = new TrackQueries(db);
    }

    @After public void close() {
        helper.close();
    }

    private static Track track(String id, String title, String artistId, String albumId, int number, long addedAt) {
        return new Track(id, title, artistId, "Artist " + artistId, albumId, "Album " + albumId, 200_000,
                "content://art/" + albumId, 1, "content://media/" + id, false, false, 2020, number, addedAt);
    }

    private void seed() {
        tracks.upsert(track("1", "Alpha", "a1", "al1", 1002, 100), 10);
        tracks.upsert(track("2", "Beta", "a1", "al1", 1001, 300), 10);
        tracks.upsert(track("3", "Gamma", "a2", "al2", 1, 200), 10);
    }

    @Test public void tracksAreListedAndMapped() {
        seed();
        List<Track> all = tracks.all();
        assertEquals(3, all.size());
        assertEquals("Alpha", all.get(0).title());
        Track alpha = all.get(0);
        assertEquals(2, alpha.trackNumber()); // disc * 1000 + track is split on read
        assertEquals("content://art/al1", alpha.artworkUri());
        assertFalse(alpha.liked());
        assertEquals(alpha.placeholderSeed(), all.get(1).placeholderSeed()); // same album, same seed
        assertEquals("2", tracks.recentlySaved(10).get(0).id());
        assertEquals(1, tracks.recentlySaved(1).size());
    }

    @Test public void albumAndArtistTracksKeepTheirOrder() {
        seed();
        List<Track> album = tracks.byAlbum("al1");
        assertEquals("2", album.get(0).id()); // track number 1001 sorts before 1002
        assertEquals(2, tracks.byArtist("a1").size());
        assertTrue(tracks.byAlbum("nope").isEmpty());
    }

    @Test public void albumsAndArtistsAreDerived() {
        seed();
        List<Album> albums = new AlbumQueries(db).all();
        assertEquals(2, albums.size());
        Album al1 = new AlbumQueries(db).byId("al1");
        assertEquals(2, al1.trackCount());
        assertEquals(2020, al1.year());
        assertEquals("a1", al1.artistId());
        assertNull(new AlbumQueries(db).byId("nope"));
        ArtistQueries artists = new ArtistQueries(db);
        assertEquals(2, artists.all().size());
        assertEquals(1, artists.byId("a1").albumCount());
        assertEquals(2, artists.byId("a1").trackCount());
        assertNull(artists.byId("nope"));
    }

    @Test public void likesDecorateTracksAndFormTheLikedList() {
        seed();
        LikeQueries likes = new LikeQueries(db);
        assertTrue(likes.toggle("3", 1));
        assertTrue(likes.toggle("1", 2));
        List<Track> liked = tracks.liked();
        assertEquals("1", liked.get(0).id()); // most recently liked first
        assertTrue(liked.get(0).liked());
        assertFalse(likes.toggle("1", 3));
        assertEquals(1, tracks.liked().size());
    }

    @Test public void playStatsDriveHistoryAndQuickPicks() {
        seed();
        PlayStatsQueries stats = new PlayStatsQueries(db);
        stats.recordPlay("1", 100);
        stats.recordPlay("2", 200);
        stats.recordPlay("1", 300);
        List<Track> history = tracks.history(10);
        assertEquals("1", history.get(0).id());
        assertEquals(2, history.size());
        List<TrackQueries.TrackStat> picks = tracks.quickPickCandidates(10);
        assertEquals(2, picks.size());
        assertEquals("1", picks.get(0).track().id());
        assertEquals(2, picks.get(0).plays());
        assertEquals(300, picks.get(0).lastPlayedMs());
        List<AlbumQueries.AlbumStat> albumStats = new AlbumQueries(db).stats();
        assertEquals(2, albumStats.size());
        AlbumQueries.AlbumStat al1 = albumStats.get(0).album().id().equals("al1") ? albumStats.get(0) : albumStats.get(1);
        assertEquals(3, al1.plays());
        assertEquals(300, al1.newestAddedMs());
        assertEquals(300, al1.lastPlayedMs());
    }

    @Test public void playlistsKeepOrderCountsAndCascade() {
        seed();
        PlaylistQueries pl = new PlaylistQueries(db);
        String id = pl.create("Mix", 1);
        pl.addTrack(id, "3");
        pl.addTrack(id, "1");
        pl.addTrack(id, "3"); // duplicate ignored
        pl.addTrack("999", "1"); // unknown playlist ignored, no foreign key crash
        List<Track> members = tracks.byPlaylist(id);
        assertEquals("3", members.get(0).id());
        assertEquals("1", members.get(1).id());
        Playlist loaded = pl.byId(id);
        assertEquals("Mix", loaded.name());
        assertEquals(2, loaded.trackCount());
        assertEquals(2, loaded.artworkUris().size());

        List<String> order = pl.orderedTrackIds(id);
        order.add(0, order.remove(1));
        pl.setOrder(id, order);
        assertEquals("1", tracks.byPlaylist(id).get(0).id());

        pl.rename(id, "Renamed");
        assertEquals("Renamed", pl.all().get(0).name());
        pl.removeTrack(id, "1");
        assertEquals(1, pl.byId(id).trackCount());
        pl.delete(id);
        assertNull(pl.byId(id));
        assertTrue(pl.orderedTrackIds(id).isEmpty()); // cascade
        assertNull(pl.byId("not-a-number"));
    }

    @Test public void missingTracksAreHiddenFromPlaylistsNotForgotten() {
        seed();
        PlaylistQueries pl = new PlaylistQueries(db);
        String id = pl.create("Mix", 1);
        pl.addTrack(id, "3");
        tracks.deleteByIds(List.of("3"));
        assertEquals(0, pl.byId(id).trackCount());
        assertTrue(tracks.byPlaylist(id).isEmpty());
        assertEquals(1, pl.orderedTrackIds(id).size());
    }

    @Test public void searchEscapesWildcardsAndFindsAllFields() {
        tracks.upsert(track("1", "100% Pure", "a1", "al1", 1, 1), 1);
        tracks.upsert(track("2", "Other", "a2", "al2", 1, 1), 1);
        assertEquals(1, tracks.search(LikePattern.contains("100%"), 10).size());
        assertEquals(1, tracks.search(LikePattern.contains("%"), 10).size());
        assertEquals(1, tracks.search(LikePattern.contains("artist a2"), 10).size()); // case-insensitive, artist field
        assertEquals(1, tracks.search(LikePattern.contains("album al1"), 10).size());
        assertEquals(1, new AlbumQueries(db).search(LikePattern.contains("al2"), 10).size());
        assertEquals(1, new ArtistQueries(db).search(LikePattern.contains("a1"), 10).size());
        PlaylistQueries pl = new PlaylistQueries(db);
        pl.create("Road trip", 1);
        assertEquals(1, pl.search(LikePattern.contains("road"), 10).size());
    }

    @Test public void incrementalHelpersWork() {
        seed();
        assertEquals(Long.valueOf(10), tracks.modifiedById().get("1"));
        tracks.upsert(track("1", "Alpha 2", "a1", "al1", 1, 100), 20); // replace, not duplicate
        assertEquals(3, tracks.all().size());
        assertEquals(Long.valueOf(20), tracks.modifiedById().get("1"));
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 1200; i++) {
            tracks.upsert(track("x" + i, "T" + i, "a9", "al9", 1, 1), 1);
            many.add("x" + i);
        }
        assertEquals(1200, tracks.deleteByIds(many)); // more ids than SQLite's bound-variable limit
        assertEquals(3, tracks.all().size());
    }

    @Test public void waveformCacheIsKeyedOnFileModification() {
        seed();
        WaveformQueries w = new WaveformQueries(db);
        assertNull(w.get("1", 50));
        w.put("1", 50, new byte[] {1, 2, 3});
        w.put("1", 80, new byte[] {9});
        assertArrayEquals(new byte[] {1, 2, 3}, w.get("1", 50));
        tracks.upsert(track("1", "Alpha", "a1", "al1", 1, 100), 99); // file changed
        assertNull(w.get("1", 50));
        w.put("1", 50, new byte[] {7}); // new entry, stale 80-bar one dropped
        assertArrayEquals(new byte[] {7}, w.get("1", 50));
        assertNull(w.get("1", 80));
        w.put("unknown", 50, new byte[] {1}); // not in library: ignored
        assertNull(w.get("unknown", 50));
        tracks.deleteByIds(List.of("1"));
        w.deleteOrphans();
        assertNull(w.get("1", 50));
    }

    @Test public void lyricsCacheRoundTripsAndDropsExpiredRows() {
        LyricsCacheQueries lyrics = new LyricsCacheQueries(db);
        assertNull(lyrics.get("1"));
        lyrics.put("1", new LyricsCacheQueries.Entry("[00:01.00]hi", "lrclib", 50));
        LyricsCacheQueries.Entry e = lyrics.get("1");
        assertNotNull(e);
        assertEquals("[00:01.00]hi", e.content());
        assertEquals("lrclib", e.source());
        lyrics.put("2", new LyricsCacheQueries.Entry(null, null, 10)); // negative result
        assertNull(lyrics.get("2").content());
        lyrics.deleteExpired(10);
        assertNull(lyrics.get("2"));
        assertNotNull(lyrics.get("1"));
        lyrics.delete("1");
        assertNull(lyrics.get("1"));
    }

    @Test public void upgradeFromVersionOneKeepsLikesAndAddsTables() {
        helper.close();
        Context context = RuntimeEnvironment.getApplication();
        context.deleteDatabase(DbHelper.NAME);
        SQLiteDatabase v1 = context.openOrCreateDatabase(DbHelper.NAME, Context.MODE_PRIVATE, null);
        v1.execSQL(com.mhrsdev.musicplayer.data.db.contract.LikesContract.CREATE);
        v1.execSQL("INSERT INTO likes VALUES ('7', 1)");
        v1.setVersion(1);
        v1.close();

        helper = new DbHelper(context);
        db = helper.getWritableDatabase();
        assertEquals(Set.of("7"), new LikeQueries(db).allTrackIds());
        new TrackQueries(db).upsert(track("1", "A", "a", "b", 1, 1), 1);
        assertEquals(1, new TrackQueries(db).all().size());
        assertEquals(DbHelper.VERSION, db.getVersion());
    }
}
