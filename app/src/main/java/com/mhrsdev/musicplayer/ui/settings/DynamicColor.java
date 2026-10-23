package com.mhrsdev.musicplayer.ui.settings;

import android.content.Context;
import androidx.annotation.Nullable;
import com.mhrsdev.musicplayer.ui.theme.ThemeController;
import java.lang.reflect.Method;

/**
 * Optional "Dynamic color (Material You)" toggle. The theme layer (agent M1) is expected to add to
 * {@link ThemeController}: {@code boolean isDynamicColorSupported()}, {@code boolean isDynamicColor()} and
 * {@code void setDynamicColor(boolean)}. Resolved by reflection so Settings builds before that lands; the row is
 * hidden until all three exist and the phone supports it (no dead row).
 */
final class DynamicColor {
    private final ThemeController theme;
    private final Method get;
    private final Method set;

    private DynamicColor(ThemeController theme, Method get, Method set) {
        this.theme = theme;
        this.get = get;
        this.set = set;
    }

    @Nullable
    static DynamicColor lookup(Context context) {
        try {
            ThemeController t = ThemeController.get(context);
            Class<?> c = t.getClass();
            Method supported = c.getMethod("isDynamicColorSupported");
            if (!Boolean.TRUE.equals(supported.invoke(t))) return null;
            return new DynamicColor(t, c.getMethod("isDynamicColor"), c.getMethod("setDynamicColor", boolean.class));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    boolean isEnabled() {
        try {
            return Boolean.TRUE.equals(get.invoke(theme));
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    void setEnabled(boolean on) {
        try {
            set.invoke(theme, on);
        } catch (ReflectiveOperationException ignored) {
            // contract changed: the row simply has no effect until rebuilt
        }
    }
}
