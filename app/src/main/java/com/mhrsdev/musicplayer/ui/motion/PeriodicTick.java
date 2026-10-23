package com.mhrsdev.musicplayer.ui.motion;

import java.util.function.BooleanSupplier;

/**
 * A fixed-rate animation (a 15 or 30 fps decorative loop) riding the one {@link FrameDriver} Choreographer callback
 * instead of owning a Handler / {@code postOnAnimationDelayed} loop. Frames come only between {@link #start()} and
 * {@link #stop()} (or the first time the body returns false), and all periodic animations of the app share one wake-up
 * per display frame. Main thread only.
 */
public class PeriodicTick implements FrameDriver.Tick {
    private final float periodSeconds;
    private final BooleanSupplier body;
    private float accumulated;
    private boolean active;

    /** @param periodMs minimum time between body calls (0 = every display frame) */
    public PeriodicTick(long periodMs, BooleanSupplier body) {
        this.periodSeconds = periodMs / 1000f;
        this.body = body;
    }

    /** Starts ticking (no-op when already running); the body runs on the next frame. */
    public void start() {
        if (active) return;
        active = true;
        accumulated = periodSeconds;
        FrameDriver.get().add(this);
    }

    public void stop() {
        active = false;
        FrameDriver.get().remove(this);
    }

    public boolean isActive() {
        return active;
    }

    /** The body returns false to stop (e.g. the view left the screen). */
    @Override public boolean onFrame(float dtSeconds) {
        if (!active) return false;
        accumulated += dtSeconds;
        if (accumulated < periodSeconds) return true;
        accumulated = 0f;
        if (!body.getAsBoolean()) active = false;
        return active;
    }
}
