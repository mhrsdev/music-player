package com.mhrsdev.musicplayer.fake;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.mhrsdev.musicplayer.domain.model.Artist;
import com.mhrsdev.musicplayer.domain.model.Lyrics;
import com.mhrsdev.musicplayer.domain.model.Track;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class SampleDataTest {
    @Test public void catalogSizes() {
        assertEquals(37, SampleData.TRACKS.size());
        assertEquals(7, SampleData.ARTISTS.size());
        assertEquals(8, SampleData.ALBUMS.size());
        assertEquals(3, SampleData.SEED_PLAYLISTS.size());
    }

    @Test public void idsAreUniqueAndReferencesResolve() {
        Set<String> trackIds = new HashSet<>();
        Set<String> albumIds = new HashSet<>();
        Set<String> artistIds = new HashSet<>();
        SampleData.ALBUMS.forEach(a -> albumIds.add(a.id()));
        SampleData.ARTISTS.forEach(a -> artistIds.add(a.id()));
        for (Track t : SampleData.TRACKS) {
            assertTrue("duplicate " + t.id(), trackIds.add(t.id()));
            assertTrue(albumIds.contains(t.albumId()));
            assertTrue(artistIds.contains(t.artistId()));
        }
        assertEquals(8, albumIds.size());
        assertEquals(7, artistIds.size());
        SampleData.SEED_HISTORY_IDS.forEach(id -> assertTrue(trackIds.contains(id)));
        SampleData.SEED_PLAYLISTS.values().forEach(ids -> ids.forEach(id -> assertTrue(trackIds.contains(id))));
    }

    @Test public void artistCountsMatchTracks() {
        for (Artist a : SampleData.ARTISTS) {
            long tracks = SampleData.TRACKS.stream().filter(t -> t.artistId().equals(a.id())).count();
            assertEquals(a.trackCount(), tracks);
        }
    }

    @Test public void hasNeffexAndPersianArtist() {
        assertTrue(SampleData.ARTISTS.stream().anyMatch(a -> a.name().equals("NEFFEX")));
        assertTrue(SampleData.ARTISTS.stream().anyMatch(a -> a.name().chars().anyMatch(c -> c >= 0x0600 && c <= 0x06FF)));
    }

    @Test public void lyricsVariants() {
        assertTrue(SampleData.lyricsFor("track-4").isEmpty());
        Lyrics plain = SampleData.lyricsFor("track-3");
        assertFalse(plain.synced());
        assertFalse(plain.isEmpty());
        assertTrue(SampleData.lyricsFor("track-35").synced());
        assertTrue(SampleData.lyricsFor("track-1").synced());
    }
}
