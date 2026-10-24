package com.mhrsdev.musicplayer.ui.motion;

import java.util.function.BooleanSupplier;

/**
 * A fixed-rate animation (a 15 or 30 fps decorative loop) riding the one {@link FrameDriver} Choreographer callback
 * instead of owning a Handler / {@code postOnAnimationDelayed} loop. Frames come only between {@link #start()} and
 * {@link #stop()} (or the first time the body returns false), and all periodic animations of the app share one wake-up
 * per display frame. Main thread only.
 *
 * <p>A loop with a period runs only on the driver's shared decorative beat ({@link FrameDriver#isBeat}), so every
 * decorative loop on screen invalidates on the same display frame (one rendered frame, not one per loop phase), and
 * the driver may sleep between beats. A 33 ms loop runs on every beat, a 66 ms loop on every second one. Period 0 =
 * every display frame (e.g. a spinner), which keeps the driver awake.
 */
public class PeriodicTick implements FrameDriver.Tick {
    private final long periodNanos;
    private final BooleanSupplier body;
    private long lastRunNanos;
    private boolean active;

    /** @param periodMs minimum time between body calls (0 = every display frame) */
    public PeriodicTick(long periodMs, BooleanSupplier body) {
        this.periodNanos = periodMs * 1_000_000L;
        this.body = body;
    }

    /** Starts ticking (no-op when already running); the body runs on the next beat (next frame for period 0). */
    public void start() {
        if (active) return;
        active = true;
        lastRunNanos = 0;
        FrameDriver.get().add(this);
    }

    public void stop() {
        active = false;
        FrameDriver.get().remove(this);
    }

    public boolean isActive() {
        return active;
    }

    @Override public boolean everyFrame() {
        return periodNanos == 0;
    }

    /** The body returns false to stop (e.g. the view left the screen). */
    @Override public boolean onFrame(float dtSeconds) {
        if (!active) return false;
        if (periodNanos > 0) {
            FrameDriver d = FrameDriver.get();
            if (!d.isBeat()) return true;
            long now = d.frameTimeNanos();
            if (lastRunNanos != 0 && !FrameDriver.due(now - lastRunNanos, periodNanos)) return true;
            lastRunNanos = now;
        }
        if (!body.getAsBoolean()) active = false;
        return active;
    }
}
