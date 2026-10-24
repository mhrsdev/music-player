package com.mhrsdev.musicplayer.ui.nav;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Build;
import android.view.Window;
import android.view.WindowManager;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * Full edge-to-edge on every supported version (API 26-37): the app draws behind transparent status and navigation
 * bars and into display cutouts; bar icons follow the APP theme (not the system one); no contrast scrim. Used by the
 * activity and by every dialog / bottom sheet window. Insets are then applied as padding to controls only.
 */
public class SystemBars {
    protected SystemBars() { }

    /** Call before {@code setContentView} (and before {@code super.onCreate} is fine). */
    public static void install(Activity activity) {
        prepare(activity.getWindow());
    }

    /** Same treatment for a dialog / bottom-sheet window, plus icon colours from {@code themed}. */
    public static void applyTo(@Nullable Dialog dialog, Context themed) {
        if (dialog == null || dialog.getWindow() == null) return;
        Window w = dialog.getWindow();
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) {
            w.setNavigationBarContrastEnforced(false);
            w.setStatusBarContrastEnforced(false);
        }
        applyAppearance(w, themed);
    }

    static void prepare(Window w) {
        WindowCompat.setDecorFitsSystemWindows(w, false);
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) {
            // Otherwise 3-button / some vendor gesture bars get a grey or black scrim band over our colours.
            w.setNavigationBarContrastEnforced(false);
            w.setStatusBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.layoutInDisplayCutoutMode = Build.VERSION.SDK_INT >= 30
                    ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            w.setAttributes(lp);
        }
    }

    /** Dark icons on light themes, light icons on dark ones, from the app's (not the system's) night mode. */
    public static void applyAppearance(Window w, Context themed) {
        boolean night = (themed.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(w, w.getDecorView());
        c.setAppearanceLightStatusBars(!night);
        c.setAppearanceLightNavigationBars(!night);
        // API 26 cannot draw dark navigation icons (windowLightNavigationBar is 27+): a light theme gets a dim bar behind
        // the white icons instead of an invisible control row.
        if (Build.VERSION.SDK_INT < 27) w.setNavigationBarColor(night ? Color.TRANSPARENT : 0x66000000);
    }

    /**
     * Space the bottom dock must keep free (before the 12dp gap token): the largest bottom inset any bar or gesture
     * zone reports. When every inset is 0 (buggy vendor "full-screen gestures", nav bar drawn over the window) the
     * 3-button bar height from the framework is used if that mode is on, and never less than {@code minPx}.
     */
    public static int dockBottomInset(WindowInsetsCompat insets, Resources res, int minPx) {
        int reported = bottomOf(insets);
        if (reported == 0 && isThreeButtonMode(res)) reported = frameworkDimen(res, "navigation_bar_height");
        return Math.max(reported, minPx);
    }

    static int bottomOf(WindowInsetsCompat insets) {
        int max = 0;
        int[] types = {WindowInsetsCompat.Type.navigationBars(), WindowInsetsCompat.Type.tappableElement(),
                WindowInsetsCompat.Type.mandatorySystemGestures()};
        for (int t : types) max = Math.max(max, insets.getInsets(t).bottom);
        return max;
    }

    /** Side insets for controls: bars + cutout (landscape nav bar / punch hole on the side). */
    public static Insets controlInsets(WindowInsetsCompat insets) {
        return insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
    }

    static boolean isThreeButtonMode(Resources res) {
        int id = res.getIdentifier("config_navBarInteractionMode", "integer", "android");
        return id != 0 && res.getInteger(id) == 0;
    }

    static int frameworkDimen(Resources res, String name) {
        int id = res.getIdentifier(name, "dimen", "android");
        return id == 0 ? 0 : res.getDimensionPixelSize(id);
    }
}
