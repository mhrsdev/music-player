package com.mhrsdev.musicplayer.ui.search;

import static com.mhrsdev.musicplayer.ui.TestLive.value;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.mhrsdev.musicplayer.domain.model.SearchFilter;
import com.mhrsdev.musicplayer.domain.model.SearchResult;
import com.mhrsdev.musicplayer.fake.FakeLibraryRepository;
import com.mhrsdev.musicplayer.fake.FakePlayerController;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class SearchViewModelTest {
    @Rule public InstantTaskExecutorRule rule = new InstantTaskExecutorRule();

    private FakePlayerController player;
    private SearchViewModel vm;

    @Before public void setUp() {
        player = new FakePlayerController();
        vm = new SearchViewModel(new FakeLibraryRepository(), player);
    }

    @Test public void blankQueryShowsPrompt() {
        value(vm.getResults());
        assertEquals(SearchViewModel.Mode.PROMPT, value(vm.getMode()));
    }

    @Test public void queryFindsTracks() {
        value(vm.getMode());
        vm.setQuery("neffex");
        assertFalse(value(vm.getResults()).isEmpty());
        assertEquals(SearchViewModel.Mode.RESULTS, value(vm.getMode()));
    }

    @Test public void unknownQueryShowsNoResults() {
        value(vm.getMode());
        vm.setQuery("zzzz-nothing");
        assertEquals(SearchViewModel.Mode.NO_RESULTS, value(vm.getMode()));
    }

    @Test public void filterRestrictsKinds() {
        value(vm.getMode());
        vm.setQuery("best");
        vm.setFilter(SearchFilter.SONGS);
        assertTrue(value(vm.getResults()).stream().allMatch(r -> r.kind() == SearchResult.Kind.TRACK));
    }

    @Test public void playTrackQueuesTrackResults() {
        value(vm.getMode());
        vm.setQuery("neffex");
        SearchResult first = value(vm.getResults()).stream()
                .filter(r -> r.kind() == SearchResult.Kind.TRACK).findFirst().orElseThrow();
        vm.playTrack(first.track());
        assertEquals(first.track().id(), value(player.getState()).currentTrack().id());
    }
}
