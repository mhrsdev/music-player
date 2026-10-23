package com.mhrsdev.musicplayer.playback;

import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * "Shuffle the queue" with a one-step undo, pure Java. {@link #shuffle} keeps everything up to and including the
 * current item in place, shuffles the rest and remembers the previous order; {@link #undo} hands that exact order
 * back. Any other queue edit must call {@link #clear()} (the snapshot would no longer match).
 */
final class QueueShuffler<T> {
    @Nullable private List<T> snapshot;

    boolean canUndo() {
        return snapshot != null;
    }

    void clear() {
        snapshot = null;
    }

    /** New order, or null (and no snapshot) when fewer than two items follow {@code currentIndex}. */
    @Nullable List<T> shuffle(List<T> queue, int currentIndex, Random random) {
        if (currentIndex < 0 || queue.size() - currentIndex - 1 < 2) return null;
        snapshot = new ArrayList<>(queue);
        List<T> result = new ArrayList<>(queue);
        Collections.shuffle(result.subList(currentIndex + 1, result.size()), random);
        return result;
    }

    /** The order from before the shuffle (and forgets it), or null when there is nothing to undo. */
    @Nullable List<T> undo() {
        List<T> previous = snapshot;
        snapshot = null;
        return previous;
    }
}
