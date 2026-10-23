package com.mhrsdev.musicplayer.data.waveform;

import java.util.Arrays;

/** Pure-Java envelope maths (no Android): gap filling, smoothing, perceptual normalisation, resampling, byte codec. */
public final class WaveformMath {
    /** Length of the master envelope stored per track; any {@code bars} count is resampled from it. */
    public static final int MASTER_BUCKETS = 256;
    /** Dynamic range shown, in dB below the track's loud reference level. */
    static final float RANGE_DB = 36f;
    /** Loud reference = this percentile of the bucket levels, so one clipped hit does not flatten the rest. */
    static final float REFERENCE_PERCENTILE = 0.95f;
    /** Below this RMS (full scale = 1) the track counts as digital silence. */
    static final float SILENCE_RMS = 1e-4f;

    private WaveformMath() { }

    /** Raw per-bucket RMS (NaN = no data) to the final master envelope in [0, 1]; null if nothing was measured. */
    public static float[] toEnvelope(float[] rms) {
        float[] filled = fillGaps(rms);
        return filled == null ? null : smooth(normalize(filled));
    }

    /** Replaces NaN buckets by linear interpolation of the nearest measured neighbours; null if all are NaN. */
    public static float[] fillGaps(float[] in) {
        int first = -1;
        for (int i = 0; i < in.length && first < 0; i++) if (!Float.isNaN(in[i])) first = i;
        if (first < 0) return null;
        float[] out = in.clone();
        int prev = first;
        for (int i = 0; i < first; i++) out[i] = in[first];
        for (int i = first + 1; i < in.length; i++) {
            if (Float.isNaN(in[i])) continue;
            for (int g = prev + 1; g < i; g++) out[g] = in[prev] + (in[i] - in[prev]) * (g - prev) / (i - prev);
            prev = i;
        }
        for (int i = prev + 1; i < in.length; i++) out[i] = in[prev];
        return out;
    }

    /**
     * RMS to [0, 1] on a dB scale (perceptual loudness): the loud reference maps to 1, {@link #RANGE_DB} below it to 0,
     * so quiet intros and loud choruses are both visible. Digital silence maps to all zeros.
     */
    public static float[] normalize(float[] rms) {
        float[] sorted = rms.clone();
        Arrays.sort(sorted);
        float ref = sorted[Math.min(sorted.length - 1, (int) (REFERENCE_PERCENTILE * sorted.length))];
        float[] out = new float[rms.length];
        if (ref < SILENCE_RMS) return out;
        for (int i = 0; i < rms.length; i++) {
            double db = 20.0 * Math.log10(Math.max(rms[i], 1e-9f) / ref);
            out[i] = (float) Math.max(0.0, Math.min(1.0, 1.0 + db / RANGE_DB));
        }
        return out;
    }

    /** Light [1 2 1]/4 smoothing; edges keep their own value as neighbour. Output stays within [0, 1]. */
    public static float[] smooth(float[] in) {
        float[] out = new float[in.length];
        for (int i = 0; i < in.length; i++) {
            float l = in[Math.max(0, i - 1)], r = in[Math.min(in.length - 1, i + 1)];
            out[i] = 0.25f * l + 0.5f * in[i] + 0.25f * r;
        }
        return out;
    }

    /**
     * Resamples to exactly {@code n} values: area average when shrinking, linear interpolation when growing.
     * An empty source gives zeros; {@code n <= 0} gives an empty array.
     */
    public static float[] resample(float[] src, int n) {
        float[] out = new float[Math.max(n, 0)];
        if (src.length == 0 || n <= 0) return out;
        if (src.length == 1) { Arrays.fill(out, src[0]); return out; }
        if (n >= src.length) {
            for (int i = 0; i < n; i++) {
                float pos = n == 1 ? 0f : (float) i * (src.length - 1) / (n - 1);
                int lo = (int) pos;
                int hi = Math.min(lo + 1, src.length - 1);
                out[i] = src[lo] + (src[hi] - src[lo]) * (pos - lo);
            }
            return out;
        }
        double step = (double) src.length / n;
        for (int i = 0; i < n; i++) {
            double from = i * step, to = from + step, sum = 0;
            for (int k = (int) from; k < src.length && k < to; k++) {
                sum += src[k] * (Math.min(to, k + 1) - Math.max(from, k));
            }
            out[i] = (float) (sum / step);
        }
        return out;
    }

    /** One byte per bar, {@code round(v * 255)}; values are clamped to [0, 1]. */
    public static byte[] encode(float[] env) {
        byte[] out = new byte[env.length];
        for (int i = 0; i < env.length; i++) out[i] = (byte) Math.round(Math.max(0f, Math.min(1f, env[i])) * 255f);
        return out;
    }

    public static float[] decode(byte[] blob) {
        float[] out = new float[blob.length];
        for (int i = 0; i < blob.length; i++) out[i] = (blob[i] & 0xFF) / 255f;
        return out;
    }
}
