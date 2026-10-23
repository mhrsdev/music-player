package com.mhrsdev.musicplayer.ui.widget.cover;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CoverRandomTest {

    @Test public void splitMix64MatchesReferenceSequence() {
        // Reference values of SplitMix64 for seed 0 (Vigna's splitmix64.c)
        CoverRandom r = new CoverRandom(0);
        assertEquals(0xE220A8397B1DCDAFL, r.nextLong());
        assertEquals(0x6E789E6AA1B965F4L, r.nextLong());
        assertEquals(0x06C45D188009454FL, r.nextLong());
    }

    @Test public void floatsAndIntsStayInRange() {
        CoverRandom r = new CoverRandom(42);
        for (int i = 0; i < 10_000; i++) {
            float f = r.nextFloat();
            assertTrue(f >= 0f && f < 1f);
            int n = r.nextInt(7);
            assertTrue(n >= 0 && n < 7);
            float g = r.range(-3f, 5f);
            assertTrue(g >= -3f && g < 5f);
        }
    }

    @Test public void hashIsStableAndPinned() {
        // pinned: a change here changes every generated cover in every user's library
        assertEquals(CoverRandom.hash("album:12|concrete gardens"), CoverRandom.hash("album:12|concrete gardens"));
        assertEquals(0xF52A15E9A9B5E89BL, CoverRandom.hash(""));
        assertEquals(0xDB53CE8242520AC5L, CoverRandom.hash("album:12|concrete gardens"));
        assertNotEquals(CoverRandom.hash("a"), CoverRandom.hash("b"));
        assertNotEquals(CoverRandom.hash("شب"), CoverRandom.hash("شب‌ها"));
    }

    @Test public void normalizeFoldsSpellingOnly() {
        assertEquals("concrete gardens", CoverRandom.normalize("  Concrete   GARDENS "));
        assertEquals(CoverRandom.normalize("علي"), CoverRandom.normalize("علی")); // Arabic vs Persian yeh
        assertEquals(CoverRandom.normalize("كوچه"), CoverRandom.normalize("کوچه")); // Arabic vs Persian kaf
        assertEquals(CoverRandom.normalize("ســلام"), CoverRandom.normalize("سلام")); // tatweel
        assertEquals(CoverRandom.hash("Kasra"), CoverRandom.hash("KASRA"));
    }
}
