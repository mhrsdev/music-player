package com.mhrsdev.musicplayer.ui.theme;

/** Pure color math (no Android dependencies, so it is JVM-testable). Colors are packed ARGB ints. */
public final class ColorMath {
    private ColorMath() {}

    public static int argb(int a, int r, int g, int b) {
        return (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
    }

    public static int alpha(int c) { return c >>> 24; }
    public static int red(int c) { return (c >> 16) & 0xFF; }
    public static int green(int c) { return (c >> 8) & 0xFF; }
    public static int blue(int c) { return c & 0xFF; }

    public static int withAlpha(int c, float alpha) {
        int a = Math.round(clamp01(alpha) * 255f);
        return (c & 0x00FFFFFF) | (a << 24);
    }

    /** Linear interpolation per channel, t in [0,1]. */
    public static int lerp(int from, int to, float t) {
        t = clamp01(t);
        return argb(
                Math.round(alpha(from) + (alpha(to) - alpha(from)) * t),
                Math.round(red(from) + (red(to) - red(from)) * t),
                Math.round(green(from) + (green(to) - green(from)) * t),
                Math.round(blue(from) + (blue(to) - blue(from)) * t));
    }

    /** RGB to HSL; out = {h 0..360, s 0..1, l 0..1}. */
    public static void rgbToHsl(int c, float[] out) {
        float r = red(c) / 255f, g = green(c) / 255f, b = blue(c) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float l = (max + min) / 2f, d = max - min, h, s;
        if (d == 0f) {
            h = 0f;
            s = 0f;
        } else {
            s = d / (1f - Math.abs(2f * l - 1f));
            if (max == r) h = ((g - b) / d) % 6f;
            else if (max == g) h = (b - r) / d + 2f;
            else h = (r - g) / d + 4f;
            h *= 60f;
            if (h < 0f) h += 360f;
        }
        out[0] = h;
        out[1] = clamp01(s);
        out[2] = clamp01(l);
    }

    public static int hslToRgb(float h, float s, float l) {
        h = ((h % 360f) + 360f) % 360f;
        s = clamp01(s);
        l = clamp01(l);
        float c = (1f - Math.abs(2f * l - 1f)) * s;
        float x = c * (1f - Math.abs((h / 60f) % 2f - 1f));
        float m = l - c / 2f, r, g, b;
        if (h < 60) { r = c; g = x; b = 0; }
        else if (h < 120) { r = x; g = c; b = 0; }
        else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; }
        else if (h < 300) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        return argb(255, Math.round((r + m) * 255f), Math.round((g + m) * 255f), Math.round((b + m) * 255f));
    }

    /** WCAG relative luminance, 0..1. */
    public static double luminance(int c) {
        return 0.2126 * lin(red(c)) + 0.7152 * lin(green(c)) + 0.0722 * lin(blue(c));
    }

    /** WCAG contrast ratio between two opaque colors, 1..21. */
    public static double contrast(int a, int b) {
        double la = luminance(a), lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double lin(int v) {
        double s = v / 255.0;
        return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }

    public static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
