package com.mhrsdev.musicplayer.ui.theme;

import android.content.Context;
import android.os.Build;
import android.os.PowerManager;

import androidx.annotation.MainThread;

/**
 * Feeds the platform thermal status (API 29+) into {@link MotionPolicy#setThermalLevel}: MODERATE = warm,
 * SEVERE or worse = hot. Event driven (the system calls back on changes, and once with the current status when
 * registered): no polling, no timer, nothing in the background beyond the registration. No-op below API 29.
 */
public class ThermalWatch {
    private static boolean installed;

    private ThermalWatch() {}

    @MainThread
    public static void install(Context context) {
        if (installed || Build.VERSION.SDK_INT < 29) return;
        PowerManager pm = (PowerManager) context.getApplicationContext().getSystemService(Context.POWER_SERVICE);
        if (pm == null) return;
        installed = true;
        try {
            pm.addThermalStatusListener(context.getApplicationContext().getMainExecutor(),
                    status -> MotionPolicy.setThermalLevel(level(status)));
        } catch (RuntimeException e) {
            installed = false; // no thermal service (some emulators): stay at the user's profile
        }
    }

    /** Platform status to 0 cool / 1 warm / 2 hot. */
    static int level(int status) {
        if (status >= PowerManager.THERMAL_STATUS_SEVERE) return 2;
        if (status >= PowerManager.THERMAL_STATUS_MODERATE) return 1;
        return 0;
    }
}
