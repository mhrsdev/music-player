package com.mhrsdev.musicplayer.diag;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.display.DisplayManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.view.Display;
import android.view.FrameMetrics;
import android.view.Window;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Local performance telemetry (owner D38, PT1): where does the app jank on a real phone? Frame timing per screen
 * (FrameMetrics), plus a 5-second snapshot of thermal / battery / memory / GC / threads / CPU, shared as text from
 * Settings &gt; About &gt; Diagnostics. Nothing leaves the phone unless the person shares the report.
 *
 * <p><b>Cost.</b> OFF (the default, and after every process start): no listener, no thread, no timer; the only work is
 * {@link #setScreen} / {@link #setFlag}, which are a few array / atomic writes on screen changes (rare events, never per
 * frame). ON: one frame-metrics listener on the main window, delivered on a dedicated background {@code HandlerThread}
 * (no main-thread work, no allocation per frame: the callback reads the metrics directly into a reused array and
 * {@link PerfStats} updates fixed arrays), one 5 s tick on that thread, auto-stop after {@link #MAX_RECORD_MS} or
 * {@link #BACKGROUND_STOP_MS} after the app left the screen (unless music is playing: screen-off drain is then measured, up to the 10 minutes).
 */
public class PerfProbe {
    /** Decorations active at the moment of a frame; set by their owners (any thread), read per frame. */
    public static final int F_PLAYING = 1;
    public static final int F_LYRICS = 1 << 1;
    public static final int F_LYRICS_BLUR = 1 << 2;
    public static final int F_BACKDROP = 1 << 3;
    public static final int F_BEAT = 1 << 4;
    public static final int F_SPRING = 1 << 5;
    public static final int F_MINI = 1 << 6;

    public static final long MAX_RECORD_MS = 10 * 60_000L;
    public static final long BACKGROUND_STOP_MS = 60_000L;
    public static final long SAMPLE_MS = 5_000L;
    static final String LYRICS_SCREEN = "PlayerFragment";
    /** Per-thread CPU is read every this many samples (30 s). */
    static final int THREAD_EVERY = 6;

    /** What only the app knows: the sampler asks it on its own thread, so both must be safe to call from any thread. */
    public interface AppState {
        boolean playing();

        /** E.g. "Full", "Balanced (thermal-adapted from Full)". */
        String profile();
    }

    /** What the Diagnostics card shows. */
    public record Summary(boolean recording, boolean hasData, long frames, double jankyPercent, @Nullable String worstScreen,
            double worstPercent, long elapsedMs) { }

    /** Test seam: the one place that talks to {@link Window}. */
    interface Hook {
        void add(Window w, Window.OnFrameMetricsAvailableListener l, Handler h);

        void remove(Window w, Window.OnFrameMetricsAvailableListener l);
    }

    static Hook hook = new Hook() {
        @Override public void add(Window w, Window.OnFrameMetricsAvailableListener l, Handler h) {
            w.addOnFrameMetricsAvailableListener(l, h);
        }

        @Override public void remove(Window w, Window.OnFrameMetricsAvailableListener l) {
            w.removeOnFrameMetricsAvailableListener(l);
        }
    };

    private static final AtomicInteger FLAGS = new AtomicInteger();
    private static final MutableLiveData<Integer> CHANGES = new MutableLiveData<>(0);
    private static int version;
    private static final String[] STACK = new String[8];
    private static int depth;
    /** The recording in progress, or the last finished one (kept until Clear or the next start). */
    private static Session current;

    private PerfProbe() { }

    // ---- cheap hooks, always on ----

    /** A screen (fragment) is now resumed: it is the one frames are attributed to until it pauses. Main thread. */
    public static synchronized void setScreen(String name) {
        removeFromStack(name);
        if (depth == STACK.length) {
            System.arraycopy(STACK, 1, STACK, 0, depth - 1);
            depth--;
        }
        STACK[depth++] = name;
        retag();
    }

    /** The screen paused / went away: frames go back to the one below it. Main thread. */
    public static synchronized void leaveScreen(String name) {
        removeFromStack(name);
        retag();
    }

    private static void removeFromStack(String name) {
        for (int i = 0; i < depth; i++) {
            if (STACK[i].equals(name)) {
                System.arraycopy(STACK, i + 1, STACK, i, depth - i - 1);
                STACK[--depth] = null;
                return;
            }
        }
    }

    /** Sets or clears a decoration bit ({@code F_*}); any thread, allocation-free. */
    public static void setFlag(int flag, boolean on) {
        int old, next;
        do {
            old = FLAGS.get();
            next = on ? old | flag : old & ~flag;
            if (next == old) return;
        } while (!FLAGS.compareAndSet(old, next));
        if (flag == F_LYRICS) synchronized (PerfProbe.class) { retag(); }
    }

    private static String currentTag() {
        String top = depth == 0 ? "-" : STACK[depth - 1];
        return (FLAGS.get() & F_LYRICS) != 0 && LYRICS_SCREEN.equals(top) ? top + "+lyrics" : top;
    }

    private static void retag() {
        Session s = current;
        if (s != null && s.active) s.onTag(currentTag());
    }

    // ---- control (main thread) ----

    public static synchronized boolean isRecording() {
        return current != null && current.active;
    }

    /** Starts a recording on {@code activity}'s window (a previous result is discarded). No-op when already recording. */
    public static synchronized void start(Activity activity, AppState state) {
        if (isRecording()) return;
        current = new Session(activity, state);
        current.begin(activity, currentTag());
        notifyChanged();
    }

    public static synchronized void stop() {
        if (isRecording()) current.end("stopped by user");
    }

    /** Stops and forgets everything recorded. */
    public static synchronized void clear() {
        if (current != null) {
            if (current.active) current.end("cleared");
            current = null;
        }
        notifyChanged();
    }

    /** Bumped on start / stop / clear and after every sample: the card re-reads {@link #summary()}. */
    public static LiveData<Integer> changes() {
        return CHANGES;
    }

    private static synchronized void notifyChanged() {
        CHANGES.postValue(++version);
    }

    public static synchronized Summary summary() {
        Session s = current;
        if (s == null) return new Summary(false, false, 0, 0, null, 0, 0);
        long frames = s.stats.totalFrames();
        PerfStats.Slot worst = s.stats.worstScreen(30);
        return new Summary(s.active, true, frames, frames == 0 ? 0 : 100.0 * s.stats.totalJanky() / frames,
                worst == null ? null : worst.tag, worst == null ? 0 : worst.jankPercent(), s.elapsedMs());
    }

    /** The shareable text, or null when nothing was recorded. File-free CPU work only: call off the main thread. */
    @Nullable public static String report(String header) {
        Session s;
        synchronized (PerfProbe.class) {
            s = current;
        }
        if (s == null) return null;
        s.flushActive();
        return PerfReport.build(new PerfReport.Meta(header, s.wallStart, s.elapsedMs(), s.endReason, s.profileNow(),
                s.threads.groups(), clockTicks()), s.stats, s.timeline);
    }

    // ---- one recording ----

    private static class Session implements Application.ActivityLifecycleCallbacks {
        final PerfStats stats;
        final PerfTimeline timeline = new PerfTimeline();
        final PerfThreads threads = new PerfThreads();
        final int pid = Process.myPid();
        int samplesSinceThreads;
        final Application app;
        final AppState state;
        final Class<?> hostClass;
        final long wallStart = System.currentTimeMillis();
        final long startElapsed = SystemClock.elapsedRealtime();
        final HandlerThread thread = new HandlerThread("perf-probe", Process.THREAD_PRIORITY_BACKGROUND);
        final Handler handler;
        final Handler main = new Handler(Looper.getMainLooper());
        final long[] phases = new long[PerfStats.PHASE_COUNT]; // probe thread only
        final PerfTimeline.Sample sample = new PerfTimeline.Sample(); // probe thread only
        final IntentFilter batteryFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        final Window.OnFrameMetricsAvailableListener listener = this::onFrame;
        final Runnable sampler = this::sampleAndSchedule;
        final Runnable lastThreadRead = this::readThreads;
        final Runnable stopForTime = () -> main.post(() -> stopIfCurrent("auto-stop after 10 minutes"));
        final Runnable stopForBackground = this::backgroundCheck;

        volatile boolean active;
        volatile int slot;
        volatile long intervalNs = 16_666_667L;
        long slotSince;
        long endElapsed;
        String endReason = "recording";
        @Nullable WeakReference<Window> attached;
        long prevCpuMs, prevWallMs, prevFrames;

        Session(Activity a, AppState state) {
            this.app = a.getApplication();
            this.state = state;
            this.hostClass = a.getClass();
            this.stats = new PerfStats(SystemClock.elapsedRealtime());
            thread.start();
            handler = new Handler(thread.getLooper());
        }

        void begin(Activity a, String tag) {
            active = true;
            slot = stats.slotFor(tag);
            slotSince = SystemClock.elapsedRealtime();
            int hz = refreshHz(app);
            if (hz > 0) intervalNs = 1_000_000_000L / hz;
            attach(a.getWindow());
            app.registerActivityLifecycleCallbacks(this);
            prevCpuMs = Process.getElapsedCpuTime();
            prevWallMs = SystemClock.elapsedRealtime();
            handler.post(lastThreadRead); // baseline for the per-thread CPU table
            handler.postDelayed(sampler, SAMPLE_MS);
            handler.postDelayed(stopForTime, MAX_RECORD_MS);
        }

        /** Probe thread, a minute after the app left the screen: stop, unless music is playing (screen-off drain is the point). */
        void backgroundCheck() {
            if (active && state.playing()) handler.postDelayed(stopForBackground, BACKGROUND_STOP_MS);
            else main.post(() -> stopIfCurrent("auto-stop: app in background for over 1 minute"));
        }

        private void stopIfCurrent(String reason) {
            synchronized (PerfProbe.class) {
                if (current == this && active) end(reason);
            }
        }

        /** Main thread, under the class lock. */
        void end(String reason) {
            if (!active) return;
            active = false;
            endReason = reason;
            endElapsed = SystemClock.elapsedRealtime();
            detach();
            app.unregisterActivityLifecycleCallbacks(this);
            handler.removeCallbacksAndMessages(null);
            handler.post(lastThreadRead); // the closing read; quitSafely lets it run
            thread.quitSafely();
            flushActive();
            notifyChanged();
        }

        private void attach(Window w) {
            WeakReference<Window> old = attached;
            if (old != null && old.get() == w) return;
            detach();
            try {
                hook.add(w, listener, handler);
                attached = new WeakReference<>(w);
            } catch (RuntimeException e) {
                attached = null; // a window without a surface: nothing to measure, the sampler still runs
            }
        }

        private void detach() {
            WeakReference<Window> old = attached;
            attached = null;
            Window w = old == null ? null : old.get();
            if (w != null) {
                try {
                    hook.remove(w, listener);
                } catch (RuntimeException ignored) {
                    // the window is already gone
                }
            }
        }

        synchronized void onTag(String tag) {
            long now = SystemClock.elapsedRealtime();
            stats.addActive(slot, now - slotSince);
            stats.tick(slot, now);
            slotSince = now;
            slot = stats.slotFor(tag);
        }

        synchronized void flushActive() {
            long now = active ? SystemClock.elapsedRealtime() : endElapsed;
            stats.tick(slot, now);
            if (now <= slotSince) return;
            stats.addActive(slot, now - slotSince);
            slotSince = now;
        }

        long elapsedMs() {
            return (active ? SystemClock.elapsedRealtime() : endElapsed) - startElapsed;
        }

        String profileNow() {
            String p = timeline.lastProfile();
            return p != null ? p : "?";
        }

        // ---- per frame (probe thread, no allocation) ----

        void onFrame(Window w, FrameMetrics m, int dropped) {
            if (dropped > 0) stats.addDropped(dropped);
            if (m.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1) { // a window's first frame (inflation): not steady state
                stats.addFirstDraw();
                return;
            }
            long[] p = phases;
            p[0] = m.getMetric(FrameMetrics.UNKNOWN_DELAY_DURATION);
            p[1] = m.getMetric(FrameMetrics.INPUT_HANDLING_DURATION);
            p[2] = m.getMetric(FrameMetrics.ANIMATION_DURATION);
            p[3] = m.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION);
            p[4] = m.getMetric(FrameMetrics.DRAW_DURATION);
            p[5] = m.getMetric(FrameMetrics.SYNC_DURATION);
            p[6] = m.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION);
            p[7] = m.getMetric(FrameMetrics.SWAP_BUFFERS_DURATION);
            long interval = intervalNs;
            if (Build.VERSION.SDK_INT >= 31) {
                p[8] = m.getMetric(FrameMetrics.GPU_DURATION);
                interval = deadline(m.getMetric(FrameMetrics.DEADLINE), m.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP), interval);
            } else {
                p[8] = 0;
            }
            stats.record(slot, FLAGS.get(), m.getMetric(FrameMetrics.TOTAL_DURATION), p, interval, SystemClock.elapsedRealtime(),
                    p[1] > 0); // input handled in this frame = the person is touching the app
        }

        // ---- every 5 s (probe thread) ----

        void sampleAndSchedule() {
            if (!active) return;
            try {
                sampleNow();
            } catch (RuntimeException ignored) {
                // a failing system call must never stop the recording
            }
            if (active) handler.postDelayed(sampler, SAMPLE_MS);
        }

        void sampleNow() {
            PerfTimeline.Sample s = sample;
            long now = SystemClock.elapsedRealtime(), cpu = Process.getElapsedCpuTime();
            long dWall = Math.max(1, now - prevWallMs);
            s.atMs = now - startElapsed;
            s.cpuPercent = (int) ((cpu - prevCpuMs) * 100 / dWall);
            long frames = stats.totalFrames();
            s.fps = (int) ((frames - prevFrames) * 1000 / dWall);
            prevCpuMs = cpu;
            prevWallMs = now;
            prevFrames = frames;

            s.thermal = PerfTimeline.UNKNOWN;
            if (Build.VERSION.SDK_INT >= 29) {
                PowerManager pm = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
                if (pm != null) s.thermal = pm.getCurrentThermalStatus();
            }
            s.battTempDeci = PerfTimeline.UNKNOWN;
            s.battLevel = PerfTimeline.UNKNOWN;
            s.voltageMv = PerfTimeline.UNKNOWN;
            Intent b = app.registerReceiver(null, batteryFilter); // sticky: no receiver is registered
            if (b != null) {
                s.battTempDeci = b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, PerfTimeline.UNKNOWN);
                int scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                s.battLevel = scale <= 0 ? PerfTimeline.UNKNOWN : b.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / scale;
                s.charging = b.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
                s.voltageMv = b.getIntExtra(BatteryManager.EXTRA_VOLTAGE, PerfTimeline.UNKNOWN);
            }
            BatteryManager bm = (BatteryManager) app.getSystemService(Context.BATTERY_SERVICE);
            s.currentUa = PerfTimeline.NO_CURRENT;
            s.chargeUah = PerfTimeline.UNKNOWN;
            if (bm != null) { // many makers answer 0 or MIN_VALUE for what they do not support
                long cur = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                if (cur != 0 && cur != Long.MIN_VALUE && cur != Integer.MIN_VALUE) s.currentUa = cur;
                long charge = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
                if (charge > 0 && charge != Long.MIN_VALUE) s.chargeUah = charge;
            }
            PowerManager screenPm = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
            s.screenOn = screenPm == null || screenPm.isInteractive();
            stats.tick(slot, now);
            Runtime rt = Runtime.getRuntime();
            s.javaUsedKb = (rt.totalMemory() - rt.freeMemory()) >> 10;
            s.javaMaxKb = rt.maxMemory() >> 10;
            s.nativeKb = Debug.getNativeHeapAllocatedSize() >> 10;
            s.gcCount = runtimeStat("art.gc.gc-count");
            s.gcBlockingCount = runtimeStat("art.gc.blocking-gc-count");
            s.gcBlockingMs = runtimeStat("art.gc.blocking-gc-time");
            s.threads = threadCount();
            s.refreshHz = refreshHz(app);
            if (s.refreshHz > 0) intervalNs = 1_000_000_000L / s.refreshHz;
            s.playing = state.playing();
            s.lyricsOpen = (FLAGS.get() & F_LYRICS) != 0;
            setFlag(F_PLAYING, s.playing);
            timeline.add(s, state.profile());
            if (++samplesSinceThreads >= THREAD_EVERY) {
                samplesSinceThreads = 0;
                readThreads();
            }
            notifyChanged();
        }

        /** One pass over /proc/self/task: a few dozen tiny files, every 30 s, on the probe thread. */
        void readThreads() {
            String[] ids = new java.io.File("/proc/self/task").list();
            if (ids == null) return;
            for (String id : ids) {
                try {
                    String stat = readFirstLine("/proc/self/task/" + id + "/stat");
                    long ticks = stat == null ? -1 : PerfThreads.ticksOf(stat);
                    if (ticks < 0) continue;
                    int tid = Integer.parseInt(id);
                    threads.sample(tid, tid == pid ? "main" : PerfThreads.commOf(stat), ticks);
                } catch (RuntimeException ignored) {
                    // the thread ended while we looked
                }
            }
            threads.endPass();
        }

        // ---- activity lifecycle: only the host activity counts ----

        @Override public void onActivityStarted(@NonNull Activity a) {
            if (a.getClass() != hostClass) return;
            handler.removeCallbacks(stopForBackground);
            stats.setForeground(true, slot, SystemClock.elapsedRealtime());
            attach(a.getWindow()); // after a rotation the window is a new one
        }

        @Override public void onActivityStopped(@NonNull Activity a) {
            if (a.getClass() != hostClass) return;
            stats.setForeground(false, slot, SystemClock.elapsedRealtime());
            handler.postDelayed(stopForBackground, BACKGROUND_STOP_MS);
        }

        @Override public void onActivityDestroyed(@NonNull Activity a) {
            WeakReference<Window> w = attached;
            if (w != null && w.get() == a.getWindow()) detach();
        }

        @Override public void onActivityCreated(@NonNull Activity a, @Nullable Bundle b) { }
        @Override public void onActivityResumed(@NonNull Activity a) { }
        @Override public void onActivityPaused(@NonNull Activity a) { }
        @Override public void onActivitySaveInstanceState(@NonNull Activity a, @NonNull Bundle b) { }
    }

    // ---- helpers (package-private for tests) ----

    /**
     * The frame budget from the frame timeline (API 31+). {@code DEADLINE} is documented as a time; the code accepts both
     * an absolute timestamp (minus the intended vsync) and a plain duration, and falls back to the display interval when
     * neither is plausible (3 to 100 ms).
     */
    static long deadline(long deadline, long intendedVsync, long fallback) {
        long d = deadline - intendedVsync;
        if (d >= 3_000_000L && d <= 100_000_000L) return d;
        if (deadline >= 3_000_000L && deadline <= 100_000_000L) return deadline;
        return fallback;
    }

    static long runtimeStat(String key) {
        try {
            String v = Debug.getRuntimeStat(key);
            return v == null ? PerfTimeline.UNKNOWN : Long.parseLong(v.trim());
        } catch (RuntimeException e) { // unknown key, or not a number
            return PerfTimeline.UNKNOWN;
        }
    }

    /** The "Threads:" line of /proc/self/status (cheaper than dumping every stack), -1 when unreadable. */
    static int threadCount() {
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/self/status"), 512)) {
            for (String line = r.readLine(); line != null; line = r.readLine()) {
                if (line.startsWith("Threads:")) return Integer.parseInt(line.substring(8).trim());
            }
        } catch (IOException | RuntimeException ignored) {
            // not readable on this device
        }
        return PerfTimeline.UNKNOWN;
    }

    private static String readFirstLine(String path) {
        try (BufferedReader r = new BufferedReader(new FileReader(path), 256)) {
            return r.readLine();
        } catch (IOException e) {
            return null;
        }
    }

    /** Clock ticks per second of /proc CPU times (100 on every Android kernel in use). */
    static int clockTicks() {
        try {
            long hz = android.system.Os.sysconf(android.system.OsConstants._SC_CLK_TCK);
            return hz > 0 ? (int) hz : 100;
        } catch (RuntimeException e) {
            return 100;
        }
    }

    private static int refreshHz(Context c) {
        DisplayManager dm = (DisplayManager) c.getSystemService(Context.DISPLAY_SERVICE);
        Display d = dm == null ? null : dm.getDisplay(Display.DEFAULT_DISPLAY);
        return d == null ? 0 : Math.round(d.getRefreshRate());
    }

    /** The running (or last) recording's numbers, for render tests that seed frames; null when nothing was started. */
    @androidx.annotation.VisibleForTesting
    @Nullable public static synchronized PerfStats statsForTest() {
        return current == null ? null : current.stats;
    }

    /** Tests only: one sampler pass on the calling thread. */
    static synchronized void sampleOnceForTest() {
        current.sampleNow();
    }

    /** Tests only: forgets everything including the screen stack. */
    static synchronized void resetForTest() {
        if (current != null && current.active) current.end("test");
        current = null;
        FLAGS.set(0);
        depth = 0;
        java.util.Arrays.fill(STACK, null);
    }
}
