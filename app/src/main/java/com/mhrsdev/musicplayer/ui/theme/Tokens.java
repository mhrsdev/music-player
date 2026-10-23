package com.mhrsdev.musicplayer.ui.theme;

import android.content.Context;
import android.content.res.Resources;
import android.util.TypedValue;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;

import com.mhrsdev.musicplayer.R;

/** Design tokens as Java constants (dp / ms) plus theme-attribute helpers. Sizes mirror res/values/dimens.xml. */
public final class Tokens {
    private Tokens() {}

    // Shape (dp)
    public static final float RADIUS_ROW_ART = 12f;
    public static final float RADIUS_GROUP_OUTER = 22f;
    public static final float RADIUS_GROUP_INNER = 6f;
    public static final float RADIUS_CARD = 24f;
    public static final float RADIUS_HERO = 28f;
    public static final float RADIUS_PLAYER_COVER = 32f;
    public static final float RADIUS_MINI_PLAYER = 24f;
    public static final float RADIUS_CHIP = 12f;
    public static final float TOUCH_TARGET = 48f;

    // Seek bar (DESIGN.md 7 / 9)
    public static final float WAVE_AMPLITUDE = 3f;
    public static final float WAVE_LENGTH = 26f;
    public static final long WAVE_PERIOD_MS = 1600L;
    public static final long WAVE_SETTLE_MS = 300L;
    public static final long SEEK_STEP_MS = 10_000L;

    // Motion (ms)
    public static final long MOTION_FAST = 150L;
    public static final long MOTION_MORPH = 350L;
    public static final long MOTION_COLOR = 600L;
    public static final long MOTION_COLOR_REDUCED = 150L;
    public static final long COOKIE_TURN_MS = 24_000L;
    /** Decorative frame interval: 30 fps is enough for slow waves and halves the GPU work of 60 fps. */
    public static final long DECOR_FRAME_MS = 33L;

    public static float dp(Context c, float dp) {
        return dp * c.getResources().getDisplayMetrics().density;
    }

    public static float sp(Context c, float sp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, c.getResources().getDisplayMetrics());
    }

    @ColorInt
    public static int color(Context c, @AttrRes int attr) {
        TypedValue tv = new TypedValue();
        if (c.getTheme().resolveAttribute(attr, tv, true)) {
            if (tv.resourceId != 0) {
                try {
                    return c.getColor(tv.resourceId);
                } catch (Resources.NotFoundException ignored) {
                    // fall through to the raw value
                }
            }
            return tv.data;
        }
        return 0xFFFF00FF;
    }

    public static int onSurface(Context c) { return color(c, R.attr.mpOnSurface); }
    public static int onSurfaceVariant(Context c) { return color(c, R.attr.mpOnSurfaceVariant); }
    public static int primary(Context c) { return color(c, R.attr.mpPrimary); }
    public static int outlineVariant(Context c) { return color(c, R.attr.mpOutlineVariant); }

    public static boolean isDark(Context c) {
        return (c.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }
}
