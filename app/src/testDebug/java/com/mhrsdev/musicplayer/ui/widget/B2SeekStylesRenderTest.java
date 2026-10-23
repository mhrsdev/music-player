package com.mhrsdev.musicplayer.ui.widget;

import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.util.function.Function;

/**
 * D25 seek styles comparison sheet (docs/screens/b2-styles-sheet.png): every style, old and new, at 0 / 40 / 100 %
 * and dragging (thumb grown) at 360 dp, on a dark and a light card, with a realistic loudness envelope.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class B2SeekStylesRenderTest {
    static final int DARK_ACTIVE = 0xFF6FD3E3, DARK_INACTIVE = 0x47FFFFFF, DARK_BG = 0xFF10161B;
    static final int LIGHT_ACTIVE = 0xFF1D5BA6, LIGHT_INACTIVE = 0x3D181C21, LIGHT_BG = 0xFFF4F6F9;
    static final long DURATION = 214_000L;

    record Style(String name, Function<Context, BaseSeekView> make) {}

    static final Style[] STYLES = {
            new Style("Wavy (old)", WavySeekView::new),
            new Style("Waveform (old)", WaveformSeekView::new),
            new Style("Bars (old)", BarsSeekView::new),
            new Style("Ribbon", RibbonSeekView::new),
            new Style("Pulse dots", PulseDotsSeekView::new),
            new Style("LED ladder", LedLadderSeekView::new),
            new Style("Mountain", MountainSeekView::new),
            new Style("Thread & beads", BeadsSeekView::new),
            new Style("Liquid", LiquidSeekView::new),
    };

    static float[] songEnvelope() {
        return SeekShapes.sampleEnvelope();
    }

    static Bitmap renderBar(BaseSeekView v, int wPx, int hPx) {
        v.measure(View.MeasureSpec.makeMeasureSpec(wPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(hPx, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, wPx, hPx);
        Bitmap bmp = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888);
        v.draw(new Canvas(bmp));
        return bmp;
    }

    static BaseSeekView configure(Style s, Context c, boolean dark, float progress, boolean drag, float[] wf) {
        BaseSeekView v = s.make().apply(c);
        v.setColors(dark ? DARK_ACTIVE : LIGHT_ACTIVE, dark ? DARK_INACTIVE : LIGHT_INACTIVE);
        v.setDuration(DURATION);
        v.setBuffered(DURATION);
        v.setWaveform(wf);
        v.setPosition(Math.round(DURATION * progress));
        if (drag) v.setPreviewDragging(true);
        return v;
    }

    @Test @Config(sdk = 35, qualifiers = "w412dp-h915dp-night-xhdpi")
    public void stylesSheet() throws Exception {
        Activity a = Robolectric.buildActivity(WidgetGalleryActivity.class).setup().get();
        float d = a.getResources().getDisplayMetrics().density;
        float[] wf = songEnvelope();
        int barW = Math.round(360 * d), barH = Math.round(56 * d);
        int pad = Math.round(16 * d), label = Math.round(30 * d), cap = Math.round(14 * d);
        int cardW = barW, cardH = 4 * (barH + cap) + pad;
        int rowH = label + cardH + pad;
        int sheetW = 2 * cardW + 3 * pad;
        Bitmap sheet = Bitmap.createBitmap(sheetW, STYLES.length * rowH + pad, Bitmap.Config.ARGB_8888);
        Canvas sc = new Canvas(sheet);
        sc.drawColor(0xFF1C2228);
        Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);
        title.setColor(Color.WHITE);
        title.setTextSize(17 * d);
        title.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
        small.setTextSize(10 * d);
        Paint card = new Paint(Paint.ANTI_ALIAS_FLAG);
        String[] states = {"0 %", "40 %", "100 %", "dragging at 40 %"};
        float[] prog = {0f, 0.4f, 1f, 0.4f};
        for (int si = 0; si < STYLES.length; si++) {
            int top = pad + si * rowH;
            sc.drawText(STYLES[si].name(), pad, top + label - 10 * d, title);
            for (int t = 0; t < 2; t++) {
                boolean dark = t == 0;
                int cx = pad + t * (cardW + pad), cy = top + label;
                card.setColor(dark ? DARK_BG : LIGHT_BG);
                sc.drawRoundRect(cx, cy, cx + cardW, cy + cardH, 16 * d, 16 * d, card);
                small.setColor(dark ? 0x99FFFFFF : 0x99181C21);
                for (int k = 0; k < 4; k++) {
                    BaseSeekView v = configure(STYLES[si], a, dark, prog[k], k == 3, wf);
                    Bitmap bmp = renderBar(v, barW, barH);
                    int y = cy + pad / 2 + k * (barH + cap);
                    sc.drawText(states[k], cx + 16 * d, y + cap - 3 * d, small);
                    sc.drawBitmap(bmp, cx, y + cap, null);
                    assertTrue(STYLES[si].name() + " drew nothing", hasInk(bmp));
                }
            }
        }
        save(sheet, "b2-styles-sheet.png");
    }

    static boolean hasInk(Bitmap b) {
        for (int y = 0; y < b.getHeight(); y += 2) {
            for (int x = 0; x < b.getWidth(); x += 2) if (Color.alpha(b.getPixel(x, y)) > 40) return true;
        }
        return false;
    }

    static void save(Bitmap bmp, String name) throws Exception {
        File dir = new File("../docs/screens");
        dir.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(dir, name))) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }
}
