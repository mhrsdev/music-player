package com.mhrsdev.musicplayer.ui.widget.cover;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.mhrsdev.musicplayer.domain.model.Album;
import com.mhrsdev.musicplayer.domain.model.Track;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class CoverSpecTest {

    private static Track track(String id, String title, String artist, String albumId, String album, int seed) {
        return new Track(id, title, "ar", artist, albumId, album, 1000, null, seed, null, false, false, 0, 0, 0);
    }

    @Test public void tracksOfOneAlbumShareTheAlbumCover() {
        CoverSpec a = CoverSpec.forTrack(track("1", "One", "Kasra", "12", "Concrete Gardens", 5));
        CoverSpec b = CoverSpec.forTrack(track("2", "Two", "Kasra", "12", "Concrete Gardens", 99));
        CoverSpec album = CoverSpec.forAlbum(new Album("12", "Concrete Gardens", "ar", "Kasra", 0, 2, null, 7));
        assertEquals(a, b);
        assertEquals(a, album);
        assertEquals(a.cacheKey(), album.cacheKey());
        assertEquals(CoverSpec.Kind.ALBUM, a.kind());
        assertEquals("Concrete Gardens", a.title());
        assertEquals("Kasra", a.subtitle());
    }

    @Test public void untaggedDownloadsGetTheirOwnCover() {
        CoverSpec a = CoverSpec.forTrack(track("1", "Song A", "Unknown artist", "3", "Download", 1));
        CoverSpec b = CoverSpec.forTrack(track("2", "Song B", "Unknown artist", "3", "Download", 1));
        assertEquals(CoverSpec.Kind.SINGLE, a.kind());
        assertNotEquals(a.seed(), b.seed());
        assertEquals("Song A", a.title());
        assertNull(a.subtitle()); // "Unknown artist" is not printed on a cover
        assertEquals(CoverSpec.Kind.SINGLE, CoverSpec.forTrack(track("3", "X", "Y", "4", "Unknown album", 0)).kind());
        assertFalse(CoverSpec.isRealAlbum("<unknown>"));
        assertTrue(CoverSpec.isRealAlbum("انار"));
    }

    @Test public void differentAlbumsDifferAndCoverEveryFamily() {
        Set<Integer> scenes = new HashSet<>();
        Set<Integer> palettes = new HashSet<>();
        Set<Long> seeds = new HashSet<>();
        for (int i = 0; i < 400; i++) {
            CoverSpec s = CoverSpec.forAlbum(String.valueOf(i), "Album " + i, "Artist");
            scenes.add(s.scene());
            palettes.add(s.palette());
            seeds.add(s.seed());
        }
        assertEquals(400, seeds.size());
        assertEquals(CoverScenes.COUNT, scenes.size());
        assertEquals(CoverPalettes.ALL.size(), palettes.size());
    }

    @Test public void artistAvatarUsesTheInitial() {
        assertEquals("K", CoverSpec.forArtist("kasra").title());
        assertEquals("س", CoverSpec.forArtist("سهراب نوری").title());
        assertEquals(CoverSpec.forArtist("Kasra").seed(), CoverSpec.forArtist(" KASRA ").seed());
    }

    @Test public void seedOnlySpecIsDeterministicAndTextless() {
        assertEquals(CoverSpec.fromSeed(17), CoverSpec.fromSeed(17));
        assertNotEquals(CoverSpec.fromSeed(17).seed(), CoverSpec.fromSeed(18).seed());
        assertTrue(CoverSpec.fromSeed(17).title().isEmpty());
    }

    @Test public void partsMatchTrack() {
        Track t = track("1", "One", "Kasra", "12", "Concrete Gardens", 5);
        assertEquals(CoverSpec.forTrack(t), CoverSpec.forTrack("12", "Concrete Gardens", "One", "Kasra"));
    }
}
