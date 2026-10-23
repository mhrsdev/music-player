package com.mhrsdev.musicplayer.ui.nav;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;

/**
 * Home's ambient top glow: a vertical gradient that starts at full strength at y=0 (under the status bar) and eases
 * out to fully transparent over many stops, so it never shows a band or a hard edge. Static drawable, no animation.
 */
public final class HomeGlow {
    static final int TINT = 0x705AA8;
    static final int PEAK_ALPHA = 0x40;

    private HomeGlow() { }

    public static GradientDrawable create(Context context) {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, stops(9));
    }

    /** Ease-out alpha ramp from PEAK_ALPHA to 0 (last stop exactly 0). Pure for tests. */
    static int[] stops(int n) {
        int[] c = new int[n];
        for (int i = 0; i < n; i++) {
            float t = i / (float) (n - 1);
            float a = (1f - t) * (1f - t) * (1f + 0.5f * t); // smooth, no visible step at the end
            c[i] = (Math.round(PEAK_ALPHA * Math.min(1f, a)) << 24) | TINT;
        }
        return c;
    }
}
