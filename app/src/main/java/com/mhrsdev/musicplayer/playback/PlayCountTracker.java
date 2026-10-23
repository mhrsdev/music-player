package com.mhrsdev.musicplayer.playback;

import androidx.annotation.Nullable;

/**
 * Decides when a track counts as "played": after min(30 s, 30 % of its length) of actual playback time
 * (seeking forward does not count). Pure logic, no Android; the caller passes a monotonic clock ({@code now}, ms)
 * and schedules ONE timeout from {@link #msUntilCount}, so nothing polls.
 */
final class PlayCountTracker {
    static final long MAX_THRESHOLD_MS = 30_000L;
    static final long MIN_THRESHOLD_MS = 1_000L;
    static final float FRACTION = 0.3f;

    @Nullable private String trackId;
    private long thresholdMs;
    private long playedMs;
    private long playingSince = -1L;
    private boolean counted;

    /** Threshold for a track of the given length; unknown length (<= 0) uses the maximum. */
    static long thresholdMs(long durationMs) {
        if (durationMs <= 0) return MAX_THRESHOLD_MS;
        return Math.max(MIN_THRESHOLD_MS, Math.min(MAX_THRESHOLD_MS, (long) (durationMs * FRACTION)));
    }

    /** A new play-through begins (item change or repeat-one loop). Starts in the not-playing state. */
    void start(@Nullable String trackId, long durationMs) {
        this.trackId = trackId;
        this.thresholdMs = thresholdMs(durationMs);
        this.playedMs = 0L;
        this.playingSince = -1L;
        this.counted = trackId == null;
    }

    void setPlaying(boolean playing, long now) {
        if (playing && playingSince < 0) {
            playingSince = now;
        } else if (!playing && playingSince >= 0) {
            playedMs += now - playingSince;
            playingSince = -1L;
        }
    }

    /** Milliseconds of further playback until the track counts, or -1 when no timeout is needed. */
    long msUntilCount(long now) {
        if (counted || playingSince < 0) return -1L;
        return Math.max(0L, thresholdMs - (playedMs + now - playingSince));
    }

    /** Returns the track id exactly once when the threshold has been reached, else null. */
    @Nullable String claim(long now) {
        if (counted || playingSince < 0 || msUntilCount(now) > 0) return null;
        counted = true;
        return trackId;
    }
}
