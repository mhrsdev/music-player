package com.mhrsdev.musicplayer.ui.motion;

import android.graphics.Path;

/**
 * Mowj shape language as point outlines in the unit box [-1, 1] x [-1, 1], all sampled with the same parameter
 * t = 2 pi i / n (i = 0 is the right-hand edge middle, going clockwise on screen), so any two shapes morph point by
 * point. Shapes are mirror-symmetric on both axes and never leave the unit box, so a morph never clips.
 * Paths are rebuilt into a caller-owned {@link Path} with quadratic smoothing through the midpoints: no allocation.
 */
public class ShapeMorph {
    /** The Mowj shape set. */
    public enum Shape { CIRCLE, SQUIRCLE, ROUNDED_SQUARE, COOKIE, PILL }

    /** Point count used for buttons: divisible by 8 so every shape has samples on both axes and both diagonals. */
    public static final int DEFAULT_POINTS = 96;
    static final double SQUIRCLE_N = 4.0;
    static final double ROUNDED_SQUARE_N = 7.0;
    static final int COOKIE_LOBES = 8;
    static final double COOKIE_DEPTH = 0.06;
    static final double PILL_HALF_HEIGHT = 0.5;

    private final float[] ax, ay, bx, by;
    private final int n;

    public ShapeMorph(Shape from, Shape to) {
        this(from, to, DEFAULT_POINTS);
    }

    public ShapeMorph(Shape from, Shape to, int points) {
        if (points < 8 || points % 4 != 0) throw new IllegalArgumentException("points must be >= 8 and divisible by 4");
        n = points;
        ax = new float[n]; ay = new float[n]; bx = new float[n]; by = new float[n];
        sample(from, ax, ay);
        sample(to, bx, by);
    }

    public int pointCount() { return n; }

    /** Fills {@code xs}/{@code ys} (length n) with {@code shape}'s outline. */
    public static void sample(Shape shape, float[] xs, float[] ys) {
        int n = xs.length;
        for (int i = 0; i < n; i++) {
            double t = 2.0 * Math.PI * i / n;
            double c = Math.cos(t), s = Math.sin(t);
            // Exact zeros on the axes keep the mirror symmetry bit-exact.
            if (i * 4 % n == 0) { c = Math.rint(c); s = Math.rint(s); }
            double x, y;
            switch (shape) {
                case SQUIRCLE: x = superX(c, SQUIRCLE_N); y = superX(s, SQUIRCLE_N); break;
                case ROUNDED_SQUARE: x = superX(c, ROUNDED_SQUARE_N); y = superX(s, ROUNDED_SQUARE_N); break;
                case COOKIE: {
                    double r = (1.0 + COOKIE_DEPTH * Math.cos(COOKIE_LOBES * t)) / (1.0 + COOKIE_DEPTH);
                    x = r * c; y = r * s; break;
                }
                case PILL: {
                    double h = PILL_HALF_HEIGHT;
                    x = Math.signum(c) * (1.0 - h) + h * c;
                    y = h * s; break;
                }
                case CIRCLE:
                default: x = c; y = s; break;
            }
            xs[i] = (float) x;
            ys[i] = (float) y;
        }
    }

    /** Superellipse coordinate: sign(c) |c|^(2/n). */
    private static double superX(double c, double n) {
        return Math.signum(c) * Math.pow(Math.abs(c), 2.0 / n);
    }

    /** Point i of the morph at fraction {@code f} (clamped to 0..1), unit box. */
    public float x(int i, float f) {
        f = clamp(f);
        return ax[i] + (bx[i] - ax[i]) * f;
    }

    public float y(int i, float f) {
        f = clamp(f);
        return ay[i] + (by[i] - ay[i]) * f;
    }

    /**
     * Writes the outline at fraction {@code f} (0 = from, 1 = to; clamped so a spring overshoot never distorts the
     * silhouette) centred on (cx, cy) with half-sizes rx / ry, into {@code out}.
     */
    public void toPath(Path out, float f, float cx, float cy, float rx, float ry) {
        out.rewind();
        f = clamp(f);
        float px = x(n - 1, f), py = y(n - 1, f);
        float qx = x(0, f), qy = y(0, f);
        out.moveTo(cx + rx * (px + qx) / 2f, cy + ry * (py + qy) / 2f);
        for (int i = 0; i < n; i++) {
            float x = x(i, f), y = y(i, f);
            int j = i + 1 == n ? 0 : i + 1;
            float nx = x(j, f), ny = y(j, f);
            out.quadTo(cx + rx * x, cy + ry * y, cx + rx * (x + nx) / 2f, cy + ry * (y + ny) / 2f);
        }
        out.close();
    }

    private static float clamp(float f) {
        return f < 0f ? 0f : (f > 1f ? 1f : f);
    }
}
