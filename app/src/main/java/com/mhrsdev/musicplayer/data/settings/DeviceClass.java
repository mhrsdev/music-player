package com.mhrsdev.musicplayer.data.settings;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import com.mhrsdev.musicplayer.data.settings.PerformanceProfile.Preset;

/**
 * What kind of phone this is (C1, D32), and the one place that turns it into defaults and cache budgets. The rules are
 * pure ({@link #presetFor}, the budget functions) so they are unit-tested for every class; {@link #detect} only reads
 * four platform numbers once.
 *
 * <p>Default Performance preset on first run (never over a saved choice, see {@code SettingsStore}):
 * <ul>
 * <li>LIGHT: very weak = ({@code isLowRamDevice} and heap class &lt;= 96 MB) or (API 26-27 with &lt;= 3 GB RAM)</li>
 * <li>BALANCED: {@code isLowRamDevice}, or heap class &lt;= 128 MB, or &lt;= 4 CPU cores, or API &lt;= 28</li>
 * <li>FULL: everything else</li>
 * </ul>
 */
public class DeviceClass {
    /** Platform facts the rules use. {@code memoryClassMb} is the per-app heap class, {@code totalMemMb} the RAM. */
    public record Signals(boolean lowRamDevice, int memoryClassMb, int cores, int sdk, long totalMemMb) { }

    private static final long MB = 1024 * 1024;
    private static volatile Signals cached;

    private DeviceClass() { }

    public static Signals detect(Context context) {
        Signals s = cached;
        if (s != null) return s;
        ActivityManager am = (ActivityManager) context.getApplicationContext().getSystemService(Context.ACTIVITY_SERVICE);
        boolean low = false;
        int memoryClass = (int) (Runtime.getRuntime().maxMemory() / MB);
        long total = Long.MAX_VALUE / MB;
        if (am != null) {
            low = am.isLowRamDevice();
            memoryClass = am.getMemoryClass();
            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(info);
            if (info.totalMem > 0) total = info.totalMem / MB;
        }
        return cached = new Signals(low, memoryClass, Runtime.getRuntime().availableProcessors(), Build.VERSION.SDK_INT, total);
    }

    /** The first-run preset for this phone. {@code -Dmowj.preset=FULL} (set by the unit-test task) overrides the rules. */
    public static Preset firstRunPreset(Context context) {
        String forced = System.getProperty("mowj.preset");
        if (forced != null) return Preset.valueOf(forced);
        return presetFor(detect(context));
    }

    public static Preset presetFor(Signals s) {
        if (veryWeak(s)) return Preset.LIGHT;
        if (s.lowRamDevice() || s.memoryClassMb() <= 128 || s.cores() <= 4 || s.sdk() <= 28) return Preset.BALANCED;
        return Preset.FULL;
    }

    public static boolean veryWeak(Signals s) {
        return (s.lowRamDevice() && s.memoryClassMb() <= 96) || (s.sdk() <= 27 && s.totalMemMb() <= 3 * 1024);
    }

    /** Small heap: every cache and decode is sized down, and the per-line lyrics blur (a GPU layer per line) stays off. */
    public static boolean lowEnd(Signals s) {
        return s.lowRamDevice() || s.memoryClassMb() <= 128;
    }

    /** Artwork decodes running at once (each can hold a multi-MB bitmap while it is scaled). */
    public static int artworkWorkers(Signals s) {
        return lowEnd(s) ? 1 : Math.max(1, Math.min(2, s.cores() - 1));
    }

    /** Largest cover bucket decoded, in px: a 1024 px RGB_565 cover is 2 MB, a 768 px one 1.1 MB. */
    public static int maxCoverBucketPx(Signals s) {
        return lowEnd(s) ? 768 : 1024;
    }

    /** ExoPlayer's target buffer for local files, in bytes (the player is audio only, so this is mostly read-ahead). */
    public static int playerBufferBytes(Signals s) {
        return lowEnd(s) ? 2 * 1024 * 1024 : 4 * 1024 * 1024;
    }

    /** Notification / lock-screen cover cache, in bytes. */
    public static int notificationCoverCacheBytes(Signals s) {
        return lowEnd(s) ? 2 * 1024 * 1024 : 4 * 1024 * 1024;
    }
}
