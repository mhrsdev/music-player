package com.mhrsdev.musicplayer;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.os.Trace;
import com.mhrsdev.musicplayer.data.settings.BatterySaverMode;
import com.mhrsdev.musicplayer.di.AppContainer;
import com.mhrsdev.musicplayer.ui.settings.ThemeApplier;
import com.mhrsdev.musicplayer.ui.theme.MotionPolicy;
import com.mhrsdev.musicplayer.ui.widget.ArtworkLoader;

public class MusicPlayerApp extends Application {
    private AppContainer container;

    @Override public void onCreate() {
        super.onCreate();
        if (BuildConfig.DEBUG) enableStrictMode(); // logcat tag "StrictMode"; release builds pay nothing
        Trace.beginSection("MusicPlayerApp.onCreate"); // visible in Perfetto; the container is lazy except the player
        container = new AppContainer(this);
        Trace.endSection();
        // Cheap: one static write per change of the setting, no polling.
        container.settings().batterySaver().observeForever(mode -> MotionPolicy.setBatterySaverOverride(
                mode == BatterySaverMode.AUTO ? null : mode == BatterySaverMode.ON));
        container.settings().reduceMotion().observeForever(MotionPolicy::setAppReduceMotion);
        // Below API 33 AppCompat keeps the chosen locale only in memory: re-apply the stored choice on every start.
        if (Build.VERSION.SDK_INT < 33) ThemeApplier.applyLanguage(container.settings().language().getValue());
        registerActivityLifecycleCallbacks(new VisibilityCallbacks());
    }

    /** The UI is gone (app backgrounded): bitmaps are the biggest thing the UI process holds; playback keeps running. */
    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_UI_HIDDEN) ArtworkLoader.trimAll();
    }

    private static void enableStrictMode() {
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build());
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build());
    }

    public AppContainer getContainer() {
        return container;
    }

    /** Every activity start rechecks the library; the last stop drops the MediaStore observer (nothing runs in background). */
    private class VisibilityCallbacks implements ActivityLifecycleCallbacks {
        private int started;

        @Override public void onActivityStarted(Activity activity) {
            started++;
            container.onAppForeground();
        }

        @Override public void onActivityStopped(Activity activity) {
            if (--started == 0) container.onAppBackground();
        }

        @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) { }
        @Override public void onActivityResumed(Activity activity) { }
        @Override public void onActivityPaused(Activity activity) { }
        @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) { }
        @Override public void onActivityDestroyed(Activity activity) { }
    }
}
