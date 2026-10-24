package com.mhrsdev.musicplayer.ui.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Android 17 per-app memory limits (D31): the artwork cache stays a small, capped fraction of the heap. */
public class ArtworkCacheBudgetTest {
    private static final long MB = 1024 * 1024;

    @Test public void cacheIsSmallAndCappedOnEveryHeap() {
        assertEquals("floor: two hero covers", 6 * MB, ArtworkLoader.cacheBytes(64 * MB));
        assertEquals(8 * MB, ArtworkLoader.cacheBytes(128 * MB));
        assertEquals("flagship (S26 Ultra class) heap: capped", 16 * MB, ArtworkLoader.cacheBytes(512 * MB));
        assertEquals("largeHeap-sized heap: still capped", 16 * MB, ArtworkLoader.cacheBytes(1024 * MB));
        for (long heap = 96 * MB; heap <= 2048 * MB; heap += 32 * MB) {
            assertTrue("over 1/16 of a " + heap / MB + " MB heap", ArtworkLoader.cacheBytes(heap) <= Math.max(6 * MB, heap / 16));
        }
    }

    @Test public void largestGeneratedCoverFitsWithRoom() {
        int largest = ArtworkLoader.BUCKETS[ArtworkLoader.BUCKETS.length - 1];
        long rgb565 = (long) largest * largest * 2;
        assertTrue("a 1024 px cover is at most a third of the smallest cache", rgb565 * 3 <= 6 * MB);
    }
}
