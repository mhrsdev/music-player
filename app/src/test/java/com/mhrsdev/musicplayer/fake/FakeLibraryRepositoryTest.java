package com.mhrsdev.musicplayer.fake;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import com.mhrsdev.musicplayer.domain.model.HomeSection;
import com.mhrsdev.musicplayer.domain.model.Playlist;
import com.mhrsdev.musicplayer.domain.model.SearchFilter;
import com.mhrsdev.musicplayer.domain.model.SearchResult;
import com.mhrsdev.musicplayer.domain.model.Track;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;

public class FakeLibraryRepositoryTest {
    @Rule public InstantTaskExecutorRule instantTaskExecutor = new InstantTaskExecutorRule();

    /** Reads the current value of a LiveData by observing it once. */
    private static <T> T value(LiveData<T> data) {
        Object[] box = new Object[1];
        Observer<T> observer = v -> box[0] = v;
        data.observeForever(observer);
        data.removeObserver(observer);
        @SuppressWarnings("unchecked") T result = (T) box[0];
        return result;
    }

    @Test public void toggleLikeUpdatesLikedTracks() {
        FakeLibraryRepository repo = new FakeLibraryRepository();
        int liked = value(repo.getLikedTracks()).size();
        assertEquals(SampleData.INITIALLY_LIKED_IDS.size(), liked);
        repo.toggleLike("track-5");
        List<Track> after = value(repo.getLikedTracks());
        assertEquals(liked + 1, after.size());
        assertTrue(after.stream().anyMatch(t -> t.id().equals("track-5")));
        repo.toggleLike("track-5");
        assertEquals(liked, value(repo.getLikedTracks()).size());
    }

    @Test public void recordPlayMovesTrackToTopOfHistory() {
        FakeLibraryRepository repo = new FakeLibraryRepository();
        repo.recordPlay("track-30");
        assertEquals("track-30", value(repo.getHistory()).get(0).id());
        repo.recordPlay("track-5");
        List<Track> history = value(repo.getHistory());
        assertEquals("track-5", history.get(0).id());
        assertEquals(1, history.stream().filter(t -> t.id().equals("track-5")).count());
    }

    @Test public void createPlaylistAndAddTrack() {
        FakeLibraryRepository repo = new FakeLibraryRepository();
        Playlist[] created = new Playlist[1];
        repo.createPlaylist("Road trip", p -> created[0] = p);
        repo.addToPlaylist(created[0].id(), "track-7");
        repo.addToPlaylist(created[0].id(), "track-7");
        assertEquals(1, value(repo.getPlaylistTracks(created[0].id())).size());
        assertEquals(1, value(repo.getPlaylist(created[0].id())).trackCount());
    }

    @Test public void searchFiltersByKindAndIgnoresBlankQuery() {
        FakeLibraryRepository repo = new FakeLibraryRepository();
        assertTrue(value(repo.search("  ", SearchFilter.ALL)).isEmpty());
        List<SearchResult> artists = value(repo.search("neffex", SearchFilter.ARTISTS));
        assertEquals(1, artists.size());
        assertEquals(SearchResult.Kind.ARTIST, artists.get(0).kind());
        assertFalse(value(repo.search("neffex", SearchFilter.SONGS)).isEmpty());
    }

    @Test public void homeFeedHasHeroSectionFirst() {
        FakeLibraryRepository repo = new FakeLibraryRepository();
        List<HomeSection> sections = value(new FakeHomeFeedSource(repo).getSections());
        assertEquals("new-releases", sections.get(0).id());
        assertTrue(sections.stream().noneMatch(s -> s.items().isEmpty()));
    }

    @Test public void libraryAdminEditsPlaylists() {
        FakeLibraryRepository repo = new FakeLibraryRepository();
        String[] created = new String[1];
        repo.createPlaylist("Mix", p -> created[0] = p.id());
        repo.addToPlaylist(created[0], "track-1");
        repo.addToPlaylist(created[0], "track-2");
        repo.moveInPlaylist(created[0], 0, 1);
        assertEquals("track-2", value(repo.getPlaylistTracks(created[0])).get(0).id());
        repo.removeFromPlaylist(created[0], "track-2");
        assertEquals(1, value(repo.getPlaylistTracks(created[0])).size());
        repo.renamePlaylist(created[0], " Renamed ");
        assertEquals("Renamed", value(repo.getPlaylist(created[0])).name());
        repo.deletePlaylist(created[0]);
        assertNull(value(repo.getPlaylist(created[0])));
        boolean[] done = new boolean[1];
        repo.rescan(r -> done[0] = r.ok());
        assertTrue(done[0]);
        assertFalse(value(repo.isScanning()));
    }
}
