package com.mhrsdev.musicplayer;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.os.Trace;
import com.mhrsdev.musicplayer.data.settings.BatterySaverMode;
import com.mhrsdev.musicplayer.data.settings.DeviceClass;
import com.mhrsdev.musicplayer.di.AppContainer;
import com.mhrsdev.musicplayer.diag.Breadcrumbs;
import com.mhrsdev.musicplayer.diag.CrashReporter;
import com.mhrsdev.musicplayer.diag.Diagnostics;
import com.mhrsdev.musicplayer.diag.ExitInfoReader;
import com.mhrsdev.musicplayer.ui.settings.ThemeApplier;
import com.mhrsdev.musicplayer.ui.theme.MotionPolicy;
import com.mhrsdev.musicplayer.ui.widget.ArtworkLoader;

public class MusicPlayerApp extends Application {
    private AppContainer container;

    @Override public void onCreate() {
        super.onCreate();
        // First: everything after this line is covered by the crash log (Settings > Diagnostics).
        CrashReporter.install(this, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE);
        Breadcrumbs.add("process start pid " + android.os.Process.myPid());
        if (BuildConfig.DEBUG) { // logcat tags "StrictMode" and "MowjJank"; release builds pay nothing
            enableStrictMode();
            com.mhrsdev.musicplayer.debug.JankLogger.install(this);
        }
        Trace.beginSection("MusicPlayerApp.onCreate"); // visible in Perfetto; the container is lazy except the player
        container = new AppContainer(this);
        Trace.endSection();
        // Cheap: one static write per change of the setting, no polling.
        container.settings().batterySaver().observeForever(mode -> MotionPolicy.setBatterySaverOverride(
                mode == BatterySaverMode.AUTO ? null : mode == BatterySaverMode.ON));
        // Settings > Performance (incl. reduce motion) applied app-wide, live. Registered before any screen exists, so
        // MotionPolicy already holds the new value when a screen's own observer of the same LiveData runs.
        container.settings().chooseDefaultPerformance(() -> DeviceClass.firstRunPreset(this)); // first run only; weaker phones start lighter
        container.settings().performance().observeForever(MotionPolicy::setPerformance);
        // Below API 33 AppCompat keeps the chosen locale only in memory: re-apply the stored choice on every start.
        if (Build.VERSION.SDK_INT < 33) ThemeApplier.applyLanguage(container.settings().language().getValue());
        registerActivityLifecycleCallbacks(new VisibilityCallbacks());
        ExitInfoReader.readAsync(this); // why did the last process end? (Android 11+, background thread, once)
    }

    /** The UI is gone (app backgrounded): bitmaps are the biggest thing the UI process holds; playback keeps running. */
    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        Breadcrumbs.add("trim memory " + level);
        // Memory is running out while visible: a kill may follow and no handler would run, so keep the trail now.
        if (level == TRIM_MEMORY_RUNNING_LOW || level == TRIM_MEMORY_RUNNING_CRITICAL) saveBreadcrumbsAsync();
        if (level >= TRIM_MEMORY_UI_HIDDEN) ArtworkLoader.trimAll();
    }

    @Override public void onLowMemory() {
        super.onLowMemory();
        Breadcrumbs.add("low memory");
        saveBreadcrumbsAsync();
    }

    private void saveBreadcrumbsAsync() {
        Diagnostics d = Diagnostics.get(this);
        new Thread(d::saveBreadcrumbs, "diag-save").start();
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
            Breadcrumbs.add("activity started " + activity.getClass().getSimpleName());
            container.onAppForeground();
        }

        @Override public void onActivityStopped(Activity activity) {
            Breadcrumbs.add("activity stopped " + activity.getClass().getSimpleName());
            if (--started == 0) container.onAppBackground();
        }

        @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) { }
        @Override public void onActivityResumed(Activity activity) { }
        @Override public void onActivityPaused(Activity activity) { }
        @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) { }
        @Override public void onActivityDestroyed(Activity activity) {
            Breadcrumbs.add("activity destroyed " + activity.getClass().getSimpleName()
                    + (activity.isFinishing() ? " (finishing)" : ""));
        }
    }
}
