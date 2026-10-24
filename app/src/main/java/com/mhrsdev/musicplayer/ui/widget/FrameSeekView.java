package com.mhrsdev.musicplayer.ui.widget;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

import com.mhrsdev.musicplayer.ui.motion.FrameDriver;

/**
 * Base of the D25 seek styles that move: frames come from M1's shared {@link FrameDriver} (one Choreographer
 * callback for the app, never our own loop), throttled to {@link #FPS}, and only while {@link #needsFrames()} and the
 * view is visible with decorative motion allowed (reduce motion, battery saver, animator scale 0 and Settings &gt;
 * Performance "Moving seek bar" off make it fully static; the Performance frame-rate cap applies in the FrameDriver).
 * Subclasses advance their own clock in {@link #advance} and draw from it; nothing allocates per frame.
 * The 30 fps styles step on the driver's shared decorative beat (same display frame as the backdrop and the lyrics,
 * and the driver may sleep between beats); a style asking for {@code frameInterval() == 0} gets every frame.
 */
public abstract class FrameSeekView extends BaseSeekView implements FrameDriver.Tick {
    /** Decorative ripples / pulses run at 30 fps at most. */
    public static final float FPS = 30f;
    private static final float FRAME = 1f / FPS;

    private boolean registered;
    private float pending;
    private long lastBeatNanos;
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
            lastBeatNanos = 0;
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

    @Override protected boolean framesFlowing() {
        return registered || super.framesFlowing();
    }

    @Override public boolean onFrame(float dt) {
        if (!(needsFrames() && canAnimate() && isAttachedToWindow())) {
            registered = false;
            invalidate(); // settle on a static frame
            return false;
        }
        if (frameInterval() > 0f) { // decorative: on the shared beat, with the real time since the last one
            FrameDriver d = FrameDriver.get();
            if (!d.isBeat()) return true;
            long now = d.frameTimeNanos();
            float step = lastBeatNanos == 0 ? FRAME : Math.min(0.1f, (now - lastBeatNanos) / 1e9f);
            lastBeatNanos = now;
            if (advance(step / com.mhrsdev.musicplayer.ui.motion.Motion.durationScale())) invalidate();
            return true;
        }
        pending += dt;
        if (pending >= frameInterval()) {
            if (advance(pending)) invalidate();
            pending = 0f;
        }
        return true;
    }

    @Override public boolean everyFrame() {
        return frameInterval() <= 0f;
    }

    @Override public void setPlaying(boolean playing) {
        super.setPlaying(playing);
        updateFrames();
    }

    /** Settings &gt; Performance "Moving seek bar" (or reduce motion) changed while shown: start / stop frames now. */
    @Override public void refreshMotion() {
        super.refreshMotion();
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
