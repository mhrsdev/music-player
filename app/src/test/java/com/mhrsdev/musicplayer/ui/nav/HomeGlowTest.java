package com.mhrsdev.musicplayer.ui.nav;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Owner bug: the top glow ended with a hard horizontal edge. The ramp must fall monotonically to alpha 0. */
public class HomeGlowTest {
    @Test public void rampEndsTransparentWithoutSteps() {
        int[] c = HomeGlow.stops(9);
        assertEquals(HomeGlow.PEAK_ALPHA, c[0] >>> 24);
        assertEquals(0, c[c.length - 1] >>> 24);
        for (int i = 1; i < c.length; i++) {
            assertTrue((c[i] >>> 24) <= (c[i - 1] >>> 24));
            assertEquals(HomeGlow.TINT, c[i] & 0xFFFFFF); // same hue all the way: no grey band
        }
    }
}
