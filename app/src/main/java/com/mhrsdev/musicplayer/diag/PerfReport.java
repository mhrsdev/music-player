package com.mhrsdev.musicplayer.diag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The shareable text (D38, PT1): readable in a chat, parseable by a script (fixed columns, {@code key=value} header,
 * one CSV block). Capped at {@link #MAX_CHARS}; only the sample CSV is shortened to fit.
 */
public class PerfReport {
    /** Characters (the text is ASCII, so also bytes): well under the 20 KB the Share intent should carry. */
    public static final int MAX_CHARS = 19_000;
    static final int CSV_ROWS = 48;

    /** What the probe knows besides the numbers. */
    public record Meta(String header, long startWallMs, long durationMs, String endReason, String profile,
            List<PerfThreads.Group> threads, int clockTicksPerSecond) { }

    private PerfReport() { }

    public static String build(Meta meta, PerfStats stats, PerfTimeline timeline) {
        List<PerfTimeline.Sample> samples = timeline.samples();
        StringBuilder sb = new StringBuilder(8192);
        sb.append("Mowj performance report v1\n").append(meta.header()).append('\n');
        sb.append(String.format(Locale.US, "recorded=%s duration=%s ended=%s\nprofile=%s\n", Diagnostics.formatTime(meta.startWallMs()),
                PerfFindings.clock(meta.durationMs()), meta.endReason(), meta.profile()));
        long frames = stats.totalFrames(), janky = stats.totalJanky();
        sb.append(String.format(Locale.US, "frames=%d janky=%d (%.1f%%) first-draw-skipped=%d listener-dropped=%d refresh=%s\n",
                frames, janky, frames == 0 ? 0.0 : 100.0 * janky / frames, stats.firstDrawFrames(), stats.droppedReports(),
                refreshRange(samples)));
        PerfPower power = PerfPower.of(samples);
        sb.append(String.format(Locale.US, "screen on in %d of %d samples, music playing in %d\n", power.screenOnSamples, power.samples, power.playingSamples));
        sb.append("janky = longer than one display interval, slow = longer than 2 intervals, frozen = longer than 700 ms (each counts in the one before)\n");

        sb.append("\n== Top findings ==\n");
        List<String> findings = PerfFindings.top(stats, samples, meta.threads(), meta.durationMs(), meta.clockTicksPerSecond());
        for (int i = 0; i < findings.size(); i++) sb.append(i + 1).append(". ").append(findings.get(i)).append('\n');

        sb.append("\n== Per screen (ms) ==\n");
        sb.append(String.format(Locale.US, "%-26s %6s %5s %5s %6s %6s %6s %6s %6s %6s %7s\n", "screen", "frames", "fps", "idle", "jank%",
                "slow%", "frozen", "p50", "p95", "p99", "max"));
        List<PerfStats.Slot> slots = new ArrayList<>();
        stats.read(slots::add);
        slots.sort((a, b) -> Long.compare(b.frames, a.frames));
        for (PerfStats.Slot s : slots) {
            double sec = s.activeMs / 1000.0;
            sb.append(String.format(Locale.US, "%-26s %6d %5s %5s %6.1f %6.1f %6d %6.1f %6.1f %6.1f %7.1f\n", clip(s.tag, 26), s.frames,
                    sec >= 1 ? String.format(Locale.US, "%.0f", s.frames / sec) : "-",
                    s.idleFps() < 0 ? "-" : String.format(Locale.US, "%.0f", s.idleFps()), s.jankPercent(),
                    s.frames == 0 ? 0.0 : 100.0 * s.slow / s.frames, s.frozen, s.percentileMs(0.5), s.percentileMs(0.95),
                    s.percentileMs(0.99), s.maxTotalNs / 1e6));
        }
        sb.append("fps = frames per second while the screen is shown; idle = frames per second while nobody touches the app (should be ~0 when nothing plays)\n");
        sb.append("\n== Per screen detail ==\n");
        for (PerfStats.Slot s : slots) {
            if (s.frames == 0) continue;
            sb.append(s.tag).append('\n').append("  hist% ");
            for (int b = 0; b < PerfStats.BUCKETS; b++) {
                sb.append(PerfStats.BUCKET_LABELS[b]).append('=').append(Math.round(100.0 * s.hist[b] / s.frames)).append(' ');
            }
            sb.append("\n  avg-phase ms (all frames) ").append(phases(s.phaseSum, s.frames));
            if (s.janky > 0) sb.append("\n  avg-phase ms (janky frames) ").append(phases(s.phaseSumJanky, s.janky));
            sb.append(String.format(Locale.US, "\n  worst %.1f ms at %s (interval %.1f): %s | %s\n", s.worstTotalNs / 1e6,
                    PerfFindings.clock(s.worstAtMs), s.worstIntervalNs / 1e6, phasesMs(s.worstPhases), flags(s.worstFlags)));
            if (s.idleMs >= 5_000) {
                sb.append(String.format(Locale.US, "  idle: %d s untouched, %.1f frames/s; decorations in those frames: ", s.idleMs / 1000, s.idleFps()));
                StringBuilder on = new StringBuilder();
                for (int f = 0; f < PerfStats.FLAG_COUNT; f++) {
                    if (s.idleFrames > 0 && s.idleFlagFrames[f] * 2 >= s.idleFrames) on.append(on.length() == 0 ? "" : ",").append(PerfStats.FLAG_NAMES[f]);
                }
                sb.append(on.length() == 0 ? "none" : on).append('\n');
            }
            StringBuilder fl = new StringBuilder();
            for (int f = 0; f < PerfStats.FLAG_COUNT; f++) {
                long on = s.flagFrames[f], off = s.frames - on;
                if (on < 10 || off < 10) continue;
                fl.append(String.format(Locale.US, " %s=%.0f%%/%.0f%%", PerfStats.FLAG_NAMES[f], 100.0 * s.flagJanky[f] / on,
                        100.0 * (s.janky - s.flagJanky[f]) / off));
            }
            if (fl.length() > 0) sb.append("  janky% with/without:").append(fl).append('\n');
        }

        sb.append("\n== Battery / power ==\n");
        powerSection(sb, power);
        sb.append("\n== CPU ==\n");
        cpuSection(sb, power, meta);
        sb.append("\n== Timeline (every 5 s; min / avg / max) ==\n");
        timelineSummary(sb, samples);
        List<Object[]> events = timeline.events();
        if (!events.isEmpty()) {
            sb.append("events:\n");
            for (Object[] e : events) sb.append("  ").append(PerfFindings.clock((Long) e[0])).append(' ').append(e[1]).append('\n');
        }
        String body = sb.toString();
        String csv = csv(samples);
        int room = MAX_CHARS - body.length() - 40;
        if (csv.length() > room) csv = room > 200 ? csv.substring(0, csv.lastIndexOf('\n', room) + 1) + "[truncated]\n" : "";
        String out = csv.isEmpty() ? body : body + "\n== Samples (csv) ==\n" + csv;
        return out.length() > MAX_CHARS ? out.substring(0, MAX_CHARS - 12) + "\n[truncated]" : out;
    }

    private static void powerSection(StringBuilder sb, PerfPower p) {
        if (p.samples == 0) {
            sb.append("(no sample yet)\n");
            return;
        }
        sb.append(String.format(Locale.US, "level %d%% -> %d%% in %s%s\n", p.levelStart, p.levelEnd, PerfFindings.clock(p.spanMs),
                p.anyCharging ? " (charger connected at some point: drain not computed)" : ""));
        if (!Double.isNaN(p.pctPerMin)) {
            sb.append(String.format(Locale.US, "drain %.2f %%/min (typical with music and the screen on: %.1f-%.1f %%/min): %s\n", p.pctPerMin,
                    PerfPower.TYPICAL_PCT_PER_MIN_LOW, PerfPower.TYPICAL_PCT_PER_MIN_HIGH, PerfPower.verdict(p.pctPerMin)));
        }
        if (!Double.isNaN(p.counterMa)) {
            sb.append(String.format(Locale.US, "charge counter: %.1f mAh used = average %.0f mA\n", p.counterMah, p.counterMa));
        }
        if (!Double.isNaN(p.currentMaOn)) sb.append(String.format(Locale.US, "current_now, screen on: %.0f mA avg of %d readings\n", p.currentMaOn, p.currentOnCount));
        if (!Double.isNaN(p.currentMaOff)) sb.append(String.format(Locale.US, "current_now, screen off: %.0f mA avg of %d readings\n", p.currentMaOff, p.currentOffCount));
        if (Double.isNaN(p.counterMa) && Double.isNaN(p.currentMaOn) && Double.isNaN(p.currentMaOff)) {
            sb.append("current / charge counter: not reported by this phone\n");
        }
        if (p.voltStartMv > 0) sb.append(String.format(Locale.US, "voltage %.2f V -> %.2f V, temperature %.1f -> %.1f C\n", p.voltStartMv / 1000.0,
                p.voltEndMv / 1000.0, p.tempStartDeci / 10.0, p.tempEndDeci / 10.0));
    }

    private static void cpuSection(StringBuilder sb, PerfPower p, Meta meta) {
        sb.append("process CPU (percent of ONE core, from the 5 s samples):\n");
        for (int b = 3; b >= 0; b--) {
            if (p.cpuMs[b] == 0) continue;
            sb.append(String.format(Locale.US, "  %-20s %5.1f%% over %d s\n", PerfPower.CPU_BUCKETS[b], p.cpuPercent[b], p.cpuMs[b] / 1000));
        }
        List<PerfThreads.Group> groups = meta.threads();
        long total = PerfThreads.totalTicks(groups);
        if (groups.isEmpty() || total == 0) {
            sb.append("per-thread CPU: no data (recording shorter than the first read, or /proc not readable)\n");
            return;
        }
        sb.append(String.format(Locale.US, "threads by CPU over %s (share of the process, percent of one core, count):\n", PerfFindings.clock(meta.durationMs())));
        for (int i = 0; i < Math.min(8, groups.size()); i++) {
            PerfThreads.Group g = groups.get(i);
            if (g.ticks() == 0) break;
            sb.append(String.format(Locale.US, "  %-26s %5.1f%% %6.1f%% x%d%s\n", clip(g.name(), 26), 100.0 * g.ticks() / total,
                    PerfFindings.corePercent(g.ticks(), meta.durationMs(), meta.clockTicksPerSecond()), g.threads(),
                    g.name().equals("perf-probe") ? "  (this recorder)" : ""));
        }
    }

    private static String clip(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n - 1) + "~";
    }

    private static String phases(long[] sums, long n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < PerfStats.PHASE_COUNT; i++) {
            sb.append(PerfStats.PHASES[i]).append('=').append(String.format(Locale.US, "%.2f", sums[i] / 1e6 / Math.max(1, n))).append(' ');
        }
        return sb.toString().trim();
    }

    private static String phasesMs(long[] ns) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < PerfStats.PHASE_COUNT; i++) {
            sb.append(PerfStats.PHASES[i]).append('=').append(String.format(Locale.US, "%.1f", ns[i] / 1e6)).append(' ');
        }
        return sb.toString().trim();
    }

    private static String flags(int flags) {
        StringBuilder sb = new StringBuilder();
        for (int f = 0; f < PerfStats.FLAG_COUNT; f++) if ((flags & (1 << f)) != 0) sb.append(sb.length() == 0 ? "" : ",").append(PerfStats.FLAG_NAMES[f]);
        return sb.length() == 0 ? "no decorations" : sb.toString();
    }

    private static String refreshRange(List<PerfTimeline.Sample> samples) {
        int lo = Integer.MAX_VALUE, hi = 0;
        for (PerfTimeline.Sample s : samples) {
            if (s.refreshHz <= 0) continue;
            lo = Math.min(lo, s.refreshHz);
            hi = Math.max(hi, s.refreshHz);
        }
        return hi == 0 ? "?" : lo == hi ? lo + "Hz" : lo + "-" + hi + "Hz";
    }

    private static void timelineSummary(StringBuilder sb, List<PerfTimeline.Sample> samples) {
        if (samples.isEmpty()) {
            sb.append("(no sample yet: the recording was shorter than 5 s)\n");
            return;
        }
        PerfTimeline.Sample a = samples.get(0), z = samples.get(samples.size() - 1);
        stat(sb, "thermal", samples, s -> s.thermal, 0);
        stat(sb, "battery temp C", samples, s -> s.battTempDeci, 10.0);
        sb.append(String.format(Locale.US, "battery level %d%% -> %d%%, charging %s -> %s\n", a.battLevel, z.battLevel, a.charging, z.charging));
        stat(sb, "java heap MB", samples, s -> s.javaUsedKb, 1024.0);
        sb.append(String.format(Locale.US, "java heap limit %d MB\n", z.javaMaxKb / 1024));
        stat(sb, "native heap MB", samples, s -> s.nativeKb, 1024.0);
        sb.append(String.format(Locale.US, "GC: %d runs, %d blocking, blocking time %d ms (over %s)\n", z.gcCount - a.gcCount,
                z.gcBlockingCount - a.gcBlockingCount, z.gcBlockingMs - a.gcBlockingMs, PerfFindings.clock(z.atMs - a.atMs)));
        stat(sb, "threads", samples, s -> s.threads, 1);
        stat(sb, "cpu % of one core", samples, s -> s.cpuPercent, 1);
        stat(sb, "frames/s", samples, s -> s.fps, 1);
        long play = samples.stream().filter(s -> s.playing).count(), lyr = samples.stream().filter(s -> s.lyricsOpen).count();
        sb.append(String.format(Locale.US, "playing in %d of %d samples, lyrics open in %d\n", play, samples.size(), lyr));
    }

    private interface Pick {
        double of(PerfTimeline.Sample s);
    }

    private static void stat(StringBuilder sb, String name, List<PerfTimeline.Sample> samples, Pick p, double div) {
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE, sum = 0;
        int n = 0;
        for (PerfTimeline.Sample s : samples) {
            double v = p.of(s);
            if (v < 0) continue; // unknown
            min = Math.min(min, v);
            max = Math.max(max, v);
            sum += v;
            n++;
        }
        if (n == 0) sb.append(name).append(": n/a\n");
        else if (div == 0) sb.append(name).append(": ").append(PerfTimeline.thermalName((int) min)).append(" / ")
                .append(PerfTimeline.thermalName((int) max)).append('\n'); // div 0 = a thermal status, shown by name
        else sb.append(String.format(Locale.US, "%s: %.1f / %.1f / %.1f\n", name, min / div, sum / n / div, max / div));
    }

    private static String csv(List<PerfTimeline.Sample> samples) {
        StringBuilder sb = new StringBuilder("t_s,thermal,batt_c,batt_pct,chg,volt_mv,cur_ma,chg_uah,java_mb,native_mb,gc,gc_block_ms,threads,cpu_pct,fps,hz,screen,play,lyrics\n");
        int step = Math.max(1, (samples.size() + CSV_ROWS - 1) / CSV_ROWS);
        for (int i = 0; i < samples.size(); i += step) {
            PerfTimeline.Sample s = samples.get(i);
            sb.append(String.format(Locale.US, "%d,%d,%.1f,%d,%d,%d,%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d\n", s.atMs / 1000, s.thermal, s.battTempDeci / 10.0,
                    s.battLevel, s.charging ? 1 : 0, s.voltageMv, s.currentUa == PerfTimeline.NO_CURRENT ? "" : Long.toString(s.currentUa / 1000),
                    s.chargeUah, s.javaUsedKb >> 10, s.nativeKb >> 10, s.gcCount, s.gcBlockingMs, s.threads,
                    s.cpuPercent, s.fps, s.refreshHz, s.screenOn ? 1 : 0, s.playing ? 1 : 0, s.lyricsOpen ? 1 : 0));
        }
        return sb.toString();
    }
}
