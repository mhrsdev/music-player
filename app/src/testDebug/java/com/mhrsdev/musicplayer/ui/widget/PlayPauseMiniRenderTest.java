package com.mhrsdev.musicplayer.ui.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;
import android.widget.FrameLayout;

import com.mhrsdev.musicplayer.fake.SampleData;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Real-device bug: the old cookie/squircle play button was clipped by its own bounds (flat cuts). Renders 40/56/72 dp
 * (with and without the mini-player's 4 dp padding) in every state and asserts the shape stays inside the bounds
 * with a clear 1 px ring and is mirror-symmetric; also renders the mini-player (docs/screens/u3-playpause.png,
 * u3-mini-*.png).
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PlayPauseMiniRenderTest {
    private static final String DARK = "w412dp-h915dp-night-xhdpi";

    @Test @Config(sdk = 35, qualifiers = DARK)
    public void shapeStaysInsideBoundsAndSymmetric() throws Exception {
        Activity a = Robolectric.buildActivity(WidgetGalleryActivity.class).setup().get();
        int[] sizesDp = {40, 56, 72};
        float d = a.getResources().getDisplayMetrics().density;
        Bitmap sheet = Bitmap.createBitmap((int) (6 * 80 * d), (int) (3 * 80 * d), Bitmap.Config.ARGB_8888);
        Canvas sc = new Canvas(sheet);
        sc.drawColor(0xFF0F1216);
        for (int si = 0; si < sizesDp.length; si++) {
            for (int state = 0; state < 6; state++) { // paused, playing, paused after spinning; each w/o and with padding
                int px = Math.round(sizesDp[si] * d);
                MorphPlayPauseView v = new MorphPlayPauseView(a);
                v.setColors(0xFFA6C8FF, 0xFF00315F);
                if (state >= 3) { int p = Math.round(4 * d); v.setPadding(p, p, p, p); }
                int kind = state % 3;
                v.setPlaying(kind == 1, false);
                if (kind == 2) {
                    v.setPlaying(true, false);
                    v.setPlaying(false, false);
                }
                v.measure(View.MeasureSpec.makeMeasureSpec(px, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(px, View.MeasureSpec.EXACTLY));
                v.layout(0, 0, px, px);
                Bitmap bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
                v.draw(new Canvas(bmp));
                String what = sizesDp[si] + "dp state " + state;
                int ring = state >= 3 ? Math.round(4 * d) : 1;
                for (int i = 0; i < px; i++) {
                    for (int k = 0; k < ring; k++) {
                        assertTrue(what + " top", Color.alpha(bmp.getPixel(i, k)) < 8);
                        assertTrue(what + " bottom", Color.alpha(bmp.getPixel(i, px - 1 - k)) < 8);
                        assertTrue(what + " left", Color.alpha(bmp.getPixel(k, i)) < 8);
                        assertTrue(what + " right", Color.alpha(bmp.getPixel(px - 1 - k, i)) < 8);
                    }
                }
                if (kind != 2) { // kind 2 = paused after a play/pause round trip
                    int diff = 0;
                    for (int y = 0; y < px; y++) for (int x = 0; x < px; x++) {
                        int m = kind == 0 ? bmp.getPixel(px - 1 - x, y) : bmp.getPixel(x, px - 1 - y);
                        if (kind == 0 && Math.abs(Color.alpha(bmp.getPixel(x, y)) - Color.alpha(bmp.getPixel(x, px - 1 - y))) > 40) diff++;
                        if (kind == 1 && Math.abs(Color.alpha(bmp.getPixel(x, y)) - Color.alpha(m)) > 40) diff++;
                    }
                    assertTrue(what + " asymmetric pixels: " + diff, diff <= px / 4);
                }
                sc.drawBitmap(bmp, state * 80 * d + 4 * d, si * 80 * d + 4 * d, null);
            }
        }
        save(sheet, "u3-playpause");
    }

    @Test @Config(sdk = 35, qualifiers = DARK)
    public void miniDark() throws Exception { mini("u3-mini-dark", 0); }

    @Test @Config(sdk = 35, qualifiers = "w412dp-h915dp-notnight-xhdpi")
    public void miniLight() throws Exception { mini("u3-mini-light", 0); }

    @Test @Config(sdk = 35, qualifiers = "fa-ldrtl-" + DARK)
    public void miniRtl() throws Exception { mini("u3-mini-fa-dark", 24); }

    private void mini(String name, int track) throws Exception {
        Activity a = Robolectric.buildActivity(WidgetGalleryActivity.class).setup().get();
        float d = a.getResources().getDisplayMetrics().density;
        int w = Math.round(412 * d), h = Math.round(120 * d);
        FrameLayout box = new FrameLayout(a);
        box.setPadding(Math.round(12 * d), Math.round(20 * d), Math.round(12 * d), Math.round(20 * d));
        MiniPlayerView mini = new MiniPlayerView(a);
        box.addView(mini, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));
        a.setContentView(box);
        mini.bind(SampleData.TRACKS.get(track));
        mini.setPlaying(true);
        mini.setProgress(70_000L, SampleData.TRACKS.get(track).durationMs());
        box.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        box.layout(0, 0, w, h);
        assertTrue("pill >= 72dp", mini.getHeight() >= 72 * d);
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(com.mhrsdev.musicplayer.ui.theme.Tokens.color(a, com.mhrsdev.musicplayer.R.attr.mpBackground));
        box.draw(c);
        save(bmp, name);
    }

    private static void save(Bitmap bmp, String name) throws Exception {
        File out = new File(new File("../docs/screens"), name + ".png");
        out.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(out)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
        assertTrue(out.length() > 0);
    }
}
