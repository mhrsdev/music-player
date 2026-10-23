package com.mhrsdev.musicplayer;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import android.os.Trace;
import com.mhrsdev.musicplayer.data.settings.BatterySaverMode;
import com.mhrsdev.musicplayer.di.AppContainer;
import com.mhrsdev.musicplayer.ui.settings.ThemeApplier;
import com.mhrsdev.musicplayer.ui.theme.MotionPolicy;

public class MusicPlayerApp extends Application {
    private AppContainer container;

    @Override public void onCreate() {
        super.onCreate();
        Trace.beginSection("MusicPlayerApp.onCreate"); // visible in Perfetto; the container is lazy except the player
        container = new AppContainer(this);
        Trace.endSection();
        // Cheap: one static write per change of the setting, no polling.
        container.settings().batterySaver().observeForever(mode -> MotionPolicy.setBatterySaverOverride(
                mode == BatterySaverMode.AUTO ? null : mode == BatterySaverMode.ON));
        // Below API 33 AppCompat keeps the chosen locale only in memory: re-apply the stored choice on every start.
        if (Build.VERSION.SDK_INT < 33) ThemeApplier.applyLanguage(container.settings().language().getValue());
        registerActivityLifecycleCallbacks(new FirstActivityStart());
    }

    public AppContainer getContainer() {
        return container;
    }

    /** Fires {@link AppContainer#onAppForeground()} for the first started activity, then removes itself. */
    private final class FirstActivityStart implements ActivityLifecycleCallbacks {
        @Override public void onActivityStarted(Activity activity) {
            unregisterActivityLifecycleCallbacks(this);
            container.onAppForeground();
        }

        @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) { }
        @Override public void onActivityResumed(Activity activity) { }
        @Override public void onActivityPaused(Activity activity) { }
        @Override public void onActivityStopped(Activity activity) { }
        @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) { }
        @Override public void onActivityDestroyed(Activity activity) { }
    }
}
