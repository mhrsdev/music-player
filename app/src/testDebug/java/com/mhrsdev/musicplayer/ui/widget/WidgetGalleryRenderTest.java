package com.mhrsdev.musicplayer.ui.widget;

import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;

/** Renders the debug widget gallery to app/build/widget-gallery-*.png (native graphics) for visual review. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WidgetGalleryRenderTest {

    @Test @Config(sdk = 35, qualifiers = "w412dp-h915dp-night-xhdpi")
    public void renderDark() throws Exception {
        render("dark");
    }

    @Test @Config(sdk = 35, qualifiers = "w412dp-h915dp-notnight-xhdpi")
    public void renderLight() throws Exception {
        render("light");
    }

    private void render(String name) throws Exception {
        WidgetGalleryActivity a = Robolectric.buildActivity(WidgetGalleryActivity.class).setup().get();
        View root = a.getWindow().getDecorView();
        int w = 412 * 2;
        View content = ((android.view.ViewGroup) a.findViewById(android.R.id.content)).getChildAt(0);
        View col = ((android.view.ViewGroup) content).getChildAt(0);
        col.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int h = col.getMeasuredHeight();
        col.layout(0, 0, w, h);
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(com.mhrsdev.musicplayer.ui.theme.Tokens.color(a, com.mhrsdev.musicplayer.R.attr.mpBackground));
        col.draw(c);
        File out = new File("build/widget-gallery-" + name + ".png");
        try (FileOutputStream fos = new FileOutputStream(out)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
        assertTrue(root != null && out.length() > 0);
    }
}
