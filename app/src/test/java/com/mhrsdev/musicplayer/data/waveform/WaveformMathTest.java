package com.mhrsdev.musicplayer.data.waveform;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;

public class WaveformMathTest {
    private static final float EPS = 1e-4f;

    @Test public void fillGapsInterpolatesAndExtendsEdges() {
        float n = Float.NaN;
        float[] out = WaveformMath.fillGaps(new float[] {n, 0.2f, n, n, 0.8f, n});
        assertArrayEquals(new float[] {0.2f, 0.2f, 0.4f, 0.6f, 0.8f, 0.8f}, out, EPS);
    }

    @Test public void fillGapsAllMissingIsNull() {
        assertNull(WaveformMath.fillGaps(new float[] {Float.NaN, Float.NaN}));
        assertNull(WaveformMath.toEnvelope(new float[] {Float.NaN}));
    }

    @Test public void silenceNormalizesToZeros() {
        assertArrayEquals(new float[8], WaveformMath.normalize(new float[8]), 0f);
    }

    @Test public void dynamicsStayVisibleOnDbScale() {
        float[] rms = new float[100];
        Arrays.fill(rms, 0.5f);          // loud
        for (int i = 0; i < 20; i++) rms[i] = 0.05f;   // 20 dB quieter intro
        float[] n = WaveformMath.normalize(rms);
        assertEquals(1f, n[50], EPS);
        assertEquals(1f - 20f / WaveformMath.RANGE_DB, n[0], 0.01f);
        assertTrue(n[0] > 0.3f && n[0] < n[50]);
    }

    @Test public void veryQuietBucketsClampToZeroAndClippingToOne() {
        float[] rms = new float[100];
        Arrays.fill(rms, 1.0f);   // fully clipped square wave
        rms[3] = 1e-7f;
        float[] n = WaveformMath.normalize(rms);
        assertEquals(1f, n[0], 0f);
        assertEquals(0f, n[3], 0f);
    }

    @Test public void oneHotHitDoesNotFlattenTheRest() {
        float[] rms = new float[100];
        Arrays.fill(rms, 0.1f);
        rms[10] = 1f; // single outlier above the 95th percentile
        float[] n = WaveformMath.normalize(rms);
        assertEquals(1f, n[50], EPS);
    }

    @Test public void smoothKeepsRangeAndFlatSignals() {
        float[] flat = {0.5f, 0.5f, 0.5f};
        assertArrayEquals(flat, WaveformMath.smooth(flat), EPS);
        float[] s = WaveformMath.smooth(new float[] {0f, 1f, 0f});
        assertArrayEquals(new float[] {0.25f, 0.5f, 0.25f}, s, EPS);
        assertArrayEquals(new float[] {0.7f}, WaveformMath.smooth(new float[] {0.7f}), EPS);
    }

    @Test public void resampleDownAveragesAreaWeighted() {
        float[] src = {0f, 1f, 0f, 1f, 0f, 1f};
        assertArrayEquals(new float[] {0.5f, 0.5f, 0.5f}, WaveformMath.resample(src, 3), EPS);
        // 3 -> 2 with fractional boundaries: [0,1.5) and [1.5,3)
        assertEquals((1f * 1f + 3f * 0.5f) / 1.5f, WaveformMath.resample(new float[] {1f, 3f, 5f}, 2)[0], EPS);
        assertEquals((3f * 0.5f + 5f * 1f) / 1.5f, WaveformMath.resample(new float[] {1f, 3f, 5f}, 2)[1], EPS);
    }

    @Test public void resampleUpInterpolatesEndpointsExactly() {
        float[] out = WaveformMath.resample(new float[] {0f, 1f}, 5);
        assertArrayEquals(new float[] {0f, 0.25f, 0.5f, 0.75f, 1f}, out, EPS);
    }

    @Test public void resampleIdentityAndEdgeCases() {
        float[] src = {0.1f, 0.9f, 0.4f};
        assertArrayEquals(src, WaveformMath.resample(src, 3), EPS);
        assertEquals(0, WaveformMath.resample(src, 0).length);
        assertEquals(0, WaveformMath.resample(src, -5).length);
        assertArrayEquals(new float[4], WaveformMath.resample(new float[0], 4), 0f);
        assertArrayEquals(new float[] {0.3f, 0.3f}, WaveformMath.resample(new float[] {0.3f}, 2), EPS);
        assertArrayEquals(new float[] {0.1f}, WaveformMath.resample(new float[] {0.1f, 0.1f}, 1), EPS);
        assertEquals(1, WaveformMath.resample(new float[] {0f, 1f, 0f}, 1).length);
    }

    @Test public void resampleDownKeepsMeanForOddLengths() {
        float[] src = new float[257];
        for (int i = 0; i < src.length; i++) src[i] = i / 256f;
        float[] out = WaveformMath.resample(src, 100);
        double a = 0, b = 0;
        for (float v : src) a += v;
        for (float v : out) b += v;
        assertEquals(a / src.length, b / out.length, 1e-3);
    }

    @Test public void encodeDecodeRoundTripWithinOneStep() {
        float[] env = {0f, 0.2f, 0.5f, 1f, 1.5f, -0.3f};
        byte[] blob = WaveformMath.encode(env);
        assertEquals(env.length, blob.length);
        float[] back = WaveformMath.decode(blob);
        assertEquals(0f, back[0], 0f);
        assertEquals(0.2f, back[1], 1f / 255);
        assertEquals(1f, back[3], 0f);
        assertEquals(1f, back[4], 0f);   // clamped
        assertEquals(0f, back[5], 0f);   // clamped
    }

    @Test public void toEnvelopeStaysInRangeAndHasMasterShape() {
        float[] rms = new float[WaveformMath.MASTER_BUCKETS];
        for (int i = 0; i < rms.length; i++) rms[i] = i % 2 == 0 ? 0.3f : Float.NaN;
        float[] env = WaveformMath.toEnvelope(rms);
        assertNotNull(env);
        assertEquals(WaveformMath.MASTER_BUCKETS, env.length);
        for (float v : env) assertTrue(v >= 0f && v <= 1f);
    }
}
