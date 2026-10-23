package com.mhrsdev.musicplayer.ui.library;

import java.text.NumberFormat;
import java.util.Locale;

/** Locale-aware number/duration formatting (Persian digits in fa, DESIGN.md §11.3). */
public final class Format {
    private Format() { }

    public static String duration(long ms, Locale locale) {
        long s = Math.max(0, ms / 1000);
        NumberFormat nf = NumberFormat.getIntegerInstance(locale);
        nf.setGroupingUsed(false);
        NumberFormat two = NumberFormat.getIntegerInstance(locale);
        two.setMinimumIntegerDigits(2);
        two.setGroupingUsed(false);
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        return h > 0 ? nf.format(h) + ":" + two.format(m) + ":" + two.format(sec) : nf.format(m) + ":" + two.format(sec);
    }
}
