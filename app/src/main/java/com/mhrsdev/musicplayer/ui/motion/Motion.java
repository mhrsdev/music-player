package com.mhrsdev.musicplayer.ui.motion;

import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.view.View;
import android.view.animation.PathInterpolator;

import androidx.annotation.Nullable;

import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.ui.theme.MotionPolicy;

/**
 * Mowj motion tokens: durations, easing curves and amplitudes shared by every screen, plus the single "jiggle"
 * vocabulary (press squish, state-change wobble, pop). Everything here animates only scale / translation / alpha.
 *
 * <p>Policy: {@link #enabled} is false under the app's reduce-motion setting, system animator scale 0 or battery
 * saver; then springs snap, jiggles are skipped and page transitions become a short fade or nothing.
 */
public class Motion {
    private Motion() {}

    // Durations (ms) for the few tweened things (fades, page transitions). Springs have no duration.
    public static final long SHORT = 120L;
    public static final long MEDIUM = 240L;
    public static final long PAGE = 320L;
    public static final long LONG = 450L;

    // Amplitudes.
    /** Pressed scale of buttons and FAB-like controls. */
    public static final float PRESS_SCALE = 0.92f;
    /** Pressed scale of large surfaces (list rows, cards): subtler. */
    public static final float PRESS_SCALE_SURFACE = 0.97f;
    /** First dip of the state-change jiggle (1 -> 0.92 -> ~1.03 -> 1). */
    public static final float JIGGLE_DIP = 0.08f;
    /** Pop of a toggled-on icon (heart): grows to ~1.18 then rests. */
    public static final float POP = 0.18f;
    /** Shared-axis page slide, dp. */
    public static final float PAGE_SLIDE_DP = 28f;

    /** Mowj "glide": quick start, long soft landing (enter / decelerate). Our own curve, not a platform token. */
    public static final TimeInterpolator GLIDE = new PathInterpolator(0.1f, 0.75f, 0.15f, 1f);
    /** Mowj "lift": slow start, quick exit (leave / accelerate). */
    public static final TimeInterpolator LIFT = new PathInterpolator(0.4f, 0f, 0.85f, 0.25f);
    /** Symmetric standard ease for colour and alpha. */
    public static final TimeInterpolator EASE = new PathInterpolator(0.3f, 0f, 0.1f, 1f);

    @Nullable private static Context app;

    /** Call once from Application.onCreate so springs without a host view can read the policy. */
    public static void init(Context context) {
        app = context.getApplicationContext();
    }

    /** True when non-essential motion may run (see class doc). */
    public static boolean enabled(@Nullable Context context) {
        Context c = context != null ? context : app;
        if (c == null) return ValueAnimator.areAnimatorsEnabled();
        return MotionPolicy.decorativeMotionEnabled(c);
    }

    /** System animator duration scale (developer options); springs run slower by the same factor. */
    public static float durationScale() {
        float s = ValueAnimator.getDurationScale();
        return s > 0f ? s : 1f;
    }

    // ---- The jiggle vocabulary (all reuse one SpringAnimator stored on the view) ----

    /** Scale spring of {@code v}, created once and cached in a view tag. */
    public static SpringAnimator scaleSpring(View v) {
        Object tag = v.getTag(R.id.mp_motion_scale);
        if (tag instanceof SpringAnimator a) return a;
        SpringAnimator a = SpringAnimator.scaleOf(v, Spring.Preset.SNAPPY);
        v.setTag(R.id.mp_motion_scale, a);
        return a;
    }

    /** Finger down: squish to {@code scale}. */
    public static void press(View v, float scale) {
        scaleSpring(v).setPreset(Spring.Preset.SNAPPY).animateTo(scale);
    }

    /** Finger up: spring back to 1 with the jiggle spring, so the release rebounds once. */
    public static void release(View v) {
        scaleSpring(v).setPreset(Spring.Preset.JIGGLE).animateTo(1f);
    }

    /** State change (play/pause, chip toggled): dip, one small rebound, rest. */
    public static void jiggle(View v) {
        jiggle(scaleSpring(v));
    }

    /** Jiggle on any scale spring (e.g. one a custom View draws internally). */
    public static void jiggle(SpringAnimator scale) {
        scale.setPreset(Spring.Preset.JIGGLE);
        scale.spring().setTarget(1f);
        scale.kick(Spring.kickFor(-JIGGLE_DIP, Spring.Preset.JIGGLE.stiffness, Spring.Preset.JIGGLE.dampingRatio));
    }

    /** Toggled on (like heart): grow and settle with a bounce. */
    public static void pop(View v) {
        SpringAnimator a = scaleSpring(v).setPreset(Spring.Preset.BOUNCY);
        a.spring().setTarget(1f);
        a.kick(Spring.kickFor(POP, Spring.Preset.BOUNCY.stiffness, Spring.Preset.BOUNCY.dampingRatio));
    }

    /**
     * Press feedback for any clickable view: squish on down, jiggle-spring back on up/cancel. Keeps the view's own
     * click handling (returns false from the touch listener).
     */
    @SuppressWarnings("ClickableViewAccessibility")
    public static void attachPress(View v, float scale) {
        v.setOnTouchListener((view, e) -> {
            if (!view.isEnabled()) return false;
            switch (e.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN -> press(view, scale);
                case android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> release(view);
                default -> { }
            }
            return false;
        });
    }
}
