package com.mhrsdev.musicplayer.diag;

import android.content.Context;
import android.os.Debug;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

/**
 * Uncaught-exception logger: writes one small report (time, app/device, thread, full stack trace with causes,
 * breadcrumbs, heap numbers) to {@link Diagnostics} and then hands the crash to the previous handler, so the process
 * still dies exactly as before. Installed first thing in {@code Application.onCreate}; costs nothing until a crash.
 * Crash-proof itself: every step is guarded, and a crash inside the handler goes straight to the previous one.
 */
public class CrashReporter implements Thread.UncaughtExceptionHandler {
    @Nullable private final Thread.UncaughtExceptionHandler previous;
    private final Diagnostics store;
    private final String header;
    private volatile boolean handling;

    @VisibleForTesting
    CrashReporter(@Nullable Thread.UncaughtExceptionHandler previous, Diagnostics store, String header) {
        this.previous = previous;
        this.store = store;
        this.header = header;
    }

    /** Chains onto the current default handler (once; installing twice is a no-op). */
    public static void install(Context context, String versionName, long versionCode) {
        try {
            Thread.UncaughtExceptionHandler current = Thread.getDefaultUncaughtExceptionHandler();
            if (current instanceof CrashReporter) return;
            Thread.setDefaultUncaughtExceptionHandler(new CrashReporter(current, Diagnostics.get(context),
                    Diagnostics.deviceInfo(versionName, versionCode)));
        } catch (Throwable ignored) {
            // never stop the app from starting
        }
    }

    @Override public void uncaughtException(Thread thread, Throwable error) {
        if (!handling) {
            handling = true;
            try {
                long now = System.currentTimeMillis();
                store.writeCrash(format(thread, error, now), now);
            } catch (Throwable ignored) {
                // out of memory / disk full: the crash still goes to the system below
            }
        }
        if (previous != null) {
            previous.uncaughtException(thread, error);
        } else {
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(10);
        }
    }

    @VisibleForTesting
    String format(Thread thread, Throwable error, long now) {
        StringBuilder sb = new StringBuilder(2048);
        sb.append("Crash at ").append(Diagnostics.formatTime(now)).append('\n').append(header).append('\n');
        sb.append("Thread \"").append(thread.getName()).append("\"\n");
        sb.append("pid ").append(android.os.Process.myPid()).append(", process up ")
                .append((android.os.SystemClock.elapsedRealtime() - android.os.Process.getStartElapsedRealtime()) / 1000)
                .append(" s\n");
        Runtime rt = Runtime.getRuntime();
        sb.append("Heap used ").append((rt.totalMemory() - rt.freeMemory()) >> 10).append(" KB of max ")
                .append(rt.maxMemory() >> 10).append(" KB; native ")
                .append(safeNativeHeap() >> 10).append(" KB\n\n");
        StringWriter sw = new StringWriter();
        try (PrintWriter pw = new PrintWriter(sw)) {
            error.printStackTrace(pw); // includes "Caused by" and suppressed
        }
        String trace = sw.toString();
        if (trace.length() > Diagnostics.CRASH_MAX - 4096) trace = trace.substring(0, Diagnostics.CRASH_MAX - 4096) + "\n...";
        sb.append(trace);
        List<String> crumbs = Breadcrumbs.snapshot();
        sb.append("\nBreadcrumbs (oldest first):\n");
        for (String c : crumbs) sb.append("  ").append(c).append('\n');
        return sb.toString();
    }

    private static long safeNativeHeap() {
        try {
            return Debug.getNativeHeapAllocatedSize();
        } catch (Throwable t) {
            return 0;
        }
    }
}
