package com.mhrsdev.musicplayer.ui.player.lyrics;

/**
 * Pure auto-scroll rule for the lyrics list: a manual scroll pauses auto-scroll for {@link #PAUSE_MS}; tapping
 * "back to current line" or a line resumes immediately. Time is passed in so it is JVM-testable.
 */
public class LyricsScrollPolicy {
    public static final long PAUSE_MS = 3_000L;
    private long userScrolledAt = Long.MIN_VALUE / 2;

    public void onUserScroll(long nowMs) { userScrolledAt = nowMs; }

    public void resume() { userScrolledAt = Long.MIN_VALUE / 2; }

    public boolean shouldAutoScroll(long nowMs) { return nowMs - userScrolledAt >= PAUSE_MS; }

    /** Milliseconds until auto-scroll resumes (0 if active). */
    public long remainingPauseMs(long nowMs) { return Math.max(0L, PAUSE_MS - (nowMs - userScrolledAt)); }

    /**
     * "Back to current line" chip: only for 4+ lines, a valid active line, and while that line is not completely
     * visible ({@code first}/{@code last} = completely visible positions, -1 when unknown).
     */
    public static boolean showBackChip(int lineCount, int active, int first, int last) {
        if (lineCount < 4 || active < 0) return false;
        if (first < 0 || last < 0) return true;
        return active < first || active > last;
    }

    /** Alpha for a line: active 1, upcoming .60, past .50 (DESIGN §8 measured alphas); unsynced lyrics are all 1. */
    public static float alphaFor(int line, int active, boolean synced) {
        if (!synced) return 1f;
        if (line == active) return 1f;
        return line < active ? 0.50f : 0.60f;
    }
}
