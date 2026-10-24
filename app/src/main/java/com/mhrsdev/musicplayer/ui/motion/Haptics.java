package com.mhrsdev.musicplayer.ui.motion;

import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.View;

/** Haptic constants newer than API 26, each with the closest older one (an unknown constant is silently ignored). */
public class Haptics {
    private Haptics() { }

    /** "Done" tick: CONFIRM on API 30+, else a virtual-key tap. */
    public static void confirm(View v) {
        v.performHapticFeedback(Build.VERSION.SDK_INT >= 30 ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.VIRTUAL_KEY);
    }
}
