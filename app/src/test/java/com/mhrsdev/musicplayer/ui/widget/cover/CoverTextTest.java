package com.mhrsdev.musicplayer.ui.widget.cover;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CoverTextTest {
    /** Monospace-ish fake: each code point is 0.6 em, Persian letters 0.5 em. */
    private static final CoverText.Measurer M = (s, size) -> {
        float w = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            w += (cp >= 0x0600 && cp <= 0x06FF ? 0.5f : cp == 0x200C ? 0f : 0.6f) * size;
            i += Character.charCount(cp);
        }
        return w;
    };

    private static void assertFits(CoverText.Fit f, float width, int maxLines) {
        assertTrue(f.lines().size() <= maxLines);
        for (String l : f.lines()) assertTrue(l + " too wide", M.width(l, f.size()) <= width + 0.001f);
    }

    @Test public void shortTitleKeepsMaxSizeOnOneLine() {
        CoverText.Fit f = CoverText.fit("WITHOUT YOU", 840, 2, 84, 54, M);
        assertEquals(84f, f.size(), 0.001f);
        assertEquals(1, f.lines().size());
        assertFalse(f.ellipsized());
    }

    @Test public void longerTitleWrapsBalancedBeforeShrinking() {
        CoverText.Fit f = CoverText.fit("MIDNIGHT RADIO SESSIONS VOL 2", 840, 2, 84, 54, M);
        assertFits(f, 840, 2);
        assertEquals(2, f.lines().size());
        float a = M.width(f.lines().get(0), f.size()), b = M.width(f.lines().get(1), f.size());
        assertTrue(Math.abs(a - b) < 0.35f * Math.max(a, b)); // balanced, no orphan word
    }

    @Test public void longWordShrinks() {
        CoverText.Fit f = CoverText.fit("SUPERCALIFRAGILISTIC", 840, 2, 84, 54, M);
        assertTrue(f.size() < 84f);
        assertFits(f, 840, 2);
        assertFalse(f.ellipsized());
    }

    @Test public void hopelessTextIsEllipsizedNeverCropped() {
        String t = "A VERY LONG TITLE THAT WILL NOT FIT ON TWO LINES AT ALL EVEN AT THE SMALLEST SIZE";
        CoverText.Fit f = CoverText.fit(t, 840, 2, 84, 54, M);
        assertEquals(54f, f.size(), 0.001f);
        assertTrue(f.ellipsized());
        assertFits(f, 840, 2);
        assertTrue(f.lines().get(1).endsWith(CoverText.ELLIPSIS));
    }

    @Test public void persianWrapsOnSpacesAndKeepsZwnj() {
        CoverText.Fit f = CoverText.fit("باران که می‌آید و شب‌های تهران", 500, 2, 84, 40, M);
        assertFits(f, 500, 2);
        for (String l : f.lines()) {
            assertFalse(l.startsWith("‌") || l.endsWith("‌"));
            assertFalse(l.contains("می‌ ") && !l.contains("می‌آید")); // no break inside a ZWNJ word
        }
        assertTrue(String.join(" ", f.lines()).contains("می‌آید"));
    }

    @Test public void persianEllipsisDropsWholeCodePoints() {
        String e = CoverText.ellipsize("شب‌های تهران تهران تهران", 40, 400, M);
        assertTrue(M.width(e, 40) <= 400);
        assertTrue(e.endsWith(CoverText.ELLIPSIS));
        assertFalse(e.contains("‌" + CoverText.ELLIPSIS));
    }

    @Test public void emptyTextHasNoLines() {
        assertTrue(CoverText.fit("  ", 100, 2, 10, 5, M).lines().isEmpty());
    }
}
