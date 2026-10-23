package com.mhrsdev.musicplayer.ui.widget.cover;

import java.text.Normalizer;
import java.util.Locale;

/**
 * The generated covers' only source of randomness: SplitMix64 (Steele, Lea, Flood 2014) seeded from a stable 64-bit
 * FNV-1a hash of normalized text. Pure Java, no {@code Math.random}, no {@code String.hashCode}: the same input gives
 * the same sequence on every device, JVM and app version.
 */
public class CoverRandom {
    private static final long GOLDEN = 0x9E3779B97F4A7C15L;
    private long state;

    public CoverRandom(long seed) {
        state = seed;
    }

    public long nextLong() {
        state += GOLDEN;
        return mix(state);
    }

    /** Uniform in [0, 1) with 24 bits of precision (exactly representable as a float). */
    public float nextFloat() {
        return (nextLong() >>> 40) * (1f / (1 << 24));
    }

    /** Uniform in [lo, hi). */
    public float range(float lo, float hi) {
        return lo + (hi - lo) * nextFloat();
    }

    /** Uniform in [0, n), n > 0 (no modulo bias worth caring about for n << 2^31). */
    public int nextInt(int n) {
        return (int) ((nextLong() >>> 33) % n);
    }

    public boolean chance(float p) {
        return nextFloat() < p;
    }

    /** SplitMix64 finalizer (also a good 64-bit avalanche mix on its own). */
    public static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Stable 64-bit hash: FNV-1a over the UTF-16 units of {@link #normalize}d text, then {@link #mix}. */
    public static long hash(CharSequence text) {
        String s = normalize(text);
        long h = 0xCBF29CE484222325L;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            h ^= c & 0xFF;
            h *= 0x100000001B3L;
            h ^= c >>> 8;
            h *= 0x100000001B3L;
        }
        return mix(h);
    }

    /**
     * Folds the spelling differences that should not change a cover: Unicode compatibility forms (NFKC), case,
     * Arabic vs Persian yeh/kaf, tatweel, and runs of whitespace.
     */
    public static String normalize(CharSequence text) {
        if (text == null) return "";
        String s = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        StringBuilder b = new StringBuilder(s.length());
        boolean space = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == 'ي' || c == 'ى') c = 'ی'; // Arabic yeh / alef maksura -> Persian yeh
            else if (c == 'ك') c = 'ک';               // Arabic kaf -> Persian keheh
            else if (c == 'ـ') continue;                   // tatweel
            if (Character.isWhitespace(c)) {
                space = b.length() > 0;
                continue;
            }
            if (space) b.append(' ');
            space = false;
            b.append(c);
        }
        return b.toString();
    }
}
