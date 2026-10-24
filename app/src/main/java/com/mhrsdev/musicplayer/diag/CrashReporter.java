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
    /** Non-fatal reports per process at most (a failing task in a loop must not turn into an I/O storm). */
    static final int NON_FATAL_MAX = 5;
    @Nullable private static volatile CrashReporter installed;
    static final java.util.concurrent.atomic.AtomicInteger NON_FATALS = new java.util.concurrent.atomic.AtomicInteger();

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
            CrashReporter r = new CrashReporter(current, Diagnostics.get(context),
                    Diagnostics.deviceInfo(versionName, versionCode));
            Thread.setDefaultUncaughtExceptionHandler(r);
            installed = r;
        } catch (Throwable ignored) {
            // never stop the app from starting
        }
    }

    /**
     * A caught background failure the app survived: written like a crash (marked non-fatal) so it reaches the
     * Diagnostics screen, at most {@link #NON_FATAL_MAX} per process. Never throws.
     */
    public static void nonFatal(Thread thread, Throwable error) {
        try {
            Breadcrumbs.add("non-fatal " + error);
            CrashReporter r = installed;
            if (r == null || NON_FATALS.incrementAndGet() > NON_FATAL_MAX) return;
            long now = System.currentTimeMillis();
            r.store.writeCrash("NON-FATAL (caught, the app kept running)\n" + r.format(thread, error, now), now);
        } catch (Throwable ignored) {
            // diagnostics never break the app
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
            previous.uncaughtException(thread, error); // Android: the platform's handler (dialog + process kill)
        } else {
            // Plain JVM (tests; never on Android, where the platform always installs one). Not ThreadGroup's
            // uncaughtException: that calls the default handler, i.e. this one, again.
            System.err.print("Exception in thread \"" + thread.getName() + "\" ");
            error.printStackTrace(System.err);
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
