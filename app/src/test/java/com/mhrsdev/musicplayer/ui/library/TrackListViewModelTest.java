package com.mhrsdev.musicplayer.ui.library;

import static com.mhrsdev.musicplayer.ui.TestLive.value;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.mhrsdev.musicplayer.domain.model.Album;
import com.mhrsdev.musicplayer.domain.model.Playlist;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.fake.FakeLibraryRepository;
import com.mhrsdev.musicplayer.fake.FakePlayerController;
import com.mhrsdev.musicplayer.ui.nav.ScreenNavigator.TrackList;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

    private TrackListViewModel vm(TrackList kind, String id, SortOrder sort) {
        return new TrackListViewModel(library, library, library, player, Runnable::run, Locale.US, kind, id, sort);
    }

    private static List<Track> rows(TrackListViewModel vm) {
        return value(vm.getRows()).tracks();
    }

    @Test public void albumDetailHasHeaderAndTracks() {
        Album album = value(library.getAlbums()).get(0);
        TrackListViewModel vm = vm(TrackList.ALBUM, album.id(), SortOrder.DEFAULT);
        assertEquals(album.title(), value(vm.getHeader()).title());
        List<Track> tracks = rows(vm);
        assertFalse(tracks.isEmpty());
        vm.playAt(1, album.title());
        assertEquals(tracks.get(1).id(), value(player.getState()).currentTrack().id());
    }

    @Test public void likedListFollowsLikes() {
        TrackListViewModel vm = vm(TrackList.LIKED, null, SortOrder.DEFAULT);
        int before = rows(vm).size();
        Track notLiked = value(library.getAllTracks()).stream().filter(t -> !t.liked()).findFirst().orElseThrow();
        library.toggleLike(notLiked.id());
        assertEquals(before + 1, rows(vm).size());
        assertNotNull(value(vm.getHeader()));
    }

    @Test public void sortsByTitleWithSections() {
        TrackListViewModel vm = vm(TrackList.SONGS, null, new SortOrder(SortOrder.Field.TITLE, true));
        List<Track> sorted = rows(vm);
        java.text.Collator collator = java.text.Collator.getInstance(Locale.US); // same rule as SortOrder (primary strength)
        collator.setStrength(java.text.Collator.PRIMARY);
        for (int i = 1; i < sorted.size(); i++) {
            assertTrue(sorted.get(i - 1).title() + " before " + sorted.get(i).title(),
                    collator.compare(sorted.get(i - 1).title(), sorted.get(i).title()) <= 0);
        }
        assertFalse(value(vm.getRows()).sections().isEmpty());
        vm.setSort(new SortOrder(SortOrder.Field.DURATION, false));
        List<Track> byLength = rows(vm);
        assertTrue(byLength.get(0).durationMs() >= byLength.get(byLength.size() - 1).durationMs());
    }

    @Test public void multiSelectQueuesAndClears() {
        TrackListViewModel vm = vm(TrackList.SONGS, null, SortOrder.DEFAULT);
        List<Track> list = rows(vm);
        vm.toggleSelected(list.get(0).id());
        vm.toggleSelected(list.get(2).id());
        assertTrue(vm.selecting());
        assertEquals(List.of(list.get(0), list.get(2)), vm.selectedTracks());
        int queueBefore = value(player.getState()).queue().size();
        vm.queueSelected();
        assertEquals(queueBefore + 2, value(player.getState()).queue().size());
        assertFalse(vm.selecting());
        vm.selectAll();
        assertEquals(list.size(), value(vm.getSelection()).size());
    }

    @Test public void playlistRemoveUndoAndDeleteUndo() {
        Playlist p = value(library.getPlaylists()).get(0);
        TrackListViewModel vm = vm(TrackList.PLAYLIST, p.id(), SortOrder.DEFAULT);
        List<Track> before = new ArrayList<>(rows(vm));
        TrackListViewModel.Removed r = vm.removeAt(1);
        assertEquals(before.size() - 1, rows(vm).size());
        vm.undoRemove(r);
        assertEquals(before, rows(vm));
        vm.move(0, 2);
        assertEquals(before.get(0), rows(vm).get(2));
        int playlists = value(library.getPlaylists()).size();
        TrackListViewModel.DeletedPlaylist d = vm.deletePlaylist();
        assertEquals(playlists - 1, value(library.getPlaylists()).size());
        TrackListViewModel.undoDelete(library, library, d);
        List<Playlist> after = value(library.getPlaylists());
        assertEquals(playlists, after.size());
        Playlist restored = after.stream().filter(x -> x.name().equals(p.name())).findFirst().orElseThrow();
        assertEquals(before.size(), restored.trackCount());
    }

    @Test public void libraryHubPlaysLiked() {
        LibraryViewModel vm = new LibraryViewModel(library, player);
        List<Track> liked = value(vm.getLiked());
        vm.playLiked("Liked songs");
        assertEquals(liked.get(0).id(), value(player.getState()).currentTrack().id());
        assertEquals(LibraryViewModel.totalMs(liked), liked.stream().mapToLong(Track::durationMs).sum());
    }
}
