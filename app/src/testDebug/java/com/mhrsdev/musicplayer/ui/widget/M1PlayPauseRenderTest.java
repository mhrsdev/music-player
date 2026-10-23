package com.mhrsdev.musicplayer.ui.widget;

import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;

/**
 * M1: the redesigned play/pause. (1) Owner proposal sheet docs/screens/m1-pause-options.png: the pause styles (A, B = shipped, D),
 * dark and light, morph frames 0 / 0.35 / 0.7 / 1 at 72 dp plus play/pause at 48 dp. (2) For every style, size
 * 40-72 dp, morph frame and the jiggle's peak scale: the silhouette keeps a clear 1 px ring inside the bounds and
 * is mirror-symmetric top/bottom (and left/right for the container alone).
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class M1PlayPauseRenderTest {
    private static final float[] FRAMES = {0f, 0.35f, 0.7f, 1f};
    private static final String[] NAMES = {"A  squircle, round-cap bars", "B  circle (shipped, owner pick)",
            "D  rounded square"};

    @Test @Config(sdk = 35, qualifiers = "w412dp-h915dp-night-xhdpi")
    public void optionsSheet() throws Exception {
        Activity a = Robolectric.buildActivity(WidgetGalleryActivity.class).setup().get();
        float d = a.getResources().getDisplayMetrics().density;
        MorphPlayPauseView.PauseStyle[] styles = MorphPlayPauseView.PauseStyle.values();
        int cell = Math.round(84 * d), labelH = Math.round(22 * d);
        int half = cell * 6 + Math.round(16 * d);
        Bitmap sheet = Bitmap.createBitmap(half * 2, (cell + labelH) * styles.length + Math.round(40 * d),
                Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(sheet);
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setTextSize(13 * d);
        for (int theme = 0; theme < 2; theme++) {
            boolean dark = theme == 0;
            int bg = dark ? 0xFF0F1216 : 0xFFF7F9FF;
            int container = dark ? 0xFFA6C8FF : 0xFF2F5F95, icon = dark ? 0xFF00315F : 0xFFFFFFFF;
            Paint bgp = new Paint();
            bgp.setColor(bg);
            int x0 = theme * half;
            c.drawRect(x0, 0, x0 + half, sheet.getHeight(), bgp);
            text.setColor(dark ? 0xFFE2E2E9 : 0xFF181C21);
            c.drawText((dark ? "Dark" : "Light") + ": play  ->  35%  ->  70%  ->  pause   |  48 dp play, pause",
                    x0 + 12 * d, 26 * d, text);
            for (int si = 0; si < styles.length; si++) {
                int y = Math.round(40 * d) + si * (cell + labelH);
                c.drawText(NAMES[si], x0 + 12 * d, y + 15 * d, text);
                for (int f = 0; f < 6; f++) {
                    int px = Math.round((f < 4 ? 72 : 48) * d);
                    float morph = f < 4 ? FRAMES[f] : (f == 4 ? 0f : 1f);
                    Bitmap b = render(a, styles[si], px, morph, 1f, container, icon, 0);
                    int ox = x0 + Math.round(12 * d) + f * cell + (cell - px) / 2;
                    c.drawBitmap(b, ox, y + labelH + (cell - px) / 2f, null);
                }
            }
        }
        save(sheet, "m1-pause-options");
    }

    @Test @Config(sdk = 35, qualifiers = "w412dp-h915dp-night-xhdpi")
    public void staysInsideBoundsAndSymmetric() throws Exception {
        Activity a = Robolectric.buildActivity(WidgetGalleryActivity.class).setup().get();
        float d = a.getResources().getDisplayMetrics().density;
        int[] sizes = {40, 48, 56, 72};
        float[] scales = {1f, MorphPlayPauseView.HEADROOM, 0.92f};
        Bitmap strip = null;
        Canvas sc = null;
        int col = 0;
        for (MorphPlayPauseView.PauseStyle style : MorphPlayPauseView.PauseStyle.values()) {
            for (int size : sizes) {
                for (float morph : new float[] {0f, 0.5f, 1f}) {
                    for (float scale : scales) {
                        for (int pad = 0; pad <= 1; pad++) {
                            int px = Math.round(size * d);
                            int padPx = pad * Math.round(4 * d);
                            Bitmap b = render(a, style, px, morph, scale, 0xFFA6C8FF, 0xFF00315F, padPx);
                            String what = style + " " + size + "dp m=" + morph + " s=" + scale + " pad=" + padPx;
                            int ring = Math.max(1, padPx);
                            int[] bp = px(b), sp;
                            for (int i = 0; i < px; i++) for (int k = 0; k < ring; k++) {
                                assertTrue(what + " top", Color.alpha(bp[k * px + i]) < 8);
                                assertTrue(what + " bottom", Color.alpha(bp[(px - 1 - k) * px + i]) < 8);
                                assertTrue(what + " left", Color.alpha(bp[i * px + k]) < 8);
                                assertTrue(what + " right", Color.alpha(bp[i * px + px - 1 - k]) < 8);
                            }
                            int diff = 0;
                            for (int y = 0; y < px; y++) for (int x = 0; x < px; x++) {
                                if (Math.abs(lum(bp[y * px + x]) - lum(bp[(px - 1 - y) * px + x])) > 40) diff++;
                            }
                            assertTrue(what + " not top/bottom symmetric: " + diff, diff <= px / 3);
                            // Container alone (icon painted in the container colour): left/right symmetric too.
                            Bitmap shapeOnly = render(a, style, px, morph, scale, 0xFFA6C8FF, 0xFFA6C8FF, padPx);
                            sp = px(shapeOnly);
                            int lr = 0;
                            for (int y = 0; y < px; y++) for (int x = 0; x < px; x++) {
                                if (Math.abs(Color.alpha(sp[y * px + x])
                                        - Color.alpha(sp[y * px + px - 1 - x])) > 40) lr++;
                            }
                            assertTrue(what + " container not left/right symmetric: " + lr, lr <= px / 3);
                            if (size == 72 && pad == 0 && scale == 1f) {
                                if (strip == null) {
                                    strip = Bitmap.createBitmap(Math.round(12 * 80 * d), Math.round(80 * d),
                                            Bitmap.Config.ARGB_8888);
                                    sc = new Canvas(strip);
                                    sc.drawColor(0xFF0F1216);
                                }
                                sc.drawBitmap(b, col * 80 * d + 4 * d, 4 * d, null);
                                col++;
                            }
                        }
                    }
                }
            }
        }
        save(strip, "m1-playpause-frames");
    }

    private static int[] px(Bitmap b) {
        int[] p = new int[b.getWidth() * b.getHeight()];
        b.getPixels(p, 0, b.getWidth(), 0, 0, b.getWidth(), b.getHeight());
        return p;
    }

    private static int lum(int c) {
        return (Color.red(c) + Color.green(c) + Color.blue(c)) / 3 * Color.alpha(c) / 255;
    }

    private static Bitmap render(Activity a, MorphPlayPauseView.PauseStyle style, int px, float morph, float scale,
                                 int container, int icon, int pad) {
        MorphPlayPauseView v = new MorphPlayPauseView(a);
        v.setPauseStyle(style);
        v.setColors(container, icon);
        v.setPadding(pad, pad, pad, pad);
        v.setPlaying(morph >= 0.5f, false);
        v.setFrameForTest(morph, scale);
        v.measure(View.MeasureSpec.makeMeasureSpec(px, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(px, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, px, px);
        Bitmap bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        v.draw(new Canvas(bmp));
        return bmp;
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
