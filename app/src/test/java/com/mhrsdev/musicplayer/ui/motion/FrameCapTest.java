package com.mhrsdev.musicplayer.ui.motion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.mhrsdev.musicplayer.data.settings.PerformanceProfile;
import com.mhrsdev.musicplayer.ui.theme.MotionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The frame-rate cap lives in the one FrameDriver, so every spring and loop obeys it. Also a JVM measurement of what
 * each preset asks of the player screen: tick-body calls (= invalidations / drawn frames) in one second of a 120 Hz
 * display while music plays with lyrics open. Printed as "PERF ..." lines; not a device measurement.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class FrameCapTest {
    private static final long NS = 1_000_000_000L;
    private static long clock = 1_000 * NS;

    @After public void reset() {
        MotionPolicy.setPerformance(PerformanceProfile.FULL);
    }

    @Test public void dueSkipsVsyncsEvenlyAtEveryRefreshRate() {
        long cap30 = NS / 30, cap60 = NS / 60;
        assertTrue(FrameDriver.due(NS / 120, 0));
        assertFalse(FrameDriver.due(NS / 60, cap30));     // 60 Hz: every 2nd frame
        assertTrue(FrameDriver.due(2 * NS / 60, cap30));
        assertFalse(FrameDriver.due(3 * NS / 120, cap30)); // 120 Hz: every 4th, never the 3rd
        assertTrue(FrameDriver.due(4 * NS / 120, cap30));
        assertFalse(FrameDriver.due(NS / 120, cap60));     // 120 Hz at 60: every 2nd
        assertTrue(FrameDriver.due(2 * NS / 120, cap60));
        assertTrue(FrameDriver.due(NS / 60 - 300_000, cap60)); // vsync jitter still counts as a frame
    }

    @Test public void capAppliesToEverySpringThroughTheSharedDriver() {
        MotionPolicy.setPerformance(PerformanceProfile.FULL.withMaxFps(30));
        assertEquals(30, run(120, List.of(PeriodicTickFactory.every(0))).get(0).get(), 1);
        MotionPolicy.setPerformance(PerformanceProfile.FULL.withMaxFps(60));
        assertEquals(60, run(120, List.of(PeriodicTickFactory.every(0))).get(0).get(), 1);
        MotionPolicy.setPerformance(PerformanceProfile.FULL);
        assertEquals(120, run(120, List.of(PeriodicTickFactory.every(0))).get(0).get(), 1);
    }

    @Test public void lightAsksForFarFewerFramesThanFull() {
        int full = playerFrames(PerformanceProfile.FULL);
        int balanced = playerFrames(PerformanceProfile.BALANCED);
        int light = playerFrames(PerformanceProfile.LIGHT);
        System.out.println("PERF player, 1 s at 120 Hz, playing + lyrics + one spring settling: tick bodies Full=" + full
                + " Balanced=" + balanced + " Light=" + light);
        assertTrue(balanced < full);
        assertTrue(light < balanced);
        assertTrue("Light: only the spring at 30 fps, got " + light, light <= 31);
    }

    /**
     * The loops the player runs while playing, at their real periods (PlayerBackdropView 66 ms, BaseSeekView and
     * LyricsTicker 33 ms), present only when the profile lets them start, plus one spring (every frame) settling.
     */
    private int playerFrames(PerformanceProfile p) {
        MotionPolicy.setPerformance(p);
        List<PeriodicTick> loops = new ArrayList<>();
        loops.add(PeriodicTickFactory.every(0)); // a spring
        if (p.movingBackdrop()) loops.add(PeriodicTickFactory.every(66));
        if (p.seekMotion()) loops.add(PeriodicTickFactory.every(33));
        if (p.lyricsWords()) loops.add(PeriodicTickFactory.every(33));
        int sum = 0;
        for (AtomicInteger n : run(120, loops)) sum += n.get();
        return sum;
    }

    /** Drives one second of {@code hz} display frames through the shared driver; returns each loop's body calls. */
    private static List<AtomicInteger> run(int hz, List<PeriodicTick> loops) {
        List<AtomicInteger> counts = new ArrayList<>();
        for (PeriodicTick t : loops) {
            counts.add(PeriodicTickFactory.counter(t));
            t.start();
        }
        FrameDriver d = FrameDriver.get();
        long t0 = clock;
        for (int f = 0; f <= hz; f++) d.doFrame(t0 + f * NS / hz);
        for (PeriodicTick t : loops) t.stop();
        d.doFrame(t0 + 2 * NS); // the driver notices it is idle and stops posting
        clock = t0 + 3 * NS;
        return counts;
    }

    /** PeriodicTicks whose body counts its calls (looked up by tick). */
    private static class PeriodicTickFactory {
        private static final java.util.Map<PeriodicTick, AtomicInteger> COUNTS = new java.util.IdentityHashMap<>();

        static PeriodicTick every(long periodMs) {
            AtomicInteger n = new AtomicInteger();
            PeriodicTick t = new PeriodicTick(periodMs, () -> { n.incrementAndGet(); return true; });
            COUNTS.put(t, n);
            return t;
        }

        static AtomicInteger counter(PeriodicTick t) {
            return COUNTS.get(t);
        }
    }
}
