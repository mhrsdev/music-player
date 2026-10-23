package com.mhrsdev.musicplayer.ui.motion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SpringTest {
    private static int settleFrames(Spring s, float dt, int max) {
        for (int i = 1; i <= max; i++) if (!s.step(dt)) return i;
        return -1;
    }

    @Test public void everyPresetSettlesOnTargetWithinASecondAndAHalf() {
        for (Spring.Preset p : Spring.Preset.values()) {
            Spring s = new Spring(p);
            s.snapTo(0f);
            s.setTarget(1f);
            int frames = settleFrames(s, 1f / 60f, 90);
            assertTrue(p + " settled in " + frames, frames > 0);
            assertEquals(1f, s.value(), 0f);
            assertEquals(0f, s.velocity(), 0f);
            assertTrue(s.isSettled());
        }
    }

    @Test public void criticalDampingNeverOvershoots() {
        Spring s = new Spring(Spring.Preset.GENTLE);
        s.snapTo(0f);
        s.setTarget(100f);
        s.setThreshold(0.01f);
        float last = 0f;
        for (int i = 0; i < 600 && s.step(1f / 120f); i++) {
            assertTrue("overshoot at " + i + ": " + s.value(), s.value() <= 100f + 1e-3f);
            assertTrue("monotonic", s.value() >= last - 1e-4f);
            last = s.value();
        }
        // Overdamped too.
        Spring o = new Spring(300f, 1.6f);
        o.snapTo(0f);
        o.setTarget(1f);
        while (o.step(1f / 60f)) assertTrue(o.value() <= 1f + 1e-5f);
    }

    @Test public void energyNeverRisesWithoutInput() {
        for (Spring.Preset p : Spring.Preset.values()) {
            Spring s = new Spring(p);
            s.snapTo(0f);
            s.setTarget(1f);
            s.setVelocity(-3f);
            double e = s.energy();
            for (int i = 0; i < 200; i++) {
                s.step(1f / 60f);
                double now = s.energy();
                assertTrue(p + " energy rose at frame " + i, now <= e + 1e-9);
                e = now;
            }
        }
    }

    @Test public void frameRateIndependent() {
        Spring a = new Spring(Spring.Preset.BOUNCY), b = new Spring(Spring.Preset.BOUNCY);
        a.snapTo(0f); a.setTarget(1f);
        b.snapTo(0f); b.setTarget(1f);
        for (int i = 0; i < 12; i++) a.step(1f / 120f);
        for (int i = 0; i < 6; i++) b.step(1f / 60f);
        assertEquals(a.value(), b.value(), 1e-4f);
        assertEquals(a.velocity(), b.velocity(), 1e-3f);
        // A long dropped frame stays finite and bounded.
        Spring c = new Spring(Spring.Preset.JIGGLE);
        c.snapTo(0f); c.setTarget(1f);
        c.step(2f);
        assertTrue(Math.abs(c.value() - 1f) < 1e-3f);
    }

    @Test public void retargetKeepsVelocity() {
        Spring s = new Spring(Spring.Preset.SNAPPY);
        s.snapTo(0f);
        s.setTarget(1f);
        for (int i = 0; i < 5; i++) s.step(1f / 60f);
        float v = s.velocity();
        s.setTarget(0f);
        assertEquals(v, s.velocity(), 0f);
        s.step(1f / 240f);
        assertTrue("still moving forward right after the retarget", s.velocity() > 0f);
    }

    @Test public void jiggleKickDipsByTheRequestedAmountThenReboundsSmaller() {
        Spring.Preset p = Spring.Preset.JIGGLE;
        Spring s = new Spring(p);
        s.snapTo(1f);
        s.setVelocity(Spring.kickFor(-Motion.JIGGLE_DIP, p.stiffness, p.dampingRatio));
        float min = 1f, maxAfter = 1f;
        boolean passedMin = false;
        for (int i = 0; i < 2000 && s.step(1f / 1000f); i++) {
            if (s.value() < min) min = s.value();
            else passedMin = true;
            if (passedMin) maxAfter = Math.max(maxAfter, s.value());
        }
        assertEquals(1f - Motion.JIGGLE_DIP, min, 0.002f);
        assertTrue("rebound " + maxAfter, maxAfter > 1.01f && maxAfter < 1.04f);
        assertFalse(s.step(1f / 60f));
    }
}
