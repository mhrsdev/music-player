package com.mhrsdev.musicplayer.ui.library;

import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Locale-aware duration formatting (Persian digits in fa, DESIGN.md §11.3). Runs for every row bind, so it does no
 * {@code NumberFormat} construction (ICU object setup costs far more than the string itself on a slow phone): the
 * zero digit of the last used locale is cached and the digits are mapped by hand.
 */
public class Format {
    /** Immutable pair so concurrent callers never see a locale with another locale's digit. */
    private record Zero(Locale locale, char digit) { }

    private static volatile Zero zero = new Zero(Locale.ROOT, '0');

    private Format() { }

    public static String duration(long ms, Locale locale) {
        long s = Math.max(0, ms / 1000);
        char z = zeroDigit(locale);
        long h = s / 3600;
        StringBuilder sb = new StringBuilder(8);
        if (h > 0) {
            append(sb, h, 1, z).append(':');
            append(sb, (s % 3600) / 60, 2, z);
        } else {
            append(sb, s / 60, 1, z);
        }
        append(sb.append(':'), s % 60, 2, z);
        return sb.toString();
    }

    private static StringBuilder append(StringBuilder sb, long n, int minDigits, char z) {
        String digits = Long.toString(n);
        for (int i = digits.length(); i < minDigits; i++) sb.append(z);
        for (int i = 0; i < digits.length(); i++) sb.append((char) (z + (digits.charAt(i) - '0')));
        return sb;
    }

    private static char zeroDigit(Locale locale) {
        Zero cached = zero;
        if (cached.locale().equals(locale)) return cached.digit();
        char digit = DecimalFormatSymbols.getInstance(locale).getZeroDigit();
        zero = new Zero(locale, digit);
        return digit;
    }
}
