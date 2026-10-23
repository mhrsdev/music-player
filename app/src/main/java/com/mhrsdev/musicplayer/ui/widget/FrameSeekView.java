package com.mhrsdev.musicplayer.ui.widget;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

import com.mhrsdev.musicplayer.ui.motion.FrameDriver;

/**
 * Base of the D25 seek styles that move: frames come from M1's shared {@link FrameDriver} (one Choreographer
 * callback for the app, never our own loop), throttled to {@link #FPS}, and only while {@link #needsFrames()} and the
 * view is visible with decorative motion allowed (reduce motion, battery saver and animator scale 0 make it fully
 * static). Subclasses advance their own clock in {@link #advance} and draw from it; nothing allocates per frame.
 */
public abstract class FrameSeekView extends BaseSeekView implements FrameDriver.Tick {
    /** Decorative ripples / pulses run at 30 fps at most. */
    public static final float FPS = 30f;
    private static final float FRAME = 1f / FPS;

    private boolean registered;
    private float pending;
    /** Seconds of motion clock; subclasses read it in drawTrack. */
    protected float clock;

    protected FrameSeekView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    /** True while the style has something moving (e.g. playing, or a spring still settling). */
    protected abstract boolean needsFrames();

    /** Advance animation state by {@code dt} seconds; return true if anything changed. */
    protected boolean advance(float dt) {
        clock += dt;
        return true;
    }

    /** Seconds between redraws: {@code 1/30} for ripples; a short spring may ask for every frame (0). */
    protected float frameInterval() {
        return FRAME;
    }

    /** Re-evaluates whether to receive frames; call after anything that changes {@link #needsFrames()}. */
    protected final void updateFrames() {
        boolean want = needsFrames() && canAnimate() && isAttachedToWindow();
        if (want && !registered) {
            registered = true;
            pending = 0f;
            FrameDriver.get().add(this);
        } else if (!want && registered) {
            registered = false;
            FrameDriver.get().remove(this);
        }
    }

    /** True while frames are flowing (tests, battery audit). */
    public boolean isAnimating() {
        return registered;
    }

    @Override public boolean onFrame(float dt) {
        if (!(needsFrames() && canAnimate() && isAttachedToWindow())) {
            registered = false;
            invalidate(); // settle on a static frame
            return false;
        }
        pending += dt;
        if (pending >= frameInterval()) {
            if (advance(pending)) invalidate();
            pending = 0f;
        }
        return true;
    }

    @Override public void setPlaying(boolean playing) {
        super.setPlaying(playing);
        updateFrames();
    }

    @Override public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        updateFrames();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateFrames();
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (registered) {
            registered = false;
            FrameDriver.get().remove(this);
        }
    }
}
