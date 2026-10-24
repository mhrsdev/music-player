package com.mhrsdev.musicplayer.domain.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class PlaybackStateTest {
    private static Track track(int i) {
        return new Track("t" + i, "T" + i, "a", "A", "al", "Al", 1000L, null, 0, "content://x/" + i, false, false, 0, 0, 0);
    }

    private static PlaybackState state(List<Track> queue, long position) {
        return new PlaybackState(queue.isEmpty() ? null : queue.get(0), true, false, position, 1000L, false,
                RepeatMode.OFF, queue, 0, SleepTimer.OFF, null, false);
    }

    @Test public void aMutableQueueIsCopiedAndTheStateCannotChangeUnderneath() {
        List<Track> source = new ArrayList<>(List.of(track(1), track(2)));
        PlaybackState s = state(source, 0);
        source.add(track(3));
        assertEquals(2, s.queue().size());
        assertThrows(UnsupportedOperationException.class, () -> s.queue().add(track(4)));
        assertThrows(UnsupportedOperationException.class, () -> s.queue().remove(0));
    }

    /** The player publishes 4 times a second: a 10k queue must not be copied for every tick (garbage, GC wakeups). */
    @Test public void aQueueFromFrozenIsSharedByEveryTick() {
        List<Track> big = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) big.add(track(i));
        List<Track> frozen = PlaybackState.frozen(big);
        PlaybackState first = state(frozen, 0), second = state(frozen, 250);
        assertSame(frozen, first.queue());
        assertSame(frozen, second.queue());
        assertSame(frozen, PlaybackState.frozen(frozen));
        assertEquals(big, frozen);
        assertEquals(big.hashCode(), frozen.hashCode());
    }

    @Test public void frozenCopiesTheSourceOnce() {
        List<Track> source = new ArrayList<>(List.of(track(1)));
        List<Track> frozen = PlaybackState.frozen(source);
        assertNotSame(source, frozen);
        source.clear();
        assertEquals(1, frozen.size());
    }
}
