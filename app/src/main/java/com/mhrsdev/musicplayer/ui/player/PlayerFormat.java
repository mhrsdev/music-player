package com.mhrsdev.musicplayer.ui.player;

import java.text.NumberFormat;
import java.util.Locale;

/** Pure formatting helpers for the player (locale-aware digits, so Persian shows ۰–۹). */
public final class PlayerFormat {
    private PlayerFormat() {}

    /** "3:07" / "1:02:09" in the locale's digits. Negative input clamps to 0. */
    public static String time(long ms, Locale locale) {
        long totalSec = Math.max(0L, ms) / 1000L;
        long h = totalSec / 3600, m = (totalSec % 3600) / 60, s = totalSec % 60;
        NumberFormat nf = NumberFormat.getIntegerInstance(locale);
        nf.setGroupingUsed(false);
        NumberFormat two = NumberFormat.getIntegerInstance(locale);
        two.setGroupingUsed(false);
        two.setMinimumIntegerDigits(2);
        if (h > 0) return nf.format(h) + ":" + two.format(m) + ":" + two.format(s);
        return nf.format(m) + ":" + two.format(s);
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
