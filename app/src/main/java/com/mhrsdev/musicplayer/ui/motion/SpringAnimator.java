package com.mhrsdev.musicplayer.ui.motion;

import android.view.View;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;

/**
 * Animates one float with a {@link Spring} on the shared {@link FrameDriver}. Retargeting mid-flight keeps the
 * velocity, so interrupted gestures never jerk. When {@link Motion#enabled} says no (reduce motion, animator scale
 * 0, battery saver) every call snaps straight to the target and no frame is ever scheduled.
 */
@MainThread
public class SpringAnimator implements FrameDriver.Tick {
    /** Receives each frame's value (e.g. {@code v -> view.setScaleX(v)}). */
    public interface Setter {
        void set(float value);
    }

    /** Called once the spring rests on its target. */
    public interface EndListener {
        void onEnd(SpringAnimator animator);
    }

    private final Spring spring;
    private final Setter setter;
    @Nullable private final View host;
    @Nullable private EndListener endListener;
    private boolean running;

    /** @param host optional: frames are skipped (and the value snapped) while it is detached. */
    public SpringAnimator(Spring.Preset preset, Setter setter, @Nullable View host) {
        this.spring = new Spring(preset);
        this.setter = setter;
        this.host = host;
    }

    public static SpringAnimator scaleOf(View v, Spring.Preset preset) {
        SpringAnimator a = new SpringAnimator(preset, s -> { v.setScaleX(s); v.setScaleY(s); }, v);
        a.spring.snapTo(v.getScaleX());
        return a;
    }

    public static SpringAnimator translationYOf(View v, Spring.Preset preset) {
        SpringAnimator a = new SpringAnimator(preset, v::setTranslationY, v);
        a.spring.setThreshold(0.5f);
        a.spring.snapTo(v.getTranslationY());
        return a;
    }

    public Spring spring() { return spring; }
    public float value() { return spring.value(); }
    public boolean isRunning() { return running; }

    public SpringAnimator setEndListener(@Nullable EndListener l) {
        endListener = l;
        return this;
    }

    public SpringAnimator setPreset(Spring.Preset p) {
        spring.setPreset(p);
        return this;
    }

    /** Springs to {@code target} from wherever it is now, keeping momentum. */
    public void animateTo(float target) {
        spring.setTarget(target);
        if (!canAnimate()) {
            snapTo(target);
            return;
        }
        start();
    }

    /** Kicks the value with {@code velocity} (units/s) toward its current target: a jiggle when it is at rest. */
    public void kick(float velocity) {
        if (!canAnimate()) return;
        spring.setVelocity(spring.velocity() + velocity);
        start();
    }

    public void snapTo(float v) {
        boolean was = running;
        stopInternal();
        spring.snapTo(v);
        setter.set(v);
        if (was && endListener != null) endListener.onEnd(this);
    }

    public void cancel() {
        stopInternal();
    }

    private boolean canAnimate() {
        return (host == null || host.isAttachedToWindow()) && Motion.enabled(host != null ? host.getContext() : null);
    }

    private void start() {
        if (!running) {
            running = true;
            FrameDriver.get().add(this);
        }
    }

    private void stopInternal() {
        if (running) {
            running = false;
            FrameDriver.get().remove(this);
        }
    }

    @Override public boolean onFrame(float dt) {
        if (!running) return false;
        if (host != null && !host.isAttachedToWindow()) {
            running = false;
            spring.snapTo(spring.target());
            setter.set(spring.value());
            return false;
        }
        boolean moving = spring.step(dt);
        setter.set(spring.value());
        if (!moving) {
            running = false;
            if (endListener != null) endListener.onEnd(this);
        }
        return moving;
    }
}
