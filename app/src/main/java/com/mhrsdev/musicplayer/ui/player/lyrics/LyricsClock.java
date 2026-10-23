package com.mhrsdev.musicplayer.ui.player.lyrics;

import android.os.SystemClock;

/**
 * Smooth playback position for the word sweep. The player publishes the position 4×/s; between ticks the clock
 * extrapolates with the uptime clock while playing (at most {@link #MAX_EXTRAPOLATE_MS}, so a stalled player never
 * runs ahead), then adds the per-track lyrics offset (+ = lyrics later).
 */
public final class LyricsClock implements LyricLineView.Clock {
    static final long MAX_EXTRAPOLATE_MS = 1_000;
    private long anchorPos, anchorAt;
    private boolean playing;
    private long offsetMs;

    public void update(long positionMs, boolean isPlaying) {
        anchorPos = positionMs;
        anchorAt = SystemClock.uptimeMillis();
        playing = isPlaying;
    }

    public void setOffsetMs(long offset) { offsetMs = offset; }

    public long offsetMs() { return offsetMs; }

    public boolean isPlaying() { return playing; }

    @Override public long positionMs() {
        return at(SystemClock.uptimeMillis());
    }

    /** Pure form for tests. */
    long at(long nowUptime) {
        long p = anchorPos;
        if (playing) p += Math.max(0, Math.min(MAX_EXTRAPOLATE_MS, nowUptime - anchorAt));
        return p - offsetMs;
    }
}
