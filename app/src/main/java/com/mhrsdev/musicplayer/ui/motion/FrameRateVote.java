package com.mhrsdev.musicplayer.ui.motion;

import android.os.Build;
import android.view.View;

/**
 * Tells an adaptive-refresh (LTPO / VRR) display what a decorative view needs (API 35+, no-op below). A view that
 * only redraws on the 30 fps decorative beat (backdrop drift, seek wave) votes "low", so its frames alone do not keep
 * a 120 Hz panel at 120 Hz; touch, scrolling and every other view keep their own (higher) vote. Main thread only.
 */
public class FrameRateVote {
    private FrameRateVote() {}

    /** @param decorative true = low category (~30 Hz is enough); false = back to the platform default */
    public static void decorative(View v, boolean decorative) {
        if (Build.VERSION.SDK_INT < 35) return;
        float want = decorative ? View.REQUESTED_FRAME_RATE_CATEGORY_LOW : View.REQUESTED_FRAME_RATE_CATEGORY_DEFAULT;
        float now = v.getRequestedFrameRate();
        if (now != want && !(Float.isNaN(now) && Float.isNaN(want))) v.setRequestedFrameRate(want);
    }
}
