package com.mhrsdev.musicplayer.ui.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.mhrsdev.musicplayer.domain.model.LyricLine;
import com.mhrsdev.musicplayer.domain.model.Lyrics;
import com.mhrsdev.musicplayer.domain.model.LyricsCandidate;
import com.mhrsdev.musicplayer.domain.model.LyricsSearchResult;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.domain.repository.LyricsRepository;
import com.mhrsdev.musicplayer.fake.FakeLibraryRepository;
import com.mhrsdev.musicplayer.fake.FakePlayerController;
import com.mhrsdev.musicplayer.fake.FakeWaveformRepository;
import com.mhrsdev.musicplayer.fake.SampleData;
import com.mhrsdev.musicplayer.ui.player.lyrics.LyricsScrollPolicy;
import com.mhrsdev.musicplayer.ui.player.lyrics.LyricsSearchViewModel;
import com.mhrsdev.musicplayer.ui.player.lyrics.LyricsSearchViewModelAccess;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;

/** Search again (rate limit), lyrics tools staying open, manual search + pick, empty states, back chip rule. */
public class LyricsToolsTest {
    @Rule public InstantTaskExecutorRule rule = new InstantTaskExecutorRule();

    /** Scripted repository: no lyrics until a pick/fresh search; records calls. */
    static class Repo implements LyricsRepository {
        final List<String> calls = new ArrayList<>();
        Lyrics freshAnswer;
        MutableLiveData<LyricsSearchResult> searchLive = new MutableLiveData<>();

        @Override public LiveData<Lyrics> lyricsFor(Track t) {
            calls.add("get:" + t.id());
            return new MutableLiveData<>(Lyrics.none(t.id()));
        }

        @Override public LiveData<Lyrics> searchAgain(Track t) {
            calls.add("fresh:" + t.id());
            return new MutableLiveData<>(freshAnswer != null ? freshAnswer : Lyrics.none(t.id()));
        }

        @Override public LiveData<LyricsSearchResult> search(String artist, String title) {
            calls.add("search:" + artist + "/" + title);
            return searchLive;
        }

        @Override public LiveData<Lyrics> useCandidate(Track t, LyricsCandidate c) {
            calls.add("pick:" + c.title());
            return new MutableLiveData<>(new Lyrics(t.id(), List.of(new LyricLine(1000, "a"), new LyricLine(2000, "b")),
                    true, "Your lyrics"));
        }
    }

    private final Repo repo = new Repo();
    private long now = 100_000;

    private PlayerViewModel vm() {
        FakePlayerController c = new FakePlayerController(SampleData.TRACKS.subList(3, 6), 0); // track-4: none
        PlayerViewModel vm = new PlayerViewModel(c, new FakeLibraryRepository(), repo, new FakeWaveformRepository());
        vm.clock = () -> now;
        vm.getLyrics().observeForever(x -> { });
        vm.getLyricsAvailability().observeForever(x -> { });
        vm.getCurrentTrack().observeForever(x -> { });
        return vm;
    }

    @Test public void searchAgainIsRateLimited() {
        PlayerViewModel vm = vm();
        assertTrue(vm.searchAgain());
        assertFalse(vm.searchAgain());
        now += PlayerViewModel.SEARCH_AGAIN_INTERVAL_MS;
        assertTrue(vm.searchAgain());
        assertEquals(2, repo.calls.stream().filter(s -> s.startsWith("fresh:")).count());
    }

    @Test public void toolsStayOpenOnNoneAndSearchAgainCanFindLyrics() {
        PlayerViewModel vm = vm();
        assertEquals(LyricsAvailability.NONE, vm.getLyricsAvailability().getValue());
        vm.toggleLyrics();
        assertFalse(vm.getShowLyrics().getValue());
        vm.openLyricsTools();
        assertTrue(vm.getShowLyrics().getValue());
        repo.freshAnswer = Lyrics.none("track-4");
        vm.searchAgain();
        assertTrue("empty state stays for the track the user works on", vm.getShowLyrics().getValue());
        now += 20_000;
        repo.freshAnswer = new Lyrics("track-4", List.of(new LyricLine(0, "x"), new LyricLine(1, "y")), true, "LRCLIB");
        vm.searchAgain();
        assertEquals(LyricsAvailability.AVAILABLE, vm.getLyricsAvailability().getValue());
    }

    @Test public void manualSearchAndPick() {
        PlayerViewModel player = vm();
        LyricsSearchViewModel s = LyricsSearchViewModelAccess.create(repo);
        s.getState().observeForever(x -> { });
        assertFalse(s.search(" ", ""));
        String[] q = player.manualSearchPrefill();
        assertTrue(s.search(q[0], q[1]));
        assertTrue(s.getState().getValue().loading());
        LyricsCandidate cand = new LyricsCandidate("Song", "Artist", "", 200_000, true, "[00:01.00]a\n[00:02.00]b");
        repo.searchLive.setValue(new LyricsSearchResult(List.of(cand), false));
        LyricsSearchViewModel.State st = s.getState().getValue();
        assertFalse(st.loading());
        assertEquals(1, st.results().size());
        player.useLyricsCandidate(cand);
        assertTrue(repo.calls.contains("pick:Song"));
        assertEquals(LyricsAvailability.AVAILABLE, player.getLyricsAvailability().getValue());
        repo.searchLive = new MutableLiveData<>();
        s.search("x", "y");
        repo.searchLive.setValue(LyricsSearchResult.failure());
        assertTrue(s.getState().getValue().failed());
    }

    @Test public void prefillIsCleaned() {
        FakePlayerController c = new FakePlayerController(List.of(new Track("w", "01 - Ghalbam [Free Download]", "a",
                "Reza Shiri @RozMusic.com", "al", "Album", 200_000L, null, 1, null, false, false, 2020, 1, 0L)), 0);
        PlayerViewModel vm = new PlayerViewModel(c, new FakeLibraryRepository(), repo, new FakeWaveformRepository());
        vm.getCurrentTrack().observeForever(x -> { });
        String[] q = vm.manualSearchPrefill();
        assertEquals("Reza Shiri", q[0]);
        assertEquals("Ghalbam", q[1]);
    }

    @Test public void emptyStates() {
        assertEquals(LyricsEmptyState.LOADING, LyricsEmptyState.of(null, true));
        assertEquals(LyricsEmptyState.NOT_FOUND, LyricsEmptyState.of(Lyrics.none("t"), true));
        assertEquals(LyricsEmptyState.FAILED, LyricsEmptyState.of(Lyrics.failed("t"), true));
        assertEquals(LyricsEmptyState.OFFLINE, LyricsEmptyState.of(Lyrics.none("t"), false));
        assertEquals(LyricsEmptyState.CONTENT,
                LyricsEmptyState.of(new Lyrics("t", List.of(new LyricLine(0, "a")), true, null), true));
        assertTrue(PlayerViewModel.showOfflineRetry(Lyrics.failed("t"), true));
        assertNull(null);
    }

    @Test public void backChipRule() {
        assertFalse(LyricsScrollPolicy.showBackChip(3, 1, -1, -1));
        assertFalse(LyricsScrollPolicy.showBackChip(10, 4, 2, 6));
        assertTrue(LyricsScrollPolicy.showBackChip(10, 8, 2, 6));
        assertFalse(LyricsScrollPolicy.showBackChip(10, -1, 2, 6));
    }
}
