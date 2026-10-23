package com.mhrsdev.musicplayer.ui.nav;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.Nullable;

/**
 * Hook for app-wide banners shown at the top of Home (today: the update banner from {@code ui.update}). Resolved by
 * class name so Home builds without it. The banner view decides its own visibility (GONE when nothing to show).
 */
public final class Banners {
    static final String UPDATE_BANNER = "com.mhrsdev.musicplayer.ui.update.UpdateBanner";

    private Banners() { }

    /** Adds the known banners to {@code slot} (once). Missing classes are skipped. */
    public static void attach(ViewGroup slot) {
        if (slot.getChildCount() > 0) return;
        View v = create(slot.getContext(), UPDATE_BANNER);
        if (v != null) slot.addView(v, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    @Nullable
    static View create(Context context, String className) {
        try {
            Class<?> c = Class.forName(className, true, context.getClassLoader());
            if (!View.class.isAssignableFrom(c)) return null;
            return (View) c.getConstructor(Context.class).newInstance(context);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return null;
        }
    }
}
