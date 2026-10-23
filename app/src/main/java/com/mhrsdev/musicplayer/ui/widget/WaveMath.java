package com.mhrsdev.musicplayer.ui.widget;

/** Pure math for the wavy line (seek bar, mini-player progress, nav underline). */
public final class WaveMath {
    public static final float TWO_PI = (float) (Math.PI * 2);

    private WaveMath() {}

    /** Phase (radians, [0, 2pi)) after {@code elapsedMs} for a wave travelling one wavelength per {@code periodMs}. */
    public static float phaseAt(long elapsedMs, long periodMs) {
        if (periodMs <= 0) return 0f;
        return TWO_PI * (float) (elapsedMs % periodMs) / periodMs;
    }

    /** Vertical offset of the wave at {@code x}. */
    public static float y(float x, float wavelength, float amplitude, float phase) {
        return amplitude * (float) Math.sin(TWO_PI * x / wavelength - phase);
    }

    /** Moves {@code current} towards {@code target} at a rate that crosses [0,1] in {@code durationMs}. */
    public static float approach(float current, float target, long dtMs, long durationMs) {
        if (durationMs <= 0) return target;
        float step = (float) dtMs / durationMs;
        if (current < target) return Math.min(target, current + step);
        return Math.max(target, current - step);
    }
}
