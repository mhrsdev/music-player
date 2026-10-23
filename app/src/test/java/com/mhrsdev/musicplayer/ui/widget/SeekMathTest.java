package com.mhrsdev.musicplayer.ui.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Locale;

public class SeekMathTest {
    @Test public void positionAndXRoundTrip() {
        long d = 200_000;
        float x = SeekMath.xFor(50_000, d, 10f, 410f);
        assertEquals(110f, x, 0.01f);
        assertEquals(50_000, SeekMath.positionFor(x, 10f, 410f, d));
    }

    @Test public void positionClampsOutsideTrack() {
        assertEquals(0, SeekMath.positionFor(-50f, 0f, 100f, 1000));
        assertEquals(1000, SeekMath.positionFor(500f, 0f, 100f, 1000));
        assertEquals(0, SeekMath.positionFor(50f, 0f, 100f, 0));
    }

    @Test public void fractionHandlesUnknownDuration() {
        assertEquals(0f, SeekMath.fraction(10, 0), 0f);
        assertEquals(1f, SeekMath.fraction(2000, 1000), 0f);
    }

    @Test public void stepClamps() {
        assertEquals(0, SeekMath.step(4000, -10_000, 60_000));
        assertEquals(60_000, SeekMath.step(55_000, 10_000, 60_000));
    }

    @Test public void formatsTime() {
        assertEquals("3:07", SeekMath.formatTime(187_400, Locale.US));
        assertEquals("1:02:03", SeekMath.formatTime(3_723_000, Locale.US));
        assertEquals("۳:۰۷", SeekMath.formatTime(187_400, Locale.forLanguageTag("fa")));
    }

    @Test public void waveMath() {
        assertEquals(0f, WaveMath.phaseAt(0, 1600), 1e-6f);
        assertEquals((float) Math.PI, WaveMath.phaseAt(800, 1600), 1e-5f);
        assertEquals(WaveMath.phaseAt(400, 1600), WaveMath.phaseAt(2000, 1600), 1e-6f);
        for (float x = 0; x < 200; x += 3.3f) {
            float y = WaveMath.y(x, 26f, 3f, 1.1f);
            assertTrue(Math.abs(y) <= 3f + 1e-4f);
            assertEquals(y, WaveMath.y(x + 26f, 26f, 3f, 1.1f), 1e-3f);
        }
        float a = 1f;
        for (int i = 0; i < 10; i++) a = WaveMath.approach(a, 0f, 33, 300);
        assertEquals(0f, a, 0f);
        assertEquals(0.5f, WaveMath.approach(0f, 1f, 150, 300), 1e-6f);
    }

    @Test public void shapeMorphEndpoints() {
        assertEquals(1f, ShapeMath.squircle(0), 1e-6f);
        // Superellipse: corners reach out to (0.84, 0.84), i.e. polar radius ~1.19, still inside the square bounds.
        assertEquals(1.189f, ShapeMath.squircle(Math.PI / 4), 0.01f);
        assertEquals(1f, ShapeMath.cookie(0), 1e-6f);
        assertEquals(ShapeMath.squircle(0.3), ShapeMath.radius(0.3, 0f), 1e-6f);
        assertEquals(ShapeMath.cookie(0.3), ShapeMath.radius(0.3, 1f), 1e-6f);
    }

    @Test public void barsResampleKeepsPeaks() {
        float[] wf = {0f, 1f, 0f, 0f, 0.5f, 0f};
        float[] out = BarsSeekView.resample(wf, 3);
        assertEquals(1f, out[0], 0f);
        assertEquals(0.12f, out[1], 0f);
        assertEquals(0.5f, out[2], 0f);
        assertEquals(10, BarsSeekView.resample(null, 10).length);
    }

    @Test public void sampleSizeKeepsTargetResolution() {
        assertEquals(1, ArtworkLoader.sampleSize(100, 100, 128));
        assertEquals(4, ArtworkLoader.sampleSize(1200, 1000, 156));
        assertEquals(8, ArtworkLoader.sampleSize(3000, 3000, 300));
    }

    @Test public void backdropBlurKeepsFlatColorAndSmoothsEdges() {
        int[] px = new int[64];
        java.util.Arrays.fill(px, 0xFF336699);
        BackdropBlur.blur(px, 8, 8, 2);
        for (int c : px) assertEquals(0xFF336699, c);
        int[] edge = new int[64];
        for (int i = 0; i < edge.length; i++) edge[i] = (i % 8) < 4 ? 0xFF000000 : 0xFFFFFFFF;
        BackdropBlur.blur(edge, 8, 8, 1);
        int mid = edge[3] & 0xFF;
        assertTrue(mid > 0 && mid < 255);
    }

    @Test public void placeholderIsDeterministic() {
        int[] a = PlaceholderArt.colors(42), b = PlaceholderArt.colors(42);
        assertEquals(a[0], b[0]);
        assertEquals(a[1], b[1]);
    }
}
