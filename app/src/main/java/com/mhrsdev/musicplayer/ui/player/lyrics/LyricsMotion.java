package com.mhrsdev.musicplayer.ui.player.lyrics;

import android.view.animation.Interpolator;

/**
 * Small, allocation-free motion math for the lyrics (pure, JVM-tested): a lightly under-damped spring curve for line
 * emphasis and scrolling, and the distance → alpha / blur falloff. Local on purpose: the lyrics only need a fixed
 * curve, not a physics engine.
 */
public final class LyricsMotion {
    private LyricsMotion() { }

    /** Spring step response (damping 0.82, ~1 overshoot of 1.5 %), normalised so f(0)=0 and f(1)=1. */
    public static final Interpolator SPRING = LyricsMotion::spring;

    static float spring(float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        double zeta = 0.82, w = 9.0, wd = w * Math.sqrt(1 - zeta * zeta);
        double v = 1 - Math.exp(-zeta * w * t) * (Math.cos(wd * t) + zeta * w / wd * Math.sin(wd * t));
        double end = 1 - Math.exp(-zeta * w) * (Math.cos(wd) + zeta * w / wd * Math.sin(wd));
        return (float) (v / end);
    }

    /** Smooth 0→1→0 bump used for the sung word's lift and glow. */
    public static float bell(float p) {
        if (p <= 0f || p >= 1f) return 0f;
        return (float) Math.sin(Math.PI * Math.min(1f, p * 1.15f));
    }

    public static float easeOutCubic(float t) {
        float u = 1f - Math.max(0f, Math.min(1f, t));
        return 1f - u * u * u;
    }

    /** Text alpha of a line {@code distance} lines away from the active one (0 = active). */
    public static float alphaForDistance(float distance, boolean past, boolean blur) {
        float d = Math.abs(distance);
        if (d <= 0f) return 1f;
        float near = past ? 0.52f : 0.60f;
        // with blur the depth comes mostly from the blur, so alpha falls off more gently
        float step = blur ? 0.06f : 0.09f;
        float a = d <= 1f ? 1f + (near - 1f) * d : near - step * (d - 1f);
        return Math.max(0.35f, Math.min(1f, a));
    }

    /** Blur radius in px for a line {@code distance} lines away: 0 for the active line, growing to {@code maxPx}. */
    public static float blurForDistance(float distance, float maxPx) {
        float d = Math.abs(distance);
        if (d <= 0.05f) return 0f;
        return Math.min(maxPx, maxPx * (0.32f * d + 0.04f * d * d));
    }

    /** Scale of a line: 1 when active, 0.94 otherwise; {@code emphasis} 0..1. */
    public static float scaleFor(float emphasis) {
        return 0.94f + 0.06f * Math.max(0f, Math.min(1f, emphasis));
    }
}
