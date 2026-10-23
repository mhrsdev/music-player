package com.mhrsdev.musicplayer.fake;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.mhrsdev.musicplayer.domain.model.PlaybackState;
import com.mhrsdev.musicplayer.domain.model.RepeatMode;
import com.mhrsdev.musicplayer.domain.model.SleepTimer;
import com.mhrsdev.musicplayer.domain.model.Track;
import java.util.HashSet;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;

public class FakePlayerControllerTest {
    @Rule public InstantTaskExecutorRule instantTaskExecutor = new InstantTaskExecutorRule();

    private final List<Track> tracks = SampleData.TRACKS.subList(0, 5);

    private FakePlayerController controller(int startIndex) {
        return new FakePlayerController(tracks, startIndex);
    }

    private static PlaybackState s(FakePlayerController c) {
        return c.getState().getValue();
    }

    @Test public void startsPausedWithQueue() {
        FakePlayerController c = controller(1);
        assertEquals(tracks.get(1), s(c).currentTrack());
        assertFalse(s(c).playing());
        assertEquals(5, s(c).queue().size());
    }

    @Test public void tickAdvancesPositionAndMovesToNextTrackAtEnd() {
        FakePlayerController c = controller(0);
        c.play(tracks, 0, "Test");
        c.seekTo(tracks.get(0).durationMs() - 1_000);
        for (int i = 0; i < 6; i++) c.tick();
        assertEquals(1, s(c).currentIndex());
        assertTrue(s(c).playing());
        assertEquals("Test", s(c).playingFrom());
    }

    @Test public void tickDoesNothingWhilePaused() {
        FakePlayerController c = controller(1);
        long before = s(c).positionMs();
        c.tick();
        assertEquals(before, s(c).positionMs());
    }

    @Test public void repeatCyclesOffAllOne() {
        FakePlayerController c = controller(0);
        c.cycleRepeat();
        assertEquals(RepeatMode.ALL, s(c).repeatMode());
        c.cycleRepeat();
        assertEquals(RepeatMode.ONE, s(c).repeatMode());
        c.cycleRepeat();
        assertEquals(RepeatMode.OFF, s(c).repeatMode());
    }

    @Test public void moveAndRemoveKeepCurrentTrack() {
        FakePlayerController c = controller(2);
        Track current = s(c).currentTrack();
        c.moveQueueItem(0, 4);
        assertEquals(current, s(c).queue().get(s(c).currentIndex()));
        c.moveQueueItem(4, 0);
        assertEquals(current, s(c).queue().get(s(c).currentIndex()));
        c.removeFromQueue(0);
        assertEquals(4, s(c).queue().size());
        assertEquals(current, s(c).queue().get(s(c).currentIndex()));
    }

    @Test public void removingCurrentTrackSelectsNeighbour() {
        FakePlayerController c = controller(4);
        c.removeFromQueue(4);
        assertEquals(3, s(c).currentIndex());
        assertEquals(tracks.get(3), s(c).currentTrack());
    }

    @Test public void shuffleThenUndoRestoresOrder() {
        FakePlayerController c = new FakePlayerController(SampleData.TRACKS.subList(0, 10), 2);
        List<Track> before = s(c).queue();
        c.shuffleQueue();
        assertTrue(s(c).canUndoShuffle());
        assertEquals(before.subList(0, 3), s(c).queue().subList(0, 3));
        assertEquals(new HashSet<>(before), new HashSet<>(s(c).queue()));
        c.undoShuffleQueue();
        assertEquals(before, s(c).queue());
        assertFalse(s(c).canUndoShuffle());
    }

    @Test public void endOfTrackSleepTimerStopsPlayback() {
        FakePlayerController c = controller(0);
        c.play(tracks, 0, null);
        c.setSleepTimerEndOfTrack();
        c.seekTo(tracks.get(0).durationMs() - 250);
        c.tick();
        assertFalse(s(c).playing());
        assertEquals(SleepTimer.OFF, s(c).sleepTimer());
    }

    @Test public void countdownSleepTimerStopsPlayback() {
        FakePlayerController c = controller(0);
        c.play(tracks, 0, null);
        c.setSleepTimer(1);
        for (int i = 0; i < 240; i++) c.tick();
        assertFalse(s(c).playing());
        assertEquals(SleepTimer.OFF, s(c).sleepTimer());
    }

    @Test public void playNextInsertsAfterCurrent() {
        FakePlayerController c = controller(1);
        Track extra = SampleData.TRACKS.get(20);
        c.playNext(extra);
        assertEquals(extra, s(c).queue().get(2));
        assertEquals(1, s(c).currentIndex());
        c.addToQueue(extra, 0);
        assertEquals(2, s(c).currentIndex());
    }

    @Test public void previousRestartsTrackAfterThreeSeconds() {
        FakePlayerController c = controller(2);
        c.seekTo(10_000);
        c.previous();
        assertEquals(2, s(c).currentIndex());
        assertEquals(0, s(c).positionMs());
        c.previous();
        assertEquals(1, s(c).currentIndex());
    }
}
