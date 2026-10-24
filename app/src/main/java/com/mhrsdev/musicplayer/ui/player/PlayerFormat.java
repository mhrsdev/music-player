package com.mhrsdev.musicplayer.ui.player;

import java.util.Locale;

/** Pure formatting helpers for the player (locale-aware digits, so Persian shows ۰–۹). */
public class PlayerFormat {
    private PlayerFormat() {}

    /**
     * "3:07" / "1:02:09" in the locale's digits. Negative input clamps to 0. Same text as two NumberFormats, without
     * building them: this runs for every queue row bind and every second in the player (SC1).
     */
    public static String time(long ms, Locale locale) {
        return com.mhrsdev.musicplayer.ui.library.Format.duration(ms, locale);
    }

    /** "-2:09": time left in the track. */
    public static String remaining(long positionMs, long durationMs, Locale locale) {
        return "−" + time(Math.max(0L, durationMs - positionMs), locale);
    }

    /** Whole minutes, rounded up so "0:30 left" still reads as 1 min. */
    public static int minutesCeil(long ms) {
        if (ms <= 0) return 0;
        return (int) ((ms + 59_999L) / 60_000L);
    }

    /** Fraction in [0,1] of position over duration (0 when duration is unknown). */
    public static float fraction(long positionMs, long durationMs) {
        if (durationMs <= 0) return 0f;
        return Math.max(0f, Math.min(1f, positionMs / (float) durationMs));
    }
}
