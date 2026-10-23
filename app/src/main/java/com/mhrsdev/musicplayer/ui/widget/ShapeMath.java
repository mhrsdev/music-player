package com.mhrsdev.musicplayer.ui.widget;

/** Pure polar shape math for the play button morph: squircle (superellipse n=4) to a 10-lobe cookie. */
public final class ShapeMath {
    public static final int COOKIE_LOBES = 10;
    static final float COOKIE_DEPTH = 0.075f;
    static final double SQUIRCLE_N = 4.0;

    private ShapeMath() {}

    /** Unit-radius distance of the squircle edge at angle {@code t} (radians). 1 on axes, ~1.19 on diagonals
     * (the point (0.84, 0.84)), so it fills a square view like a 30%-corner squircle. */
    public static float squircle(double t) {
        double c = Math.abs(Math.cos(t)), s = Math.abs(Math.sin(t));
        return (float) Math.pow(Math.pow(c, SQUIRCLE_N) + Math.pow(s, SQUIRCLE_N), -1.0 / SQUIRCLE_N);
    }

    /** Unit-radius distance of the cookie edge at angle {@code t}; lobe tips at 1. */
    public static float cookie(double t) {
        return (float) ((1.0 + COOKIE_DEPTH * Math.cos(COOKIE_LOBES * t)) / (1.0 + COOKIE_DEPTH));
    }

    /** Morph between squircle (m=0) and cookie (m=1); m may overshoot slightly for spring feel. */
    public static float radius(double t, float m) {
        float a = squircle(t), b = cookie(t);
        return a + (b - a) * m;
    }
}
