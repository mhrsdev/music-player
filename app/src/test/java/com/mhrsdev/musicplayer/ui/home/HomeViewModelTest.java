package com.mhrsdev.musicplayer.ui.home;

import static com.mhrsdev.musicplayer.ui.TestLive.value;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.mhrsdev.musicplayer.domain.model.HomeSection;
import com.mhrsdev.musicplayer.domain.model.HomeSectionStyle;
import com.mhrsdev.musicplayer.domain.model.PlaybackState;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.fake.FakeHomeFeedSource;
import com.mhrsdev.musicplayer.fake.FakeLibraryRepository;
import com.mhrsdev.musicplayer.fake.FakePlayerController;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class HomeViewModelTest {
    @Rule public InstantTaskExecutorRule rule = new InstantTaskExecutorRule();

    private FakePlayerController player;
    private HomeViewModel vm;

    @Before public void setUp() {
        player = new FakePlayerController();
        vm = new HomeViewModel(new FakeHomeFeedSource(new FakeLibraryRepository()), player);
    }

    private HomeSection listSection() {
        for (HomeSection s : value(vm.getSections())) if (s.style() == HomeSectionStyle.LIST) return s;
        throw new AssertionError("no list section");
    }

    @Test public void exposesSections() {
        List<HomeSection> sections = value(vm.getSections());
        assertNotNull(sections);
        assertFalse(sections.isEmpty());
    }

    @Test public void playFromSectionQueuesSectionTracksAtIndex() {
        HomeSection s = listSection();
        List<Track> tracks = HomeViewModel.tracksOf(s);
        vm.playFromSection(s, tracks.get(1));
        PlaybackState st = value(player.getState());
        assertEquals(tracks.get(1).id(), st.currentTrack().id());
        assertEquals(tracks.size(), st.queue().size());
        assertEquals(s.title(), st.playingFrom());
    }

    @Test public void flattenEmitsHeaderPerSection() {
        List<HomeSection> sections = value(vm.getSections());
        long headers = HomeAdapter.flatten(sections).stream().filter(r -> r.type() == HomeAdapter.Row.HEADER).count();
        assertEquals(sections.size(), headers);
    }
}
