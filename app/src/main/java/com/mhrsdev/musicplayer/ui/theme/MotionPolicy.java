package com.mhrsdev.musicplayer.ui.theme;

import android.content.Context;
import android.os.PowerManager;
import android.provider.Settings;

/**
 * Decides whether decorative animation (wave travel, cookie spin, glow) may run.
 * Off when the system animator scale is 0, battery saver is on, or the app's own reduce-motion setting is on.
 * Widgets then draw a static, equally legible frame.
 */
public final class MotionPolicy {
    private static volatile boolean appReduceMotion;

    private MotionPolicy() {}

    /** App-level override (Settings screen). */
    public static void setAppReduceMotion(boolean reduce) {
        appReduceMotion = reduce;
    }

    public static boolean decorativeMotionEnabled(Context context) {
        if (appReduceMotion) return false;
        Context app = context.getApplicationContext();
        float scale = Settings.Global.getFloat(app.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        if (scale == 0f) return false;
        PowerManager pm = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
        return pm == null || !pm.isPowerSaveMode();
    }

    /** Battery saver only (skip grain, glow and other purely decorative layers). */
    public static boolean batterySaver(Context context) {
        PowerManager pm = (PowerManager) context.getApplicationContext().getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isPowerSaveMode();
    }
}
