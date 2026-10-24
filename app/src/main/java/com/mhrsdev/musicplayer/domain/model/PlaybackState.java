package com.mhrsdev.musicplayer.domain.model;

import androidx.annotation.Nullable;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.RandomAccess;

/**
 * Immutable snapshot of the player, published as {@code LiveData<PlaybackState>}.
 *
 * @param currentTrack current item, null when the queue is empty
 * @param currentIndex index of {@code currentTrack} in {@code queue}, -1 when the queue is empty
 * @param playingFrom label of the queue origin, e.g. "NEFFEX" (the "Playing from" chip); may be null
 * @param canUndoShuffle true right after {@code PlayerController.shuffleQueue()}; drives the Undo snackbar
 */
public record PlaybackState(
        @Nullable Track currentTrack,
        boolean playing,
        boolean buffering,
        long positionMs,
        long durationMs,
        boolean shuffleEnabled,
        RepeatMode repeatMode,
        List<Track> queue,
        int currentIndex,
        SleepTimer sleepTimer,
        @Nullable String playingFrom,
        boolean canUndoShuffle) {

    public PlaybackState {
        queue = frozen(queue);
    }

    /**
     * An immutable copy of {@code tracks}; a list that already came from here is returned as is. The player publishes a
     * state 4 times a second and a 10k-track queue must not be copied each time (PerfBudget: no per-tick O(queue) work).
     */
    public static List<Track> frozen(List<Track> tracks) {
        return tracks instanceof Frozen ? tracks : new Frozen(new ArrayList<>(tracks));
    }

    private static class Frozen extends AbstractList<Track> implements RandomAccess {
        private final List<Track> items;

        Frozen(List<Track> items) {
            this.items = items;
        }

        @Override public Track get(int index) {
            return items.get(index);
        }

        @Override public int size() {
            return items.size();
        }
    }

    /** Idle player: empty queue, nothing playing. */
    public static PlaybackState empty() {
        return new PlaybackState(null, false, false, 0L, 0L, false, RepeatMode.OFF,
                List.of(), -1, SleepTimer.OFF, null, false);
    }

    public boolean hasNext() {
        return currentIndex >= 0 && currentIndex < queue.size() - 1;
    }

    public boolean hasPrevious() {
        return currentIndex > 0;
    }

    /** Remaining time of the current track plus the durations of all tracks after it. */
    public long remainingQueueMs() {
        if (currentIndex < 0) return 0L;
        long total = Math.max(0L, durationMs - positionMs);
        for (int i = currentIndex + 1; i < queue.size(); i++) total += queue.get(i).durationMs();
        return total;
    }
}
