package com.mhrsdev.musicplayer.diag;

import java.util.ArrayList;
import java.util.List;

/**
 * The 5-second snapshots of a recording (thermal, battery, memory, GC, threads, CPU, fps) in a fixed ring of reused
 * {@link Sample}s (10 minutes = 120 samples), plus a few notable events. Pure Java; written by the probe thread, read by
 * the report builder under the same lock.
 */
public class PerfTimeline {
    public static final int CAPACITY = 128;
    public static final int MAX_EVENTS = 24;
    public static final int UNKNOWN = -1;
    /** {@link Sample#currentUa} when the phone gives no current reading (its sign differs between makers, so 0 is a value). */
    public static final long NO_CURRENT = Long.MIN_VALUE;

    /** One snapshot. Unknown numeric values are {@link #UNKNOWN}. */
    public static class Sample {
        public long atMs;            // since the recording started
        public int thermal;          // PowerManager.THERMAL_STATUS_*: 0 none .. 6 shutdown
        public int battTempDeci;     // tenths of a degree C
        public int battLevel;        // percent
        public boolean charging;
        public long javaUsedKb, javaMaxKb, nativeKb;
        public long gcCount, gcBlockingCount, gcBlockingMs; // cumulative since process start
        public int threads;
        public int cpuPercent;       // of ONE core, over the interval since the previous sample
        public int fps;              // frames drawn per second over that interval
        public int refreshHz;        // display mode rate
        public boolean playing, lyricsOpen;
        public boolean screenOn;     // PowerManager.isInteractive() at the sample
        public int voltageMv;
        public long currentUa;       // BATTERY_PROPERTY_CURRENT_NOW, microamps, sign differs by maker
        public long chargeUah;       // BATTERY_PROPERTY_CHARGE_COUNTER, microamp-hours (UNKNOWN when 0 / unsupported)

        void copyFrom(Sample o) {
            atMs = o.atMs; thermal = o.thermal; battTempDeci = o.battTempDeci; battLevel = o.battLevel;
            charging = o.charging; javaUsedKb = o.javaUsedKb; javaMaxKb = o.javaMaxKb; nativeKb = o.nativeKb;
            gcCount = o.gcCount; gcBlockingCount = o.gcBlockingCount; gcBlockingMs = o.gcBlockingMs; threads = o.threads;
            cpuPercent = o.cpuPercent; fps = o.fps; refreshHz = o.refreshHz; playing = o.playing; lyricsOpen = o.lyricsOpen;
            screenOn = o.screenOn; voltageMv = o.voltageMv; currentUa = o.currentUa; chargeUah = o.chargeUah;
        }
    }

    private final Object lock = new Object();
    private final Sample[] ring = new Sample[CAPACITY];
    private final List<String> events = new ArrayList<>();
    private final List<Long> eventTimes = new ArrayList<>();
    private int count; // total samples ever added
    private int lastThermal = UNKNOWN;
    private String lastProfile;
    private int lastScreen = UNKNOWN, lastCharging = UNKNOWN; // 0 / 1

    public PerfTimeline() {
        for (int i = 0; i < CAPACITY; i++) ring[i] = new Sample();
    }

    /** Copies {@code s} into the ring (the oldest is overwritten) and notes thermal / profile changes as events. */
    public void add(Sample s, String profile) {
        synchronized (lock) {
            ring[count % CAPACITY].copyFrom(s);
            count++;
            if (s.thermal != UNKNOWN && s.thermal != lastThermal) {
                if (lastThermal != UNKNOWN) event(s.atMs, "thermal " + thermalName(lastThermal) + " -> " + thermalName(s.thermal));
                else if (s.thermal > 0) event(s.atMs, "thermal already " + thermalName(s.thermal) + " at start");
                lastThermal = s.thermal;
            }
            int screen = s.screenOn ? 1 : 0, chg = s.charging ? 1 : 0;
            if (lastScreen != UNKNOWN && screen != lastScreen) event(s.atMs, s.screenOn ? "screen on" : "screen off");
            if (lastCharging != UNKNOWN && chg != lastCharging) event(s.atMs, s.charging ? "charger connected" : "charger disconnected");
            lastScreen = screen;
            lastCharging = chg;
            if (profile != null && !profile.equals(lastProfile)) {
                if (lastProfile != null) event(s.atMs, "profile " + lastProfile + " -> " + profile);
                lastProfile = profile;
            }
        }
    }

    public void event(long atMs, String text) {
        synchronized (lock) {
            if (events.size() >= MAX_EVENTS) return;
            events.add(text);
            eventTimes.add(atMs);
        }
    }

    public int size() {
        synchronized (lock) {
            return Math.min(count, CAPACITY);
        }
    }

    /** Oldest first. */
    public List<Sample> samples() {
        synchronized (lock) {
            int n = Math.min(count, CAPACITY);
            List<Sample> out = new ArrayList<>(n);
            for (int i = count - n; i < count; i++) {
                Sample c = new Sample();
                c.copyFrom(ring[i % CAPACITY]);
                out.add(c);
            }
            return out;
        }
    }

    /** Events as {@code [timeMs, text]} pairs in order. */
    public List<Object[]> events() {
        synchronized (lock) {
            List<Object[]> out = new ArrayList<>(events.size());
            for (int i = 0; i < events.size(); i++) out.add(new Object[] {eventTimes.get(i), events.get(i)});
            return out;
        }
    }

    public String lastProfile() {
        synchronized (lock) {
            return lastProfile;
        }
    }

    public static String thermalName(int status) {
        return switch (status) {
            case 0 -> "NONE";
            case 1 -> "LIGHT";
            case 2 -> "MODERATE";
            case 3 -> "SEVERE";
            case 4 -> "CRITICAL";
            case 5 -> "EMERGENCY";
            case 6 -> "SHUTDOWN";
            default -> "?";
        };
    }
}
