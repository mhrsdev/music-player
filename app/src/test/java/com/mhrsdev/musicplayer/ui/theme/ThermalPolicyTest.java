package com.mhrsdev.musicplayer.ui.theme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.PowerManager;
import com.mhrsdev.musicplayer.data.settings.PerformanceProfile;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Adaptive thermal: warm = at most Balanced, hot = at most Light, never more than the user chose; back when cool. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ThermalPolicyTest {
    @After public void cool() {
        MotionPolicy.setThermalLevel(0);
        MotionPolicy.setPerformance(PerformanceProfile.FULL);
    }

    @Test public void stepsDownWhileHotAndBackWhenCool() {
        MotionPolicy.setPerformance(PerformanceProfile.FULL);
        assertFalse(MotionPolicy.thermalThrottled());
        MotionPolicy.setThermalLevel(ThermalWatch.level(PowerManager.THERMAL_STATUS_MODERATE));
        assertEquals(PerformanceProfile.BALANCED, MotionPolicy.performance());
        assertTrue(MotionPolicy.thermalThrottled());
        MotionPolicy.setThermalLevel(ThermalWatch.level(PowerManager.THERMAL_STATUS_SEVERE));
        assertEquals(PerformanceProfile.LIGHT, MotionPolicy.performance());
        MotionPolicy.setThermalLevel(ThermalWatch.level(PowerManager.THERMAL_STATUS_LIGHT));
        assertEquals(PerformanceProfile.FULL, MotionPolicy.performance());
        assertFalse(MotionPolicy.thermalThrottled());
    }

    @Test public void neverAddsWhatTheUserTurnedOff() {
        PerformanceProfile mine = PerformanceProfile.FULL.withMaxFps(30).withLyricsWords(false);
        PerformanceProfile warm = MotionPolicy.effective(mine, 1);
        assertEquals(30, warm.maxFps());
        assertFalse(warm.lyricsWords());
        assertFalse(warm.movingBackdrop());
        assertEquals(PerformanceProfile.LIGHT, MotionPolicy.effective(PerformanceProfile.LIGHT, 1));
    }
}
