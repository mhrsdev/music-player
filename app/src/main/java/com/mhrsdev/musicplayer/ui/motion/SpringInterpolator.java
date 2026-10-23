package com.mhrsdev.musicplayer.ui.motion;

import android.animation.TimeInterpolator;

/**
 * A {@link Spring} preset as a TimeInterpolator, for framework animations that need one (Transitions, Animator
 * XML, ViewPropertyAnimator). Pair it with {@link #durationMs()}: the time the spring needs to settle, so the
 * curve ends exactly at rest. The value is evaluated in closed form per call; no allocation.
 */
public final class SpringInterpolator implements TimeInterpolator {
    private final Spring scratch;
    private final float durationSec;

    public SpringInterpolator(Spring.Preset preset) {
        scratch = new Spring(preset);
        Spring probe = new Spring(preset);
        probe.snapTo(0f);
        probe.setTarget(1f);
        int frames = 0;
        while (probe.step(1f / 240f) && frames < 2400) frames++;
        durationSec = Math.max(frames, 1) / 240f;
    }

    /** Settle time in ms (unscaled; the framework applies the animator duration scale). */
    public long durationMs() {
        return Math.round(durationSec * 1000f);
    }

    @Override public float getInterpolation(float input) {
        if (input >= 1f) return 1f;
        scratch.snapTo(0f);
        scratch.setTarget(1f);
        scratch.step(input * durationSec);
        return scratch.value();
    }
}
