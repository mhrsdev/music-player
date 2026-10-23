package com.mhrsdev.musicplayer.appwidget;

import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RemoteViews;
import com.mhrsdev.musicplayer.domain.model.NowPlaying;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Debug-only visual check: inflates the widget RemoteViews and writes docs/screens/s1-widget-*.png. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35)
public class WidgetRenderTest {
    private static final String DARK = "w412dp-h915dp-night-xhdpi";
    private static final String LIGHT = "w412dp-h915dp-notnight-xhdpi";
    private static final int[][] SIZES_DP = {{360, 74}, {160, 160}, {360, 150}};

    @Test @Config(qualifiers = DARK) public void dark() throws Exception { sheet("dark", true, true, false); }

    @Test @Config(qualifiers = LIGHT) public void light() throws Exception { sheet("light", false, true, false); }

    @Test @Config(qualifiers = "fa-" + DARK) public void persian() throws Exception { sheet("fa", true, true, false); }

    @Test @Config(qualifiers = DARK) public void empty() throws Exception { sheet("empty", true, false, false); }

    @Test @Config(qualifiers = LIGHT) public void emptyLight() throws Exception { sheet("empty-light", false, false, false); }

    /** The unmodified layouts: what the picker preview (previewLayout / previewImage) shows. */
    @Test @Config(qualifiers = DARK) public void previews() throws Exception { sheet("preview", true, false, true); }

    private void sheet(String name, boolean dark, boolean withTrack, boolean rawLayouts) throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        float d = app.getResources().getDisplayMetrics().density;
        Bitmap art = withTrack ? cover(400) : null;
        boolean rtl = app.getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        NowPlaying np = withTrack ? new NowPlaying(rtl ? "ساعت طلایی" : "Golden Hour", rtl ? "سهراب نوری" : "NEFFEX", null,
                true, 74_000, 210_000) : null;
        LinearLayout column = new LinearLayout(app);
        column.setOrientation(LinearLayout.VERTICAL);
        if (rtl) column.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); // a bare view tree does not pick it up by itself
        column.setPadding(Math.round(16 * d), Math.round(16 * d), Math.round(16 * d), Math.round(16 * d));
        WidgetKind[] kinds = WidgetKind.values();
        for (int i = 0; i < kinds.length; i++) {
            View v = rawLayouts ? LayoutInflater.from(app).inflate(kinds[i].layout, column, false)
                    : WidgetRenderer.build(app, kinds[i], np, art, dark, SIZES_DP[i][0], SIZES_DP[i][1]).apply(app, column);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.round(SIZES_DP[i][0] * d), Math.round(SIZES_DP[i][1] * d));
            lp.bottomMargin = Math.round(16 * d);
            column.addView(v, lp);
            if (rawLayouts) save(preview(v, lp.width, lp.height), "widget_preview_" + kinds[i].name().toLowerCase(java.util.Locale.ROOT));
        }
        int w = Math.round((360 + 32) * d);
        column.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        column.layout(0, 0, w, column.getMeasuredHeight());
        Bitmap bmp = Bitmap.createBitmap(w, column.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        // Stand-in wallpaper so the widgets' rounded corners and translucency are visible.
        Paint paint = new Paint();
        paint.setShader(new LinearGradient(0, 0, w, bmp.getHeight(), dark ? 0xFF2A2F3A : 0xFFC9D6E8,
                dark ? 0xFF0E1116 : 0xFFF0E6DA, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, bmp.getHeight(), paint);
        column.draw(c);
        File out = save(bmp, "s1-widget-" + name);
        assertTrue(out.length() > 0);
    }

    /** Widget on a transparent background, tightly cropped: the picker PNG fallback. */
    private static Bitmap preview(View v, int w, int h) {
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, w, h);
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        v.draw(new Canvas(bmp));
        return bmp;
    }

    private static File save(Bitmap bmp, String name) throws Exception {
        File dir = new File("../docs/screens");
        if (!dir.isDirectory()) dir = new File("build");
        File out = new File(dir, name + ".png");
        try (FileOutputStream fos = new FileOutputStream(out)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
        return out;
    }

    /** A synthetic cover with a warm-to-teal diagonal and a bright disc: stands in for album art. */
    private static Bitmap cover(int px) {
        Bitmap b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, px, px, 0xFFE8742A, 0xFF1F6F78, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, px, px, p);
        p.setShader(null);
        p.setColor(0xFFF6D365);
        c.drawCircle(px * 0.68f, px * 0.32f, px * 0.16f, p);
        return b;
    }
}
