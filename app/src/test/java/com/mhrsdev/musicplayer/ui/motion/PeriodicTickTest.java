package com.mhrsdev.musicplayer.ui.motion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Decorative loops share the FrameDriver: they run at their own rate, and leave nothing registered once stopped. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class PeriodicTickTest {
    @Test public void runsAtItsRateAndLeavesNothingBehindWhenStopped() {
        AtomicInteger calls = new AtomicInteger();
        PeriodicTick tick = new PeriodicTick(66, () -> {
            calls.incrementAndGet();
            return true;
        });
        tick.start();
        tick.start(); // idempotent
        assertEquals(1, FrameDriver.get().activeCount());
        long t0 = 5_000_000_000_000L;
        for (int frame = 0; frame < 60; frame++) FrameDriver.get().doFrame(t0 + frame * 1_000_000_000L / 60); // 1 s at 60 Hz
        System.out.println("PERF PeriodicTick 66 ms over 1 s of 60 Hz frames: " + calls.get() + " body calls");
        assertTrue("about 15 fps, got " + calls.get(), calls.get() >= 13 && calls.get() <= 16);
        tick.stop();
        assertEquals(0, FrameDriver.get().activeCount());
        assertFalse("a stopped tick asks the driver to drop it", tick.onFrame(1f));
    }

    @Test public void bodyReturningFalseStopsTheLoop() {
        PeriodicTick tick = new PeriodicTick(0, () -> false);
        tick.start();
        assertFalse(tick.onFrame(1f / 60f)); // period 0: every frame, beat or not
        assertFalse(tick.isActive());
    }
}
