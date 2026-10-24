package com.mhrsdev.musicplayer.diag;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Android 11+: why did the previous processes end (crash, native crash, ANR, low-memory kill, user swipe...)? Read
 * once per process start on a short-lived background thread from {@code ActivityManager}'s exit history, compacted
 * into {@link Diagnostics}; new entries only (de-duplicated by timestamp). Also tags the running process with a tiny
 * state summary (last screen) that the next start reads back from its exit record.
 */
public class ExitInfoReader {
    static final int READ_MAX = 5;
    private static final int SUMMARY_MAX = 120; // platform limit is 128 bytes

    private ExitInfoReader() { }

    /** Starts the one-off read; no-op below Android 11. */
    public static void readAsync(Context context) {
        if (Build.VERSION.SDK_INT < 30) return;
        Context app = context.getApplicationContext();
        Thread t = new Thread(() -> {
            try {
                read(app);
            } catch (Throwable ignored) {
                // diagnostics never break the app
            }
        }, "exit-info");
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    @RequiresApi(30) @WorkerThread
    private static void read(Context context) throws IOException {
        ActivityManager am = context.getSystemService(ActivityManager.class);
        if (am == null) return;
        List<ApplicationExitInfo> infos = am.getHistoricalProcessExitReasons(context.getPackageName(), 0, READ_MAX);
        Diagnostics store = Diagnostics.get(context);
        List<ExitRecord> stored = store.exits();
        Set<Long> known = new HashSet<>();
        for (ExitRecord r : stored) known.add(r.timestamp());
        List<ExitRecord> fresh = new ArrayList<>();
        Map<Long, String> traces = new java.util.HashMap<>();
        for (ApplicationExitInfo i : infos) {
            if (known.contains(i.getTimestamp())) continue;
            byte[] s = i.getProcessStateSummary();
            ExitRecord r = new ExitRecord(i.getTimestamp(), i.getReason(), i.getStatus(), i.getImportance(), i.getPss(),
                    i.getRss(), ExitRecord.clean(i.getDescription()),
                    s == null ? "" : ExitRecord.clean(new String(s, StandardCharsets.UTF_8)));
            fresh.add(r);
            if (r.reason() == ExitReasons.ANR || r.reason() == ExitReasons.CRASH_NATIVE) {
                String t = trace(i, r.reason() == ExitReasons.CRASH_NATIVE);
                if (t != null) traces.put(r.timestamp(), t);
            }
        }
        if (ingest(store, stored, fresh, traces)) Diagnostics.notifyChanged();
    }

    /** Stores the new records and their traces; true when anything was added. */
    @VisibleForTesting @WorkerThread
    static boolean ingest(Diagnostics store, List<ExitRecord> stored, List<ExitRecord> fresh, Map<Long, String> traces)
            throws IOException {
        if (fresh.isEmpty()) return false;
        for (Map.Entry<Long, String> e : traces.entrySet()) store.saveTrace(e.getKey(), e.getValue());
        store.saveExits(ExitRecord.merge(stored, fresh, Diagnostics.MAX_EXITS));
        return true;
    }

    /** ANR: the text dump (main thread first). Native crash: a binary tombstone, so only its readable strings. */
    @RequiresApi(30) @Nullable
    private static String trace(ApplicationExitInfo info, boolean binary) {
        try (InputStream in = info.getTraceInputStream()) {
            if (in == null) return null;
            String raw = Diagnostics.readTrimmed(in, binary ? Diagnostics.TRACE_MAX * 4 : Diagnostics.TRACE_MAX);
            return binary ? printable(raw, Diagnostics.TRACE_MAX) : raw;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Like {@code strings}: runs of 4+ printable characters, one per line, at most {@code max} chars. */
    @VisibleForTesting
    static String printable(String raw, int max) {
        StringBuilder out = new StringBuilder();
        StringBuilder run = new StringBuilder();
        for (int i = 0; i <= raw.length() && out.length() < max; i++) {
            char c = i < raw.length() ? raw.charAt(i) : 0;
            if (c >= 0x20 && c < 0x7f) {
                run.append(c);
            } else {
                if (run.length() >= 4) out.append(run).append('\n');
                run.setLength(0);
            }
        }
        return out.length() > max ? out.substring(0, max) : out.toString();
    }

    /**
     * Android 11+: attaches {@code state} (e.g. "screen=HomeFragment") to this process, so if the system kills it the
     * next start sees what was on screen. One small binder call; call on rare events only (screen changes).
     */
    public static void setStateSummary(Context context, String state) {
        if (Build.VERSION.SDK_INT < 30) return;
        try {
            ActivityManager am = context.getSystemService(ActivityManager.class);
            byte[] b = state.getBytes(StandardCharsets.UTF_8);
            if (b.length > SUMMARY_MAX) b = java.util.Arrays.copyOf(b, SUMMARY_MAX);
            if (am != null) am.setProcessStateSummary(b);
        } catch (Throwable ignored) {
            // diagnostics never break the app
        }
    }
}
