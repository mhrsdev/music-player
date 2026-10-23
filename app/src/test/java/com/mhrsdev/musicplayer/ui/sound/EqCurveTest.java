package com.mhrsdev.musicplayer.ui.sound;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Locale;
import org.junit.Test;

public class EqCurveTest {
    @Test public void curvePassesThroughPointsAndDoesNotOvershoot() {
        float[] xs = {0, 1, 2, 3, 4};
        float[] ys = {0, 5, 5, -3, 0};
        float[] out = EqCurve.sample(xs, ys, 5);
        for (int i = 0; i < 5; i++) assertEquals(ys[i], out[i], 1e-4f);
        float[] dense = EqCurve.sample(xs, ys, 81);
        for (float v : dense) assertTrue(v <= 5.0001f && v >= -3.0001f);
        assertEquals(5f, dense[30], 1e-3f); // flat top between two equal points stays flat
    }

    @Test public void degenerateInputs() {
        assertEquals(0, EqCurve.sample(new float[0], new float[0], 0).length);
        assertEquals(7f, EqCurve.sample(new float[] {1}, new float[] {7}, 3)[2], 0f);
        assertEquals(1f, EqCurve.sample(new float[] {0, 1}, new float[] {0, 2}, 3)[1], 1e-4f);
    }

    @Test public void levelAndYAreInverse() {
        assertEquals(100f, EqCurve.yFor(1500, -1500, 1500, 100f, 300f), 1e-4f);
        assertEquals(300f, EqCurve.yFor(-1500, -1500, 1500, 100f, 300f), 1e-4f);
        assertEquals(200f, EqCurve.yFor(0, -1500, 1500, 100f, 300f), 1e-4f);
        assertEquals(0, EqCurve.levelFor(201f, -1500, 1500, 100f, 300f, 50)); // snaps to the 50 mB grid
        assertEquals(1500, EqCurve.levelFor(-50f, -1500, 1500, 100f, 300f, 50)); // above the track: clamped
        assertEquals(-1500, EqCurve.levelFor(999f, -1500, 1500, 100f, 300f, 50));
        assertEquals(750, EqCurve.levelFor(150f, -1500, 1500, 100f, 300f, 50));
    }

    @Test public void labels() {
        assertEquals("60", EqCurve.formatHz(60, Locale.US));
        assertEquals("3.6k", EqCurve.formatHz(3600, Locale.US));
        assertEquals("14k", EqCurve.formatHz(14000, Locale.US));
        assertEquals("+3", EqCurve.formatDb(300, Locale.US));
        assertEquals("−1.5", EqCurve.formatDb(-150, Locale.US));
        assertEquals("0", EqCurve.formatDb(0, Locale.US));
    }
}
