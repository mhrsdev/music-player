package com.mhrsdev.musicplayer.ui.motion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ShapeMorphTest {
    @Test public void everyShapeHasTheSamePointCountAndStaysInTheUnitBox() {
        for (ShapeMorph.Shape a : ShapeMorph.Shape.values()) {
            for (ShapeMorph.Shape b : ShapeMorph.Shape.values()) {
                ShapeMorph m = new ShapeMorph(a, b);
                assertEquals(ShapeMorph.DEFAULT_POINTS, m.pointCount());
                for (float f = -0.2f; f <= 1.2f; f += 0.1f) { // includes spring overshoot
                    for (int i = 0; i < m.pointCount(); i++) {
                        assertTrue(a + "->" + b + " x", Math.abs(m.x(i, f)) <= 1f + 1e-6f);
                        assertTrue(a + "->" + b + " y", Math.abs(m.y(i, f)) <= 1f + 1e-6f);
                    }
                }
            }
        }
    }

    @Test public void shapesAreMirrorSymmetricOnBothAxes() {
        int n = ShapeMorph.DEFAULT_POINTS;
        float[] xs = new float[n], ys = new float[n];
        for (ShapeMorph.Shape s : ShapeMorph.Shape.values()) {
            ShapeMorph.sample(s, xs, ys);
            for (int i = 0; i < n; i++) {
                int mirrorY = (n / 2 - i + n) % n; // t -> pi - t mirrors x
                int mirrorX = (n - i) % n;         // t -> -t mirrors y
                assertEquals(s + " L/R x", -xs[i], xs[mirrorY], 1e-5f);
                assertEquals(s + " L/R y", ys[i], ys[mirrorY], 1e-5f);
                assertEquals(s + " T/B x", xs[i], xs[mirrorX], 1e-5f);
                assertEquals(s + " T/B y", -ys[i], ys[mirrorX], 1e-5f);
            }
        }
    }

    @Test public void endpointsMatchTheShapesAndAxesTouchTheBox() {
        ShapeMorph m = new ShapeMorph(ShapeMorph.Shape.SQUIRCLE, ShapeMorph.Shape.CIRCLE);
        int n = m.pointCount();
        assertEquals(1f, m.x(0, 0f), 1e-6f);
        assertEquals(1f, m.y(n / 4, 1f), 1e-6f);
        int diag = n / 8;
        assertEquals(Math.pow(0.5, 0.25), m.x(diag, 0f), 1e-4); // squircle corner ~0.84
        assertEquals(Math.sqrt(0.5), m.x(diag, 1f), 1e-4);       // circle ~0.707
        // Cookie lobes reach the radius 1 at t = 0 and dip between lobes.
        float[] xs = new float[n], ys = new float[n];
        ShapeMorph.sample(ShapeMorph.Shape.COOKIE, xs, ys);
        assertEquals(1f, xs[0], 1e-6f);
        int between = n / (2 * ShapeMorph.COOKIE_LOBES);
        assertTrue(Math.hypot(xs[between], ys[between]) < 0.9);
    }
}
