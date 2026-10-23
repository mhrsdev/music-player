package com.mhrsdev.musicplayer.data.scanner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.mhrsdev.musicplayer.data.db.query.Seeds;
import com.mhrsdev.musicplayer.domain.model.Track;
import java.util.Map;
import java.util.Set;
import com.mhrsdev.musicplayer.domain.model.ScanResult;
import org.junit.Test;

public class ScannerLogicTest {
    private static ScannedAudio audio(String title, String displayName, String artist, long albumId) {
        return new ScannedAudio(42, title, displayName, artist, 7, "Album", albumId, 215_000, 1003, 2019, 5_000, 6_000,
                "/sdcard/Music/a.mp3");
    }

    @Test public void mapsMediaStoreRowToTrack() {
        Track t = audio("  Song ", "x.mp3", "Band", 9).toTrack();
        assertEquals("42", t.id());
        assertEquals("Song", t.title());
        assertEquals("7", t.artistId());
        assertEquals("Band", t.artistName());
        assertEquals("9", t.albumId());
        assertEquals("content://media/external/audio/media/42", t.mediaUri());
        assertEquals("content://media/external/audio/albumart/9", t.artworkUri());
        assertEquals(Seeds.of("9"), t.placeholderSeed());
        assertEquals(1003, t.trackNumber());
        assertEquals(2019, t.year());
        assertEquals(5_000, t.addedAtMs());
        assertFalse(t.liked());
    }

    @Test public void fillsMissingTags() {
        Track t = audio(null, "My Song.final.flac", "<unknown>", 0).toTrack();
        assertEquals("My Song.final", t.title());
        assertEquals("Unknown artist", t.artistName());
        assertNull(t.artworkUri());
        assertEquals("Unknown title", audio(" ", null, "a", 1).toTrack().title());
    }

    @Test public void seedsAreStableAndSpread() {
        assertEquals(Seeds.of("album-1"), Seeds.of("album-1"));
        assertTrue(Seeds.of("1") != Seeds.of("2"));
    }

    @Test public void folderFilterIncludeExcludeAndPrefixBoundaries() {
        FolderFilter everything = new FolderFilter(Set.of(), Set.of());
        assertTrue(everything.accepts("/sdcard/Music/a.mp3"));
        assertTrue(everything.accepts(null));

        FolderFilter filter = new FolderFilter(Set.of("/sdcard/Music"), Set.of("/sdcard/Music/Podcasts/"));
        assertTrue(filter.accepts("/sdcard/Music/a.mp3"));
        assertTrue(filter.accepts("/sdcard/Music/Rock/b.mp3"));
        assertFalse(filter.accepts("/sdcard/Music2/c.mp3"));
        assertFalse(filter.accepts("/sdcard/Music/Podcasts/d.mp3"));
        assertFalse(filter.accepts("/sdcard/Download/e.mp3"));
        assertFalse(filter.accepts(null));

        FolderFilter excludeOnly = new FolderFilter(Set.of(), Set.of("/sdcard/Alarms"));
        assertFalse(excludeOnly.accepts("/sdcard/Alarms/x.mp3"));
        assertTrue(excludeOnly.accepts("/sdcard/Music/x.mp3"));
    }

    @Test public void incrementalDiffClassifiesAndFindsMissing() {
        IncrementalDiff diff = new IncrementalDiff(Map.of("1", 10L, "2", 10L, "3", 10L));
        assertEquals(IncrementalDiff.Change.UNCHANGED, diff.visit("1", 10));
        assertEquals(IncrementalDiff.Change.UPDATED, diff.visit("2", 11));
        assertEquals(IncrementalDiff.Change.ADDED, diff.visit("9", 1));
        assertEquals(Set.of("3"), diff.missingIds());
    }

    @Test public void scanResultReportsChanges() {
        assertFalse(new ScanResult(true, 0, 0, 0).changed());
        assertTrue(new ScanResult(true, 0, 0, 1).changed());
        assertFalse(ScanResult.FAILED.ok());
    }
}
