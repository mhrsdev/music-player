package com.mhrsdev.musicplayer.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.mhrsdev.musicplayer.domain.model.SleepTimer;
import org.junit.Test;

public class SleepTimerLogicTest {
    private static final long MIN = 60_000L;

    @Test public void startsOff() {
        SleepTimerLogic t = new SleepTimerLogic();
        assertEquals(SleepTimer.OFF, t.snapshot(0));
        assertEquals(-1L, t.nextWakeMs(0));
        assertEquals(1f, t.volume(0), 0f);
        assertFalse(t.isExpired(0));
    }

    @Test public void countdownRunsOnlyWhilePlaying() {
        SleepTimerLogic t = new SleepTimerLogic();
        t.startCountdown(10 * MIN, true, 0L);
        assertEquals(SleepTimer.countdown(9 * MIN, 10 * MIN), t.snapshot(MIN));
        t.setPlaying(false, 2 * MIN); // paused after 2 min
        assertEquals(8 * MIN, t.remainingMs(100 * MIN));
        assertEquals(-1L, t.nextWakeMs(100 * MIN));
        t.setPlaying(true, 100 * MIN);
        assertEquals(7 * MIN, t.remainingMs(101 * MIN));
    }

    @Test public void startedWhilePausedWaitsForPlayback() {
        SleepTimerLogic t = new SleepTimerLogic();
        t.startCountdown(5 * MIN, false, 0L);
        assertEquals(5 * MIN, t.remainingMs(50 * MIN));
        assertEquals(-1L, t.nextWakeMs(50 * MIN));
        t.setPlaying(true, 50 * MIN);
        assertEquals(5 * MIN - SleepTimerLogic.FADE_MS, t.nextWakeMs(50 * MIN));
    }

    @Test public void fadesOutInTheLastSecondsThenExpires() {
        SleepTimerLogic t = new SleepTimerLogic();
        t.startCountdown(MIN, true, 0L);
        long fadeStart = MIN - SleepTimerLogic.FADE_MS;
        assertEquals(1f, t.volume(fadeStart), 0f);
        assertEquals(0.5f, t.volume(MIN - SleepTimerLogic.FADE_MS / 2), 0.001f);
        assertEquals(SleepTimerLogic.FADE_STEP_MS, t.nextWakeMs(fadeStart));
        assertFalse(t.isExpired(MIN - 1));
        assertTrue(t.isExpired(MIN));
        assertEquals(0f, t.volume(MIN), 0f);
    }

    @Test public void endOfTrackAndCancel() {
        SleepTimerLogic t = new SleepTimerLogic();
        t.startEndOfTrack();
        assertEquals(SleepTimer.END_OF_TRACK, t.snapshot(0));
        assertEquals(-1L, t.nextWakeMs(0));
        t.setPlaying(true, 0); // ignored for end-of-track
        assertEquals(1f, t.volume(10 * MIN), 0f);
        t.cancel();
        assertEquals(SleepTimer.OFF, t.snapshot(0));
    }

    @Test public void restartingReplacesPreviousTimer() {
        SleepTimerLogic t = new SleepTimerLogic();
        t.startEndOfTrack();
        t.startCountdown(MIN, true, 0L);
        assertEquals(SleepTimer.Kind.COUNTDOWN, t.kind());
        t.startEndOfTrack();
        assertEquals(SleepTimer.Kind.END_OF_TRACK, t.kind());
    }
}
