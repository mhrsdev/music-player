package com.mhrsdev.musicplayer.ui.widget;

import com.mhrsdev.musicplayer.ui.theme.ColorMath;

/** Deterministic placeholder gradient colors from {@code Track.placeholderSeed} (pure, JVM-testable). */
public final class PlaceholderArt {
    private PlaceholderArt() {}

    /** Two colors (top-left, bottom-right) for a seed; same seed, same colors, on every device. */
    public static int[] colors(int seed) {
        int h = mix(seed);
        float hue = (h & 0xFFFF) % 360;
        float hue2 = (hue + 28f + ((h >>> 16) & 0x1F)) % 360f;
        return new int[] {
                ColorMath.hslToRgb(hue, 0.42f, 0.42f),
                ColorMath.hslToRgb(hue2, 0.48f, 0.26f)
        };
    }

    /** The "seed color" of the placeholder, for {@code ThemeController.setFromSeedColor}. */
    public static int seedColor(int seed) {
        return colors(seed)[0];
    }

    static int mix(int x) {
        x ^= x >>> 16;
        x *= 0x7feb352d;
        x ^= x >>> 15;
        x *= 0x846ca68b;
        x ^= x >>> 16;
        return x & 0x7FFFFFFF;
    }
}
