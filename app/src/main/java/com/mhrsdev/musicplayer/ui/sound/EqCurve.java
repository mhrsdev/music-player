package com.mhrsdev.musicplayer.ui.sound;

import java.text.NumberFormat;
import java.util.Locale;

/** Pure maths and number formatting of {@link EqualizerView}: response curve, level/position mapping, labels. */
final class EqCurve {
    private EqCurve() {}

    /**
     * {@code count} samples of the smooth curve through the points ({@code xs} ascending), x spread evenly from the
     * first to the last point. Monotone cubic (Fritsch-Carlson): it passes through every point and never overshoots
     * between two neighbours, so the drawn response does not invent boosts that the bands do not have.
     */
    static float[] sample(float[] xs, float[] ys, int count) {
        float[] out = new float[count];
        int n = xs.length;
        if (n == 0 || count == 0) return out;
        if (n == 1 || count == 1) {
            java.util.Arrays.fill(out, ys[0]);
            return out;
        }
        float[] d = new float[n - 1];
        for (int i = 0; i < n - 1; i++) d[i] = (ys[i + 1] - ys[i]) / (xs[i + 1] - xs[i]);
        float[] m = new float[n];
        m[0] = d[0];
        m[n - 1] = d[n - 2];
        for (int i = 1; i < n - 1; i++) m[i] = d[i - 1] * d[i] <= 0 ? 0 : (d[i - 1] + d[i]) / 2f;
        for (int i = 0; i < n - 1; i++) {
            if (d[i] == 0) {
                m[i] = 0;
                m[i + 1] = 0;
                continue;
            }
            float a = m[i] / d[i];
            float b = m[i + 1] / d[i];
            float s = a * a + b * b;
            if (s > 9f) {
                float t = 3f / (float) Math.sqrt(s);
                m[i] = t * a * d[i];
                m[i + 1] = t * b * d[i];
            }
        }
        int seg = 0;
        for (int k = 0; k < count; k++) {
            float x = xs[0] + (xs[n - 1] - xs[0]) * k / (count - 1);
            while (seg < n - 2 && x > xs[seg + 1]) seg++;
            float h = xs[seg + 1] - xs[seg];
            float t = (x - xs[seg]) / h;
            float t2 = t * t;
            float t3 = t2 * t;
            out[k] = (2 * t3 - 3 * t2 + 1) * ys[seg] + (t3 - 2 * t2 + t) * h * m[seg]
                    + (-2 * t3 + 3 * t2) * ys[seg + 1] + (t3 - t2) * h * m[seg + 1];
        }
        return out;
    }

    /** y in [top, bottom] for a level in [minMb, maxMb] (max at the top). */
    static float yFor(int levelMb, int minMb, int maxMb, float top, float bottom) {
        if (maxMb <= minMb) return (top + bottom) / 2f;
        float frac = (levelMb - minMb) / (float) (maxMb - minMb);
        return bottom - frac * (bottom - top);
    }

    /** Level for a touch at {@code y}, clamped to the range and snapped to {@code stepMb}, with 0 as a magnet. */
    static int levelFor(float y, int minMb, int maxMb, float top, float bottom, int stepMb) {
        float frac = bottom <= top ? 0.5f : (bottom - y) / (bottom - top);
        frac = Math.max(0f, Math.min(1f, frac));
        int raw = Math.round(minMb + frac * (maxMb - minMb));
        int snapped = Math.round(raw / (float) stepMb) * stepMb;
        return Math.max(minMb, Math.min(maxMb, snapped));
    }

    /** 60 -> "60", 3600 -> "3.6k", 14000 -> "14k" (digits of {@code locale}). */
    static String formatHz(int hz, Locale locale) {
        NumberFormat nf = NumberFormat.getNumberInstance(locale);
        nf.setMaximumFractionDigits(1);
        nf.setGroupingUsed(false);
        return hz < 1000 ? nf.format(hz) : nf.format(hz / 1000.0) + "k";
    }

    /** Millibel -> "+3", "-1.5", "0" (decibel, one decimal at most). */
    static String formatDb(int mb, Locale locale) {
        NumberFormat nf = NumberFormat.getNumberInstance(locale);
        nf.setMaximumFractionDigits(1);
        nf.setGroupingUsed(false);
        String body = nf.format(Math.abs(mb) / 100.0);
        return mb > 0 ? "+" + body : mb < 0 ? "−" + body : body;
    }
}
