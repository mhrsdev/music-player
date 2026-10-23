package com.mhrsdev.musicplayer.ui.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.mhrsdev.musicplayer.ui.player.lyrics.LyricsMotion;
import org.junit.Test;

/** Distance falloff, spring endpoints, drift bounds. */
public class LyricsMotionTest {

    @Test public void falloffIsMonotonicAndBounded() {
        assertEquals(1f, LyricsMotion.alphaForDistance(0, false, true), 0f);
        float prev = 1f;
        for (int d = 1; d <= 8; d++) {
            float a = LyricsMotion.alphaForDistance(d, false, true);
            assertTrue(a <= prev && a >= 0.35f);
            prev = a;
        }
        assertEquals(0.35f, LyricsMotion.alphaForDistance(20, true, false), 1e-6);
        assertEquals(0f, LyricsMotion.blurForDistance(0, 14f), 0f);
        assertTrue(LyricsMotion.blurForDistance(1, 14f) < LyricsMotion.blurForDistance(3, 14f));
        assertEquals(14f, LyricsMotion.blurForDistance(9, 14f), 0f);
    }

    @Test public void springAndBell() {
        assertEquals(0f, LyricsMotion.SPRING.getInterpolation(0f), 0f);
        assertEquals(1f, LyricsMotion.SPRING.getInterpolation(1f), 0f);
        assertTrue(LyricsMotion.SPRING.getInterpolation(0.3f) > 0.5f); // fast start
        assertEquals(0f, LyricsMotion.bell(0f), 0f);
        assertEquals(0f, LyricsMotion.bell(1f), 0f);
        assertTrue(LyricsMotion.bell(0.45f) > 0.9f);
        assertEquals(0.94f, LyricsMotion.scaleFor(0f), 1e-6);
    }

    @Test public void backdropDriftStaysSmall() {
        for (int i = 0; i < 4; i++) {
            for (float t = 0; t < 120; t += 0.7f) {
                assertTrue(Math.abs(PlayerBackdropView.driftX(i, t)) <= 0.12f + 1e-6);
                assertTrue(Math.abs(PlayerBackdropView.driftY(i, t)) <= 0.09f + 1e-6);
            }
        }
    }
}
