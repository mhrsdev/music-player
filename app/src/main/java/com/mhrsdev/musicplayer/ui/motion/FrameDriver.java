package com.mhrsdev.musicplayer.ui.motion;

import android.view.Choreographer;

import androidx.annotation.MainThread;

import java.util.ArrayList;

/**
 * One Choreographer callback for every spring in the app. Posts a frame only while something is moving and stops
 * the moment the last animation settles: no idle ticking, no timers. Main thread only; no per-frame allocation.
 *
 * <p>Frame-rate cap (Settings &gt; Performance): with {@link #setMaxFps} 60 or 30 the ticks run only on display frames
 * at least ~one cap period apart, so every spring and decorative loop in the app obeys it in one place. Skipped vsyncs
 * cost one callback and draw nothing (nothing is invalidated). Springs integrate the real elapsed time, so they look
 * the same, only with fewer frames.
 */
@MainThread
public class FrameDriver implements Choreographer.FrameCallback {
    /** Something that advances once per frame. */
    public interface Tick {
        /** @return true to keep receiving frames. */
        boolean onFrame(float dtSeconds);

        /**
         * True (default) for anything that must move on every display frame (springs, touch). A decorative loop that
         * only acts on the shared beat returns false, which lets the driver sleep between beats.
         */
        default boolean everyFrame() {
            return true;
        }
    }

    private static FrameDriver instance;
    /** Longest step we integrate in one go (a long jank pause should not teleport a spring). */
    private static final float MAX_DT = 1f / 24f; // > one 30 fps-cap step, so the cap never slows springs down

    /** The shared decorative beat: 30 fps (the fastest decorative loop); 15 fps loops run on every 2nd beat. */
    public static final long BEAT_NANOS = 1_000_000_000L / 30;
    /** Wake this long before the next beat, so the delayed callback lands on the vsync at (or just after) it. */
    static final long WAKE_EARLY_NANOS = 6_000_000L;

    private final ArrayList<Tick> ticks = new ArrayList<>();
    private boolean posted;
    /** Posted with a delay (only beat loops registered); a new every-frame tick must re-post at once. */
    private boolean sleeping;
    private long lastNanos;
    private long frameNanos;
    private long lastBeatNanos;
    private boolean beat;
    /** 0 = every display frame. */
    private long minFrameNanos;

    public static FrameDriver get() {
        if (instance == null) instance = new FrameDriver();
        return instance;
    }

    public void add(Tick t) {
        if (!ticks.contains(t)) ticks.add(t);
        if (!posted) {
            posted = true;
            lastNanos = 0;
            Choreographer.getInstance().postFrameCallback(this);
        } else if (sleeping && t.everyFrame()) {
            // e.g. a press spring while only the backdrop drifts: start on the next vsync, not the next beat
            Choreographer c = Choreographer.getInstance();
            c.removeFrameCallback(this);
            sleeping = false;
            lastNanos = 0;
            c.postFrameCallback(this);
        }
    }

    public void remove(Tick t) {
        ticks.remove(t);
    }

    /** Caps every animation driven here; {@code fps <= 0} removes the cap. Takes effect on the next frame. */
    public void setMaxFps(int fps) {
        minFrameNanos = fps <= 0 ? 0 : 1_000_000_000L / fps;
    }

    /**
     * Whether a display frame {@code sinceLastNanos} after the last run may run the ticks. 15 % slack absorbs vsync
     * jitter, so a 30 fps cap runs every 2nd frame at 60 Hz and every 4th at 120 Hz (never 3 and 5 alternating).
     */
    static boolean due(long sinceLastNanos, long minFrameNanos) {
        // negative = a new time base (never on a device; a test's synthetic clock): treat as due, never stall
        return minFrameNanos == 0 || sinceLastNanos < 0 || sinceLastNanos >= minFrameNanos - minFrameNanos * 15 / 100;
    }

    /** Frame time (System.nanoTime base) of the frame being run; beat loops time themselves with it. */
    public long frameTimeNanos() {
        return frameNanos;
    }

    /** True on the frames of the shared decorative beat (the only frames a {@link PeriodicTick} body may run on). */
    public boolean isBeat() {
        return beat;
    }

    /**
     * How long the driver may sleep after a frame: 0 while any tick needs every frame, else until
     * {@link #WAKE_EARLY_NANOS} before the next beat. Package-private for tests.
     */
    long sleepMillis(long nowNanos) {
        for (int i = 0, n = ticks.size(); i < n; i++) if (ticks.get(i).everyFrame()) return 0;
        long wake = lastBeatNanos + BEAT_NANOS - WAKE_EARLY_NANOS;
        return Math.max(0L, (wake - nowNanos) / 1_000_000L);
    }

    /** Number of live animations (tests, battery audit). */
    public int activeCount() {
        return ticks.size();
    }

    @Override public void doFrame(long frameTimeNanos) {
        sleeping = false;
        if (lastNanos != 0 && !due(frameTimeNanos - lastNanos, minFrameNanos)) {
            Choreographer.getInstance().postFrameCallback(this); // capped: skip this vsync, draw nothing
            return;
        }
        float dt = lastNanos == 0 ? 1f / 60f : Math.min(MAX_DT, (frameTimeNanos - lastNanos) / 1e9f);
        lastNanos = frameTimeNanos;
        frameNanos = frameTimeNanos;
        beat = lastBeatNanos == 0 || due(frameTimeNanos - lastBeatNanos, BEAT_NANOS);
        if (beat) lastBeatNanos = frameTimeNanos;
        if (dt <= 0f) dt = 1f / 120f;
        float scaled = dt / Motion.durationScale();
        for (int i = ticks.size() - 1; i >= 0; i--) {
            if (i >= ticks.size()) continue; // a tick removed others
            Tick t = ticks.get(i);
            if (!t.onFrame(scaled)) {
                int idx = ticks.indexOf(t);
                if (idx >= 0) ticks.remove(idx);
            }
        }
        if (ticks.isEmpty()) {
            posted = false;
            return;
        }
        long sleep = sleepMillis(System.nanoTime());
        if (sleep > 0) {
            sleeping = true;
            Choreographer.getInstance().postFrameCallbackDelayed(this, sleep);
        } else {
            Choreographer.getInstance().postFrameCallback(this);
        }
    }
}
