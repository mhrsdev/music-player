package com.mhrsdev.musicplayer.ui.library;

import static org.junit.Assert.assertEquals;

import java.text.NumberFormat;
import java.util.Locale;
import org.junit.Test;

public class FormatTest {
    /** The previous implementation (two NumberFormat objects per call): the new one must give identical text. */
    private static String reference(long ms, Locale locale) {
        long s = Math.max(0, ms / 1000);
        NumberFormat nf = NumberFormat.getIntegerInstance(locale);
        nf.setGroupingUsed(false);
        NumberFormat two = NumberFormat.getIntegerInstance(locale);
        two.setMinimumIntegerDigits(2);
        two.setGroupingUsed(false);
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0 ? nf.format(h) + ":" + two.format(m) + ":" + two.format(sec) : nf.format(m) + ":" + two.format(sec);
    }

    @Test public void matchesTheNumberFormatVersionInEveryLocale() {
        long[] samples = {0, 999, 1_000, 59_999, 60_000, 65_000, 3_599_000, 3_600_000, 3_725_000, 36_000_000, -5};
        for (Locale l : new Locale[] {Locale.US, Locale.forLanguageTag("fa"), Locale.forLanguageTag("ar"), Locale.GERMANY}) {
            for (long ms : samples) assertEquals(l + " " + ms, reference(ms, l), Format.duration(ms, l));
        }
    }

    @Test public void persianDigits() {
        assertEquals("۳:۰۷", Format.duration(187_000, Locale.forLanguageTag("fa")));
    }
}
