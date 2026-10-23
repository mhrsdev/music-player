package com.mhrsdev.musicplayer.data.waveform;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SamplingPlanTest {
    @Test public void shortTrackIsDecodedFully() {
        SamplingPlan plan = new SamplingPlan(30_000_000, 256); // 30 s -> 117 ms slices, below the window cap
        for (long t = 0; t < 30_000_000; t += 9_973) {
            assertEquals("t=" + t, t, plan.nextFeedUs(t));
        }
    }

    @Test public void longTrackSkipsMostOfTheAudio() {
        long duration = 60L * 60 * 1_000_000; // 1 h
        SamplingPlan plan = new SamplingPlan(duration, 256);
        long step = 20_000, fed = 0, total = 0;
        for (long t = 0; t < duration; t += step) {
            total++;
            if (plan.nextFeedUs(t) == t) fed++;
        }
        assertTrue("fed fraction " + (double) fed / total, (double) fed / total < 0.03);
    }

    @Test public void nextFeedJumpsToWarmupBeforeNextWindow() {
        SamplingPlan plan = new SamplingPlan(256_000_000L, 256, 200_000, 50_000); // 1 s slices
        assertEquals(0, plan.nextFeedUs(0));
        assertEquals(199_999, plan.nextFeedUs(199_999));
        assertEquals(1_000_000 - 50_000, plan.nextFeedUs(200_000));
        assertEquals(1_000_000 - 50_000, plan.nextFeedUs(500_000));
        assertEquals(1_000_000 - 50_000, plan.nextFeedUs(950_000));
        assertEquals(255_000_000L - 50_000, plan.nextFeedUs(254_500_000L));
        assertEquals(-1, plan.nextFeedUs(255_200_000L));
        assertEquals(-1, plan.nextFeedUs(256_000_000L));
    }

    @Test public void bucketAtOnlyAcceptsMeasuredWindows() {
        SamplingPlan plan = new SamplingPlan(256_000_000L, 256, 200_000, 50_000);
        assertEquals(0, plan.bucketAt(0));
        assertEquals(0, plan.bucketAt(199_999));
        assertEquals(-1, plan.bucketAt(200_000));
        assertEquals(-1, plan.bucketAt(990_000));   // warm-up of bucket 1: decoded, not measured
        assertEquals(1, plan.bucketAt(1_000_000));
        assertEquals(255, plan.bucketAt(255_100_000L));
        assertEquals(-1, plan.bucketAt(-1));
        assertEquals(-1, plan.bucketAt(256_000_000L));
    }

    @Test public void everyBucketGetsAWindowForOddDurations() {
        for (long duration : new long[] {1, 255, 256, 257, 1_234_567, 99_999_999}) {
            SamplingPlan plan = new SamplingPlan(duration, 256);
            boolean[] seen = new boolean[256];
            long step = Math.max(1, duration / 20_000);
            for (long t = 0; t < duration; t += step) {
                int b = plan.bucketAt(t);
                if (b >= 0) seen[b] = true;
            }
            if (duration >= 256 * 20) { // enough resolution to hit every window
                for (int b = 0; b < 256; b++) assertTrue("duration " + duration + " bucket " + b, seen[b]);
            }
        }
    }

    @Test public void rejectsEmptyInput() {
        assertThrows(IllegalArgumentException.class, () -> new SamplingPlan(0, 256));
        assertThrows(IllegalArgumentException.class, () -> new SamplingPlan(1000, 0));
    }
}
