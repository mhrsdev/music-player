package com.mhrsdev.musicplayer.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.Test;

public class QueueShufflerTest {
    private static final List<String> QUEUE = Arrays.asList("a", "b", "c", "d", "e", "f", "g", "h");

    @Test public void keepsHeadAndCurrentShufflesTailAndUndoRestoresExactly() {
        QueueShuffler<String> s = new QueueShuffler<>();
        List<String> shuffled = s.shuffle(QUEUE, 2, new Random(1));
        assertEquals(QUEUE.subList(0, 3), shuffled.subList(0, 3));
        List<String> tail = new ArrayList<>(shuffled.subList(3, 8));
        java.util.Collections.sort(tail);
        assertEquals(QUEUE.subList(3, 8), tail); // same items
        assertTrue(s.canUndo());
        assertEquals(QUEUE, s.undo());
        assertFalse(s.canUndo());
        assertNull(s.undo());
    }

    @Test public void tooShortOrNoCurrentDoesNothing() {
        QueueShuffler<String> s = new QueueShuffler<>();
        assertNull(s.shuffle(QUEUE, 6, new Random(1))); // one item after current
        assertNull(s.shuffle(QUEUE, -1, new Random(1)));
        assertFalse(s.canUndo());
    }

    @Test public void clearForgetsSnapshotAndInputIsNotMutated() {
        QueueShuffler<String> s = new QueueShuffler<>();
        List<String> input = new ArrayList<>(QUEUE);
        s.shuffle(input, 0, new Random(7));
        assertEquals(QUEUE, input);
        s.clear();
        assertFalse(s.canUndo());
    }
}
