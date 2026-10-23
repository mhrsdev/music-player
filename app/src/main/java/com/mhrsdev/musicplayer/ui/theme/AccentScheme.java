package com.mhrsdev.musicplayer.ui.theme;

/**
 * Artwork-derived roles (DESIGN.md 2.2): primary/onPrimary/container/onContainer/glow.
 * Tones approximate M3 tones with HSL lightness: dark 80/20/30/90, light 40/100/90/10.
 */
public record AccentScheme(int primary, int onPrimary, int container, int onContainer, int glow) {

    /** Lapis default used when there is no artwork or "Colors from artwork" is off. */
    public static AccentScheme base(boolean dark) {
        return dark
                ? new AccentScheme(0xFFA6C8FF, 0xFF00315F, 0xFF1E4878, 0xFFD4E3FF, 0xFF1E4878)
                : new AccentScheme(0xFF2F5F95, 0xFFFFFFFF, 0xFFD4E3FF, 0xFF001C3A, 0xFFD4E3FF);
    }

    public static AccentScheme fromSeed(int seed, boolean dark) {
        float[] hsl = new float[3];
        ColorMath.rgbToHsl(seed, hsl);
        float h = hsl[0];
        // Keep fidelity to the cover's hue but tame saturation so text stays calm; greys get a hint of lapis.
        float s = hsl[1] < 0.08f ? 0.18f : Math.max(0.35f, Math.min(0.80f, hsl[1]));
        if (hsl[1] < 0.08f) h = 212f;
        if (dark) {
            return new AccentScheme(
                    ColorMath.hslToRgb(h, s, 0.80f),
                    ColorMath.hslToRgb(h, s, 0.16f),
                    ColorMath.hslToRgb(h, s * 0.85f, 0.27f),
                    ColorMath.hslToRgb(h, s, 0.91f),
                    ColorMath.hslToRgb(h, s, 0.35f));
        }
        return new AccentScheme(
                ColorMath.hslToRgb(h, s, 0.36f),
                0xFFFFFFFF,
                ColorMath.hslToRgb(h, s, 0.90f),
                ColorMath.hslToRgb(h, s, 0.12f),
                ColorMath.hslToRgb(h, s, 0.82f));
    }

    public static AccentScheme lerp(AccentScheme a, AccentScheme b, float t) {
        return new AccentScheme(
                ColorMath.lerp(a.primary, b.primary, t),
                ColorMath.lerp(a.onPrimary, b.onPrimary, t),
                ColorMath.lerp(a.container, b.container, t),
                ColorMath.lerp(a.onContainer, b.onContainer, t),
                ColorMath.lerp(a.glow, b.glow, t));
    }
}
