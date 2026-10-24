package com.mhrsdev.musicplayer.data.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.mhrsdev.musicplayer.data.settings.DeviceClass.Signals;
import com.mhrsdev.musicplayer.data.settings.PerformanceProfile.Preset;
import org.junit.Test;

/** C1 (D32): the first-run Performance preset and the cache budgets for each class of phone. */
public class DeviceClassTest {
    private static Signals phone(boolean lowRam, int heapMb, int cores, int sdk, long ramMb) {
        return new Signals(lowRam, heapMb, cores, sdk, ramMb);
    }

    @Test public void flagshipsGetFull() {
        assertEquals(Preset.FULL, DeviceClass.presetFor(phone(false, 512, 8, 37, 12_000)));   // S26 Ultra class
        assertEquals(Preset.FULL, DeviceClass.presetFor(phone(false, 256, 8, 33, 6_000)));    // 2022 mid-range
        assertEquals(Preset.FULL, DeviceClass.presetFor(phone(false, 192, 6, 29, 3_700)));    // 4 GB, Android 10, 6 cores
    }

    @Test public void eachBalancedTriggerAloneIsEnough() {
        assertEquals("low-RAM flag", Preset.BALANCED, DeviceClass.presetFor(phone(true, 256, 8, 34, 4_000)));
        assertEquals("small heap class", Preset.BALANCED, DeviceClass.presetFor(phone(false, 128, 8, 34, 4_000)));
        assertEquals("4 cores", Preset.BALANCED, DeviceClass.presetFor(phone(false, 256, 4, 34, 6_000)));
        assertEquals("2 cores", Preset.BALANCED, DeviceClass.presetFor(phone(false, 256, 2, 34, 6_000)));
        assertEquals("API 28", Preset.BALANCED, DeviceClass.presetFor(phone(false, 256, 8, 28, 6_000)));
        assertEquals("API 26 with plenty of RAM", Preset.BALANCED, DeviceClass.presetFor(phone(false, 256, 8, 26, 6_000)));
    }

    @Test public void veryWeakPhonesGetLight() {
        assertEquals("Go edition: low RAM and a 96 MB heap", Preset.LIGHT, DeviceClass.presetFor(phone(true, 96, 4, 33, 1_900)));
        assertEquals("below 96 too", Preset.LIGHT, DeviceClass.presetFor(phone(true, 64, 4, 30, 1_000)));
        assertEquals("API 26 with 3 GB", Preset.LIGHT, DeviceClass.presetFor(phone(false, 192, 8, 26, 2_800)));
        assertEquals("API 27 with 2 GB", Preset.LIGHT, DeviceClass.presetFor(phone(false, 192, 8, 27, 1_900)));
    }

    @Test public void veryWeakNeedsItsWholeCondition() {
        assertEquals("low RAM but a 128 MB heap is only Balanced", Preset.BALANCED, DeviceClass.presetFor(phone(true, 128, 8, 33, 2_000)));
        assertEquals("API 27 with 4 GB is only Balanced", Preset.BALANCED, DeviceClass.presetFor(phone(false, 256, 8, 27, 3_700)));
        assertEquals("API 28 with little RAM is only Balanced", Preset.BALANCED, DeviceClass.presetFor(phone(false, 192, 8, 28, 2_000)));
    }

    @Test public void budgetsShrinkOnLowEndPhonesAndNeverGrowPastTheDefaults() {
        Signals strong = phone(false, 512, 8, 37, 12_000);
        Signals lowRam = phone(true, 256, 8, 33, 2_000);
        Signals smallHeap = phone(false, 128, 8, 33, 4_000);
        assertFalse(DeviceClass.lowEnd(strong));
        assertTrue(DeviceClass.lowEnd(lowRam));
        assertTrue(DeviceClass.lowEnd(smallHeap));
        assertEquals(1024, DeviceClass.maxCoverBucketPx(strong));
        assertEquals(768, DeviceClass.maxCoverBucketPx(smallHeap));
        assertEquals(4 << 20, DeviceClass.playerBufferBytes(strong));
        assertEquals(2 << 20, DeviceClass.playerBufferBytes(lowRam));
        assertEquals(2 << 20, DeviceClass.notificationCoverCacheBytes(smallHeap));
        assertEquals("one decode at a time", 1, DeviceClass.artworkWorkers(lowRam));
        assertEquals(2, DeviceClass.artworkWorkers(strong));
        assertEquals("never fewer than one worker", 1, DeviceClass.artworkWorkers(phone(false, 256, 1, 33, 4_000)));
    }

    @Test public void lowEndPhonesCannotTurnTheLyricsBlurOn() {
        // blurSupported = API 31+ and not lowEnd: the rule it relies on
        assertTrue(DeviceClass.lowEnd(phone(true, 256, 8, 34, 2_000)));
        assertTrue(DeviceClass.lowEnd(phone(false, 96, 8, 34, 2_000)));
        assertFalse(DeviceClass.lowEnd(phone(false, 192, 8, 34, 4_000)));
    }
}
