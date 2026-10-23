package com.mhrsdev.musicplayer.ui.motion;

import android.view.Choreographer;

import androidx.annotation.MainThread;

import java.util.ArrayList;

/**
 * One Choreographer callback for every spring in the app. Posts a frame only while something is moving and stops
 * the moment the last animation settles: no idle ticking, no timers. Main thread only; no per-frame allocation.
 */
@MainThread
public class FrameDriver implements Choreographer.FrameCallback {
    /** Something that advances once per frame. */
    public interface Tick {
        /** @return true to keep receiving frames. */
        boolean onFrame(float dtSeconds);
    }

    private static FrameDriver instance;
    /** Longest step we integrate in one go (a long jank pause should not teleport a spring). */
    private static final float MAX_DT = 1f / 30f;

    private final ArrayList<Tick> ticks = new ArrayList<>();
    private boolean posted;
    private long lastNanos;

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
        }
    }

    public void remove(Tick t) {
        ticks.remove(t);
    }

    /** Number of live animations (tests, battery audit). */
    public int activeCount() {
        return ticks.size();
    }

    @Override public void doFrame(long frameTimeNanos) {
        float dt = lastNanos == 0 ? 1f / 60f : Math.min(MAX_DT, (frameTimeNanos - lastNanos) / 1e9f);
        lastNanos = frameTimeNanos;
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
        } else {
            Choreographer.getInstance().postFrameCallback(this);
        }
    }
}
