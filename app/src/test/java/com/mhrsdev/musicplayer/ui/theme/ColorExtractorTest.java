package com.mhrsdev.musicplayer.ui.theme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class ColorExtractorTest {
    @Test public void dominantColorfulHueWinsOverDarkBackground() {
        int[] px = new int[1024];
        Arrays.fill(px, 0xFF0A0A0A);
        for (int i = 0; i < 200; i++) px[i] = 0xFFD81B60;
        for (int i = 200; i < 260; i++) px[i] = 0xFF1E88E5;
        int seed = ColorExtractor.extractSeed(px, px.length);
        float[] hsl = new float[3];
        ColorMath.rgbToHsl(seed, hsl);
        assertTrue("hue " + hsl[0], hsl[0] > 320 || hsl[0] < 10);
    }

    @Test public void greyscaleFallsBackToMean() {
        int[] px = new int[64];
        Arrays.fill(px, 0xFF808080);
        assertEquals(0xFF808080, ColorExtractor.extractSeed(px, px.length));
    }

    @Test public void hslRoundTrip() {
        float[] hsl = new float[3];
        for (int c : new int[] {0xFFA6C8FF, 0xFF00504A, 0xFFFFB4AB, 0xFF5E1133}) {
            ColorMath.rgbToHsl(c, hsl);
            int back = ColorMath.hslToRgb(hsl[0], hsl[1], hsl[2]);
            assertTrue(Math.abs(ColorMath.red(c) - ColorMath.red(back)) <= 1);
            assertTrue(Math.abs(ColorMath.green(c) - ColorMath.green(back)) <= 1);
            assertTrue(Math.abs(ColorMath.blue(c) - ColorMath.blue(back)) <= 1);
        }
    }

    @Test public void accentSchemesAreReadable() {
        int[] seeds = {0xFFD81B60, 0xFF1E88E5, 0xFF43A047, 0xFFFDD835, 0xFF808080, 0xFF6A1B9A, 0xFFFF6F00};
        for (int s : seeds) {
            for (boolean dark : new boolean[] {true, false}) {
                AccentScheme a = AccentScheme.fromSeed(s, dark);
                assertTrue("onPrimary/primary " + Integer.toHexString(s) + " dark=" + dark,
                        ColorMath.contrast(a.onPrimary(), a.primary()) >= 4.5);
                assertTrue("onContainer/container " + Integer.toHexString(s) + " dark=" + dark,
                        ColorMath.contrast(a.onContainer(), a.container()) >= 4.5);
            }
        }
    }

    @Test public void contrastMatchesDesignTable() {
        assertEquals(14.56, ColorMath.contrast(0xFFE2E2E9, 0xFF0F1216), 0.05);
    }
}
