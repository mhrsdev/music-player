package com.mhrsdev.musicplayer;

import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import com.mhrsdev.musicplayer.ui.nav.ScreenNavigator;
import com.mhrsdev.musicplayer.ui.theme.Tokens;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowLooper;

/**
 * U1 visual checks (settings, search, shell) at phone / large-phone / tablet widths and at font scale 1.3, written
 * to docs/screens/u1-*.png. Same approach as {@link ScreenRenderTest}: real shell, fake data, native graphics.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(application = FakeRenderApp.class, sdk = 35)
public class U1RenderTest {

    @Test @Config(qualifiers = "w411dp-h2200dp-night-xhdpi")
    public void settings411() throws Exception { render(ScreenNavigator.Tab.SETTINGS, null, "settings-411-dark", 411, 2200); }

    @Test @Config(qualifiers = "w360dp-h2300dp-notnight-xhdpi")
    public void settings360Light() throws Exception { render(ScreenNavigator.Tab.SETTINGS, null, "settings-360-light", 360, 2300); }

    @Test @Config(qualifiers = "w411dp-h2600dp-night-xhdpi", fontScale = 1.3f)
    public void settingsFont13() throws Exception { render(ScreenNavigator.Tab.SETTINGS, null, "settings-411-font1.3", 411, 2600); }

    @Test @Config(qualifiers = "fa-w411dp-h2200dp-night-xhdpi")
    public void settingsFa() throws Exception { render(ScreenNavigator.Tab.SETTINGS, null, "settings-411-fa", 411, 2200); }

    @Test @Config(qualifiers = "w800dp-h1280dp-night-mdpi")
    public void settings800() throws Exception { render(ScreenNavigator.Tab.SETTINGS, null, "settings-800-dark", 800, 1280); }

    @Test @Config(qualifiers = "w411dp-h915dp-night-xhdpi")
    public void searchPrompt411() throws Exception { render(ScreenNavigator.Tab.SEARCH, null, "search-prompt-411", 411, 915); }

    @Test @Config(qualifiers = "w411dp-h915dp-night-xhdpi")
    public void searchResults411() throws Exception { render(ScreenNavigator.Tab.SEARCH, "ra", "search-results-411", 411, 915); }

    @Test @Config(qualifiers = "w360dp-h800dp-night-xhdpi")
    public void searchNoResults360() throws Exception { render(ScreenNavigator.Tab.SEARCH, "neffexx grateful", "search-none-360", 360, 800); }

    @Test @Config(qualifiers = "w800dp-h1280dp-night-mdpi")
    public void search800() throws Exception { render(ScreenNavigator.Tab.SEARCH, "ra", "search-results-800", 800, 1280); }

    @Test @Config(qualifiers = "w800dp-h1280dp-night-mdpi")
    public void home800() throws Exception { render(ScreenNavigator.Tab.HOME, null, "home-800", 800, 1280); }

    @Test @Config(qualifiers = "w891dp-h411dp-night-xhdpi")
    public void homeLandscape() throws Exception { render(ScreenNavigator.Tab.HOME, null, "home-land", 891, 411); }

    @Test @Config(qualifiers = "w360dp-h800dp-night-xhdpi", fontScale = 1.3f)
    public void home360Font13() throws Exception { render(ScreenNavigator.Tab.HOME, null, "home-360-font1.3", 360, 800); }

    private void render(ScreenNavigator.Tab tab, String query, String name, int wDp, int hDp) throws Exception {
        android.app.Application app = org.robolectric.RuntimeEnvironment.getApplication();
        org.robolectric.shadows.ShadowApplication sa = Shadow.extract(app);
        sa.grantPermissions(android.Manifest.permission.READ_MEDIA_AUDIO,
                android.Manifest.permission.READ_EXTERNAL_STORAGE);
        app.getSharedPreferences("nav", android.content.Context.MODE_PRIVATE).edit().putBoolean("perm_done", true).commit();
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        settle();
        if (tab != ScreenNavigator.Tab.HOME) {
            a.openTab(tab);
            settle();
        }
        if (query != null) {
            EditText q = a.findViewById(R.id.query);
            q.setText(query);
            settle();
            q.clearFocus();
            settle();
        }
        View root = a.getWindow().getDecorView();
        float d = a.getResources().getDisplayMetrics().density;
        int w = Math.round(wDp * d), h = Math.round(hDp * d);
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, w, h);
        settle();
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, w, h);
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(Tokens.color(a, R.attr.mpBackground));
        root.draw(c);
        File dir = new File("../docs/screens");
        if (!dir.isDirectory()) dir = new File("build");
        File out = new File(dir, "u1-" + name + ".png");
        try (FileOutputStream fos = new FileOutputStream(out)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
        assertTrue(out.length() > 0);
    }

    private static void settle() throws InterruptedException {
        for (int i = 0; i < 6; i++) {
            ((ShadowLooper) Shadow.extract(Looper.getMainLooper())).idle();
            Thread.sleep(60);
        }
        ((ShadowLooper) Shadow.extract(Looper.getMainLooper())).idleFor(java.time.Duration.ofMillis(500));
    }
}
