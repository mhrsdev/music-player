package com.mhrsdev.musicplayer.ui.widget;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * v0.3.1 (D29): each colour blob of the player / ambient backdrop is drawn as its own circle (its gradient is
 * transparent beyond the radius), not as a full-screen rect. Measures the GPU fill that remains, over a drift cycle.
 */
public class AmbientFieldCoverageTest {
    @Test public void blobsFillWellUnderFourScreens() {
        int w = 1440, h = 3120; // S26 Ultra at QHD+
        float worst = 0f, sum = 0f;
        int samples = 0;
        for (float sec = 0f; sec < 62f; sec += 0.5f, samples++) {
            float frame = 0f;
            for (int i = 0; i < AmbientField.BLOBS; i++) frame += AmbientField.coverage(i, w, h, sec, 1f);
            worst = Math.max(worst, frame);
            sum += frame;
        }
        System.out.println("PERF backdrop blobs, full-screen fills per drift frame: before 4.00, now avg "
                + String.format(java.util.Locale.ROOT, "%.2f", sum / samples) + ", worst "
                + String.format(java.util.Locale.ROOT, "%.2f", worst));
        assertTrue("worst " + worst, worst < 3.4f);
    }
}
