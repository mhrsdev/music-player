package com.mhrsdev.musicplayer.ui.widget;

import java.util.Locale;

/** Pure seek-bar math (JVM-testable). The bar always runs left-to-right, also in RTL (tape direction, D-A). */
public final class SeekMath {
    private SeekMath() {}

    /** Fraction 0..1 of {@code pos} in {@code duration}; 0 when duration is unknown. */
    public static float fraction(long pos, long duration) {
        if (duration <= 0) return 0f;
        if (pos <= 0) return 0f;
        if (pos >= duration) return 1f;
        return (float) ((double) pos / duration);
    }

    /** X coordinate of a position on a track spanning [left, right]. */
    public static float xFor(long pos, long duration, float left, float right) {
        return left + (right - left) * fraction(pos, duration);
    }

    /** Position for a touch at {@code x} on a track spanning [left, right], clamped to [0, duration]. */
    public static long positionFor(float x, float left, float right, long duration) {
        if (duration <= 0 || right <= left) return 0;
        float f = (x - left) / (right - left);
        if (f <= 0f) return 0;
        if (f >= 1f) return duration;
        return Math.round((double) f * duration);
    }

    /** Clamp a keyboard/accessibility step. */
    public static long step(long pos, long delta, long duration) {
        long p = pos + delta;
        if (p < 0) return 0;
        return duration > 0 && p > duration ? duration : p;
    }

    /** "m:ss" or "h:mm:ss", digits in the given locale (Persian digits for fa). */
    public static String formatTime(long ms, Locale locale) {
        if (ms < 0) ms = 0;
        long total = ms / 1000;
        long h = total / 3600, m = (total / 60) % 60, s = total % 60;
        return h > 0
                ? String.format(locale, "%d:%02d:%02d", h, m, s)
                : String.format(locale, "%d:%02d", m, s);
    }
}
