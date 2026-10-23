package com.mhrsdev.musicplayer.ui.library;

import static com.mhrsdev.musicplayer.ui.TestLive.value;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.mhrsdev.musicplayer.domain.model.Album;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.fake.FakeLibraryRepository;
import com.mhrsdev.musicplayer.fake.FakePlayerController;
import com.mhrsdev.musicplayer.ui.nav.ScreenNavigator.TrackList;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class TrackListViewModelTest {
    @Rule public InstantTaskExecutorRule rule = new InstantTaskExecutorRule();

    private FakeLibraryRepository library;
    private FakePlayerController player;

    @Before public void setUp() {
        library = new FakeLibraryRepository();
        player = new FakePlayerController();
    }

    @Test public void albumDetailHasHeaderAndTracks() {
        Album album = value(library.getAlbums()).get(0);
        TrackListViewModel vm = new TrackListViewModel(library, player, TrackList.ALBUM, album.id());
        assertEquals(album.title(), value(vm.getHeader()).title());
        List<Track> tracks = value(vm.getTracks());
        assertFalse(tracks.isEmpty());
        vm.playAt(1, album.title());
        assertEquals(tracks.get(1).id(), value(player.getState()).currentTrack().id());
    }

    @Test public void likedListFollowsLikes() {
        TrackListViewModel vm = new TrackListViewModel(library, player, TrackList.LIKED, null);
        int before = value(vm.getTracks()).size();
        Track notLiked = value(library.getAllTracks()).stream().filter(t -> !t.liked()).findFirst().orElseThrow();
        library.toggleLike(notLiked.id());
        assertEquals(before + 1, value(vm.getTracks()).size());
        assertNotNull(value(vm.getHeader()));
    }

    @Test public void libraryHubPlaysLiked() {
        LibraryViewModel vm = new LibraryViewModel(library, player);
        List<Track> liked = value(vm.getLiked());
        vm.playLiked("Liked songs");
        assertEquals(liked.get(0).id(), value(player.getState()).currentTrack().id());
        assertEquals(LibraryViewModel.totalMs(liked), liked.stream().mapToLong(Track::durationMs).sum());
    }
}
