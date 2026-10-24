package com.mhrsdev.musicplayer.appwidget;

import static org.junit.Assert.assertNotNull;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.widget.FrameLayout;
import com.mhrsdev.musicplayer.domain.model.NowPlaying;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * C1 (D32): every widget layout builds and inflates on every supported API (the RemoteViews calls, the dynamic-colour
 * palette on 31+, the size-dependent layouts), with and without a cover, playing and idle, light and dark.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 28, 29, 30, 31, 33, 34, 35}, qualifiers = "w412dp-h915dp-xhdpi")
public class WidgetApiMatrixTest {
    @Test public void everyWidgetBuildsAndDrawsOnThisApi() {
        Application app = RuntimeEnvironment.getApplication();
        Bitmap art = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888);
        new Canvas(art).drawColor(0xFFE8742A);
        NowPlaying playing = new NowPlaying("Golden Hour", "NEFFEX", null, true, 74_000, 210_000);
        NowPlaying paused = new NowPlaying("سهراب", "موج", null, false, 0, 0);
        int[][] sizes = {{360, 74}, {160, 160}, {360, 150}, {110, 110}};
        for (WidgetKind kind : WidgetKind.values()) {
            for (NowPlaying np : new NowPlaying[] {playing, paused, null}) {
                for (boolean dark : new boolean[] {false, true}) {
                    for (int[] size : sizes) {
                        Bitmap cover = np == playing && !dark ? art : null;
                        View v = WidgetRenderer.build(app, kind, np, cover, dark, size[0], size[1]).apply(app, new FrameLayout(app));
                        assertNotNull(v);
                        float d = app.getResources().getDisplayMetrics().density;
                        int w = Math.round(size[0] * d), h = Math.round(size[1] * d);
                        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
                        v.layout(0, 0, w, h);
                    }
                }
            }
        }
    }
}
