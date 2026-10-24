package com.mhrsdev.musicplayer.diag;

import java.util.List;

/** Battery drain and CPU use per screen/music state, computed from the 5 s samples (shared by the report and the rules). */
public class PerfPower {
    /** What a normal session costs, from the owner's own phone: 1-2 % per 5 minutes with the screen on. */
    public static final double TYPICAL_PCT_PER_MIN_LOW = 0.2, TYPICAL_PCT_PER_MIN_HIGH = 0.4;

    /** CPU buckets: index = (screenOn ? 2 : 0) + (playing ? 1 : 0). */
    public static final String[] CPU_BUCKETS = {"screen off, paused", "screen off, playing", "screen on, paused", "screen on, playing"};

    public int levelStart = -1, levelEnd = -1;
    public long spanMs;
    public boolean anyCharging;
    public int voltStartMv = -1, voltEndMv = -1, tempStartDeci = -1, tempEndDeci = -1;
    /** Percent of charge per minute, NaN when charging or the span is under a minute. */
    public double pctPerMin = Double.NaN;
    /** Drained charge from the charge counter (mAh) and the average current it implies (mA); NaN when unavailable. */
    public double counterMah = Double.NaN, counterMa = Double.NaN;
    /** Mean |current_now| (mA) while discharging, screen on / off, and the number of readings. */
    public double currentMaOn = Double.NaN, currentMaOff = Double.NaN;
    public int currentOnCount, currentOffCount;
    public final double[] cpuPercent = new double[4];
    public final long[] cpuMs = new long[4];
    public int screenOnSamples, playingSamples, samples;

    public static PerfPower of(List<PerfTimeline.Sample> list) {
        PerfPower p = new PerfPower();
        p.samples = list.size();
        if (list.isEmpty()) return p;
        PerfTimeline.Sample first = list.get(0), last = list.get(list.size() - 1);
        p.spanMs = last.atMs - first.atMs;
        p.levelStart = first.battLevel;
        p.levelEnd = last.battLevel;
        p.voltStartMv = first.voltageMv;
        p.voltEndMv = last.voltageMv;
        p.tempStartDeci = first.battTempDeci;
        p.tempEndDeci = last.battTempDeci;
        double[] cpuSum = new double[4];
        double sumOn = 0, sumOff = 0;
        PerfTimeline.Sample firstCounter = null, lastCounter = null;
        for (int i = 0; i < list.size(); i++) {
            PerfTimeline.Sample s = list.get(i);
            p.anyCharging |= s.charging;
            if (s.screenOn) p.screenOnSamples++;
            if (s.playing) p.playingSamples++;
            if (s.chargeUah > 0) {
                if (firstCounter == null) firstCounter = s;
                lastCounter = s;
            }
            if (s.currentUa != PerfTimeline.NO_CURRENT && !s.charging) {
                double ma = Math.abs(s.currentUa) / 1000.0;
                if (s.screenOn) { sumOn += ma; p.currentOnCount++; } else { sumOff += ma; p.currentOffCount++; }
            }
            if (i > 0) {
                long dt = s.atMs - list.get(i - 1).atMs;
                int b = (s.screenOn ? 2 : 0) + (s.playing ? 1 : 0);
                cpuSum[b] += s.cpuPercent * (double) dt;
                p.cpuMs[b] += dt;
            }
        }
        for (int b = 0; b < 4; b++) p.cpuPercent[b] = p.cpuMs[b] == 0 ? Double.NaN : cpuSum[b] / p.cpuMs[b];
        if (p.currentOnCount > 0) p.currentMaOn = sumOn / p.currentOnCount;
        if (p.currentOffCount > 0) p.currentMaOff = sumOff / p.currentOffCount;
        if (!p.anyCharging && p.spanMs >= 60_000 && p.levelStart >= 0 && p.levelEnd >= 0) {
            p.pctPerMin = (p.levelStart - p.levelEnd) * 60_000.0 / p.spanMs;
        }
        if (!p.anyCharging && firstCounter != null && lastCounter != firstCounter && lastCounter.atMs - firstCounter.atMs >= 30_000) {
            p.counterMah = (firstCounter.chargeUah - lastCounter.chargeUah) / 1000.0;
            p.counterMa = p.counterMah * 3_600_000.0 / (lastCounter.atMs - firstCounter.atMs);
        }
        return p;
    }

    /** "typical" / "HIGH" classification of a drain in percent per minute, as one short phrase. */
    public static String verdict(double pctPerMin) {
        if (Double.isNaN(pctPerMin)) return "";
        if (pctPerMin <= TYPICAL_PCT_PER_MIN_HIGH * 1.5) return "normal";
        return String.format(java.util.Locale.US, "HIGH: %.1fx the typical maximum", pctPerMin / TYPICAL_PCT_PER_MIN_HIGH);
    }
}
