package com.mhrsdev.musicplayer.ui.theme;

/**
 * Palette-free seed color extraction from a tiny (e.g. 32x32) ARGB pixel array.
 *
 * <p>Pixels are binned by hue (24 bins); each pixel votes with its chroma so grey, near-black and near-white pixels
 * barely count. The winning bin's weighted mean color is the seed. Falls back to the plain mean for greyscale art.
 * Runs in well under a millisecond for 1024 pixels and allocates nothing beyond the caller's scratch arrays.
 */
public final class ColorExtractor {
    static final int BINS = 24;

    private ColorExtractor() {}

    public static int extractSeed(int[] pixels, int count) {
        double[] w = new double[BINS];
        double[] rs = new double[BINS], gs = new double[BINS], bs = new double[BINS];
        float[] hsl = new float[3];
        long ar = 0, ag = 0, ab = 0;
        int n = 0;
        for (int i = 0; i < count; i++) {
            int c = pixels[i];
            if (ColorMath.alpha(c) < 128) continue;
            int r = ColorMath.red(c), g = ColorMath.green(c), b = ColorMath.blue(c);
            ar += r; ag += g; ab += b; n++;
            ColorMath.rgbToHsl(c, hsl);
            float s = hsl[1], l = hsl[2];
            if (l < 0.08f || l > 0.94f || s < 0.12f) continue;
            // Chroma-like weight; mid lightness is favoured (that is where colors read as "the color" of a cover).
            double weight = s * (1.0 - Math.abs(2.0 * l - 1.0));
            int bin = Math.min(BINS - 1, (int) (hsl[0] / (360f / BINS)));
            w[bin] += weight;
            rs[bin] += r * weight; gs[bin] += g * weight; bs[bin] += b * weight;
        }
        if (n == 0) return 0xFF5F7FA6; // lapis-ish default
        int best = -1;
        double bestScore = 0;
        for (int i = 0; i < BINS; i++) {
            // Smooth with neighbours so a hue split across two bins still wins.
            double score = w[i] + 0.5 * (w[(i + BINS - 1) % BINS] + w[(i + 1) % BINS]);
            if (score > bestScore) { bestScore = score; best = i; }
        }
        // Needs a meaningful share of colorful pixels, otherwise treat the cover as greyscale.
        if (best < 0 || w[best] < n * 0.01) {
            return ColorMath.argb(255, (int) (ar / n), (int) (ag / n), (int) (ab / n));
        }
        return ColorMath.argb(255, (int) (rs[best] / w[best]), (int) (gs[best] / w[best]), (int) (bs[best] / w[best]));
    }
}
