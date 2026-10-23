package com.mhrsdev.musicplayer.ui.nav;

import android.content.Context;
import android.view.View;

/** Width-class helpers so phone, tablet and landscape share one layout (no duplicated sw600dp screens). */
public class Adaptive {
    /** Reading width for single-column screens (settings, search) on wide windows. */
    public static final int CONTENT_MAX_DP = 640;

    private Adaptive() { }

    public static boolean isWide(Context c) {
        return c.getResources().getConfiguration().screenWidthDp >= 600;
    }

    /**
     * Keeps {@code content} at most {@code maxDp} wide, centered inside its parent, by growing its horizontal
     * padding. Safe to call once; it follows later size changes (rotation, split screen, fold).
     */
    public static void limitWidth(View content, int maxDp) {
        int basePadStart = content.getPaddingStart();
        int basePadEnd = content.getPaddingEnd();
        float density = content.getResources().getDisplayMetrics().density;
        int max = Math.round(maxDp * density);
        content.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int extra = Math.max(0, (r - l - basePadStart - basePadEnd - max) / 2);
            int start = basePadStart + extra, end = basePadEnd + extra;
            if (v.getPaddingStart() != start || v.getPaddingEnd() != end) {
                v.post(() -> v.setPaddingRelative(start, v.getPaddingTop(), end, v.getPaddingBottom()));
            }
        });
    }
}
