package com.mhrsdev.musicplayer.debug;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.FrameMetrics;
import android.view.Window;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

/**
 * Debug builds only (installed by {@code MusicPlayerApp} behind {@code BuildConfig.DEBUG}): logs every frame slower
 * than 16.7 ms to logcat tag {@code MowjJank} with the visible screen and where the time went, so a tester on a slow
 * phone can send {@code adb logcat -s MowjJank}. The listener lives only while an activity is started, on its own
 * thread, so it adds nothing to the UI thread.
 */
public class JankLogger implements Application.ActivityLifecycleCallbacks {
    public static final String TAG = "MowjJank";
    private static final long BUDGET_NS = 16_700_000L;

    private final HandlerThread thread = new HandlerThread("jank-log");
    private Handler handler;
    private volatile String screen = "-";

    public static void install(Application app) {
        app.registerActivityLifecycleCallbacks(new JankLogger());
    }

    private final Window.OnFrameMetricsAvailableListener listener = (window, metrics, dropped) -> {
        long total = metrics.getMetric(FrameMetrics.TOTAL_DURATION);
        if (total <= BUDGET_NS) return;
        Log.w(TAG, String.format("%s total=%.1fms input=%.1f anim=%.1f layout=%.1f draw=%.1f sync=%.1f issue=%.1f swap=%.1f "
                        + "delay=%.1f dropped=%d first=%b",
                screen, ms(total), ms(metrics.getMetric(FrameMetrics.INPUT_HANDLING_DURATION)),
                ms(metrics.getMetric(FrameMetrics.ANIMATION_DURATION)),
                ms(metrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION)), ms(metrics.getMetric(FrameMetrics.DRAW_DURATION)),
                ms(metrics.getMetric(FrameMetrics.SYNC_DURATION)), ms(metrics.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION)),
                ms(metrics.getMetric(FrameMetrics.SWAP_BUFFERS_DURATION)), ms(metrics.getMetric(FrameMetrics.UNKNOWN_DELAY_DURATION)),
                dropped, metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1));
    };

    private static double ms(long ns) {
        return ns / 1_000_000.0;
    }

    @Override public void onActivityCreated(@NonNull Activity a, Bundle state) {
        if (a instanceof FragmentActivity f) {
            f.getSupportFragmentManager().registerFragmentLifecycleCallbacks(new FragmentManager.FragmentLifecycleCallbacks() {
                @Override public void onFragmentResumed(@NonNull FragmentManager fm, @NonNull Fragment fr) {
                    screen = fr.getClass().getSimpleName();
                }
            }, true);
        }
    }

    @Override public void onActivityStarted(@NonNull Activity a) {
        if (handler == null) {
            thread.start();
            handler = new Handler(thread.getLooper());
        }
        a.getWindow().addOnFrameMetricsAvailableListener(listener, handler);
    }

    @Override public void onActivityStopped(@NonNull Activity a) {
        a.getWindow().removeOnFrameMetricsAvailableListener(listener);
    }

    @Override public void onActivityResumed(@NonNull Activity a) { }
    @Override public void onActivityPaused(@NonNull Activity a) { }
    @Override public void onActivitySaveInstanceState(@NonNull Activity a, @NonNull Bundle out) { }
    @Override public void onActivityDestroyed(@NonNull Activity a) { }
}
