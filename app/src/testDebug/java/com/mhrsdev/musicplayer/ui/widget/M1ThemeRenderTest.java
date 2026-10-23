package com.mhrsdev.musicplayer.ui.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.fake.SampleData;
import com.mhrsdev.musicplayer.ui.theme.Tokens;
import com.mhrsdev.musicplayer.ui.theme.TypeRoles;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;

/**
 * M1: type roles (weight + roundness), the M3 role mapping and the floating chrome over a busy, striped backdrop in
 * dark / light / black / wallpaper colours (docs/screens/m1-theme-*.png). Asserts the nav dock is fully opaque.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class M1ThemeRenderTest {
    private static final String DARK = "w412dp-h915dp-night-xhdpi";
    private static final String LIGHT = "w412dp-h915dp-notnight-xhdpi";

    @Test @Config(sdk = 35, qualifiers = DARK) public void dark() throws Exception { render("dark", 0, false); }
    @Test @Config(sdk = 35, qualifiers = LIGHT) public void light() throws Exception { render("light", 0, false); }
    @Test @Config(sdk = 35, qualifiers = DARK) public void black() throws Exception {
        render("black", R.style.Theme_MusicPlayer_Black, false);
    }
    @Test @Config(sdk = 35, qualifiers = DARK) public void dynamicDark() throws Exception { render("dynamic-dark", 0, true); }
    @Test @Config(sdk = 35, qualifiers = LIGHT) public void dynamicLight() throws Exception { render("dynamic-light", 0, true); }
    @Test @Config(sdk = 35, qualifiers = "fa-ldrtl-" + DARK) public void persian() throws Exception { render("fa-dark", 0, false); }

    private void render(String name, int themeRes, boolean dynamic) throws Exception {
        Activity a = Robolectric.buildActivity(WidgetGalleryActivity.class).setup().get();
        ContextThemeWrapper c = new ContextThemeWrapper(a, themeRes != 0 ? themeRes : R.style.Theme_MusicPlayer);
        if (dynamic) c.getTheme().applyStyle(R.style.ThemeOverlay_Mowj_Dynamic, true);
        float d = c.getResources().getDisplayMetrics().density;
        int w = Math.round(412 * d);

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(16 * d);
        col.setPadding(pad, pad, pad, pad);
        boolean fa = c.getResources().getConfiguration().getLocales().get(0).getLanguage().equals("fa");
        String[][] roles = fa
                ? new String[][] {{"DISPLAY", "موج، موسیقی شب"}, {"HEADLINE", "تازه‌ها"}, {"TITLE", "شب‌های تهران"},
                    {"LABEL", "آهنگ‌ها"}, {"BODY", "پخش از کتابخانه"}, {"NUMERIC", "۳:۴۵ / ۱۲:۰۸"}}
                : new String[][] {{"DISPLAY", "Mowj nights"}, {"HEADLINE", "Recently added"}, {"TITLE", "Without You"},
                    {"LABEL", "Songs  Albums"}, {"BODY", "Playing from your library"}, {"NUMERIC", "03:45 / 12:08"}};
        float[] sizes = {34, 26, 18, 14, 15, 15};
        for (int i = 0; i < roles.length; i++) {
            TextView t = new TextView(c);
            TypeRoles.apply(t, TypeRoles.Role.valueOf(roles[i][0]));
            t.setTextSize(sizes[i]);
            t.setTextColor(Tokens.onSurface(c));
            t.setText(roles[i][1] + (fa ? "" : "   · " + roles[i][0].toLowerCase()));
            col.addView(t);
        }
        // Colour roles strip.
        LinearLayout swatches = new LinearLayout(c);
        int[] attrs = {R.attr.mpPrimary, R.attr.mpPrimaryContainer, R.attr.mpSecondaryContainer, R.attr.mpTertiary,
                R.attr.mpSurfaceContainer, R.attr.mpSurfaceContainerHigh, R.attr.mpSurfaceFloating, R.attr.mpOutline};
        for (int attr : attrs) {
            View v = new View(c);
            v.setBackgroundColor(Tokens.color(c, attr));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Math.round(28 * d), 1f);
            lp.setMargins(Math.round(2 * d), Math.round(12 * d), Math.round(2 * d), Math.round(12 * d));
            swatches.addView(v, lp);
        }
        col.addView(swatches);

        // Floating chrome over a busy backdrop: nav, mini-player, chip.
        FrameLayout stage = new FrameLayout(c) {
            final Paint p = new Paint();
            @Override protected void dispatchDraw(Canvas canvas) {
                p.setShader(new LinearGradient(0, 0, 40 * d, 40 * d, 0xFFFF3D7F, 0xFF22E0C8, Shader.TileMode.MIRROR));
                canvas.drawRect(0, 0, getWidth(), getHeight(), p);
                super.dispatchDraw(canvas);
            }
        };
        stage.setWillNotDraw(false);
        LinearLayout chrome = new LinearLayout(c);
        chrome.setOrientation(LinearLayout.VERTICAL);
        chrome.setPadding(pad, pad, pad, pad);
        ChipView chip = new ChipView(c);
        chip.setText(fa ? "آلبوم‌ها" : "Albums");
        chip.setCheckable(true);
        chip.setChecked(true);
        chrome.addView(chip, new LinearLayout.LayoutParams(-2, -2));
        MiniPlayerView mini = new MiniPlayerView(c);
        mini.bind(fa ? SampleData.TRACKS.stream().filter(t -> ScriptFont.hasArabicScript(t.title())).findFirst()
                .orElse(SampleData.TRACKS.get(0)) : SampleData.TRACKS.get(0)); // first Persian track, not a fixed index
        mini.setPlaying(true);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-1, -2);
        mlp.topMargin = pad;
        chrome.addView(mini, mlp);
        FloatingNavView nav = new FloatingNavView(c);
        nav.addDefaultItems();
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-2, -2);
        nlp.topMargin = Math.round(8 * d);
        chrome.addView(nav, nlp);
        stage.addView(chrome);
        col.addView(stage);

        col.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        col.layout(0, 0, w, col.getMeasuredHeight());
        Bitmap bmp = Bitmap.createBitmap(w, col.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(Tokens.color(c, R.attr.mpBackground));
        col.draw(canvas);

        // Opacity: the nav's background colour is fully opaque and a point inside the dock (between items) matches it.
        int floating = Tokens.color(c, R.attr.mpSurfaceFloating);
        assertEquals(0xFF, Color.alpha(floating));
        int[] loc = {0, 0};
        int nx = Math.round(nav.getLeft() + chrome.getLeft()) + nav.getWidth() - Math.round(12 * d);
        int ny = stage.getTop() + chrome.getTop() + nav.getTop() + nav.getHeight() / 2;
        int px = bmp.getPixel(nx, ny);
        assertTrue(name + ": dock lets the backdrop through: " + Integer.toHexString(px),
                Math.abs(Color.red(px) - Color.red(floating)) < 6 && Math.abs(Color.green(px) - Color.green(floating)) < 6);

        File out = new File(new File("../docs/screens"), "m1-theme-" + name + ".png");
        out.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(out)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
        assertTrue(out.length() > 0);
    }
}
