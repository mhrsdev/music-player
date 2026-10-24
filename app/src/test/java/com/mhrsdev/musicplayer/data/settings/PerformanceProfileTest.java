package com.mhrsdev.musicplayer.data.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.mhrsdev.musicplayer.data.settings.PerformanceProfile.Preset;
import org.junit.Test;

/** Preset -> switches mapping (owner D26) and "Custom" for any hand-made mix. */
public class PerformanceProfileTest {

    @Test public void fullKeepsEveryEffectAtScreenRate() {
        PerformanceProfile p = PerformanceProfile.of(Preset.FULL);
        assertFalse(p.reduceMotion());
        assertTrue(p.transitions() && p.bounce() && p.movingBackdrop() && p.lyricsBlur() && p.lyricsWords() && p.seekMotion());
        assertEquals(PerformanceProfile.FPS_SCREEN, p.maxFps());
        assertEquals(Preset.FULL, p.preset());
    }

    @Test public void balancedDropsOnlyTheTwoHeavyLayersAndCapsAt60() {
        PerformanceProfile p = PerformanceProfile.of(Preset.BALANCED);
        assertFalse(p.reduceMotion());
        assertFalse(p.movingBackdrop());
        assertFalse(p.lyricsBlur());
        assertTrue(p.transitions() && p.bounce() && p.lyricsWords() && p.seekMotion());
        assertEquals(60, p.maxFps());
        assertEquals(Preset.BALANCED, p.preset());
    }

    @Test public void lightStopsEveryLoopAndCapsAt30ButKeepsMotionForTouches() {
        PerformanceProfile p = PerformanceProfile.of(Preset.LIGHT);
        assertFalse("springs still answer touches: Light is not reduce motion", p.reduceMotion());
        assertFalse(p.transitions() || p.bounce() || p.movingBackdrop() || p.lyricsBlur() || p.lyricsWords() || p.seekMotion());
        assertEquals(30, p.maxFps());
        assertEquals(Preset.LIGHT, p.preset());
    }

    @Test public void customIsAnyMixThatIsNotAPreset() {
        PerformanceProfile full = PerformanceProfile.FULL;
        assertEquals(Preset.CUSTOM, full.withLyricsBlur(false).preset());
        assertEquals(Preset.CUSTOM, full.withMaxFps(30).preset());
        assertEquals(Preset.CUSTOM, full.withReduceMotion(true).preset());
        assertEquals(Preset.CUSTOM, PerformanceProfile.LIGHT.withLyricsWords(true).preset());
        // Back by hand to a preset's exact mix = that preset again.
        assertEquals(Preset.FULL, full.withSeekMotion(false).withSeekMotion(true).preset());
        assertEquals(Preset.BALANCED, full.withMovingBackdrop(false).withLyricsBlur(false).withMaxFps(60).preset());
        assertEquals("Custom is not selectable", Preset.FULL, PerformanceProfile.of(Preset.CUSTOM).preset());
    }

    @Test public void everyWitherChangesExactlyItsOwnField() {
        PerformanceProfile f = PerformanceProfile.FULL;
        assertEquals(new PerformanceProfile(true, true, true, true, true, true, true, 0), f.withReduceMotion(true));
        assertEquals(new PerformanceProfile(false, false, true, true, true, true, true, 0), f.withTransitions(false));
        assertEquals(new PerformanceProfile(false, true, false, true, true, true, true, 0), f.withBounce(false));
        assertEquals(new PerformanceProfile(false, true, true, false, true, true, true, 0), f.withMovingBackdrop(false));
        assertEquals(new PerformanceProfile(false, true, true, true, false, true, true, 0), f.withLyricsBlur(false));
        assertEquals(new PerformanceProfile(false, true, true, true, true, false, true, 0), f.withLyricsWords(false));
        assertEquals(new PerformanceProfile(false, true, true, true, true, true, false, 0), f.withSeekMotion(false));
        assertEquals(new PerformanceProfile(false, true, true, true, true, true, true, 60), f.withMaxFps(60));
    }

    @Test public void unknownFrameRateFallsBackToScreenRate() {
        assertEquals(PerformanceProfile.FPS_SCREEN, PerformanceProfile.FULL.withMaxFps(45).maxFps());
        assertEquals(PerformanceProfile.FPS_SCREEN, PerformanceProfile.FULL.withMaxFps(-1).maxFps());
    }
}
