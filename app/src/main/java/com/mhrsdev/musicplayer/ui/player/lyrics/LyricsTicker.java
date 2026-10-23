package com.mhrsdev.musicplayer.ui.player.lyrics;

import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

/**
 * The one frame loop of the lyrics: {@code postOnAnimationDelayed} at ~30 fps, running only while the lyrics list is
 * shown, attached, the player is playing and the active line has word timing. Each frame it lets the owner move the
 * active line (cheap binary search) and invalidates the active line's view only; nothing else redraws. Stops itself
 * the first frame any condition is false.
 */
public final class LyricsTicker {
    public interface OnFrame { void onFrame(); }

    static final long FRAME_MS = 33;
    private final RecyclerView list;
    private final LyricsClock clock;
    private final OnFrame onFrame;
    private boolean running;
    private boolean enabled;
    private final Runnable frame = this::frame;

    public LyricsTicker(RecyclerView list, LyricsClock clock, OnFrame onFrame) {
        this.list = list;
        this.clock = clock;
        this.onFrame = onFrame;
    }

    /** Lyrics visible on screen (the fragment's view lifecycle covers "player visible"). */
    public void setEnabled(boolean on) {
        enabled = on;
        poke();
    }

    /** Re-evaluates after a position/playing update. */
    public void poke() {
        if (shouldRun() && !running) {
            running = true;
            list.postOnAnimation(frame);
        } else if (!shouldRun()) {
            stop();
        }
    }

    public void stop() {
        running = false;
        list.removeCallbacks(frame);
    }

    public boolean isRunning() { return running; }

    private boolean shouldRun() {
        return enabled && clock.isPlaying() && list.isAttachedToWindow() && list.getVisibility() == View.VISIBLE
                && list.getWindowVisibility() == View.VISIBLE;
    }

    private void frame() {
        if (!running) return;
        if (!shouldRun()) { running = false; return; }
        onFrame.onFrame();
        invalidateActive();
        list.postOnAnimationDelayed(frame, FRAME_MS);
    }

    /** Draws the current word state once (also used after a seek while paused). */
    public void invalidateActive() {
        for (int i = 0, n = list.getChildCount(); i < n; i++) {
            View v = list.getChildAt(i);
            if (v instanceof LyricLineView l && l.isActiveLine() && l.hasWordTiming()) l.invalidate();
        }
    }
}
