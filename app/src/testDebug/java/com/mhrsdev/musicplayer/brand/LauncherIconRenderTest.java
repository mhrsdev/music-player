package com.mhrsdev.musicplayer.brand;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlendMode;
import android.graphics.BlendModeColorFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;

import com.mhrsdev.musicplayer.FakeRenderApp;
import com.mhrsdev.musicplayer.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Renders the real adaptive launcher icon (mipmap/ic_launcher layers) through launcher masks, the themed
 * monochrome layer and the status-bar icon to app/build/launcher-icon-render.png (copied to docs/brand/).
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = FakeRenderApp.class, sdk = 35, qualifiers = "xxhdpi")
public class LauncherIconRenderTest {

    private static final int CELL = 216;   // one 72dp visible area rendered at 3x
    private static final int GAP = 24;

    @Test
    public void render() throws Exception {
        Context ctx = RuntimeEnvironment.getApplication();
        Drawable d = ctx.getDrawable(R.mipmap.ic_launcher);
        assertTrue("launcher icon must be adaptive", d instanceof AdaptiveIconDrawable);
        AdaptiveIconDrawable icon = (AdaptiveIconDrawable) d;
        assertTrue("themed icon needs a monochrome layer", icon.getMonochrome() != null);
        assertTrue(ctx.getDrawable(R.mipmap.ic_launcher_round) instanceof AdaptiveIconDrawable);

        int cols = 5;
        int w = GAP + cols * (CELL + GAP);
        int h = GAP + 2 * (CELL + GAP) + 96 + GAP;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(Color.parseColor("#0F1216"));
        Paint light = new Paint(Paint.ANTI_ALIAS_FLAG);
        light.setColor(Color.parseColor("#F7F9FF"));
        c.drawRect(0, GAP + CELL + GAP / 2f, w, h, light);

        // Row 1 (dark wallpaper): circle, squircle, rounded square, full 108dp canvas with safe zone, themed (dark).
        int y1 = GAP;
        drawMasked(c, icon, GAP, y1, circle());
        drawMasked(c, icon, GAP + (CELL + GAP), y1, squircle());
        drawMasked(c, icon, GAP + 2 * (CELL + GAP), y1, rounded());
        drawFullCanvas(c, icon, GAP + 3 * (CELL + GAP), y1);
        drawThemed(c, icon.getMonochrome(), GAP + 4 * (CELL + GAP), y1,
                Color.parseColor("#1E4878"), Color.parseColor("#D4E3FF"));

        // Row 2 (light wallpaper): same masks + themed (light).
        int y2 = GAP + CELL + GAP;
        drawMasked(c, icon, GAP, y2, circle());
        drawMasked(c, icon, GAP + (CELL + GAP), y2, squircle());
        drawMasked(c, icon, GAP + 2 * (CELL + GAP), y2, rounded());
        drawThemed(c, icon.getMonochrome(), GAP + 3 * (CELL + GAP), y2,
                Color.parseColor("#D4E3FF"), Color.parseColor("#001C3A"));
        drawThemed(c, icon.getMonochrome(), GAP + 4 * (CELL + GAP), y2,
                Color.parseColor("#9EF2E7"), Color.parseColor("#00201D"));

        // Row 3: status-bar icon at 24dp (x3 = 72px) and 1x (24px), white on ink and tinted on paper.
        int y3 = GAP + 2 * (CELL + GAP);
        Drawable stat = ctx.getDrawable(R.drawable.ic_stat_playback);
        Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
        ink.setColor(Color.parseColor("#0F1216"));
        c.drawRoundRect(new RectF(GAP, y3, GAP + 220, y3 + 96), 24, 24, ink);
        drawAt(c, stat, GAP + 16, y3 + 12, 72, Color.WHITE);
        drawAt(c, stat, GAP + 120, y3 + 36, 24, Color.WHITE);
        drawAt(c, stat, GAP + 160, y3 + 40, 16, Color.WHITE);
        drawAt(c, stat, GAP + 260, y3 + 12, 72, Color.parseColor("#2F5F95"));
        drawAt(c, stat, GAP + 364, y3 + 36, 24, Color.parseColor("#181C21"));
        drawAt(c, stat, GAP + 404, y3 + 40, 16, Color.parseColor("#181C21"));
        // Small launcher sizes: 48dp-at-1x and 24px.
        drawMaskedScaled(c, icon, GAP + 460, y3 + 24, 48, circle());
        drawMaskedScaled(c, icon, GAP + 524, y3 + 36, 24, circle());

        File out = new File("build/launcher-icon-render.png");
        try (FileOutputStream fos = new FileOutputStream(out)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
        assertTrue(out.length() > 0);
    }

    /** Mask paths in the 72x72 visible-area space. */
    private static Path circle() {
        Path p = new Path();
        p.addCircle(36, 36, 36, Path.Direction.CW);
        return p;
    }

    private static Path rounded() {
        Path p = new Path();
        p.addRoundRect(new RectF(0, 0, 72, 72), 16, 16, Path.Direction.CW);
        return p;
    }

    private static Path squircle() {
        Path p = new Path();
        p.moveTo(36, 0);
        p.cubicTo(62, 0, 72, 10, 72, 36);
        p.cubicTo(72, 62, 62, 72, 36, 72);
        p.cubicTo(10, 72, 0, 62, 0, 36);
        p.cubicTo(0, 10, 10, 0, 36, 0);
        p.close();
        return p;
    }

    private static void drawMasked(Canvas c, AdaptiveIconDrawable icon, int x, int y, Path mask) {
        drawMaskedScaled(c, icon, x, y, CELL, mask);
    }

    private static void drawMaskedScaled(Canvas c, AdaptiveIconDrawable icon, int x, int y, int size, Path mask) {
        float s = size / 72f;
        c.save();
        c.translate(x, y);
        c.scale(s, s);
        c.clipPath(mask);
        c.translate(-18, -18);           // 108dp canvas, 18dp bleed on each side
        layers(c, icon.getBackground(), icon.getForeground());
        c.restore();
    }

    private static void drawFullCanvas(Canvas c, AdaptiveIconDrawable icon, int x, int y) {
        float s = CELL / 108f;
        c.save();
        c.translate(x, y);
        c.scale(s, s);
        layers(c, icon.getBackground(), icon.getForeground());
        Paint guide = new Paint(Paint.ANTI_ALIAS_FLAG);
        guide.setStyle(Paint.Style.STROKE);
        guide.setStrokeWidth(0.6f);
        guide.setColor(Color.parseColor("#FF4FD8"));
        c.drawRect(18, 18, 90, 90, guide);   // visible area
        c.drawCircle(54, 54, 33, guide);     // safe zone
        c.restore();
    }

    private static void drawThemed(Canvas c, Drawable mono, int x, int y, int bg, int fg) {
        float s = CELL / 72f;
        c.save();
        c.translate(x, y);
        c.scale(s, s);
        c.clipPath(circle());
        c.drawColor(bg);
        c.translate(-18, -18);
        Drawable m = mono.mutate();
        m.setColorFilter(new BlendModeColorFilter(fg, BlendMode.SRC_IN));
        m.setBounds(0, 0, 108, 108);
        m.draw(c);
        m.setColorFilter(null);
        c.restore();
    }

    private static void layers(Canvas c, Drawable bg, Drawable fg) {
        bg.setBounds(0, 0, 108, 108);
        bg.draw(c);
        fg.setBounds(0, 0, 108, 108);
        fg.draw(c);
    }

    private static void drawAt(Canvas c, Drawable d, int x, int y, int size, int tint) {
        Drawable m = d.mutate();
        m.setColorFilter(new BlendModeColorFilter(tint, BlendMode.SRC_IN));
        m.setBounds(x, y, x + size, y + size);
        m.draw(c);
        m.setColorFilter(null);
    }
}
