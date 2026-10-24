package com.mhrsdev.musicplayer.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import com.mhrsdev.musicplayer.domain.model.Album;
import com.mhrsdev.musicplayer.domain.model.Artist;
import com.mhrsdev.musicplayer.domain.model.Playlist;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.domain.repository.LibraryReader;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class BrowseTreeTest {
    private static Track sample(String id) {
        return new Track(id, "Song " + id, "ar", "Artist", "al", "Album", 1000, "content://art/1", 1,
                "content://media/" + id, false, false, 0, 1, 1);
    }

    private static class Stub implements LibraryReader {
        public List<Track> recentlyAdded(int limit) { return List.of(sample("1"), sample("2")); }
        public List<Track> mostPlayed(int limit) { return List.of(sample("2")); }
        public List<Track> liked(int limit) { return List.of(); }
        public List<Playlist> playlists() { return List.of(new Playlist("7", "Mix", 1, null, List.of(), 1)); }
        public List<Album> albums() { return List.of(new Album("al", "Album", "ar", "Artist", 0, 2, null, 1)); }
        public List<Artist> artists() { return List.of(new Artist("ar", "Artist", 1, 2, null, 1)); }
        public List<Track> albumTracks(String id) { return List.of(sample("1")); }
        public List<Track> artistTracks(String id) { return List.of(sample("1"), sample("2")); }
        public List<Track> playlistTracks(String id) { return id.equals("7") ? List.of(sample("2")) : List.of(); }
        @Nullable public Track track(String id) { return id.equals("1") ? sample("1") : null; }
    }

    private BrowseTree tree() {
        return new BrowseTree(RuntimeEnvironment.getApplication().getResources(), new Stub());
    }

    @Test public void rootHasTheSixFolders() {
        List<MediaItem> top = tree().children(BrowseTree.ROOT);
        assertEquals(6, top.size());
        for (MediaItem folder : top) {
            assertTrue(folder.mediaMetadata.isBrowsable);
            assertFalse(folder.mediaMetadata.isPlayable);
        }
    }

    @Test public void foldersLeadToPlayableTracks() {
        BrowseTree t = tree();
        for (String id : List.of(BrowseTree.RECENT, BrowseTree.MOST)) {
            for (MediaItem item : t.children(id)) {
                assertTrue(item.mediaMetadata.isPlayable);
                assertNotNull(item.localConfiguration);
            }
        }
        MediaItem playlist = t.children(BrowseTree.PLAYLISTS).get(0);
        assertEquals("Mix", playlist.mediaMetadata.title.toString());
        assertEquals("2", t.children(playlist.mediaId).get(0).mediaId);
        assertEquals(2, t.children(t.children(BrowseTree.ARTISTS).get(0).mediaId).size());
        assertEquals(1, t.children(t.children(BrowseTree.ALBUMS).get(0).mediaId).size());
    }

    @Test public void bareTrackIdsResolveAndUnknownOnesDoNot() {
        BrowseTree t = tree();
        assertNotNull(t.item("1").localConfiguration);
        assertNull(t.item("999"));
        assertNull(t.item("[nope]"));
    }

    @Test public void withoutALibraryTheTreeIsEmpty() {
        BrowseTree t = new BrowseTree(RuntimeEnvironment.getApplication().getResources(), null);
        assertTrue(t.children(BrowseTree.ROOT).isEmpty());
        assertNull(t.item("1"));
    }
}
