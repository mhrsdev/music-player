package com.mhrsdev.musicplayer.ui.motion;

import android.content.Context;

import androidx.fragment.app.FragmentTransaction;

import com.mhrsdev.musicplayer.R;

/**
 * The shell's page transitions, one place for MainActivity to call. All are Animator resources on translation /
 * alpha / scale only (render-thread friendly, nothing re-laid out per frame) using the Mowj glide/lift curves, so
 * AndroidX Fragment's predictive back (enableOnBackInvokedCallback, API 34+) scrubs them with the back gesture.
 * The full player keeps its own cover flight (PlayerFragment) and is not touched here.
 * Under reduce motion / animator scale 0 / battery saver, or with Settings &gt; Performance "Screen transitions" off,
 * they are skipped entirely (instant swap).
 */
public class PageTransitions {
    private PageTransitions() {}

    /** Tab switch: fade through with a slight 0.96 -> 1 scale; the tab's state is kept by the caller. */
    public static FragmentTransaction tab(FragmentTransaction ft, Context c) {
        if (!Motion.transitions(c)) return ft;
        return ft.setCustomAnimations(R.animator.mp_fade_through_enter, R.animator.mp_fade_through_exit);
    }

    /** Drill-down (album, artist, playlist ...): shared X axis; mirrored in RTL (animator-ldrtl). */
    public static FragmentTransaction push(FragmentTransaction ft, Context c) {
        if (!Motion.transitions(c)) return ft;
        return ft.setCustomAnimations(R.animator.mp_axis_enter, R.animator.mp_axis_exit,
                R.animator.mp_axis_pop_enter, R.animator.mp_axis_pop_exit);
    }
}
