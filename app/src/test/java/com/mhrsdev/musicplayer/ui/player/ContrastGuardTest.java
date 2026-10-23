package com.mhrsdev.musicplayer.ui.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class ContrastGuardTest {
    private static final int DARK_SCRIM = 0x0F1216;
    private static final int LIGHT_SCRIM = 0xF0F7FD;

    private static int[] fill(int color) {
        int[] px = new int[20 * 20];
        Arrays.fill(px, color);
        return px;
    }

    @Test public void ratioMatchesWcagReference() {
        assertEquals(21.0, ContrastGuard.ratio(0xFFFFFFFF, 0xFF000000), 0.01);
        assertEquals(1.0, ContrastGuard.ratio(0xFF777777, 0xFF777777), 0.001);
    }

    @Test public void darkCoverKeepsBaseAlpha() {
        float a = ContrastGuard.requiredScrimAlpha(fill(0xFF101820), 20, 20, 0f, 1f, DARK_SCRIM, 0.3f,
                0xFFFFFFFF, 4.5);
        assertEquals(0.3f, a, 0.001f);
    }

    @Test public void brightPinkCoverRaisesScrimUntilTextPasses() {
        int[] px = fill(0xFFFFB0CD);
        int text = 0x99FFFFFF; // inactive lyric, white .60
        float a = ContrastGuard.requiredScrimAlpha(px, 20, 20, 0.1f, 0.6f, DARK_SCRIM, 0.3f, text, 3.0);
        assertTrue(a > 0.3f);
        int bg = ContrastGuard.blend(DARK_SCRIM, px[0], a);
        assertTrue(ContrastGuard.ratio(ContrastGuard.blend(text, bg, 0.6f), bg) >= 3.0);
        // and it is the smallest step that passes
        int bgLess = ContrastGuard.blend(DARK_SCRIM, px[0], a - 0.02f);
        assertTrue(ContrastGuard.ratio(ContrastGuard.blend(text, bgLess, 0.6f), bgLess) < 3.0);
    }

    @Test public void onlyPixelsInsideTheBandCount() {
        int[] px = fill(0xFF101820);
        for (int i = 0; i < 20 * 5; i++) px[i] = 0xFFFFFFFF; // bright top quarter
        float bottom = ContrastGuard.requiredScrimAlpha(px, 20, 20, 0.5f, 1f, DARK_SCRIM, 0.3f, 0xFFFFFFFF, 4.5);
        float top = ContrastGuard.requiredScrimAlpha(px, 20, 20, 0f, 0.25f, DARK_SCRIM, 0.3f, 0xFFFFFFFF, 4.5);
        assertEquals(0.3f, bottom, 0.001f);
        assertTrue(top > 0.3f);
    }

    @Test public void lightThemeDarkTextOverDarkCover() {
        int[] px = fill(0xFF000000);
        int text = 0x99191C20; // inactive lyric, onSurface .60
        float a = ContrastGuard.requiredScrimAlpha(px, 20, 20, 0f, 1f, LIGHT_SCRIM, 0.55f, text, 3.0);
        assertTrue(a > 0.55f);
        int bg = ContrastGuard.blend(LIGHT_SCRIM, px[0], a);
        assertTrue(ContrastGuard.ratio(ContrastGuard.blend(text, bg, 0.6f), bg) >= 3.0);
    }

    @Test public void nullOrShortInputReturnsBase() {
        assertEquals(0.4f, ContrastGuard.requiredScrimAlpha(null, 20, 20, 0f, 1f, DARK_SCRIM, 0.4f, -1, 4.5), 0f);
        assertEquals(0.4f, ContrastGuard.requiredScrimAlpha(new int[3], 20, 20, 0f, 1f, DARK_SCRIM, 0.4f, -1, 4.5), 0f);
    }
}
