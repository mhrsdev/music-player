package com.mhrsdev.musicplayer.diag;

import java.util.List;
import java.util.Map;

/** Seeds a realistic store for renders and smoke tests: a crash on Home plus a low-memory kill before it. */
public class DiagnosticsFixtures {
    private DiagnosticsFixtures() { }

    public static void seed(Diagnostics store, long now) throws Exception {
        Breadcrumbs.add("screen HomeFragment");
        Breadcrumbs.add("scan end +0 ~0 -0");
        CrashReporter r = new CrashReporter((t, e) -> { }, store, Diagnostics.deviceInfo("0.3.0", 30));
        IllegalStateException e = new IllegalStateException("Cannot draw recycled bitmaps");
        e.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("android.graphics.BaseCanvas", "throwIfCannotDraw", "BaseCanvas.java", 77),
                new StackTraceElement("com.mhrsdev.musicplayer.ui.widget.ArtworkView", "onDraw", "ArtworkView.java", 248),
                new StackTraceElement("android.view.View", "draw", "View.java", 25013)});
        store.writeCrash(r.format(new Thread("main"), e, now - 90_000), now - 90_000);
        ExitInfoReader.ingest(store, List.of(), List.of(
                new ExitRecord(now - 90_000, ExitReasons.CRASH, 0, 100, 312_000, 401_000, "crash", "screen=HomeFragment"),
                new ExitRecord(now - 86_400_000, ExitReasons.LOW_MEMORY, 9, 125, 640_000, 820_000, "lmk", "")), Map.of());
    }

    public static void seedLowMemoryOnly(Diagnostics store, long now) throws Exception {
        ExitInfoReader.ingest(store, List.of(), List.of(
                new ExitRecord(now - 120_000, ExitReasons.LOW_MEMORY, 9, 100, 640_000, 820_000, "lmk", "screen=HomeFragment")),
                Map.of());
    }
}
