package com.mhrsdev.musicplayer.ui.motion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * v0.3.1 (D29): the player's decorative loops (seek wave 33 ms, lyrics sweep 33 ms, backdrop drift 66 ms) started at
 * different moments used to invalidate on three unrelated phases: each phase is a separately rendered frame. On the
 * shared beat they land on the same display frame, and the driver sleeps between beats while no spring moves.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class DecorBeatTest {
    private static final long NS = 1_000_000_000L;

    @Test public void playerLoopsStartedOutOfPhaseShareOneFramePerBeat() {
        FrameDriver d = FrameDriver.get();
        int hz = 120;
        long t0 = 9_000 * NS;
        int[] marks = new int[hz + 1];
        List<PeriodicTick> loops = new ArrayList<>();
        List<AtomicInteger> calls = new ArrayList<>();
        long[] periods = {33, 33, 66};
        for (int f = 0; f <= hz; f++) {
            if (f < periods.length) { // started on consecutive frames: three different phases
                AtomicInteger n = new AtomicInteger();
                PeriodicTick t = new PeriodicTick(periods[f], () -> { n.incrementAndGet(); marks[currentFrame[0]]++; return true; });
                loops.add(t);
                calls.add(n);
                t.start();
            }
            currentFrame[0] = f;
            d.doFrame(t0 + f * NS / hz);
        }
        int rendered = 0;
        for (int m : marks) if (m > 0) rendered++;
        for (PeriodicTick t : loops) t.stop();
        d.doFrame(t0 + 2 * NS);
        int before = unalignedFrames(hz, periods);
        System.out.println("PERF decorative beat, 1 s at 120 Hz, seek+lyrics+backdrop started out of phase: "
                + rendered + " rendered frames (v0.3.0 per-loop phases: " + before + "); body calls " + calls.get(0)
                + "/" + calls.get(1) + "/" + calls.get(2));
        assertTrue("the old scheme really was worse: " + before, before > 2 * rendered);
        assertTrue("one frame per beat, got " + rendered, rendered <= 31);
        assertTrue("30 fps loops keep their rate: " + calls.get(0), calls.get(0).get() >= 29);
        assertTrue("15 fps loop keeps its rate: " + calls.get(2), calls.get(2).get() >= 14 && calls.get(2).get() <= 16);
    }

    private static final int[] currentFrame = new int[1];

    /** v0.3.0 PeriodicTick: each loop its own accumulator (fires on its first frame, resets to 0), own phase. */
    private static int unalignedFrames(int hz, long[] periodsMs) {
        float dt = 1f / hz;
        float[] acc = new float[periodsMs.length];
        int rendered = 0;
        for (int f = 0; f <= hz; f++) {
            boolean any = false;
            for (int i = 0; i < periodsMs.length; i++) {
                if (f < i) continue; // loop i starts on frame i
                float period = periodsMs[i] / 1000f;
                acc[i] = f == i ? period : acc[i] + dt;
                if (acc[i] >= period) {
                    acc[i] = 0f;
                    any = true;
                }
            }
            if (any) rendered++;
        }
        return rendered;
    }

    @Test public void driverSleepsBetweenBeatsOnlyWhileNoSpringMoves() {
        FrameDriver d = FrameDriver.get();
        long t0 = 20_000 * NS;
        PeriodicTick drift = new PeriodicTick(66, () -> true);
        drift.start();
        d.doFrame(t0); // a beat
        long sleep = d.sleepMillis(t0 + NS / 1000); // asked 1 ms after the beat frame
        System.out.println("PERF driver sleep after a beat with only decorative loops: " + sleep + " ms (wake-ups/s at "
                + "120 Hz: 30 instead of 120)");
        assertTrue("sleeps until just before the next beat, got " + sleep, sleep >= 20 && sleep <= 27);
        PeriodicTick spring = new PeriodicTick(0, () -> true); // stands in for a spring / spinner
        spring.start();
        assertEquals("a moving spring needs every frame", 0, d.sleepMillis(t0 + NS / 1000));
        spring.stop();
        drift.stop();
        d.doFrame(t0 + NS);
        assertEquals(0, d.activeCount());
    }
}
