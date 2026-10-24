package com.mhrsdev.musicplayer.ui.motion;

import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * C1 (D32): the shared decorative beat (30 fps) must not assume a 120 Hz panel. Most phones are 60 Hz (some 90, some
 * 72): one second of frames at each rate gives about 30 beats, and the 30 fps and 15 fps loops keep their rates.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class DecorBeatRefreshRateTest {
    private static final long NS = 1_000_000_000L;

    @Test public void thirtyFpsLoopsKeepTheirRateOnEveryPanel() {
        int[] rates = {60, 72, 90, 120, 144};
        long base = 30_000 * NS;
        for (int r = 0; r < rates.length; r++) {
            int hz = rates[r];
            FrameDriver d = FrameDriver.get();
            AtomicInteger fast = new AtomicInteger(), slow = new AtomicInteger();
            PeriodicTick f = new PeriodicTick(33, () -> { fast.incrementAndGet(); return true; });
            PeriodicTick s = new PeriodicTick(66, () -> { slow.incrementAndGet(); return true; });
            f.start();
            s.start();
            long t0 = base + r * 10 * NS;
            for (int frame = 0; frame <= hz; frame++) d.doFrame(t0 + frame * NS / hz);
            f.stop();
            s.stop();
            d.doFrame(t0 + 2 * NS);
            System.out.println("PERF decorative beat at " + hz + " Hz: 33 ms loop " + fast + " calls/s, 66 ms loop " + slow);
            // 72 Hz lands on every 3rd frame (24 beats/s): a loop never runs faster than asked, nor slower than ~3/4
            assertTrue(hz + " Hz: 33 ms loop ran " + fast, fast.get() >= 23 && fast.get() <= 31);
            assertTrue(hz + " Hz: 66 ms loop ran " + slow, slow.get() >= 11 && slow.get() <= 16);
            if (hz == 60 || hz == 90 || hz == 120) assertTrue(hz + " Hz: " + fast, fast.get() >= 29);
        }
    }
}
