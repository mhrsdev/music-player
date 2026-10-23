package com.mhrsdev.musicplayer.playback;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class EffectsPolicyTest {
    @Test public void holdsOnlyWhenObservedOrPlayingWithActiveEffect() {
        assertTrue(EffectsPolicy.shouldHold(true, false, false));
        assertTrue(EffectsPolicy.shouldHold(false, true, true));
        assertFalse(EffectsPolicy.shouldHold(false, true, false));
        assertFalse(EffectsPolicy.shouldHold(false, false, true));
    }

    @Test public void activeMeansEnabledNonFlatOrStrengthSet() {
        assertFalse(EffectsPolicy.anyActive(true, new int[] {0, 0, 0}, 0, 0, 0));
        assertFalse(EffectsPolicy.anyActive(false, new int[] {300, 0, 0}, 0, 0, 0));
        assertTrue(EffectsPolicy.anyActive(true, new int[] {0, 300, 0}, 0, 0, 0));
        assertTrue(EffectsPolicy.anyActive(true, new int[0], 0, 0, 0)); // not probed yet: assume it matters
        assertTrue(EffectsPolicy.anyActive(false, new int[] {0}, 1, 0, 0));
        assertTrue(EffectsPolicy.anyActive(false, new int[] {0}, 0, 1, 0));
        assertTrue(EffectsPolicy.anyActive(false, new int[] {0}, 0, 0, 1));
    }

    @Test public void levelsRoundTripAndClamp() {
        int[] levels = {-1500, 0, 450, 1500, 12};
        assertArrayEquals(levels, EffectsPolicy.parseLevels(EffectsPolicy.formatLevels(levels), 5, -1500, 1500));
        assertArrayEquals(new int[] {-100, 100}, EffectsPolicy.parseLevels("-900,900", 2, -100, 100));
    }

    @Test public void malformedLevelsFallBackToFlat() {
        assertArrayEquals(new int[3], EffectsPolicy.parseLevels("", 3, -1500, 1500));
        assertArrayEquals(new int[3], EffectsPolicy.parseLevels(null, 3, -1500, 1500));
        assertArrayEquals(new int[3], EffectsPolicy.parseLevels("1,2", 3, -1500, 1500)); // other device, other bands
        assertArrayEquals(new int[2], EffectsPolicy.parseLevels("1,x", 2, -1500, 1500));
        assertEquals("", EffectsPolicy.formatLevels(new int[0]));
    }
}
