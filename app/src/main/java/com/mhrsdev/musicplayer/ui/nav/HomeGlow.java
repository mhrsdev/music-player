package com.mhrsdev.musicplayer.ui.nav;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.TransitionDrawable;
import android.view.View;
import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import com.mhrsdev.musicplayer.R;

/**
 * Home's ambient top glow: a vertical gradient that starts at full strength at y=0 (under the status bar) and eases
 * out to fully transparent over many stops, so it never shows a band or a hard edge. Static drawable, no animation.
 */
public final class HomeGlow {
    static final int TINT = 0x705AA8;
    static final int PEAK_ALPHA = 0x40;

    private HomeGlow() { }

    /** Cross-fade length of {@link #setTint}; one fade per change, nothing animates otherwise. */
    static final int FADE_MS = 450;
    static final int DEFAULT_TINT = TINT;

    public static GradientDrawable create(Context context) {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, stops(9));
    }

    /** The shell's glow view, or null when the activity has none (tests, other hosts). */
    @Nullable public static View find(Activity activity) {
        return activity.findViewById(R.id.home_glow);
    }

    /**
     * Re-tints the glow (rgb, alpha ignored; {@code null} = the neutral default). {@code animate} = one cross-fade of
     * {@link #FADE_MS}; otherwise instant. Static drawables only: the fade is a single TransitionDrawable run.
     */
    public static void setTint(View glow, @Nullable @ColorInt Integer rgb, boolean animate) {
        int tint = rgb == null ? DEFAULT_TINT : rgb & 0xFFFFFF;
        Object current = glow.getTag(R.id.home_glow);
        if (current instanceof Integer c && c == tint) return;
        glow.setTag(R.id.home_glow, tint);
        GradientDrawable next = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, stops(9, tint));
        Drawable bg = glow.getBackground();
        Drawable from = bg instanceof TransitionDrawable t ? t.getDrawable(1) : bg;
        if (!animate || from == null || glow.getVisibility() != View.VISIBLE) {
            glow.setBackground(next);
            return;
        }
        TransitionDrawable fade = new TransitionDrawable(new Drawable[] {from, next});
        fade.setCrossFadeEnabled(true);
        glow.setBackground(fade);
        fade.startTransition(FADE_MS);
    }

    /** Ease-out alpha ramp from PEAK_ALPHA to 0 (last stop exactly 0). Pure for tests. */
    static int[] stops(int n) {
        return stops(n, TINT);
    }

    static int[] stops(int n, int tint) {
        int[] c = new int[n];
        for (int i = 0; i < n; i++) {
            float t = i / (float) (n - 1);
            float a = (1f - t) * (1f - t) * (1f + 0.5f * t); // smooth, no visible step at the end
            c[i] = (Math.round(PEAK_ALPHA * Math.min(1f, a)) << 24) | tint;
        }
        return c;
    }
}
