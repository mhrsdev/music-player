package com.mhrsdev.musicplayer.ui.player;

/**
 * Runtime contrast guard (DESIGN §2.3 step 4). Pure Java so it runs in JVM tests: given the downscaled backdrop
 * pixels, finds the smallest scrim alpha (never below the design's base alpha) so that text of the given color keeps
 * at least {@code minRatio} (WCAG 2.x) against every backdrop pixel inside a horizontal band. Runs once per track on
 * a ~20×20 bitmap, so it costs nothing per frame.
 */
public final class ContrastGuard {
    private ContrastGuard() {}

    /** WCAG relative luminance of an opaque sRGB color. */
    public static double luminance(int rgb) {
        return 0.2126 * lin((rgb >> 16) & 0xFF) + 0.7152 * lin((rgb >> 8) & 0xFF) + 0.0722 * lin(rgb & 0xFF);
    }

    private static double lin(int c) {
        double s = c / 255.0;
        return s <= 0.04045 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }

    public static double ratio(int a, int b) {
        double la = luminance(a), lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    /** {@code top} over {@code bottom} with {@code alpha} in 0..1 (sRGB "over", like the canvas does). */
    public static int blend(int top, int bottom, float alpha) {
        int r = Math.round(((top >> 16) & 0xFF) * alpha + ((bottom >> 16) & 0xFF) * (1 - alpha));
        int g = Math.round(((top >> 8) & 0xFF) * alpha + ((bottom >> 8) & 0xFF) * (1 - alpha));
        int bl = Math.round((top & 0xFF) * alpha + (bottom & 0xFF) * (1 - alpha));
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    /**
     * @param px        backdrop pixels (ARGB, alpha ignored), row-major {@code w × h}
     * @param bandTop   band start as a fraction of the height (0..1)
     * @param bandBottom band end as a fraction of the height (0..1)
     * @param scrimRgb  scrim color (alpha ignored)
     * @param baseAlpha the scrim alpha the design asks for in this band (0..1)
     * @param textArgb  text color, its alpha = the text's opacity (e.g. white .60 for inactive lyrics)
     * @param minRatio  4.5 for body text, 3.0 for large text
     * @return scrim alpha in [baseAlpha, 1]
     */
    public static float requiredScrimAlpha(int[] px, int w, int h, float bandTop, float bandBottom, int scrimRgb,
                                           float baseAlpha, int textArgb, double minRatio) {
        if (px == null || w <= 0 || h <= 0 || px.length < w * h) return baseAlpha;
        int y0 = Math.max(0, Math.min(h - 1, (int) Math.floor(bandTop * h)));
        int y1 = Math.max(y0 + 1, Math.min(h, (int) Math.ceil(bandBottom * h)));
        float textAlpha = ((textArgb >>> 24) & 0xFF) / 255f;
        for (float a = baseAlpha; a < 1f; a += 0.02f) {
            if (passes(px, w, y0, y1, scrimRgb, a, textArgb, textAlpha, minRatio)) return a;
        }
        return 1f;
    }

    private static boolean passes(int[] px, int w, int y0, int y1, int scrim, float a, int text, float textAlpha,
                                  double minRatio) {
        for (int y = y0; y < y1; y++) {
            for (int x = 0; x < w; x++) {
                int bg = blend(scrim, px[y * w + x], a);
                int fg = blend(text, bg, textAlpha);
                if (ratio(fg, bg) < minRatio) return false;
            }
        }
        return true;
    }
}
