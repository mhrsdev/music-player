package com.mhrsdev.musicplayer.diag;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.os.Handler;
import android.view.Window;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * PT1 (D38): the frame statistics maths, the report (size cap, rules) and the "OFF costs nothing" contract. Frames are
 * fed to {@link PerfStats} directly (a {@code FrameMetrics} cannot be built off-device); the listener wiring is checked
 * through the {@link PerfProbe} hook seam on a real Robolectric window.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class PerfProbeTest {
    private static final long MS = 1_000_000L;
    private static final long IV = 16_666_667L; // 60 Hz

    private int added, removed;
    /** The sample() timeline drains 3.6 A (the owner's D40 case) unless a test wants the other findings to show. */
    private static boolean heavyPower = true;

    @Before public void setUp() {
        PerfProbe.resetForTest();
        PerfProbe.hook = new PerfProbe.Hook() {
            @Override public void add(Window w, Window.OnFrameMetricsAvailableListener l, Handler h) {
                added++;
            }

            @Override public void remove(Window w, Window.OnFrameMetricsAvailableListener l) {
                removed++;
            }
        };
    }

    @After public void tearDown() {
        heavyPower = true;
        PerfProbe.resetForTest();
    }

    // ---- histogram and aggregation ----

    private static long[] phases(long delay, long layout, long draw, long gpu) {
        long[] p = new long[PerfStats.PHASE_COUNT];
        p[0] = delay;
        p[3] = layout;
        p[4] = draw;
        p[PerfStats.GPU] = gpu;
        return p;
    }

    @Test public void bucketBoundariesAreLowerInclusive() {
        PerfStats st = new PerfStats(0);
        int s = st.slotFor("A");
        long[] edges = {0, 3_999_999, 4 * MS, 8 * MS, 12 * MS, 16_700_000L, 33 * MS, 66 * MS, 150 * MS, 5_000 * MS};
        int[] expect = {0, 0, 1, 2, 3, 4, 5, 6, 7, 7};
        for (long e : edges) st.record(s, 0, e, new long[PerfStats.PHASE_COUNT], IV, 0, false);
        PerfStats.Slot[] box = new PerfStats.Slot[1];
        st.read(x -> { if (x.tag.equals("A")) box[0] = x; });
        long[] counts = new long[PerfStats.BUCKETS];
        for (int b : expect) counts[b]++;
        assertEquals(java.util.Arrays.toString(counts), java.util.Arrays.toString(box[0].hist));
        assertEquals(edges.length, box[0].frames);
    }

    @Test public void countersPercentilesWorstAndFlags() {
        PerfStats st = new PerfStats(1000);
        int s = st.slotFor("Home");
        for (int i = 0; i < 100; i++) st.record(s, 0, 6 * MS, phases(0, MS, 2 * MS, 0), IV, 1000, false);     // smooth
        for (int i = 0; i < 10; i++) st.record(s, PerfProbe.F_LYRICS_BLUR, 20 * MS, phases(MS, 2 * MS, 15 * MS, 0), IV, 2000, false); // janky
        for (int i = 0; i < 2; i++) st.record(s, PerfProbe.F_LYRICS_BLUR, 80 * MS, phases(0, MS, 70 * MS, 0), IV, 3000, false);       // slow
        st.record(s, PerfProbe.F_BACKDROP | PerfProbe.F_LYRICS_BLUR, 800 * MS, phases(5 * MS, 5 * MS, 780 * MS, 0), IV, 4000, false); // frozen
        PerfStats.Slot[] box = new PerfStats.Slot[1];
        st.read(x -> box[0] = x);
        PerfStats.Slot x = box[0];
        assertEquals(113, x.frames);
        assertEquals(13, x.janky);
        assertEquals(3, x.slow);
        assertEquals(1, x.frozen);
        assertEquals(800 * MS, x.maxTotalNs);
        assertEquals(800 * MS, x.worstTotalNs);
        assertEquals(3000, x.worstAtMs);
        assertEquals(780 * MS, x.worstPhases[4]);
        assertEquals(PerfProbe.F_BACKDROP | PerfProbe.F_LYRICS_BLUR, x.worstFlags);
        assertEquals(13, x.flagFrames[2]);    // lyrics-blur
        assertEquals(13, x.flagJanky[2]);
        assertEquals(1, x.flagFrames[3]);     // backdrop-drift
        assertEquals(100 * 6 * MS + 10 * 20 * MS + 2 * 80 * MS + 800 * MS, x.sumTotalNs);
        // p50: the 57th of 113 frames sits in bucket 4-8 ms (100 frames, all 6 ms)
        double p50 = x.percentileMs(0.5);
        assertTrue("p50 " + p50, p50 >= 4 && p50 < 8);
        double p99 = x.percentileMs(0.99);
        assertTrue("p99 " + p99, p99 >= 66 && p99 <= 800);
        assertEquals(800.0, x.percentileMs(1.0), 0.001);
        assertEquals(1, st.totalJanky() - 12); // totals
        assertEquals(113, st.totalFrames());
    }

    @Test public void screenOverflowFallsIntoOther() {
        PerfStats st = new PerfStats(0);
        for (int i = 0; i < PerfStats.MAX_SLOTS + 5; i++) st.slotFor("S" + i);
        assertEquals(0, st.slotFor("brand new"));
        assertEquals(st.slotFor("S3"), st.slotFor("S3"));
    }

    // ---- the report ----

    private static PerfTimeline.Sample sample(long atMs, int thermal, int cpu, long gcBlockMs) {
        PerfTimeline.Sample s = new PerfTimeline.Sample();
        s.atMs = atMs;
        s.thermal = thermal;
        s.battTempDeci = 372;
        s.battLevel = 80;
        s.javaUsedKb = 30_000;
        s.javaMaxKb = 256_000;
        s.nativeKb = 40_000;
        s.gcCount = atMs / 4000;
        s.gcBlockingCount = atMs / 5000;
        s.gcBlockingMs = gcBlockMs;
        s.threads = 40;
        s.cpuPercent = cpu;
        s.fps = 55;
        s.refreshHz = 120;
        s.playing = true;
        s.lyricsOpen = atMs > 20_000;
        s.screenOn = true;
        s.voltageMv = 4050 - (int) (atMs / 5000);
        s.battLevel = heavyPower ? 80 - (int) (atMs / 30_000) : 80;
        s.currentUa = heavyPower ? -3_600_000L : -700_000L;
        s.chargeUah = heavyPower ? 3_000_000L - atMs : PerfTimeline.UNKNOWN; // 1 uAh per ms = 3600 mA
        s.javaUsedKb = 30_000 + atMs / 10;
        return s;
    }

    /** A full-size synthetic session: the worst case for report length. */
    static String syntheticReport(long seed, int screens, int samples) {
        Random r = new Random(seed);
        PerfStats st = new PerfStats(0);
        long[] ph = new long[PerfStats.PHASE_COUNT];
        long base = 0;
        for (int sc = 0; sc < screens; sc++) {
            int slot = st.slotFor(sc == 0 ? "PlayerFragment+lyrics" : sc == 1 ? "HomeFragment" : "LibraryFragment_tab_with_a_very_long_name_" + sc);
            st.addActive(slot, 60_000);
            int n = sc == 0 ? 3000 : sc == 1 ? 150 : 800;
            long step = sc == 0 ? 33 : sc == 1 ? 500 : 32; // Player 30 fps; Home 2 fps; a Library tab 31 fps with nobody touching it
            int fl = sc == 2 ? PerfProbe.F_BEAT | PerfProbe.F_BACKDROP : 0;
            for (int i = 0; i < n; i++) {
                boolean bad = sc == 0 && r.nextInt(100) < 38;
                long total = bad ? (12 + r.nextInt(50)) * MS : (2 + r.nextInt(5)) * MS; // 120 Hz: 8.3 ms budget
                java.util.Arrays.fill(ph, 0);
                ph[0] = r.nextInt(2) * MS;
                ph[3] = total / 10;
                ph[4] = bad ? total * 7 / 10 : total / 2;
                ph[5] = total / 20;
                ph[PerfStats.GPU] = total / 3;
                st.record(slot, fl | (bad ? PerfProbe.F_LYRICS | PerfProbe.F_LYRICS_BLUR | PerfProbe.F_PLAYING : PerfProbe.F_PLAYING), total, ph,
                        IV / 2, base + i * step, i % 40 == 0 && i < 80);
            }
            base += n * step;
            st.tick(slot, base);
        }
        PerfTimeline tl = new PerfTimeline();
        for (int i = 0; i < samples; i++) {
            int thermal = i < 12 ? 0 : i < 24 ? 1 : 2;
            tl.add(sample(i * 5000L, thermal, 30 + (i % 50), i * 4L), i < 60 ? "FULL (fps cap screen)" : "FULL (thermal-adapted to BALANCED, fps cap 60)");
        }
        return PerfReport.build(new PerfReport.Meta("App 0.3.2 (302)\nDevice samsung SM-S948U (e3q)\nAndroid 17 (SDK 37)",
                1_790_000_000_000L, samples * 5000L, "stopped by user", tl.lastProfile(), threads(), 100), st, tl);
    }

    private static List<PerfThreads.Group> threads() {
        List<PerfThreads.Group> g = new ArrayList<>();
        g.add(new PerfThreads.Group("RenderThread", 1, 3200));
        g.add(new PerfThreads.Group("main", 1, 2600));
        g.add(new PerfThreads.Group("ExoPlayer:Playb", 1, 700));
        g.add(new PerfThreads.Group("pool-#-thread-#", 6, 520));
        g.add(new PerfThreads.Group("HeapTaskDaemon", 1, 240));
        g.add(new PerfThreads.Group("binder:#_#", 4, 90));
        g.add(new PerfThreads.Group("perf-probe", 1, 12));
        for (int i = 0; i < 12; i++) g.add(new PerfThreads.Group("worker-" + i, 1, 5));
        return g;
    }

    @Test public void reportStaysUnderTheSizeCapEvenAtMaximumSize() {
        String report = syntheticReport(7, PerfStats.MAX_SLOTS, PerfTimeline.CAPACITY);
        assertTrue("report is " + report.length() + " chars", report.length() <= PerfReport.MAX_CHARS);
        assertTrue(report.getBytes(StandardCharsets.UTF_8).length < 20 * 1024);
        assertTrue(report.contains("== Top findings =="));
        assertTrue(report.contains("== Per screen (ms) =="));
        assertTrue(report.contains("PlayerFragment+lyrics"));
        assertTrue(report.contains("== Timeline"));
    }

    @Test public void reportListsPercentilesAndKeyValueHeader() {
        String report = syntheticReport(7, 3, 30);
        assertTrue(report.contains("frames="));
        assertTrue(report.contains("recorded="));
        assertTrue(report.contains("refresh=120Hz"));
        assertTrue(report.contains("p50"));
        assertTrue(report.contains("t_s,thermal,"));
    }

    @Test public void sampleReportForTheDocs() throws IOException {
        String report = syntheticReport(42, 4, 36);
        File out = new File("../docs/sample-perf-report.txt");
        if (out.getParentFile().isDirectory()) Files.write(out.toPath(), report.getBytes(StandardCharsets.UTF_8));
        assertTrue(report.length() < PerfReport.MAX_CHARS);
    }

    // ---- the rules ----

    @Test public void drawDominatedScreenIsReportedWithItsCause() {
        heavyPower = false;
        String report = syntheticReport(7, 2, 10);
        String findings = report.substring(report.indexOf("== Top findings =="), report.indexOf("== Per screen"));
        String first = null;
        for (String line : findings.split("\n")) if (line.contains("PlayerFragment+lyrics: ")) first = line;
        assertNotNull(findings, first);
        assertTrue(first, first.contains("DRAW dominates"));
        assertTrue(first, first.contains("overdraw"));
        assertTrue(first, first.contains("with lyrics"));
    }

    @Test public void rulesFindGcThermalAndAtMostThree() {
        PerfStats st = new PerfStats(0);
        List<PerfTimeline.Sample> s = new ArrayList<>();
        for (int i = 0; i < 13; i++) {
            PerfTimeline.Sample x = sample(i * 5000L, i < 8 ? 1 : 2, 20, i * 120L); // 1440 ms/min of blocking GC
            x.currentUa = PerfTimeline.NO_CURRENT;
            x.chargeUah = PerfTimeline.UNKNOWN;
            x.battLevel = 80;
            s.add(x);
        }
        List<String> f = PerfFindings.top(st, s, java.util.List.of(), 0, 100);
        assertTrue(f.toString(), f.size() >= 2 && f.size() <= 3);
        String all = String.join("\n", f);
        assertTrue(all, all.contains("thermal MODERATE reached at 00:40"));
        assertTrue(all, all.contains("GC blocking time"));
        assertTrue(all, all.contains("allocation churn"));
    }

    // ---- D40: battery, per-thread CPU, idle redraws ----

    @Test public void threadStatLinesParseEvenWithOddNames() {
        String line = "4242 (Binder:42_(x y)) S 1 42 0 0 -1 4194560 120 0 0 0 150 30 0 0 20 0 7 0 12345 1 2 3 4";
        assertEquals("Binder:42_(x y)", PerfThreads.commOf(line));
        assertEquals(180, PerfThreads.ticksOf(line));
        assertEquals(-1, PerfThreads.ticksOf("garbage"));
        assertEquals(-1, PerfThreads.ticksOf("1 (a) S 1 2"));
        assertEquals("pool-#-thread-#", PerfThreads.normalize("pool-12-thread-3"));
        assertEquals("ExoPlayer:Playb", PerfThreads.normalize("ExoPlayer:Playb"));
    }

    @Test public void threadGroupsCountOnlyTheRecordedDeltaAndNewThreadsFromZero() {
        PerfThreads t = new PerfThreads();
        t.sample(1, "main", 1000);
        t.sample(2, "pool-1-thread-1", 500);
        t.endPass();                       // baseline
        t.sample(1, "main", 1250);          // +250
        t.sample(2, "pool-1-thread-1", 600); // +100
        t.sample(3, "pool-2-thread-1", 80);  // born during the recording: all of it counts
        t.endPass();
        List<PerfThreads.Group> g = t.groups();
        assertEquals("main", g.get(0).name());
        assertEquals(250, g.get(0).ticks());
        assertEquals("pool-#-thread-#", g.get(1).name());
        assertEquals(2, g.get(1).threads());
        assertEquals(180, g.get(1).ticks());
        assertEquals(430, PerfThreads.totalTicks(g));
        assertEquals(25.0, PerfFindings.corePercent(250, 10_000, 100), 1e-9); // 2.5 s of CPU in 10 s
    }

    @Test public void idleTimeAndFramesCountOnlyWhenNobodyTouchesAndTheAppIsShown() {
        PerfStats st = new PerfStats(0);
        int s = st.slotFor("HomeFragment");
        long[] ph = new long[PerfStats.PHASE_COUNT];
        st.record(s, 0, 5 * MS, ph, IV, 100, true);              // a touch at 100 ms
        for (int i = 0; i < 10; i++) st.record(s, PerfProbe.F_BEAT, 5 * MS, ph, IV, 1000 + i, false); // within 1.5 s: not idle
        st.tick(s, 11_600);                                       // idle from 1.6 s to 11.6 s = 10 s
        for (int i = 0; i < 300; i++) st.record(s, PerfProbe.F_BEAT | PerfProbe.F_BACKDROP, 5 * MS, ph, IV, 11_600, false);
        st.setForeground(false, s, 20_000);                        // 8.4 more idle seconds up to here
        st.tick(s, 60_000);                                        // background: nothing counts
        PerfStats.Slot[] box = new PerfStats.Slot[1];
        st.read(x -> box[0] = x);
        assertEquals(300, box[0].idleFrames);
        assertEquals(18_400, box[0].idleMs, 200);                 // 1.6 s .. 20 s
        assertEquals(300 * 1000.0 / box[0].idleMs, box[0].idleFps(), 1e-6);
        assertEquals(300, box[0].idleFlagFrames[4]);              // decor-beat
        assertEquals(300, box[0].idleFlagFrames[3]);              // backdrop-drift
    }

    @Test public void powerAnalysisSeparatesScreenAndMusicAndComputesDrain() {
        List<PerfTimeline.Sample> list = new ArrayList<>();
        for (int i = 0; i <= 60; i++) {   // 5 minutes
            PerfTimeline.Sample x = sample(i * 5000L, 0, 10, 0);
            x.battLevel = 80 - i / 10;     // 6 % in 5 min
            x.chargeUah = 3_000_000L - i * 5000L; // 5 mAh per 5 s = 3600 mA
            x.screenOn = i < 40;
            x.playing = true;
            list.add(x);
        }
        PerfPower p = PerfPower.of(list);
        assertEquals(1.2, p.pctPerMin, 0.01);
        assertTrue(PerfPower.verdict(p.pctPerMin).startsWith("HIGH"));
        assertEquals(300.0, p.counterMah, 0.01);
        assertEquals(3600.0, p.counterMa, 1.0);                    // 300 mAh in 5 min
        assertEquals(3600.0, p.currentMaOn, 0.01);
        assertEquals(40, p.screenOnSamples);
        assertEquals(10.0, p.cpuPercent[3], 0.01);
        assertTrue(Double.isNaN(p.cpuPercent[2]));
    }

    @Test public void chargingSessionsDoNotClaimADrainRate() {
        List<PerfTimeline.Sample> list = new ArrayList<>();
        for (int i = 0; i <= 30; i++) {
            PerfTimeline.Sample x = sample(i * 5000L, 0, 10, 0);
            x.charging = true;
            list.add(x);
        }
        assertTrue(Double.isNaN(PerfPower.of(list).pctPerMin));
        assertTrue(Double.isNaN(PerfPower.of(list).counterMa));
    }

    @Test public void batteryCpuAndThreadRulesFireOnTheirThresholds() {
        PerfStats st = new PerfStats(0);
        // screen-off leak: 40 s with the screen off, nothing playing, 6 % CPU
        List<PerfTimeline.Sample> s = new ArrayList<>();
        for (int i = 0; i <= 10; i++) {
            PerfTimeline.Sample x = sample(i * 5000L, 0, 6, 0);
            x.screenOn = false;
            x.playing = false;
            x.currentUa = PerfTimeline.NO_CURRENT;
            x.chargeUah = PerfTimeline.UNKNOWN;
            x.battLevel = 80;
            s.add(x);
        }
        List<PerfThreads.Group> g = new ArrayList<>();
        g.add(new PerfThreads.Group("RenderThread", 1, 3000));          // 60 s run: 3000 ticks = 50 % of a core
        g.add(new PerfThreads.Group("pool-#-thread-#", 3, 900));        // 15 %
        g.add(new PerfThreads.Group("HeapTaskDaemon", 1, 300));         // 5 %
        g.add(new PerfThreads.Group("ReferenceQueueD", 1, 60));
        List<String> top = PerfFindings.top(st, s, g, 60_000, 100);
        String all = String.join("\n", top);
        assertEquals(3, top.size());                                       // never more than three
        assertTrue(all, all.contains("RenderThread uses 50% of a core"));
        assertTrue(all, all.contains("with the screen off and nothing playing -> a leak"));
        assertTrue(all, all.contains("GC / finalizer threads use 6.0% of a core"));
        g.remove(0);                                                       // without the RenderThread the pool threads show up
        g.remove(2);
        String pool = String.join("\n", PerfFindings.top(st, s, g, 60_000, 100));
        assertTrue(pool, pool.contains("background threads \"pool-#-thread-#\" use 15% of a core (3 thread(s))"));
    }

    @Test public void idleRedrawOnAScreenIsFlaggedWithItsAnimationBitmask() {
        PerfStats st = new PerfStats(0);
        int s = st.slotFor("HomeFragment");
        long[] ph = new long[PerfStats.PHASE_COUNT];
        for (int i = 0; i < 1200; i++) st.record(s, PerfProbe.F_BEAT | PerfProbe.F_BACKDROP, 4 * MS, ph, IV, 2000 + i * 33L, false); // 30 fps for 40 s
        st.tick(s, 42_000);
        List<String> f = PerfFindings.top(st, new ArrayList<>(), java.util.List.of(), 0, 100);
        assertTrue(f.toString(), f.get(0).startsWith("HomeFragment: redraws 30 frames/s while nobody touches"));
        assertTrue(f.get(0), f.get(0).contains("backdrop-drift") && f.get(0).contains("decor-beat"));
        // the player is allowed its 30 fps by design
        PerfStats st2 = new PerfStats(0);
        int p = st2.slotFor("PlayerFragment");
        for (int i = 0; i < 1200; i++) st2.record(p, PerfProbe.F_BEAT, 4 * MS, ph, IV, 2000 + i * 33L, false);
        st2.tick(p, 42_000);
        assertTrue(PerfFindings.top(st2, new ArrayList<>(), java.util.List.of(), 0, 100).get(0).startsWith("Nothing suspicious"));
    }

    @Test public void sampleReportCarriesBatteryCpuAndIdleSections() {
        String report = syntheticReport(42, 4, 36);
        assertTrue(report.contains("== Battery / power =="));
        assertTrue(report.contains("charge counter:"));
        assertTrue(report.contains("== CPU =="));
        assertTrue(report.contains("RenderThread"));
        assertTrue(report.contains("(this recorder)"));
        assertTrue(report.contains("idle"));
        assertTrue(report.contains("screen on in"));
    }

    @Test public void aCleanSessionSaysSo() {
        PerfStats st = new PerfStats(0);
        int s = st.slotFor("Home");
        for (int i = 0; i < 200; i++) st.record(s, 0, 5 * MS, new long[PerfStats.PHASE_COUNT], IV, 0, false);
        List<String> f = PerfFindings.top(st, new ArrayList<>(), java.util.List.of(), 0, 100);
        assertEquals(1, f.size());
        assertTrue(f.get(0).startsWith("Nothing suspicious"));
    }

    @Test public void deadlineAcceptsATimestampOrADurationAndFallsBack() {
        assertEquals(8_333_333L, PerfProbe.deadline(1_000_000_000L + 8_333_333L, 1_000_000_000L, IV));
        assertEquals(8_333_333L, PerfProbe.deadline(8_333_333L, 5_000_000_000L, IV));
        assertEquals(IV, PerfProbe.deadline(0, 123, IV));
    }

    // ---- OFF = nothing registered ----

    @Test public void offMeansNoListenerNoThreadAndHooksAreHarmless() {
        PerfProbe.setScreen("HomeFragment");
        PerfProbe.setFlag(PerfProbe.F_LYRICS, true);
        PerfProbe.setScreen("PlayerFragment");
        PerfProbe.leaveScreen("PlayerFragment");
        assertFalse(PerfProbe.isRecording());
        assertEquals(0, added);
        assertFalse(threadAlive());
        assertNull(PerfProbe.report("h"));
        assertFalse(PerfProbe.summary().hasData());
    }

    private static void assertNull(Object o) {
        org.junit.Assert.assertNull(o);
    }

    private static boolean threadAlive() {
        for (Thread t : Thread.getAllStackTraces().keySet()) if (t.getName().equals("perf-probe") && t.isAlive()) return true;
        return false;
    }

    private static final PerfProbe.AppState STATE = new PerfProbe.AppState() {
        @Override public boolean playing() {
            return true;
        }

        @Override public String profile() {
            return "FULL";
        }
    };

    @Test public void startRegistersOneListenerStopRemovesItAndEndsTheThread() throws Exception {
        Activity a = Robolectric.buildActivity(Activity.class).setup().get();
        PerfProbe.setScreen("HomeFragment");
        PerfProbe.start(a, STATE);
        assertTrue(PerfProbe.isRecording());
        assertEquals(1, added);
        assertTrue(threadAlive());
        PerfProbe.start(a, STATE); // already recording: nothing more
        assertEquals(1, added);

        PerfProbe.sampleOnceForTest();
        PerfProbe.setScreen("PlayerFragment");
        PerfProbe.setFlag(PerfProbe.F_LYRICS, true); // retags to PlayerFragment+lyrics
        PerfProbe.Summary live = PerfProbe.summary();
        assertTrue(live.recording() && live.hasData());

        PerfProbe.stop();
        assertFalse(PerfProbe.isRecording());
        assertEquals(1, removed);
        for (int i = 0; i < 50 && threadAlive(); i++) Thread.sleep(20);
        assertFalse("the probe thread must end with the recording", threadAlive());

        String report = PerfProbe.report("Header line");
        assertNotNull(report);
        assertTrue(report.contains("Header line"));
        assertTrue(report.contains("ended=stopped by user"));
        assertTrue(report.contains("t_s,thermal,"));
        assertTrue("sampler ran once", report.contains("threads:"));

        PerfProbe.clear();
        assertFalse(PerfProbe.summary().hasData());
        assertNull(PerfProbe.report("h"));
    }
}
