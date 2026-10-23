package com.mhrsdev.musicplayer;

import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;

import com.mhrsdev.musicplayer.ui.nav.ScreenNavigator;
import com.mhrsdev.musicplayer.ui.theme.Tokens;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

import java.io.File;
import java.io.FileOutputStream;


/**
 * Debug-only visual check: renders the real shell (fake data) per tab to docs/screens/p3-*.png with native graphics,
 * so screens can be compared against docs/design/png without a device.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(application = FakeRenderApp.class)
public class ScreenRenderTest {

    private static final String DARK = "w412dp-h915dp-night-xhdpi";
    private static final String LIGHT = "w412dp-h915dp-notnight-xhdpi";

    @Test @Config(sdk = 35, qualifiers = DARK)
    public void homeDark() throws Exception { render(ScreenNavigator.Tab.HOME, "home-dark"); }

    @Test @Config(sdk = 35, qualifiers = LIGHT)
    public void homeLight() throws Exception { render(ScreenNavigator.Tab.HOME, "home-light"); }

    @Test @Config(sdk = 35, qualifiers = DARK)
    public void libraryDark() throws Exception { render(ScreenNavigator.Tab.LIBRARY, "library-dark"); }

    @Test @Config(sdk = 35, qualifiers = DARK)
    public void searchDark() throws Exception { render(ScreenNavigator.Tab.SEARCH, "search-dark"); }

    @Test @Config(sdk = 35, qualifiers = "fa-" + DARK)
    public void homeFaDark() throws Exception { render(ScreenNavigator.Tab.HOME, "home-fa-dark"); }

    @Test @Config(sdk = 35, qualifiers = DARK)
    public void artistsDark() throws Exception { render(ScreenNavigator.Tab.LIBRARY, "artists-dark", R.id.hub_artists); }

    private void render(ScreenNavigator.Tab tab, String name) throws Exception {
        render(tab, name, 0);
    }

    private void render(ScreenNavigator.Tab tab, String name, int clickId) throws Exception {
        android.app.Application app = org.robolectric.RuntimeEnvironment.getApplication();
        org.robolectric.shadows.ShadowApplication sa = org.robolectric.shadow.api.Shadow.extract(app);
        sa.grantPermissions(android.Manifest.permission.READ_MEDIA_AUDIO,
                android.Manifest.permission.READ_EXTERNAL_STORAGE);
        app.getSharedPreferences("nav", android.content.Context.MODE_PRIVATE).edit().putBoolean("perm_done", true).commit();
        ActivityController<MainActivity> ctl = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity a = ctl.get();
        settle();
        if (tab != ScreenNavigator.Tab.HOME) {
            a.openTab(tab);
            settle();
        }
        if (clickId != 0) {
            a.findViewById(clickId).performClick();
            settle();
        }
        View root = a.getWindow().getDecorView();
        int w = 412 * 2, h = 915 * 2;
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, w, h);
        settle();
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(Tokens.color(a, R.attr.mpBackground));
        root.draw(c);
        File dir = new File("../docs/screens");
        if (!dir.isDirectory()) dir = new File("build");
        File out = new File(dir, "p3-" + name + ".png");
        try (FileOutputStream fos = new FileOutputStream(out)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
        assertTrue(out.length() > 0);
    }

    /** Lets the io executor and the main looper drain (fake repositories post back to main). */
    private static void settle() throws InterruptedException {
        for (int i = 0; i < 6; i++) {
            ((org.robolectric.shadows.ShadowLooper) org.robolectric.shadow.api.Shadow.extract(Looper.getMainLooper())).idle();
            Thread.sleep(60);
        }
        ((org.robolectric.shadows.ShadowLooper) org.robolectric.shadow.api.Shadow.extract(Looper.getMainLooper())).idleFor(java.time.Duration.ofMillis(500));
    }
}
