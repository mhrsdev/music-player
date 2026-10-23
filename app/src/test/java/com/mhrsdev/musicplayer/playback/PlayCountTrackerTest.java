package com.mhrsdev.musicplayer.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class PlayCountTrackerTest {
    @Test public void thresholdIsMinOf30sAnd30Percent() {
        assertEquals(30_000L, PlayCountTracker.thresholdMs(240_000L));
        assertEquals(12_000L, PlayCountTracker.thresholdMs(40_000L));
        assertEquals(30_000L, PlayCountTracker.thresholdMs(0L));
        assertEquals(1_000L, PlayCountTracker.thresholdMs(2_000L));
    }

    @Test public void countsOnceAfterThresholdOfPlayedTime() {
        PlayCountTracker t = new PlayCountTracker();
        t.start("a", 240_000L);
        assertEquals(-1L, t.msUntilCount(0)); // not playing: nothing to schedule
        t.setPlaying(true, 1_000L);
        assertEquals(30_000L, t.msUntilCount(1_000L));
        assertNull(t.claim(20_000L));
        assertEquals("a", t.claim(31_000L));
        assertNull(t.claim(40_000L));
        assertEquals(-1L, t.msUntilCount(40_000L));
    }

    @Test public void pausedTimeDoesNotCount() {
        PlayCountTracker t = new PlayCountTracker();
        t.start("a", 240_000L);
        t.setPlaying(true, 0L);
        t.setPlaying(false, 20_000L); // 20 s played
        assertEquals(-1L, t.msUntilCount(500_000L));
        t.setPlaying(true, 600_000L);
        assertEquals(10_000L, t.msUntilCount(600_000L));
        assertNull(t.claim(605_000L));
        assertEquals("a", t.claim(610_000L));
    }

    @Test public void restartResetsAndAllowsCountingAgain() {
        PlayCountTracker t = new PlayCountTracker();
        t.start("a", 100_000L);
        t.setPlaying(true, 0L);
        assertEquals("a", t.claim(30_000L));
        t.start("a", 100_000L); // repeat-one loop
        t.setPlaying(true, 30_000L);
        assertEquals("a", t.claim(60_000L));
    }

    @Test public void noTrackNeverCounts() {
        PlayCountTracker t = new PlayCountTracker();
        t.start(null, 0L);
        t.setPlaying(true, 0L);
        assertEquals(-1L, t.msUntilCount(100_000L));
        assertNull(t.claim(100_000L));
    }
}
