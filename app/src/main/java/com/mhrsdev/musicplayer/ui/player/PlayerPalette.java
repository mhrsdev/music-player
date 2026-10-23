package com.mhrsdev.musicplayer.ui.player;

import android.content.Context;

import androidx.core.graphics.ColorUtils;

/**
 * Artwork-derived roles (DESIGN §2.2) computed from a track's placeholder seed until T2's palette extraction from
 * real artwork lands. Deterministic, allocation-free after construction. Also hosts the motion gate.
 */
public final class PlayerPalette {
    public final int primary, onPrimary, container, onContainer, glow, deep;

    private PlayerPalette(float hue) {
        primary = ColorUtils.HSLToColor(new float[]{hue, 0.78f, 0.80f});
        onPrimary = ColorUtils.HSLToColor(new float[]{hue, 0.90f, 0.14f});
        container = ColorUtils.HSLToColor(new float[]{hue, 0.50f, 0.25f});
        onContainer = ColorUtils.HSLToColor(new float[]{hue, 0.90f, 0.90f});
        glow = ColorUtils.HSLToColor(new float[]{hue, 0.60f, 0.42f});
        deep = ColorUtils.HSLToColor(new float[]{(hue + 20f) % 360f, 0.55f, 0.08f});
    }

    /** From a color extracted from real artwork (T2's ColorExtractor seed): keeps the cover's hue. */
    public static PlayerPalette fromColor(int argb) {
        float[] hsl = new float[3];
        ColorUtils.colorToHSL(argb, hsl);
        return new PlayerPalette(hsl[1] < 0.08f ? 212f : hsl[0]);
    }

    public static PlayerPalette forSeed(int seed) {
        // Lapis-leaning hue spread: avoids muddy yellows that read poorly at night.
        float hue = ((seed & 0x7fffffff) % 300 + 180) % 360;
        return new PlayerPalette(hue);
    }

    /** False under reduce-motion (system or app setting) or battery saver: static frames, no rotation/drift. */
    public static boolean motionAllowed(Context c) {
        return com.mhrsdev.musicplayer.ui.theme.MotionPolicy.decorativeMotionEnabled(c);
    }
}
