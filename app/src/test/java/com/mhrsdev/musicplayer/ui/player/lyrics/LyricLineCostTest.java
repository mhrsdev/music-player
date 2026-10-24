package com.mhrsdev.musicplayer.ui.player.lyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import com.mhrsdev.musicplayer.domain.model.LyricLine;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/**
 * v0.3.1 (D29): when the active line moves, the lines that stay inactive only change alpha (RenderNode property) and
 * blur (cached effect): no re-recorded display list, so no re-rendered + re-blurred line layer on every animation frame.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class LyricLineCostTest {
    static class Counting extends LyricLineView {
        int invalidations;

        Counting(Context c) {
            super(c);
        }

        @Override public void invalidate() {
            invalidations++;
            super.invalidate();
        }
    }

    @Test public void inactiveLineTransitionDoesNotRedraw() {
        Counting v = new Counting(RuntimeEnvironment.getApplication());
        v.bind(new LyricLine(1000, "a line that stays inactive"), 26f, 0xFFFFFFFF, () -> 0L);
        v.setEffects(true, true, true);
        v.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        v.layout(0, 0, 1080, v.getMeasuredHeight());
        v.setDistance(2f, false);
        v.invalidations = 0;

        v.setDistance(3f, true); // the active line moved down by one: this line is now 3 away
        ShadowLooper.idleMainLooper(600, TimeUnit.MILLISECONDS);

        System.out.println("PERF inactive lyric line, one 460 ms distance transition: " + v.invalidations
                + " display-list re-records (v0.3.0: one per animation frame, ~55 at 120 Hz)");
        assertEquals(0, v.invalidations);
        assertEquals(LyricsMotion.alphaForDistance(3f, false, true), v.getAlpha(), 1e-4f);
        assertTrue("plain line: alpha goes to the paint, no offscreen layer", !v.hasOverlappingRendering());
    }

    @Test public void lineBecomingActiveStillAnimatesItsEmphasis() {
        Counting v = new Counting(RuntimeEnvironment.getApplication());
        v.bind(new LyricLine(1000, "the next line"), 26f, 0xFFFFFFFF, () -> 0L);
        v.setDistance(1f, false);
        v.invalidations = 0;
        v.setDistance(0f, true);
        ShadowLooper.idleMainLooper(600, TimeUnit.MILLISECONDS);
        assertTrue("scale 0.94 -> 1 is drawn by hand: " + v.invalidations, v.invalidations > 3);
        assertEquals(1f, v.getAlpha(), 1e-4f);
    }
}
