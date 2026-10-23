package com.mhrsdev.musicplayer.ui.widget;

import androidx.annotation.Nullable;

/**
 * Pure geometry shared by the D25 seek styles (Ribbon, Pulse, Ladder, Ridge, Beads, Liquid): loudness sampling with
 * a placeholder until the envelope arrives, the magnifier falloff of the pulse dots, LED block counts and bead
 * spacing. No Android types, so it is unit-tested on the JVM.
 */
public class SeekShapes {
    private SeekShapes() {}

    /** Loudness at fraction {@code f} (0..1), linearly interpolated, clamped to 0..1; placeholder when null. */
    public static float loudnessAt(@Nullable float[] wf, float f) {
        f = clamp01(f);
        if (wf == null || wf.length == 0) return placeholder(f);
        if (wf.length == 1) return clamp01(wf[0]);
        float pos = f * (wf.length - 1);
        int i = (int) pos;
        if (i >= wf.length - 1) return clamp01(wf[wf.length - 1]);
        float t = pos - i;
        return clamp01(wf[i] + (wf[i + 1] - wf[i]) * t);
    }

    /** Max loudness over the fraction range [f0, f1] (peak-preserving for few, wide cells). */
    public static float peakIn(@Nullable float[] wf, float f0, float f1) {
        if (wf == null || wf.length == 0) return placeholder((f0 + f1) / 2f);
        int a = Math.max(0, (int) (clamp01(f0) * wf.length));
        int b = Math.min(wf.length, Math.max(a + 1, (int) Math.ceil(clamp01(f1) * wf.length)));
        float v = 0f;
        for (int j = a; j < b; j++) v = Math.max(v, wf[j]);
        return clamp01(v);
    }

    /** A calm, low, deterministic contour shown before the real envelope is ready (never reads as "real" data). */
    public static float placeholder(float f) {
        double x = f * Math.PI * 2;
        return 0.32f + 0.08f * (float) Math.sin(x * 3.0) + 0.05f * (float) Math.sin(x * 7.0 + 1.3);
    }

    /**
     * Magnifier falloff: 1 at the thumb, 0 at {@code radius} and beyond, smooth (cosine) in between.
     * Used for the pulse dots' swell and lift.
     */
    public static float magnify(float distance, float radius) {
        if (radius <= 0f) return 0f;
        float d = Math.abs(distance) / radius;
        if (d >= 1f) return 0f;
        return 0.5f + 0.5f * (float) Math.cos(d * Math.PI);
    }

    /** Lit LED blocks (1..{@code max}) for a loudness 0..1; the quietest column keeps one block so the ladder reads. */
    public static int ledBlocks(float loudness, int max) {
        if (max <= 1) return Math.max(0, max);
        int n = Math.round(clamp01(loudness) * max);
        return Math.max(1, Math.min(max, n));
    }

    /** Number of cells of width {@code cell} with {@code gap} between them that fit in {@code span}. */
    public static int cellCount(float span, float cell, float gap) {
        if (span <= 0f || cell <= 0f) return 0;
        return Math.max(0, (int) ((span + gap) / (cell + gap)));
    }

    /**
     * Bead spacing for Thread &amp; Beads: one bead a minute; 15 s for tracks under 3 min (so a short song still has
     * beads), 5 min for long mixes (over 40 min) so the thread never turns into a dotted line.
     */
    public static long beadIntervalMs(long durationMs) {
        if (durationMs <= 0) return 0;
        if (durationMs < 180_000L) return 15_000L;
        if (durationMs > 2_400_000L) return 300_000L;
        return 60_000L;
    }

    /** Linear blend of two ARGB colours, {@code t} 0 = a, 1 = b (alpha included). */
    public static int blend(int a, int b, float t) {
        t = clamp01(t);
        int aa = a >>> 24, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = b >>> 24, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16)
                | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }

    /** Same colour with alpha multiplied by {@code f}. */
    public static int withAlpha(int color, float f) {
        int a = Math.round((color >>> 24) * clamp01(f));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    public static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
