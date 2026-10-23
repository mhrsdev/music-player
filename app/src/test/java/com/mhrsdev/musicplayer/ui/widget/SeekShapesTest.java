package com.mhrsdev.musicplayer.ui.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.mhrsdev.musicplayer.data.settings.SeekBarStyle;

import org.junit.Test;

/** D25 seek styles: shared geometry, the hit-testing contract every style inherits, and the style catalog. */
public class SeekShapesTest {
    @Test public void loudnessInterpolatesAndClamps() {
        float[] wf = {0f, 1f, 2f, -1f};
        assertEquals(0f, SeekShapes.loudnessAt(wf, 0f), 1e-6f);
        assertEquals(0.5f, SeekShapes.loudnessAt(wf, 1f / 6f), 1e-4f);
        assertEquals(1f, SeekShapes.loudnessAt(wf, 2f / 3f), 1e-6f); // 2 clamped to 1
        assertEquals(0f, SeekShapes.loudnessAt(wf, 1f), 1e-6f);      // -1 clamped to 0
        assertEquals(0f, SeekShapes.loudnessAt(wf, -3f), 1e-6f);     // fraction clamped
    }

    @Test public void placeholderIsCalmAndLowUntilTheEnvelopeArrives() {
        for (int i = 0; i <= 100; i++) {
            float v = SeekShapes.loudnessAt(null, i / 100f);
            assertTrue(v > 0.15f && v < 0.5f);
            assertEquals(v, SeekShapes.peakIn(new float[0], i / 100f, i / 100f), 1e-6f);
        }
    }

    @Test public void peakKeepsTheLoudestBucket() {
        float[] wf = {0.1f, 0.9f, 0.2f, 0.3f};
        assertEquals(0.9f, SeekShapes.peakIn(wf, 0f, 0.5f), 1e-6f);
        assertEquals(0.3f, SeekShapes.peakIn(wf, 0.75f, 1f), 1e-6f);
        assertEquals(0.2f, SeekShapes.peakIn(wf, 0.5f, 0.5f), 1e-6f); // empty range still reads one bucket
    }

    @Test public void magnifierPeaksAtTheThumbAndFadesToZero() {
        assertEquals(1f, SeekShapes.magnify(0f, 40f), 1e-6f);
        assertEquals(0.5f, SeekShapes.magnify(20f, 40f), 1e-4f);
        assertEquals(SeekShapes.magnify(-13f, 40f), SeekShapes.magnify(13f, 40f), 1e-6f);
        assertEquals(0f, SeekShapes.magnify(40f, 40f), 0f);
        assertEquals(0f, SeekShapes.magnify(5f, 0f), 0f);
        float prev = 2f;
        for (int d = 0; d <= 40; d++) {
            float m = SeekShapes.magnify(d, 40f);
            assertTrue(m <= prev);
            prev = m;
        }
    }

    @Test public void ledBlocksAlwaysShowOneAndNeverOverflow() {
        assertEquals(1, SeekShapes.ledBlocks(0f, 6));
        assertEquals(6, SeekShapes.ledBlocks(1f, 6));
        assertEquals(6, SeekShapes.ledBlocks(7f, 6));
        assertEquals(3, SeekShapes.ledBlocks(0.5f, 6));
        assertEquals(24, SeekShapes.cellCount(24 * 4.5f + 23 * 2f, 4.5f, 2f));
        assertEquals(0, SeekShapes.cellCount(-1f, 4.5f, 2f));
    }

    @Test public void beadSpacingScalesWithLength() {
        assertEquals(0, SeekShapes.beadIntervalMs(0));
        assertEquals(15_000, SeekShapes.beadIntervalMs(150_000));
        assertEquals(60_000, SeekShapes.beadIntervalMs(214_000));
        assertEquals(300_000, SeekShapes.beadIntervalMs(3_600_000));
        for (long d : new long[] {30_000, 179_999, 600_000, 2_400_000, 10_000_000}) {
            long beads = d / SeekShapes.beadIntervalMs(d);
            assertTrue("beads for " + d + ": " + beads, beads >= 1 && beads <= 40);
        }
    }

    @Test public void colourHelpers() {
        assertEquals(0xFF808080, SeekShapes.blend(0xFF000000, 0xFFFFFFFF, 0.502f));
        assertEquals(0xFF123456, SeekShapes.blend(0xFF123456, 0xFFFFFFFF, 0f));
        assertEquals(0x80FFFFFF, SeekShapes.withAlpha(0xFFFFFFFF, 0.5f) | 0x00FFFFFF);
        assertEquals(0x00123456, SeekShapes.withAlpha(0x80123456, 0f));
    }

    @Test public void sampleEnvelopeIsSongShapedAndDeterministic() {
        float[] a = SeekShapes.sampleEnvelope(), b = SeekShapes.sampleEnvelope();
        assertEquals(120, a.length);
        for (int i = 0; i < a.length; i++) {
            assertEquals(a[i], b[i], 0f);
            assertTrue(a[i] >= 0f && a[i] <= 1f);
        }
        assertTrue(SeekShapes.peakIn(a, 0.3f, 0.48f) > SeekShapes.peakIn(a, 0f, 0.05f)); // chorus louder than intro
    }

    @Test public void ridgeIsSmoothAndBounded() {
        float[] out = new float[MountainSeekView.POINTS];
        MountainSeekView.ridgeHeights(SeekShapes.sampleEnvelope(), out);
        for (int i = 0; i < out.length; i++) {
            assertTrue(out[i] >= 0f && out[i] <= 1f);
            if (i > 0) assertTrue("ridge jumps at " + i, Math.abs(out[i] - out[i - 1]) < 0.3f);
        }
        float[] flat = new float[MountainSeekView.POINTS];
        MountainSeekView.ridgeHeights(null, flat);
        for (float v : flat) assertTrue(v > 0.15f && v < 0.4f); // placeholder: a low calm hill
    }

    /** Every style inherits BaseSeekView's touch mapping: the same SeekMath round trip at any width. */
    @Test public void hitTestingRoundTripsAcrossTheTrack() {
        long d = 214_000;
        float left = 8f * 2.75f, right = 360f * 2.75f - 8f * 2.75f;
        for (int pct = 0; pct <= 100; pct += 5) {
            long pos = d * pct / 100;
            float x = SeekMath.xFor(pos, d, left, right);
            assertEquals(pos, SeekMath.positionFor(x, left, right, d), 30);
        }
        assertEquals(0, SeekMath.positionFor(left - 100f, left, right, d));
        assertEquals(d, SeekMath.positionFor(right + 100f, left, right, d));
    }

    @Test public void catalogCoversEveryStyleAndFallsBackToWavy() {
        int loudness = 0;
        for (SeekBarStyle s : SeekBarStyle.values()) {
            assertTrue(SeekStyles.nameRes(s) != 0);
            assertTrue(SeekStyles.descRes(s) != 0);
            assertEquals(s, SeekStyles.parse(s.name()));
            if (SeekStyles.usesWaveform(s) && s.ordinal() > SeekBarStyle.BARS.ordinal()) loudness++;
        }
        assertTrue("at least three new styles draw real loudness", loudness >= 3);
        assertEquals(SeekBarStyle.WAVY, SeekStyles.parse("SPARKLE"));
        assertEquals(SeekBarStyle.WAVY, SeekStyles.parse(null));
        assertEquals(SeekStyles.Family.SEGMENTS, SeekStyles.family(SeekBarStyle.LED_LADDER));
        assertEquals(SeekStyles.Family.PILL, SeekStyles.family(SeekBarStyle.LIQUID));
        assertEquals(SeekStyles.Family.THREAD, SeekStyles.family(SeekBarStyle.BEADS));
    }

    /** Stored prefs use names; the original three must keep their ordinals and names (append-only). */
    @Test public void enumIsAppendOnly() {
        assertEquals(0, SeekBarStyle.WAVY.ordinal());
        assertEquals(1, SeekBarStyle.WAVEFORM.ordinal());
        assertEquals(2, SeekBarStyle.BARS.ordinal());
        for (SeekBarStyle s : SeekBarStyle.values()) {
            assertEquals(s.name(), com.mhrsdev.musicplayer.ui.settings.SettingsStore.SeekStyle.values()[s.ordinal()].name());
        }
    }
}
