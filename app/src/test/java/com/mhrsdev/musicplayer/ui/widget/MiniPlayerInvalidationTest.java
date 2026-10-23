package com.mhrsdev.musicplayer.ui.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * Perf regression guard: the shell feeds the mini-player a state every 250 ms. Unchanged values must cost nothing and
 * progress must repaint only its strip, never the whole pill (gradient, shadow, art, texts).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class MiniPlayerInvalidationTest {
    static class Counting extends MiniPlayerView {
        int full, partial;

        Counting(Context c) {
            super(c);
        }

        @Override public void invalidate() {
            full++;
            super.invalidate();
        }

        @Override public void invalidate(Rect dirty) {
            partial++;
            super.invalidate(dirty);
        }

        @Override public void invalidate(int l, int t, int r, int b) {
            partial++;
            super.invalidate(l, t, r, b);
        }
    }

    @Test public void fourHertzTicksOnlyRepaintTheProgressStrip() {
        Counting v = new Counting(new android.view.ContextThemeWrapper(RuntimeEnvironment.getApplication(),
                com.mhrsdev.musicplayer.R.style.Theme_MusicPlayer));
        v.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        v.layout(0, 0, 1000, v.getMeasuredHeight());
        v.setPlaying(true);
        int fullAfterFirst = v.full;
        v.full = 0;
        v.partial = 0;
        long duration = 200_000;
        for (int tick = 0; tick < 400; tick++) { // 100 s of playback at 4 Hz
            v.setPlaying(true);
            v.setProgress(tick * 250L, duration);
        }
        assertTrue(fullAfterFirst >= 1);
        assertEquals("whole-view invalidations during ticks", 0, v.full);
        assertTrue("progress strip repaints: " + v.partial, v.partial > 0 && v.partial <= 400);
    }
}
