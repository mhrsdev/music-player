package com.mhrsdev.musicplayer.ui.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.mhrsdev.musicplayer.domain.model.LyricLine;
import com.mhrsdev.musicplayer.domain.model.Lyrics;
import com.mhrsdev.musicplayer.fake.FakeLibraryRepository;
import com.mhrsdev.musicplayer.fake.FakeLyricsRepository;
import com.mhrsdev.musicplayer.fake.FakePlayerController;
import com.mhrsdev.musicplayer.fake.FakeWaveformRepository;
import com.mhrsdev.musicplayer.fake.SampleData;
import com.mhrsdev.musicplayer.ui.player.lyrics.LyricsScrollPolicy;

import org.junit.Rule;
import org.junit.Test;

import java.util.Arrays;
import java.util.Locale;

public class PlayerViewModelTest {
    @Rule public InstantTaskExecutorRule rule = new InstantTaskExecutorRule();

    private PlayerViewModel vm(FakePlayerController c) {
        PlayerViewModel vm = new PlayerViewModel(c, new FakeLibraryRepository(), new FakeLyricsRepository(),
                new FakeWaveformRepository());
        vm.getActiveLyricLine().observeForever(x -> {});
        vm.getLyricsAvailability().observeForever(x -> {});
        vm.getLiked().observeForever(x -> {});
        vm.getCurrentTrack().observeForever(x -> {});
        return vm;
    }

    @Test public void formatsTimeAndRemaining() {
        assertEquals("3:07", PlayerFormat.time(187_400, Locale.US));
        assertEquals("1:02:09", PlayerFormat.time(3_729_000, Locale.US));
        assertEquals("−2:09", PlayerFormat.remaining(52_000, 181_000, Locale.US));
        assertEquals("۳:۰۷", PlayerFormat.time(187_000, Locale.forLanguageTag("fa")));
        assertEquals(1, PlayerFormat.minutesCeil(30_000));
        assertEquals(0, PlayerFormat.minutesCeil(0));
        assertEquals(0.5f, PlayerFormat.fraction(50, 100), 1e-6);
        assertEquals(0f, PlayerFormat.fraction(50, 0), 1e-6);
    }

    @Test public void activeLineMapping() {
        Lyrics l = new Lyrics("t1", Arrays.asList(new LyricLine(1000, "a"), new LyricLine(5000, "b")), true, "lrc");
        com.mhrsdev.musicplayer.domain.model.PlaybackState s = new com.mhrsdev.musicplayer.domain.model.PlaybackState(
                SampleData.TRACKS.get(0), true, false, 6000, 10000, false,
                com.mhrsdev.musicplayer.domain.model.RepeatMode.OFF, SampleData.TRACKS.subList(0, 2), 0,
                com.mhrsdev.musicplayer.domain.model.SleepTimer.OFF, null, false);
        // lyrics for another track never highlight
        assertEquals(-1, PlayerViewModel.activeLineFor(l, s));
        Lyrics mine = new Lyrics(SampleData.TRACKS.get(0).id(), l.lines(), true, "lrc");
        assertEquals(1, PlayerViewModel.activeLineFor(mine, s));
        Lyrics plain = new Lyrics(mine.trackId(), l.lines(), false, "lrc");
        assertEquals(-1, PlayerViewModel.activeLineFor(plain, s));
        assertEquals(-1, PlayerViewModel.activeLineFor(null, s));
    }

    @Test public void lyricsToggleAndLike() {
        FakePlayerController c = new FakePlayerController(SampleData.TRACKS.subList(0, 4), 0);
        PlayerViewModel vm = vm(c);
        assertFalse(vm.getShowLyrics().getValue());
        vm.toggleLyrics();
        assertTrue(vm.getShowLyrics().getValue());
        boolean before = vm.getLiked().getValue();
        vm.toggleLike();
        assertEquals(!before, vm.getLiked().getValue());
    }

    @Test public void lyricsButtonFollowsAvailability() {
        // track-4 has no lyrics (index 3), track-1 has synced lyrics
        FakePlayerController c = new FakePlayerController(SampleData.TRACKS.subList(0, 5), 0);
        PlayerViewModel vm = vm(c);
        assertEquals(LyricsAvailability.AVAILABLE, vm.getLyricsAvailability().getValue());
        vm.toggleLyrics();
        assertTrue(vm.getShowLyrics().getValue());
        c.next(); c.next(); c.next(); // -> track-4
        assertEquals(LyricsAvailability.NONE, vm.getLyricsAvailability().getValue());
        assertFalse("lyrics view closes on a track without lyrics", vm.getShowLyrics().getValue());
        vm.toggleLyrics();
        assertFalse("cannot open lyrics that do not exist", vm.getShowLyrics().getValue());
    }

    /** Lyrics are asked for the current track only (never the queue), once per track, while the player observes. */
    @Test public void lyricsAreRequestedForTheCurrentTrackOnly() {
        FakePlayerController c = new FakePlayerController(SampleData.TRACKS.subList(0, 4), 0);
        java.util.List<String> asked = new java.util.ArrayList<>();
        FakeLyricsRepository fake = new FakeLyricsRepository();
        com.mhrsdev.musicplayer.domain.repository.LyricsRepository repo = t -> {
            asked.add(t.id());
            return fake.lyricsFor(t);
        };
        PlayerViewModel vm = new PlayerViewModel(c, new FakeLibraryRepository(), repo, new FakeWaveformRepository());
        assertTrue("nobody observing: no request", asked.isEmpty());
        vm.getCurrentTrack().observeForever(x -> {});
        vm.getLyrics().observeForever(x -> {});
        assertEquals(java.util.List.of("track-1"), asked);
        vm.toggleLyrics();
        vm.toggleLyrics();
        assertEquals("toggling does not ask again", 1, asked.size());
        c.next();
        assertEquals(java.util.List.of("track-1", "track-2"), asked);
    }

    @Test public void seekClampsAndSteps() {
        FakePlayerController c = new FakePlayerController(SampleData.TRACKS.subList(0, 4), 0);
        PlayerViewModel vm = vm(c);
        vm.seekToFraction(2f);
        assertEquals(c.getState().getValue().durationMs(), c.getState().getValue().positionMs());
        vm.seekTo(-5);
        assertEquals(0, c.getState().getValue().positionMs());
        vm.seekBy(10_000);
        assertEquals(10_000, c.getState().getValue().positionMs());
    }

    @Test public void scrollPolicyPausesThreeSeconds() {
        LyricsScrollPolicy p = new LyricsScrollPolicy();
        assertTrue(p.shouldAutoScroll(1_000));
        p.onUserScroll(1_000);
        assertFalse(p.shouldAutoScroll(3_999));
        assertEquals(1, p.remainingPauseMs(3_999));
        assertTrue(p.shouldAutoScroll(4_000));
        p.onUserScroll(5_000);
        p.resume();
        assertTrue(p.shouldAutoScroll(5_001));
        assertEquals(1f, LyricsScrollPolicy.alphaFor(3, 3, true), 0);
        assertEquals(0.5f, LyricsScrollPolicy.alphaFor(2, 3, true), 0);
        assertEquals(0.6f, LyricsScrollPolicy.alphaFor(4, 3, true), 0);
        assertEquals(1f, LyricsScrollPolicy.alphaFor(4, 3, false), 0);
    }

    @org.junit.Test public void titleFitsLargestSizeThenFloorsAtMin() {
        // width grows 10 px per sp: 300 px available -> 30 sp fits; 250 -> 24 sp; 100 -> floor 20 sp
        org.junit.Assert.assertEquals(30f, PlayerFragment.fitTextSize(300f, 30f, 20f, sp -> sp * 10), 0f);
        org.junit.Assert.assertEquals(24f, PlayerFragment.fitTextSize(250f, 30f, 20f, sp -> sp * 10), 0f);
        org.junit.Assert.assertEquals(20f, PlayerFragment.fitTextSize(100f, 30f, 20f, sp -> sp * 10), 0f);
    }
}
