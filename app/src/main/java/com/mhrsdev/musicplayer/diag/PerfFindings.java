package com.mhrsdev.musicplayer.diag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The "top 3 suspicious findings" of a performance report: simple, explainable rules over {@link PerfStats} and the timeline. */
public class PerfFindings {
    static final int MAX = 3;
    static final long MIN_SCREEN_FRAMES = 60;
    static final double MIN_JANK_PERCENT = 5;
    static final String LYRICS_OR_PLAYER = "PlayerFragment";
    /** By design the player's decorative loops draw 30 frames/s while music plays; everything else should rest. */
    static final double IDLE_FPS = 20, IDLE_FPS_PLAYER = 40;
    static final double DRAIN_PCT_PER_MIN = 0.8;
    static final double CURRENT_SCREEN_ON_MA = 1500, CURRENT_SCREEN_OFF_MA = 100, CURRENT_SCREEN_OFF_PLAYING_MA = 300;
    static final double CPU_ON_PAUSED = 15, CPU_OFF_PAUSED = 3, CPU_OFF_PLAYING = 12;
    static final double RENDER_THREAD_CORE = 10, POOL_THREAD_CORE = 10, MAIN_THREAD_CORE = 40, GC_THREADS_CORE = 3;

    private PerfFindings() { }

    private record Finding(double score, String text) { }

    /** Rules and thresholds: docs/DIAGNOSTICS.md "Findings". {@code hz} is the kernel's clock-tick rate (usually 100). */
    public static List<String> top(PerfStats stats, List<PerfTimeline.Sample> samples, List<PerfThreads.Group> threads,
            long durationMs, int hz) {
        List<Finding> all = new ArrayList<>();
        stats.read(s -> screenFinding(s, all));
        stats.read(s -> idleRedrawFinding(s, all));
        powerFindings(PerfPower.of(samples), all);
        threadFindings(threads, durationMs, hz, all);
        gcFinding(samples, all);
        thermalFinding(samples, all);
        cpuFinding(samples, all);
        heapFinding(samples, all);
        batteryFinding(samples, all);
        all.sort((a, b) -> Double.compare(b.score, a.score));
        List<String> out = new ArrayList<>();
        for (int i = 0; i < Math.min(MAX, all.size()); i++) out.add(all.get(i).text);
        if (out.isEmpty()) out.add("Nothing suspicious: no screen above " + (int) MIN_JANK_PERCENT
                + "% janky frames, no GC, thermal or CPU alarm in this session.");
        return out;
    }

    private static void screenFinding(PerfStats.Slot s, List<Finding> out) {
        if (s.frames < MIN_SCREEN_FRAMES || s.jankPercent() < MIN_JANK_PERCENT) return;
        int phase = dominantPhase(s);
        long janky = Math.max(1, s.jankyTotalNs);
        double share = 100.0 * s.phaseSumJanky[phase] / janky;
        StringBuilder t = new StringBuilder(String.format(Locale.US, "%s: %.0f%% janky (%d of %d frames), %s dominates (%.0f%% of the slow-frame time) -> %s",
                s.tag, s.jankPercent(), s.janky, s.frames, PerfStats.PHASES[phase].toUpperCase(Locale.US), share, hint(phase)));
        String flag = worstFlag(s);
        if (flag != null) t.append(flag);
        if (s.frozen > 0) t.append(String.format(Locale.US, "; %d frozen frame(s) over 700 ms", s.frozen));
        out.add(new Finding(s.jankPercent() + Math.min(20, s.slow * 0.5) + (s.frozen > 0 ? 15 : 0), t.toString()));
    }

    /** The phase with the largest share of the slow frames' time. GPU wins only when it alone exceeds half of it. */
    static int dominantPhase(PerfStats.Slot s) {
        int best = 0;
        for (int i = 1; i < PerfStats.GPU; i++) if (s.phaseSumJanky[i] > s.phaseSumJanky[best]) best = i;
        return s.phaseSumJanky[PerfStats.GPU] * 2 > s.jankyTotalNs && s.phaseSumJanky[PerfStats.GPU] > s.phaseSumJanky[best]
                ? PerfStats.GPU : best;
    }

    static String hint(int phase) {
        return switch (phase) {
            case 0 -> "the main thread was busy before the frame started (GC, binder or other work between frames)";
            case 1 -> "touch handlers are slow";
            case 2 -> "animation callbacks are heavy (too many animators or springs per frame)";
            case 3 -> "layout / measure churn (wrap_content updates, nested layouts, requestLayout during scroll)";
            case 4 -> "overdraw or heavy drawing (blur, big gradients, custom onDraw)";
            case 5 -> "texture upload (decoding or uploading large bitmaps on the frame path)";
            case 6 -> "GPU command submission is slow or the GPU is falling behind";
            case 7 -> "waiting for a buffer from the compositor (GPU / display backpressure)";
            default -> "GPU-bound (fill rate: blur, overdraw, full-screen layers)";
        };
    }

    /** "; with lyrics-blur on: 41% janky vs 3% off" for the decoration whose presence hurts the most, or null. */
    private static String worstFlag(PerfStats.Slot s) {
        int best = -1;
        double bestGap = 0;
        for (int f = 0; f < PerfStats.FLAG_COUNT; f++) {
            long on = s.flagFrames[f], off = s.frames - on;
            if (on < 30 || off < 30) continue;
            double gap = 100.0 * s.flagJanky[f] / on - 100.0 * (s.janky - s.flagJanky[f]) / off;
            if (gap > bestGap) {
                bestGap = gap;
                best = f;
            }
        }
        if (best < 0 || bestGap < 10) return null;
        long on = s.flagFrames[best], off = s.frames - on;
        return String.format(Locale.US, "; with %s on: %.0f%% janky vs %.0f%% off", PerfStats.FLAG_NAMES[best],
                100.0 * s.flagJanky[best] / on, 100.0 * (s.janky - s.flagJanky[best]) / off);
    }

    /** Redraws while nobody touches the screen: on Home this should be ~0 fps; every frame is GPU + CPU = battery. */
    private static void idleRedrawFinding(PerfStats.Slot s, List<Finding> out) {
        double fps = s.idleFps();
        double limit = s.tag.startsWith(LYRICS_OR_PLAYER) ? IDLE_FPS_PLAYER : IDLE_FPS;
        if (fps < limit) return;
        StringBuilder on = new StringBuilder();
        for (int f = 0; f < PerfStats.FLAG_COUNT; f++) {
            if (s.idleFlagFrames[f] * 2 >= s.idleFrames) on.append(on.length() == 0 ? "" : ", ").append(PerfStats.FLAG_NAMES[f]);
        }
        out.add(new Finding(55 + Math.min(30, fps / 2), String.format(Locale.US,
                "%s: redraws %.0f frames/s while nobody touches the screen (%d s idle) -> something animates continuously, a direct battery cost; decorations active: %s",
                s.tag, fps, s.idleMs / 1000, on.length() == 0 ? "none flagged (a view invalidates itself)" : on)));
    }

    private static void powerFindings(PerfPower p, List<Finding> out) {
        if (!Double.isNaN(p.pctPerMin) && p.levelStart - p.levelEnd >= 2 && p.pctPerMin >= DRAIN_PCT_PER_MIN) {
            out.add(new Finding(60 + Math.min(30, p.pctPerMin * 10), String.format(Locale.US,
                    "battery fell %d%% -> %d%% in %s = %.1f %%/min (typical with music and the screen on: %.1f-%.1f %%/min) -> heavy drain",
                    p.levelStart, p.levelEnd, PerfFindings.clock(p.spanMs), p.pctPerMin, PerfPower.TYPICAL_PCT_PER_MIN_LOW,
                    PerfPower.TYPICAL_PCT_PER_MIN_HIGH)));
        }
        double on = !Double.isNaN(p.counterMa) && p.screenOnSamples * 2 > p.samples ? p.counterMa : p.currentMaOn;
        if (!Double.isNaN(on) && on >= CURRENT_SCREEN_ON_MA) {
            out.add(new Finding(58 + Math.min(30, on / 200), String.format(Locale.US,
                    "average battery current %.0f mA with the screen on (a phone playing music at normal brightness draws 500-1200 mA) -> the app, or the screen at 120 Hz, burns power",
                    on)));
        }
        double off = p.currentMaOff;
        if (!Double.isNaN(off) && off >= (p.playingSamples > 0 ? CURRENT_SCREEN_OFF_PLAYING_MA : CURRENT_SCREEN_OFF_MA)) {
            out.add(new Finding(57, String.format(Locale.US, "average battery current %.0f mA with the screen off -> background work", off)));
        }
        double[] c = p.cpuPercent;
        if (p.cpuMs[2] >= 15_000 && c[2] >= CPU_ON_PAUSED) {
            out.add(new Finding(50 + c[2] / 4, String.format(Locale.US,
                    "CPU %.0f%% of one core for %d s with the screen on and nothing playing -> idle work (redraws, polling, scans); see the thread table", c[2], p.cpuMs[2] / 1000)));
        }
        if (p.cpuMs[0] >= 15_000 && c[0] >= CPU_OFF_PAUSED) {
            out.add(new Finding(62, String.format(Locale.US,
                    "CPU %.1f%% of one core for %d s with the screen off and nothing playing -> a leak: something keeps running in the background", c[0], p.cpuMs[0] / 1000)));
        }
        if (p.cpuMs[1] >= 15_000 && c[1] >= CPU_OFF_PLAYING) {
            out.add(new Finding(52, String.format(Locale.US,
                    "CPU %.0f%% of one core for %d s with the screen off and music playing (decoding alone is 2-8%%) -> extra background work", c[1], p.cpuMs[1] / 1000)));
        }
    }

    private static void threadFindings(List<PerfThreads.Group> groups, long durationMs, int hz, List<Finding> out) {
        if (groups.isEmpty() || durationMs < 20_000) return;
        double gc = 0;
        for (PerfThreads.Group g : groups) {
            double core = corePercent(g.ticks(), durationMs, hz);
            String n = g.name();
            if (n.startsWith("HeapTaskDaemon") || n.startsWith("ReferenceQueue") || n.startsWith("FinalizerDaemon") || n.startsWith("FinalizerWatch")) gc += core;
            else if (n.equals("RenderThread") && core >= RENDER_THREAD_CORE) {
                out.add(new Finding(48 + core / 2, String.format(Locale.US,
                        "RenderThread uses %.0f%% of a core -> the GPU pipeline is busy (continuous redraw, blur, overdraw)", core)));
            } else if ((n.startsWith("pool-") || n.startsWith("Thread-") || n.startsWith("AsyncTask") || n.startsWith("DefaultDispatch"))
                    && core >= POOL_THREAD_CORE) {
                out.add(new Finding(47 + core / 2, String.format(Locale.US,
                        "background threads \"%s\" use %.0f%% of a core (%d thread(s)) -> background churn (scan, artwork, lyrics, waveform)",
                        n, core, g.threads())));
            } else if (n.equals("main") && core >= MAIN_THREAD_CORE) {
                out.add(new Finding(46 + core / 2, String.format(Locale.US,
                        "main thread busy %.0f%% of a core over the whole session -> UI work that never rests", core)));
            }
        }
        if (gc >= GC_THREADS_CORE) {
            out.add(new Finding(49 + gc, String.format(Locale.US,
                    "GC / finalizer threads use %.1f%% of a core -> allocation churn costs CPU even when frames look fine", gc)));
        }
    }

    /** CPU of a thread group as a percentage of one core over the recording. */
    static double corePercent(long ticks, long durationMs, int hz) {
        return durationMs <= 0 ? 0 : ticks * 1000.0 / hz * 100.0 / durationMs;
    }

    private static void gcFinding(List<PerfTimeline.Sample> samples, List<Finding> out) {
        if (samples.size() < 3) return;
        PerfTimeline.Sample a = samples.get(0), z = samples.get(samples.size() - 1);
        if (a.gcBlockingMs < 0 || z.gcBlockingMs < 0) return;
        long spanS = (z.atMs - a.atMs) / 1000;
        if (spanS < 10) return;
        long blockMs = z.gcBlockingMs - a.gcBlockingMs;
        long gcs = z.gcCount - a.gcCount;
        double perMin = blockMs * 60.0 / spanS;
        if (perMin >= 200) {
            out.add(new Finding(30 + Math.min(40, perMin / 20), String.format(Locale.US,
                    "GC blocking time %d ms in %d s (%d blocking GCs) -> allocation churn on the main path", blockMs, spanS,
                    z.gcBlockingCount - a.gcBlockingCount)));
        } else if (gcs * 60.0 / spanS >= 60) {
            out.add(new Finding(25, String.format(Locale.US, "%d GCs in %d s (%.0f per minute) -> allocation churn", gcs, spanS,
                    gcs * 60.0 / spanS)));
        }
    }

    private static void thermalFinding(List<PerfTimeline.Sample> samples, List<Finding> out) {
        for (PerfTimeline.Sample s : samples) {
            if (s.thermal >= 2) {
                int max = 0;
                for (PerfTimeline.Sample x : samples) max = Math.max(max, x.thermal);
                out.add(new Finding(50 + 10 * max, String.format(Locale.US,
                        "thermal %s reached at %s into the session (peak %s) -> the phone is throttling; frame times after this point are not representative",
                        PerfTimeline.thermalName(s.thermal), clock(s.atMs), PerfTimeline.thermalName(max))));
                return;
            }
        }
    }

    private static void cpuFinding(List<PerfTimeline.Sample> samples, List<Finding> out) {
        if (samples.size() < 3) return;
        long sum = 0;
        int peak = 0;
        for (PerfTimeline.Sample s : samples) {
            sum += s.cpuPercent;
            peak = Math.max(peak, s.cpuPercent);
        }
        long avg = sum / samples.size();
        if (avg >= 50) {
            out.add(new Finding(25 + avg / 4.0, String.format(Locale.US,
                    "process CPU averages %d%% of one core (peak %d%%) -> sustained load; compare with the screens above", avg, peak)));
        }
    }

    private static void heapFinding(List<PerfTimeline.Sample> samples, List<Finding> out) {
        int worst = 0;
        for (PerfTimeline.Sample s : samples) {
            if (s.javaMaxKb > 0) worst = Math.max(worst, (int) (100 * s.javaUsedKb / s.javaMaxKb));
        }
        if (worst >= 80) out.add(new Finding(35, "Java heap reached " + worst + "% of its limit -> memory pressure, expect frequent GC"));
    }

    private static void batteryFinding(List<PerfTimeline.Sample> samples, List<Finding> out) {
        int max = 0;
        for (PerfTimeline.Sample s : samples) max = Math.max(max, s.battTempDeci);
        if (max >= 400) out.add(new Finding(30 + (max - 400) / 2.0, String.format(Locale.US,
                "battery temperature reached %.1f C -> the phone is hot (charging and screen brightness also heat it)", max / 10.0)));
    }

    static String clock(long ms) {
        long s = ms / 1000;
        return String.format(Locale.US, "%02d:%02d", s / 60, s % 60);
    }
}
